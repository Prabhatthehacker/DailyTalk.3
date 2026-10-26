package com.dailytalk.app

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * All Realtime Database access. The data layout matches the website exactly (see database.rules.json):
 *  users/{uid}, phoneIndex/{number}, contacts/{uid}, cleared/{uid}, userChats/{uid}/{cid},
 *  conversations/{cid}/{meta,members,admins,messages}, statuses, statusFeed, statusViews, calls, callInbox.
 */
object Repo {
    private const val TAG = "Repo"
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val rdb: FirebaseDatabase get() = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
    private fun ref(path: String = ""): DatabaseReference = if (path.isEmpty()) rdb.reference else rdb.getReference(path)

    // ------------------------------------------------------------ helpers
    private fun Query.values(): Flow<DataSnapshot> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
            override fun onCancelled(error: DatabaseError) { Log.w(TAG, "listener cancelled: ${error.message}") }
        }
        addValueEventListener(l)
        awaitClose { removeEventListener(l) }
    }

    private fun DataSnapshot.str(key: String): String? = child(key).getValue(String::class.java)
    private fun DataSnapshot.num(key: String): Long? = child(key).getValue(Long::class.java)
    private fun DataSnapshot.keys(key: String): List<String> = child(key).children.mapNotNull { it.key }
    private fun isDenied(e: Exception) = e.message?.contains("ermission denied", ignoreCase = true) == true

    class UserFacingException(message: String) : Exception(message)
    private fun fail(msg: String): Nothing = throw UserFacingException(msg)

    // ------------------------------------------------------------ auth
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    fun authMessage(e: Throwable): String = when {
        e is FirebaseNetworkException -> "Cannot reach the server. Check your connection."
        e is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address is not valid."
            "ERROR_WEAK_PASSWORD" -> "Choose a password with at least 6 characters."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Sign in instead."
            "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD" -> "Email or password is incorrect."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a few minutes and try again."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "For safety, sign out, sign in again, then retry."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is not enabled in the Firebase Console."
            else -> "Something went wrong. Please try again."
        }
        e is UserFacingException -> e.message ?: "Something went wrong. Please try again."
        else -> "Something went wrong. Please try again."
    }

    suspend fun register(name: String, email: String, password: String) {
        if (name.isBlank()) fail("Enter your name.")
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        ensureAccount(user.uid, name.trim())
    }
    suspend fun login(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun resetPassword(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }
    fun logout() { profileCache.clear(); auth.signOut() }

    // ------------------------------------------------------------ profile & assigned number
    fun profile(uid: String): Flow<Profile?> = ref("users/$uid").values().map { s ->
        if (s.exists()) Profile(uid, s.str("displayName") ?: "Me", s.str("phone")) else null
    }

    private val profileCache = HashMap<String, Profile>()
    suspend fun profiles(uids: Collection<String>): List<Profile> = uids.map { uid ->
        profileCache[uid] ?: runCatching {
            val s = ref("users/$uid").get().await()
            Profile(uid, s.str("displayName") ?: "Unknown", s.str("phone"))
        }.getOrElse { Profile(uid, "Unknown", null) }.also { profileCache[uid] = it }
    }

    /**
     * Gives the account its permanent Daily Talk number (only if it has none yet).
     * phoneIndex is create-only, so a taken number is refused and we retry with another random one.
     */
    suspend fun ensureAccount(uid: String, name: String): String {
        var last: Exception? = null
        repeat(12) {
            val existing = ref("users/$uid").get().await()   // re-read: another request may have assigned it
            existing.str("phone")?.let { return it }
            val number = ChatNumber.generate()
            try { ref("phoneIndex/$number").setValue(uid).await() }
            catch (e: Exception) { last = e; if (!isDenied(e)) throw e; return@repeat }
            try {
                val data: Map<String, Any> = if (existing.exists()) mapOf("phone" to number)
                    else mapOf("displayName" to name, "phone" to number, "createdAt" to ServerValue.TIMESTAMP)
                ref("users/$uid").updateChildren(data).await()
                profileCache.remove(uid); return number
            } catch (e: Exception) {
                last = e
                runCatching { ref("phoneIndex/$number").removeValue().await() }   // give the unused claim back
                if (!isDenied(e)) throw e
            }
        }
        throw last ?: UserFacingException("Could not create your number. Please try again.")
    }

    suspend fun updateName(uid: String, name: String) {
        if (name.isBlank()) fail("Enter your name.")
        ref("users/$uid").updateChildren(mapOf("displayName" to name.trim())).await()
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
        profileCache.remove(uid)
    }

    // ------------------------------------------------------------ contacts
    fun contacts(uid: String): Flow<List<Contact>> = ref("contacts/$uid").values().map { s ->
        s.children.mapNotNull { c -> c.key?.let { Contact(it, c.getValue(String::class.java) ?: it) } }
    }
    suspend fun saveContact(uid: String, number: String, name: String) {
        if (name.isBlank()) fail("Enter a name.")
        ref("contacts/$uid/$number").setValue(name.trim()).await()
    }
    suspend fun deleteContact(uid: String, number: String) { ref("contacts/$uid/$number").removeValue().await() }

    // ------------------------------------------------------------ conversations
    fun conversations(uid: String): Flow<List<Conversation>> = ref("userChats/$uid").values().map { s ->
        s.children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            Conversation(
                id = id, type = c.str("type") ?: "direct", members = c.keys("members"), admins = c.keys("admins"), name = c.str("name"),
                lastText = c.child("last").str("text"), lastSender = c.child("last").str("senderId"),
                updatedAt = c.num("updatedAt") ?: 0L, unread = mapOf(uid to (c.num("unread") ?: 0L)),
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun cleared(uid: String): Flow<Map<String, Long>> = ref("cleared/$uid").values().map { s ->
        s.children.mapNotNull { c -> c.key?.let { k -> c.getValue(Long::class.java)?.let { k to it } } }.toMap()
    }

    private fun entryFor(type: String, members: List<String>, admins: List<String>, name: String?): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "type" to type, "members" to members.associateWith { true }, "updatedAt" to ServerValue.TIMESTAMP, "unread" to 0,
        )
        if (name != null) m["name"] = name
        if (admins.isNotEmpty()) m["admins"] = admins.associateWith { true }
        return m
    }

    /** Creates a conversation (in steps, because each step is checked by the security rules) unless you are already in it. */
    private suspend fun ensureConversation(cid: String, type: String, members: List<String>, me: String, name: String? = null) {
        val mine = runCatching { ref("conversations/$cid/members/$me").get().await().getValue(Boolean::class.java) == true }.getOrDefault(false)
        if (!mine) {
            val meta = mutableMapOf<String, Any>("type" to type, "createdBy" to me, "createdAt" to ServerValue.TIMESTAMP)
            if (name != null) meta["name"] = name
            ref("conversations/$cid/meta").setValue(meta).await()
            val base = mutableMapOf<String, Any?>()
            members.forEach { base["conversations/$cid/members/$it"] = true }
            if (type == "group") base["conversations/$cid/admins/$me"] = true
            ref().updateChildren(base).await()
            val entries = mutableMapOf<String, Any?>()
            members.forEach { entries["userChats/$it/$cid"] = entryFor(type, members, if (type == "group") listOf(me) else emptyList(), name) }
            ref().updateChildren(entries).await()
        }
        runCatching { ref("cleared/$me/$cid").removeValue().await() }
    }

    suspend fun startChatWithNumber(me: Profile, raw: String): String {
        val number = ChatNumber.normalize(raw) ?: fail("Enter a 10-digit Daily Talk number.")
        if (number == me.phone) fail("That is your own number.")
        val idx = ref("phoneIndex/$number").get().await()
        val peer = idx.getValue(String::class.java) ?: fail("No Daily Talk account has this number. Check it and try again.")
        val cid = "d_" + listOf(me.uid, peer).sorted().joinToString("_")
        ensureConversation(cid, "direct", listOf(me.uid, peer), me.uid)
        return cid
    }

    data class Resolved(val uids: List<String>, val missing: List<String>)
    suspend fun resolveNumbers(numbers: List<String>, self: String): Resolved {
        val uids = mutableListOf<String>(); val missing = mutableListOf<String>()
        for (raw in numbers.map { it.trim() }.filter { it.isNotEmpty() }) {
            val number = ChatNumber.normalize(raw)
            if (number == null) { missing += raw; continue }
            val uid = ref("phoneIndex/$number").get().await().getValue(String::class.java)
            if (uid == null) missing += raw else if (uid != self && uid !in uids) uids += uid
        }
        return Resolved(uids, missing)
    }

    suspend fun createGroup(me: Profile, name: String, numbers: List<String>): Pair<String, List<String>> {
        if (name.isBlank()) fail("Enter a group name.")
        val r = resolveNumbers(numbers, me.uid)
        val id = ref("conversations").push().key!!
        ensureConversation(id, "group", listOf(me.uid) + r.uids, me.uid, name.trim())
        return id to r.missing
    }

    suspend fun addMembers(c: Conversation, numbers: List<String>, self: String): Pair<Int, List<String>> {
        val r = resolveNumbers(numbers, self)
        val add = r.uids.filter { it !in c.members }
        if (add.isNotEmpty()) {
            val grant = mutableMapOf<String, Any?>()
            add.forEach { grant["conversations/${c.id}/members/$it"] = true }
            ref().updateChildren(grant).await()                              // step 1: an admin adds them
            val all = c.members + add
            val entries = mutableMapOf<String, Any?>()
            add.forEach { entries["userChats/$it/${c.id}"] = entryFor("group", all, c.admins, c.name) }
            c.members.forEach { m -> add.forEach { n -> entries["userChats/$m/${c.id}/members/$n"] = true } }
            ref().updateChildren(entries).await()                            // step 2: everyone's chat list
        }
        return add.size to r.missing
    }
    suspend fun renameGroup(c: Conversation, name: String) {
        if (name.isBlank()) fail("Enter a group name.")
        val u = mutableMapOf<String, Any?>("conversations/${c.id}/meta/name" to name.trim())
        c.members.forEach { u["userChats/$it/${c.id}/name"] = name.trim() }
        ref().updateChildren(u).await()
    }
    suspend fun leaveGroup(c: Conversation, uid: String) {
        val wasAdmin = uid in c.admins
        val u = mutableMapOf<String, Any?>("conversations/${c.id}/members/$uid" to null, "userChats/$uid/${c.id}" to null)
        if (wasAdmin) u["conversations/${c.id}/admins/$uid"] = null
        c.members.filter { it != uid }.forEach { m ->
            u["userChats/$m/${c.id}/members/$uid"] = null
            if (wasAdmin) u["userChats/$m/${c.id}/admins/$uid"] = null
        }
        ref().updateChildren(u).await()
    }
    /** Per-user: hides history for you only. Saved contacts and other members are untouched. */
    suspend fun clearConversation(uid: String, cid: String) { ref("cleared/$uid/$cid").setValue(ServerValue.TIMESTAMP).await() }
    suspend fun markConversationRead(cid: String, uid: String) { ref("userChats/$uid/$cid/unread").setValue(0).await() }

    // ------------------------------------------------------------ messages (text, GIFs, photos, videos)
    fun messages(cid: String, limit: Int, after: Long?): Flow<List<Message>> {
        var q: Query = ref("conversations/$cid/messages").orderByChild("createdAt")
        if (after != null) q = q.startAt((after + 1).toDouble())
        q = q.limitToLast(limit)
        return q.values().map { s ->
            s.children.mapNotNull { d ->
                val id = d.key ?: return@mapNotNull null
                Message(
                    id = id, senderId = d.str("senderId") ?: "", senderName = d.str("senderName") ?: "", kind = d.str("kind") ?: "text",
                    text = d.str("text"), url = d.str("url"), createdAt = d.num("createdAt") ?: System.currentTimeMillis(), readBy = d.keys("readBy"),
                )
            }.sortedBy { it.createdAt }
        }
    }

    fun newMessageId(cid: String): String = ref("conversations/$cid/messages").push().key!!

    data class Outgoing(
        val id: String, val kind: String, val text: String? = null, val url: String? = null,
        val uri: android.net.Uri? = null, val info: Media.Info? = null,
    )

    /** Returns only after the server accepted the write; throws otherwise so the UI shows "failed". */
    suspend fun sendMessage(c: Conversation, me: Profile, m: Outgoing) {
        val body = mutableMapOf<String, Any?>(
            "senderId" to me.uid, "senderName" to me.displayName, "kind" to m.kind,
            "createdAt" to ServerValue.TIMESTAMP, "readBy" to mapOf(me.uid to true),
        )
        if (m.kind == "text") {
            val t = m.text?.trim().orEmpty()
            if (t.isEmpty() || t.length > 4000) fail("Messages must be 1–4000 characters.")
            body["text"] = t
        } else {
            if (m.kind != "gif" && (m.url == null || !Media.hostedUrl.matches(m.url))) fail("The photo or video is not uploaded yet.")
            body["url"] = m.url
        }
        val preview = when (m.kind) { "gif" -> "GIF"; "image" -> "Photo"; "video" -> "Video"; else -> (body["text"] as String).take(120) }
        val u = mutableMapOf<String, Any?>("conversations/${c.id}/messages/${m.id}" to body)
        val last = mapOf("text" to preview, "senderId" to me.uid)
        for (member in c.members) {
            u["userChats/$member/${c.id}/last"] = last
            u["userChats/$member/${c.id}/updatedAt"] = ServerValue.TIMESTAMP
            if (member != me.uid) u["userChats/$member/${c.id}/unread"] = ServerValue.increment(1)
        }
        ref().updateChildren(u).await()
    }

    suspend fun markMessagesRead(cid: String, uid: String, msgs: List<Message>) {
        val todo = msgs.filter { it.senderId != uid && uid !in it.readBy }.take(400)
        if (todo.isEmpty()) return
        ref().updateChildren(todo.associate { "conversations/$cid/messages/${it.id}/readBy/$uid" to (true as Any?) }).await()
    }

    // ------------------------------------------------------------ status (text only, 24 hours)
    /** Reads your feed (ids), loads each status (the rules refuse expired ones) and tidies up expired entries. */
    fun statuses(uid: String): Flow<List<Status>> {
        val cache = HashMap<String, Status?>()
        return ref("statusFeed/$uid").values().map { feed ->
            val now = System.currentTimeMillis()
            val ids = mutableSetOf<String>()
            for (e in feed.children) {
                val sid = e.key ?: continue
                val expires = e.num("expiresAt") ?: 0L
                if (expires <= now) {
                    runCatching { ref("statusFeed/$uid/$sid").removeValue().await() }
                    if (e.str("authorId") == uid) runCatching { ref("statuses/$sid").removeValue().await() }
                    cache.remove(sid); continue
                }
                ids += sid
                if (cache.containsKey(sid)) continue
                try {
                    val s = ref("statuses/$sid").get().await()
                    cache[sid] = if (s.exists()) Status(
                        sid, s.str("authorId") ?: "", s.str("authorName") ?: "", "text", s.str("text"),
                        s.num("createdAt") ?: 0L, s.num("expiresAt") ?: 0L, s.keys("viewers"),
                    ) else null
                    if (!s.exists()) runCatching { ref("statusFeed/$uid/$sid").removeValue().await() }
                } catch (ex: Exception) {
                    if (isDenied(ex)) { cache[sid] = null; runCatching { ref("statusFeed/$uid/$sid").removeValue().await() } }
                }
            }
            cache.entries.filter { it.key in ids }.mapNotNull { it.value }.filter { it.expiresAt > System.currentTimeMillis() }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun publishStatus(me: Profile, viewers: List<String>, text: String) {
        val t = text.trim()
        if (t.isEmpty()) fail("Write something to share.")
        val sid = ref("statuses").push().key!!
        val audience = viewers.filter { it != me.uid }.distinct().take(500)
        val expiresAt = System.currentTimeMillis() + 24 * 3600_000L
        ref("statuses/$sid").setValue(mapOf(
            "authorId" to me.uid, "authorName" to me.displayName, "text" to t.take(700), "createdAt" to ServerValue.TIMESTAMP,
            "expiresAt" to expiresAt, "viewers" to audience.associateWith { true },
        )).await()
        val feed = mutableMapOf<String, Any?>()
        (listOf(me.uid) + audience).forEach { feed["statusFeed/$it/$sid"] = mapOf("authorId" to me.uid, "createdAt" to ServerValue.TIMESTAMP, "expiresAt" to expiresAt) }
        ref().updateChildren(feed).await()
    }
    suspend fun deleteStatus(s: Status) {
        ref().updateChildren(mapOf("statuses/${s.id}" to null, "statusViews/${s.id}" to null, "statusFeed/${s.authorId}/${s.id}" to null)).await()
    }
    suspend fun markStatusViewed(sid: String, uid: String) { runCatching { ref("statusViews/$sid/$uid").setValue(ServerValue.TIMESTAMP).await() } }
    fun statusViews(sid: String): Flow<Int> = ref("statusViews/$sid").values().map { it.childrenCount.toInt() }

    // ------------------------------------------------------------ account deletion
    /** Removes profile, number, contacts, chat list and the sign-in. Sent messages remain (see README). */
    suspend fun deleteAccount(profile: Profile) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        ref().updateChildren(mapOf("contacts/$uid" to null, "cleared/$uid" to null, "userChats/$uid" to null, "callInbox/$uid" to null)).await()
        profile.phone?.let { runCatching { ref("phoneIndex/$it").removeValue().await() } }
        ref("users/$uid").removeValue().await()
        user.delete().await()
    }

    // ------------------------------------------------------------ call signaling
    /** Incoming calls arrive in your private callInbox; each entry is read once and then removed. */
    fun incomingCalls(uid: String): Flow<List<CallDoc>> = callbackFlow {
        val l = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val id = snapshot.key ?: return
                ref("callInbox/$uid/$id").removeValue()
                launch {
                    runCatching {
                        val c = callDoc(ref("calls/$id").get().await())
                        if (c.callerId != uid && c.state == "ringing" && System.currentTimeMillis() - c.createdAt < 45_000) trySend(listOf(c))
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { Log.w(TAG, "call inbox cancelled: ${error.message}") }
        }
        val r = ref("callInbox/$uid")
        r.addChildEventListener(l)
        awaitClose { r.removeEventListener(l) }
    }
    fun callDoc(d: DataSnapshot): CallDoc {
        val caller = d.str("callerId") ?: ""; val callee = d.str("calleeId") ?: ""
        return CallDoc(d.key ?: "", caller, listOf(caller, callee), d.str("conversationId") ?: "", d.str("type") ?: "voice",
            d.str("state") ?: "", d.child("offer").str("type"), d.child("offer").str("sdp"), d.num("createdAt") ?: System.currentTimeMillis())
    }
    fun callRef(id: String): DatabaseReference = ref("calls/$id")
    fun candidates(id: String): DatabaseReference = ref("calls/$id/candidates")
    fun inbox(uid: String, callId: String): DatabaseReference = ref("callInbox/$uid/$callId")
    fun newCallId(): String = ref("calls").push().key!!
}
