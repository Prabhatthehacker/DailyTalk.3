package com.dailytalk.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val EMOJI = "😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😢 😭 😡 👍 👎 👏 🙏 💪 🎉 🔥 ❤️ 💛 🧡 ✨ 🌟 🎂 🍕 ☕ 🌅 📚 💻".split(" ")
private const val PAGE = 40

@Composable
fun ChatScreen(vm: AppViewModel, cid: String, onBack: () -> Unit, onAction: (ChatAction, Conversation) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val all by vm.allConversations.collectAsStateCompat(); val cleared by vm.cleared.collectAsStateCompat()
    val profile by vm.profile.collectAsStateCompat(); val contacts by vm.contacts.collectAsStateCompat(); val profiles by vm.profiles.collectAsStateCompat()
    val online by vm.online.collectAsStateCompat()
    val conv = all.firstOrNull { it.id == cid }
    val me = vm.user.value?.uid ?: return
    val after = cleared[cid]

    DisposableEffect(cid) { vm.openChatId = cid; onDispose { if (vm.openChatId == cid) vm.openChatId = null } }

    var pages by remember(cid) { mutableIntStateOf(1) }
    var server by remember(cid) { mutableStateOf<List<Message>>(emptyList()) }
    val outbox: SnapshotStateList<Message> = remember(cid) { emptyList<Message>().toMutableStateList() }
    val outgoing = remember(cid) { HashMap<String, Repo.Outgoing>() }
    var text by remember(cid) { mutableStateOf("") }
    var panel by remember(cid) { mutableStateOf<String?>(null) }

    LaunchedEffect(cid, pages, after) { Repo.messages(cid, PAGE * pages, after).collect { server = it } }
    val messages = remember(server, outbox.toList()) { server + outbox.filter { o -> server.none { it.id == o.id } } }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    LaunchedEffect(server, conv?.unread) {
        runCatching { Repo.markMessagesRead(cid, me, server) }
        if ((conv?.unread?.get(me) ?: 0L) > 0) runCatching { Repo.markConversationRead(cid, me) }
    }

    fun dispatch(id: String) {
        val c = conv ?: return; val p = profile ?: return; val first = outgoing[id] ?: return
        fun patch(f: (Message) -> Message) { val i = outbox.indexOfFirst { it.id == id }; if (i >= 0) outbox[i] = f(outbox[i]) }
        patch { it.copy(state = SendState.SENDING, progress = 0f) }
        scope.launch {
            try {
                var out = first
                // Photos/videos go to the file host first; the link is kept so a retry never uploads twice.
                if (out.uri != null && out.url == null) {
                    val link = withTimeout(20 * 60_000L) { Media.upload(ctx.contentResolver, out.uri!!, out.info!!) { pr -> patch { m -> m.copy(progress = pr) } } }
                    out = out.copy(url = link); outgoing[id] = out
                }
                withTimeout(30_000) { Repo.sendMessage(c, p, out) }
                outbox.removeAll { it.id == id }; outgoing.remove(id)
            } catch (e: Exception) {
                patch { it.copy(state = SendState.FAILED) }
                vm.say(if (e is TimeoutCancellationException) "Not sent in time. Check your connection and retry." else Repo.authMessage(e))
            }
        }
    }
    fun enqueue(out: Repo.Outgoing) {
        val p = profile ?: return
        val msg = Message(out.id, me, p.displayName, out.kind, out.text, out.url ?: out.uri?.toString(), System.currentTimeMillis(), listOf(me), SendState.SENDING)
        outgoing[out.id] = out; outbox.add(msg); dispatch(out.id)
    }
    fun newId() = Repo.newMessageId(cid)
    var noticeFor by remember(cid) { mutableStateOf<List<android.net.Uri>?>(null) }
    fun sendUris(uris: List<android.net.Uri>) = uris.forEach { u ->
        val info = Media.describe(ctx.contentResolver, u)
        val err = Media.validate(info.mime, info.size)
        val kind = Media.kind(info.mime)
        if (err != null || kind == null) vm.say("${info.name}: $err") else enqueue(Repo.Outgoing(newId(), kind, uri = u, info = info))
    }
    val prefs = remember { ctx.getSharedPreferences("prefs", 0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) { if (prefs.getBoolean("mediaNoticeAccepted", false)) sendUris(uris) else noticeFor = uris }
    }
    noticeFor?.let { pending ->
        AlertDialog(
            onDismissRequest = { noticeFor = null }, title = { Text("Photos and videos are public links") },
            text = { Text("Photos and videos are uploaded to a free public file host (catbox.moe), not to Daily Talk. Anyone who gets the link can open the file, and it cannot be deleted from here. Files up to ${Media.maxBytes / 1_000_000} MB are allowed. Only send media you are happy to have on a public site.") },
            confirmButton = { TextButton({ prefs.edit().putBoolean("mediaNoticeAccepted", true).apply(); noticeFor = null; sendUris(pending) }) { Text("Upload anyway") } },
            dismissButton = { TextButton({ noticeFor = null }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        val title = if (conv != null) remember(conv, contacts, profiles) { vm.title(conv) } else ""
        Row(Modifier.fillMaxWidth().background(Palette.Ivory).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chats") }
            Avatar(title, 38.dp, conv?.isGroup == true)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { conv?.let { onAction(if (it.isGroup) ChatAction.GROUP_INFO else ChatAction.MANAGE, it) } }) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (conv?.isGroup == true) Text("${conv.members.size} members", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            }
            val canCall = conv != null && !conv.isGroup && conv.members.size == 2
            IconButton({ conv?.let { onAction(ChatAction.VOICE, it) } }, enabled = canCall) { Icon(Icons.Default.Call, contentDescription = "Voice call") }
            TextButton({ conv?.let { onAction(ChatAction.VIDEO, it) } }, enabled = canCall) { Text("Video") }
            if (conv != null) ChatMenu(conv, vm, onAction)
        }
        if (!online) Text("You're offline.", Modifier.fillMaxWidth().background(Palette.Sand).padding(8.dp), color = Palette.BrownDeep)

        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Palette.Cream).padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (server.size >= PAGE * pages) item { TextButton({ pages++ }, Modifier.fillMaxWidth()) { Text("Load earlier messages") } }
            if (messages.isEmpty()) item { Text("No messages yet. Say hello.", Modifier.fillMaxWidth().padding(24.dp), color = Palette.Muted) }
            items(messages.size, key = { messages[it].id }) { i ->
                val m = messages[i]
                val newDay = i == 0 || java.text.DateFormat.getDateInstance().format(java.util.Date(m.createdAt)) != java.text.DateFormat.getDateInstance().format(java.util.Date(messages[i - 1].createdAt))
                if (newDay) Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(m.createdAt)), Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = Palette.Muted, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Bubble(m, mine = m.senderId == me, group = conv?.isGroup == true, onRetry = { dispatch(m.id) })
            }
        }

        if (panel == "emoji") LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxWidth().heightIn(max = 180.dp).background(Palette.Ivory).padding(8.dp)) {
            items(EMOJI) { e -> Text(e, Modifier.clickable { text += e }.padding(6.dp), style = MaterialTheme.typography.headlineSmall) }
        }
        if (panel == "gif") GifPanel { g -> enqueue(Repo.Outgoing(newId(), "gif", url = g.url)); panel = null }

        Row(Modifier.fillMaxWidth().background(Palette.Ivory).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            TextButton({ panel = if (panel == "emoji") null else "emoji" }, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp), modifier = Modifier.width(48.dp)) { Text("😊") }
            TextButton({ panel = if (panel == "gif") null else "gif" }, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp), modifier = Modifier.width(48.dp)) { Text("GIF", fontWeight = FontWeight.Bold) }
            TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp), modifier = Modifier.width(56.dp)) { Text("Media") }
            OutlinedTextField(text, { text = it.take(4000) }, Modifier.weight(1f), placeholder = { Text("Type a message") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
            IconButton({ if (text.isNotBlank()) { enqueue(Repo.Outgoing(newId(), "text", text = text)); text = "" } }, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = if (text.isNotBlank()) Palette.Brown else Palette.Muted)
            }
        }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean, group: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) Palette.Mine else Palette.Ivory).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!mine && group) Text(m.senderName, color = Palette.Brown, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            MessageBody(m)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatTime(m.createdAt), color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                if (mine) Text(when {
                    m.state == SendState.SENDING -> "…"; m.state == SendState.FAILED -> "!"
                    m.readBy.any { it != m.senderId } -> "✓✓"; else -> "✓"
                }, color = if (m.state == SendState.FAILED) Palette.Danger else Palette.Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (m.state == SendState.SENDING && m.progress in 0.01f..0.99f) LinearProgressIndicator(progress = { m.progress }, Modifier.fillMaxWidth(), color = Palette.Brown)
            if (m.state == SendState.FAILED) TextButton(onRetry) { Text("Not sent. Retry", color = Palette.Danger) }
        }
    }
}

@Composable
private fun gifLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember {
        ImageLoader.Builder(ctx).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }
}

@Composable
private fun MessageBody(m: Message) {
    val ctx = LocalContext.current; val loader = gifLoader()
    when {
        (m.kind == "gif" || m.kind == "image") && m.url != null ->
            AsyncImage(ImageRequest.Builder(ctx).data(m.url).crossfade(true).build(), if (m.kind == "gif") "GIF" else "Photo",
                Modifier.heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = m.kind == "image" && m.state == SendState.SENT) {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(m.url)))
                }, imageLoader = loader, contentScale = ContentScale.Fit)
        m.kind == "video" && m.url != null ->
            Row(Modifier.clickable(enabled = m.state == SendState.SENT) {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(m.url)))
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\uD83C\uDFAC"); Column { Text("Video", fontWeight = FontWeight.SemiBold); Text("Tap to play", color = Palette.Muted, style = MaterialTheme.typography.bodySmall) }
            }
        else -> Text(m.text.orEmpty())
    }
}

@Composable
private fun GifPanel(onPick: (Gif) -> Unit) {
    val loader = gifLoader()
    var q by remember { mutableStateOf("") }; var gifs by remember { mutableStateOf<List<Gif>>(emptyList()) }; var error by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (!Giphy.configured) return@LaunchedEffect
        kotlinx.coroutines.delay(350); error = false
        runCatching { Giphy.search(q) }.onSuccess { gifs = it }.onFailure { error = true }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Palette.Ivory).padding(8.dp)) {
        if (!Giphy.configured) { Text("GIF search needs a Giphy key. Set GIPHY_API_KEY and rebuild.", color = Palette.Muted); return@Column }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search GIFs") }, singleLine = true)
        if (error) Text("Could not load GIFs.", color = Palette.Danger)
        LazyVerticalGrid(GridCells.Adaptive(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(gifs, key = { it.id }) { g -> AsyncImage(g.preview, null, Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(g) }, imageLoader = loader, contentScale = ContentScale.Crop) }
        }
    }
}
