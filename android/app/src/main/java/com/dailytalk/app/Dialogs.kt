package com.dailytalk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun StartChatDialog(vm: AppViewModel, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val me by vm.profile.collectAsStateCompat()
    var number by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add a number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(number, { number = it }, label = { Text("Daily Talk number") }, placeholder = { Text("12345 67890") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Enter the 10-digit number the other person sees in their Settings. It shows as the contact's name until you save a name for it.",
                    color = Palette.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = Palette.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && number.isNotBlank() && me != null, onClick = {
                busy = true; error = null
                scope.launch {
                    try { onOpen(Repo.startChatWithNumber(me!!, number)); onDismiss() }
                    catch (e: Exception) { error = Repo.authMessage(e); busy = false }
                }
            }) { Text("Start chat") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NewGroupDialog(vm: AppViewModel, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val me by vm.profile.collectAsStateCompat()
    var name by remember { mutableStateOf("") }; var numbers by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add a group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Group name") }, singleLine = true)
                OutlinedTextField(numbers, { numbers = it }, label = { Text("Participants' numbers (one per line)") }, minLines = 3)
                error?.let { Text(it, color = Palette.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && me != null, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val (id, missing) = Repo.createGroup(me!!, name, numbers.split('\n', ',', ';'))
                        if (missing.isNotEmpty()) vm.say("Not added (no account with this number): ${missing.joinToString()}")
                        onOpen(id); onDismiss()
                    } catch (e: Exception) { error = Repo.authMessage(e); busy = false }
                }
            }) { Text("Create group") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Rename a saved contact, or save the number under a name ("Manage saved number"). */
@Composable
fun ContactDialog(vm: AppViewModel, c: Conversation, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val contacts by vm.contacts.collectAsStateCompat()
    val number = vm.peerNumber(c); val saved = number?.let { contacts[it] }
    var name by remember { mutableStateOf(saved?.name ?: "") }
    val me = vm.user.value?.uid ?: return
    if (number == null) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("OK") } },
            title = { Text("Contact") }, text = { Text("This person's number is not available, so there is nothing to save.") })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (saved != null) "Rename contact" else "Save number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(ChatNumber.format(number), color = Palette.Muted)
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                Text("To go back to showing the number, delete the saved number from the chat menu.", color = Palette.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch { try { Repo.saveContact(me, number, name); onDismiss() } catch (e: Exception) { vm.say(Repo.authMessage(e)) } }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun GroupInfoDialog(vm: AppViewModel, c: Conversation, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profiles by vm.profiles.collectAsStateCompat()
    val me = vm.user.value?.uid ?: return
    val admin = me in c.admins
    var name by remember { mutableStateOf(c.name ?: "") }; var numbers by remember { mutableStateOf("") }; var leave by remember { mutableStateOf(false) }
    fun run(block: suspend () -> Unit) { scope.launch { try { block() } catch (e: Exception) { vm.say(Repo.authMessage(e)) } } }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Group details") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (admin) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Group name") }, singleLine = true)
                    OutlinedButton({ run { Repo.renameGroup(c, name) } }, enabled = name.isNotBlank() && name != c.name) { Text("Rename") }
                } else Text(c.name ?: "Group", fontWeight = FontWeight.Bold)
                Text("${c.members.size} members", color = Palette.Muted)
                c.members.forEach { m ->
                    val p = profiles[m]; val label = if (m == me) "You" else p?.displayName ?: "Member"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(label, 34.dp)
                        Column(Modifier.weight(1f)) { Text(label); if (m != me && p?.phone != null) Text(ChatNumber.format(p.phone), color = Palette.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                        if (m in c.admins) Text("Admin", color = Palette.Brown, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
                if (admin) {
                    OutlinedTextField(numbers, { numbers = it }, label = { Text("Add participants (one number per line)") }, minLines = 2)
                    OutlinedButton({
                        run {
                            val (added, missing) = Repo.addMembers(c, numbers.split('\n', ',', ';'), me)
                            numbers = ""; vm.say("$added added" + if (missing.isNotEmpty()) ". No account with this number: ${missing.joinToString()}" else "")
                        }
                    }, enabled = numbers.isNotBlank()) { Text("Add member") }
                }
                OutlinedButton({ leave = true }) { Text("Leave group", color = Palette.Danger) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
    if (leave) Confirm("Leave this group?", "You will stop receiving messages from it. You cannot rejoin unless an admin adds you again.", "Leave group",
        onDismiss = { leave = false; onDismiss() }, onConfirm = { Repo.leaveGroup(c, me) }, onError = vm::say)
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsStateCompat(); val user by vm.user.collectAsStateCompat()
    val p = profile ?: return
    var name by remember { mutableStateOf(p.displayName) }
    var del by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().background(Palette.Sand, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your Daily Talk number", color = Palette.Muted, fontWeight = FontWeight.SemiBold)
                    Text(p.phone?.let { ChatNumber.format(it) } ?: "Creating…", color = Palette.BrownDeep, fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                    Text("This is your number from now on. It was given to you when you signed up and cannot be changed. Write it down, and share it so people can chat with you.",
                        color = Palette.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    OutlinedButton({
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Daily Talk number", p.phone ?: "")); vm.say("Number copied")
                    }, enabled = p.phone != null) { Text("Copy number") }
                }
                Text(user?.email ?: "", color = Palette.Muted)
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Your name") }, singleLine = true)
                Text("Privacy: your name and number are visible to anyone signed in who has your number, and to people you chat with.",
                    color = Palette.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onDismiss(); Repo.logout() }) { Text("Log out") }
                    OutlinedButton({ del = true }) { Text("Delete account", color = Palette.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { try { Repo.updateName(p.uid, name); vm.say("Saved"); onDismiss() } catch (e: Exception) { vm.say(Repo.authMessage(e)) } }
            }) { Text("Save name") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
    if (del) Confirm("Delete your account?", "This removes your profile, your number, saved contacts and sign-in. Messages you already sent stay in other people's chats. Your number stops working and cannot be recovered. This cannot be undone.",
        "Delete account", onDismiss = { del = false }, onConfirm = { Repo.deleteAccount(p) }, onError = vm::say)
}
