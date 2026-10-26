package com.dailytalk.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    vm: AppViewModel, onOpenChat: (String) -> Unit, onAction: (ChatAction, Conversation) -> Unit,
    onAddChat: () -> Unit, onAddGroup: () -> Unit, onSettings: () -> Unit, onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val conversations by vm.conversations.collectAsStateCompat()
    val online by vm.online.collectAsStateCompat()
    // Recompute titles when contacts/profiles change.
    val contacts by vm.contacts.collectAsStateCompat(); val profiles by vm.profiles.collectAsStateCompat()
    val me = vm.user.value?.uid ?: ""

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(30.dp)
            Text("Daily Talk", fontWeight = FontWeight.Bold, color = Palette.BrownDeep, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp).weight(1f))
            IconButton(onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            TextButton(onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Log out", color = Palette.BrownDeep, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!online) Text("You're offline. Messages will send when you reconnect.", Modifier.fillMaxWidth().background(Palette.Sand).padding(10.dp), color = Palette.BrownDeep)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("Chats") })
            FilterChip(tab == 1, { tab = 1 }, label = { Text("Status") })
            Box(Modifier.weight(1f))
            Box {
                androidx.compose.material3.Button({ addOpen = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Brown),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Default.Add, null); Text("Add")
                }
                DropdownMenu(addOpen, { addOpen = false }) {
                    DropdownMenuItem(text = { Text("Add a chat") }, onClick = { addOpen = false; onAddChat() })
                    DropdownMenuItem(text = { Text("Add a group") }, onClick = { addOpen = false; onAddGroup() })
                }
            }
        }
        if (tab == 1) { StatusScreen(vm); return@Column }

        OutlinedTextField(query, { query = it }, placeholder = { Text("Search chats") }, singleLine = true, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        val rows = remember(conversations, contacts, profiles, query) {
            conversations.map { it to vm.title(it) }.filter { it.second.contains(query.trim(), ignoreCase = true) }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "No chats yet. Use Add to start one with a phone number." else "No chats match your search.", color = Palette.Muted)
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.first.id }) { (c, title) ->
                val unread = c.unread[me] ?: 0L
                Row(Modifier.fillMaxWidth().clickable { onOpenChat(c.id) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(title, group = c.isGroup)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row {
                            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatTime(c.updatedAt), color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.lastText?.let { (if (c.lastSender == me) "You: " else "") + it } ?: "No messages yet", color = Palette.Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (unread > 0) Box(Modifier.background(Palette.Brown, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ChatMenu(c, vm, onAction)
                }
            }
        }
    }
}
