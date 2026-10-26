package com.dailytalk.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private fun <T> st(initial: T, f: kotlinx.coroutines.flow.Flow<T>): StateFlow<T> = f.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val user = st(Repo.auth.currentUser, Repo.authState())
    private val uid = user.map { it?.uid }.distinctUntilChanged()

    val profile = st<Profile?>(null, uid.flatMapLatest { if (it == null) flowOf(null) else Repo.profile(it) })
    val allConversations = st(emptyList<Conversation>(), uid.flatMapLatest { if (it == null) flowOf(emptyList()) else Repo.conversations(it) })
    val cleared = st(emptyMap<String, Long>(), uid.flatMapLatest { if (it == null) flowOf(emptyMap()) else Repo.cleared(it) })
    private val contactList = st(emptyList<Contact>(), uid.flatMapLatest { if (it == null) flowOf(emptyList()) else Repo.contacts(it) })
    val statuses = st(emptyList<Status>(), uid.flatMapLatest { if (it == null) flowOf(emptyList()) else Repo.statuses(it) })

    val contacts = st(emptyMap<String, Contact>(), contactList.map { l -> l.associateBy { it.number } })
    val conversations = st(emptyList<Conversation>(), combine(allConversations, cleared) { all, cl ->
        all.filter { c -> cl[c.id]?.let { c.updatedAt > it } ?: true }
    })
    val profiles = MutableStateFlow(emptyMap<String, Profile>())
    val online = MutableStateFlow(true)
    val toast = MutableStateFlow<String?>(null)
    var openChatId: String? = null
    /** Set by MainActivity: asks for microphone/camera permission, then runs the action. */
    var requestCallPermissions: ((Boolean, () -> Unit) -> Unit)? = null

    private val startedAt = System.currentTimeMillis()
    private val notified = HashMap<String, Long>()

    private var assigning = false

    init {
        // Make sure every signed-in account has its permanent Daily Talk number (also repairs an interrupted sign-up).
        viewModelScope.launch {
            combine(user, profile) { u, p -> u to p }.collect { (u, p) ->
                if (u != null && (p == null || p.phone == null) && !assigning) {
                    assigning = true
                    try { Repo.ensureAccount(u.uid, u.displayName ?: "Me") }
                    catch (e: Exception) { say("Could not create your Daily Talk number. Check your connection and reopen the app.") }
                    finally { assigning = false }
                }
            }
        }
        viewModelScope.launch {
            allConversations.map { l -> l.flatMap { it.members }.toSet() }.distinctUntilChanged()
                .collect { ids -> if (ids.isNotEmpty()) profiles.value = Repo.profiles(ids).associateBy { it.uid } }
        }
        // Local notifications while the process is alive.
        viewModelScope.launch {
            allConversations.collect { list ->
                val me = user.value?.uid ?: return@collect
                list.forEach { c ->
                    if (c.updatedAt > startedAt && c.lastSender != null && c.lastSender != me && (c.unread[me] ?: 0) > 0 &&
                        c.id != openChatId && (notified[c.id] ?: 0) < c.updatedAt) {
                        notified[c.id] = c.updatedAt
                        Notifier.message(ctx, c.id, title(c), c.lastText ?: "New message")
                    }
                }
            }
        }
        // Incoming calls.
        viewModelScope.launch {
            uid.flatMapLatest { if (it == null) emptyFlow() else Repo.incomingCalls(it) }.collect { calls ->
                val call = calls.firstOrNull() ?: return@collect
                val c = allConversations.value.firstOrNull { it.id == call.conversationId }
                CallManager.showIncoming(ctx, call, c?.let(::title) ?: "Incoming call")
            }
        }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true }
            override fun onLost(network: Network) { online.value = false }
        })
    }

    fun title(c: Conversation): String {
        val me = user.value?.uid ?: ""
        if (c.isGroup) return c.name ?: "Group"
        val peer = c.members.firstOrNull { it != me }?.let { profiles.value[it] }
        val number = peer?.phone
        if (number != null) return contacts.value[number]?.name ?: ChatNumber.format(number)
        return peer?.displayName ?: "Unknown"
    }
    fun peerNumber(c: Conversation): String? {
        if (c.isGroup) return null
        val me = user.value?.uid ?: ""
        return c.members.firstOrNull { it != me }?.let { profiles.value[it]?.phone }
    }
    fun say(msg: String) { toast.value = msg }
}
