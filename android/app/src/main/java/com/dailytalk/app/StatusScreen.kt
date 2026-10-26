package com.dailytalk.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val JOKES = listOf(
    "I told my computer I needed a break. Now it will not stop sending me vacation ads.",
    "Why do programmers prefer dark mode? Because light attracts bugs.",
    "There are 10 kinds of people: those who understand binary and those who do not.",
)

@Composable
fun StatusScreen(vm: AppViewModel) {
    val statuses by vm.statuses.collectAsStateCompat(); val conversations by vm.conversations.collectAsStateCompat()
    val me = vm.user.value?.uid ?: return
    var compose by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<List<Status>?>(null) }
    var deleting by remember { mutableStateOf<Status?>(null) }
    val live = statuses.filter { it.expiresAt > System.currentTimeMillis() }
    val mine = live.filter { it.authorId == me }
    val others = live.filter { it.authorId != me }.groupBy { it.authorId }.values.toList()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { androidx.compose.material3.Button({ compose = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Brown)) { Text("New status") } }
        item { Text("My status", fontWeight = FontWeight.Bold, color = Palette.BrownDeep) }
        if (mine.isEmpty()) item { Text("You have not shared a status in the last 24 hours.", color = Palette.Muted) }
        items(mine, key = { it.id }) { s -> StatusRow(vm, s, true, 1, { viewing = mine.sortedBy { it.createdAt } }, { deleting = s }) }
        item { Text("Recent updates", fontWeight = FontWeight.Bold, color = Palette.BrownDeep, modifier = Modifier.padding(top = 8.dp)) }
        if (others.isEmpty()) item { Text("When people you chat with post a status, it appears here for 24 hours.", color = Palette.Muted) }
        items(others, key = { it.first().authorId }) { g -> StatusRow(vm, g.first(), false, g.size, { viewing = g.sortedBy { it.createdAt } }, {}) }
    }
    if (compose) StatusComposer(vm, conversations, onDismiss = { compose = false })
    viewing?.let { StatusViewer(vm, it, onDismiss = { viewing = null }) }
    deleting?.let { s -> Confirm("Delete this status?", "It will disappear for everyone right away.", "Delete status",
        onDismiss = { deleting = null }, onConfirm = { Repo.deleteStatus(s) }, onError = vm::say) }
}

@Composable
private fun StatusRow(vm: AppViewModel, s: Status, mine: Boolean, count: Int, onOpen: () -> Unit, onDelete: () -> Unit) {
    var views by remember { mutableStateOf(0) }
    if (mine) LaunchedEffect(s.id) { Repo.statusViews(s.id).collect { views = it } }
    val left = ((s.expiresAt - System.currentTimeMillis()) / 3_600_000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(s.authorName)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text((if (mine) "You" else s.authorName) + if (count > 1) " ($count)" else "", fontWeight = FontWeight.SemiBold)
            Text( s.text.orEmpty() + " · " + formatTime(s.createdAt) + if (mine) " · $views viewed · ${left}h left" else "",
                color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mine) TextButton(onDelete) { Text("Delete", color = Palette.Danger) }
    }
}

@Composable
private fun StatusComposer(vm: AppViewModel, conversations: List<Conversation>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsStateCompat()
    var text by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val audience = conversations.filter { !it.isGroup }.flatMap { it.members }.toSet()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("New status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it.take(700) }, placeholder = { Text("What's on your mind?") }, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ text = JOKES.random() }) { Text("Add a joke") }
                }
                Text("Visible for 24 hours to the people you chat with.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank() && profile != null, onClick = {
                busy = true
                scope.launch {
                    try { Repo.publishStatus(profile!!, audience.toList(), text); onDismiss() }
                    catch (e: Exception) { vm.say(Repo.authMessage(e)); busy = false }
                }
            }) { Text(if (busy) "Posting…" else "Post status") }
        },
        dismissButton = { TextButton({ if (!busy) onDismiss() }) { Text("Cancel") } },
    )
}

@Composable
private fun StatusViewer(vm: AppViewModel, list: List<Status>, onDismiss: () -> Unit) {
    var i by remember { mutableStateOf(0) }
    val s = list.getOrNull(i) ?: return
    val me = vm.user.value?.uid ?: return
    LaunchedEffect(s.id) { if (s.authorId != me) Repo.markStatusViewed(s.id, me) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("${s.authorName} · ${formatTime(s.createdAt)}") },
        text = { Text(s.text.orEmpty(), style = MaterialTheme.typography.titleMedium) },
        confirmButton = { TextButton(onClick = { if (i < list.lastIndex) i++ else onDismiss() }) { Text(if (i < list.lastIndex) "Next" else "Close") } },
        dismissButton = { if (i > 0) TextButton({ i-- }) { Text("Previous") } },
    )
}
