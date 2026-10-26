package com.dailytalk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

object Palette {
    val Cream = Color(0xFFFBF1E1); val Ivory = Color(0xFFFFF9EE); val Sand = Color(0xFFF3E4CB); val Line = Color(0xFFEAD9BF)
    val Gold = Color(0xFFC58A47); val Brown = Color(0xFF9A5D24); val BrownDeep = Color(0xFF6F4018); val Orange = Color(0xFFF2A65A)
    val Ink = Color(0xFF3B2A1A); val Muted = Color(0xFF7D6650); val Danger = Color(0xFFB3321F); val Mine = Color(0xFFF6DCB4)
}

@Composable
fun DailytalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Brown, onPrimary = Color.White, secondary = Palette.Gold, background = Palette.Cream, onBackground = Palette.Ink,
            surface = Palette.Ivory, onSurface = Palette.Ink, surfaceVariant = Palette.Sand, onSurfaceVariant = Palette.Muted,
            outline = Palette.Line, error = Palette.Danger, primaryContainer = Palette.Sand, onPrimaryContainer = Palette.BrownDeep,
        ),
        typography = Typography(),
        content = content,
    )
}

@Composable
fun Avatar(name: String, size: Dp = 46.dp, group: Boolean = false) {
    val parts = name.filter { it.isLetterOrDigit() || it == ' ' }.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val text = if (group) "G" else if (parts.isEmpty()) "?" else (parts.first().take(1) + if (parts.size > 1) parts.last().take(1) else "").uppercase()
    val hue = 20f + (name.fold(0) { h, c -> (h * 31 + c.code) % 360 } % 40)
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.hsv(hue, 0.25f, 0.95f)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.hsv(hue, 0.7f, 0.35f), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp) }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Palette.Brown)) { Text(text) }

@Composable
fun Confirm(title: String, body: String, action: String, danger: Boolean = true, onDismiss: () -> Unit, onConfirm: suspend () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) }, text = { Text(body) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { onConfirm(); onDismiss() } catch (e: Exception) { onError(Repo.authMessage(e)); busy = false }
                }
            }) { Text(action, color = if (danger) Palette.Danger else Palette.Brown, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

enum class ChatAction { VOICE, VIDEO, RENAME, MANAGE, DELETE_CONTACT, DELETE_CONVERSATION, GROUP_INFO, LEAVE }

@Composable
fun ChatMenu(c: Conversation, vm: AppViewModel, onAction: (ChatAction, Conversation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val contacts by vm.contacts.collectAsStateCompat()
    val number = vm.peerNumber(c)
    val saved = number != null && contacts.containsKey(number)
    val canCall = !c.isGroup && c.members.size == 2
    fun item(a: ChatAction, label: String, enabled: Boolean = true, danger: Boolean = false) = @Composable {
        DropdownMenuItem(
            text = { Text(label, color = if (danger) Palette.Danger else Palette.Ink) }, enabled = enabled,
            onClick = { open = false; onAction(a, c) },
        )
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Chat menu") }
        DropdownMenu(open, { open = false }) {
            item(ChatAction.VOICE, "Voice call", canCall)()
            item(ChatAction.VIDEO, "Video call", canCall)()
            if (!c.isGroup) {
                item(ChatAction.RENAME, "Rename contact", saved)()
                item(ChatAction.MANAGE, if (saved) "Manage saved number" else "Save number", number != null)()
                item(ChatAction.DELETE_CONTACT, "Delete saved number", saved)()
            } else {
                item(ChatAction.GROUP_INFO, "Group details")()
                item(ChatAction.LEAVE, "Leave group", danger = true)()
            }
            item(ChatAction.DELETE_CONVERSATION, "Delete conversation", danger = true)()
        }
    }
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    this.collectAsStateWithLifecycle()

fun formatTime(ms: Long): String {
    val now = java.util.Calendar.getInstance(); val d = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        now.get(java.util.Calendar.YEAR) == d.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == d.get(java.util.Calendar.DAY_OF_YEAR) ->
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        now.get(java.util.Calendar.DAY_OF_YEAR) - d.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
    }
}
