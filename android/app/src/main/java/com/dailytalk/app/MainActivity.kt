package com.dailytalk.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailytalkTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.Cream) { Root(vm) }
            }
        }
    }
}

private sealed interface Dialog {
    data object Start : Dialog
    data object Group : Dialog
    data object Settings : Dialog
    data object Logout : Dialog
    data class Contact(val c: Conversation) : Dialog
    data class Info(val c: Conversation) : Dialog
    data class DeleteContact(val c: Conversation) : Dialog
    data class DeleteConversation(val c: Conversation) : Dialog
    data class Leave(val c: Conversation) : Dialog
}

@Composable
private fun Root(vm: AppViewModel) {
    val ctx = LocalContext.current
    val user by vm.user.collectAsStateCompat(); val profile by vm.profile.collectAsStateCompat()
    val call by CallManager.ui.collectAsStateCompat()
    val toast by vm.toast.collectAsStateCompat()

    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.toast.value = null } }

    // Notification permission (Android 13+), asked once after sign-in.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(user?.uid) {
        if (user != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Microphone / camera permission gate for calls.
    var pendingGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pendingGrant?.invoke() else vm.say("Allow microphone (and camera) access in Settings to make calls.")
        pendingGrant = null
    }
    vm.requestCallPermissions = { video, run ->
        val needed = buildList { add(Manifest.permission.RECORD_AUDIO); if (video) add(Manifest.permission.CAMERA) }
        if (needed.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) run()
        else { pendingGrant = run; permLauncher.launch(needed.toTypedArray()) }
    }

    if (BuildConfig.FIREBASE_API_KEY == "unset") { SetupNeeded(); return }
    if (user == null) { AuthScreen(); return }
    if (profile == null) { Box(Modifier.fillMaxSize()) { androidx.compose.material3.Text("Loading your account…", Modifier.padding(32.dp)) }; return }

    Box(Modifier.fillMaxSize()) {
        MainNav(vm)
        call?.let { CallScreen(it, vm) }
    }
}

@Composable
private fun MainNav(vm: AppViewModel) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val me = vm.user.value?.uid ?: return
    val call by CallManager.ui.collectAsStateCompat()

    fun startCall(c: Conversation, video: Boolean) {
        val peer = c.members.firstOrNull { it != me } ?: return
        vm.requestCallPermissions?.invoke(video) { CallManager.start(ctx, me, peer, c.id, vm.title(c), video) }
    }
    val onAction: (ChatAction, Conversation) -> Unit = { a, c ->
        when (a) {
            ChatAction.VOICE -> startCall(c, false)
            ChatAction.VIDEO -> startCall(c, true)
            ChatAction.RENAME, ChatAction.MANAGE -> dialog = Dialog.Contact(c)
            ChatAction.GROUP_INFO -> dialog = Dialog.Info(c)
            ChatAction.DELETE_CONTACT -> dialog = Dialog.DeleteContact(c)
            ChatAction.DELETE_CONVERSATION -> dialog = Dialog.DeleteConversation(c)
            ChatAction.LEAVE -> dialog = Dialog.Leave(c)
        }
    }
    // System back closes the call screen only by ending/declining; otherwise navigation handles back.
    BackHandler(enabled = call != null) { /* swallow: use the on-screen End/Decline buttons */ }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(vm, onOpenChat = { nav.navigate("chat/$it") }, onAction = onAction,
                onAddChat = { dialog = Dialog.Start }, onAddGroup = { dialog = Dialog.Group }, onSettings = { dialog = Dialog.Settings }, onLogout = { dialog = Dialog.Logout })
        }
        composable("chat/{cid}") { entry ->
            ChatScreen(vm, entry.arguments?.getString("cid") ?: "", onBack = { nav.popBackStack() }, onAction = onAction)
        }
    }

    when (val d = dialog) {
        Dialog.Start -> StartChatDialog(vm, { dialog = null }) { nav.navigate("chat/$it") }
        Dialog.Group -> NewGroupDialog(vm, { dialog = null }) { nav.navigate("chat/$it") }
        Dialog.Settings -> SettingsDialog(vm) { dialog = null }
        Dialog.Logout -> Confirm("Log out of Daily Talk?", "You will need to sign in again to read your chats on this device. Your chats and your number stay safe in your account.", "Log out",
            danger = false, onDismiss = { dialog = null }, onConfirm = { CallManager.dismiss(ctx); Repo.logout() }, onError = vm::say)
        is Dialog.Contact -> ContactDialog(vm, d.c) { dialog = null }
        is Dialog.Info -> GroupInfoDialog(vm, d.c) { dialog = null }
        is Dialog.DeleteContact -> Confirm("Delete saved number?",
            "\"${vm.title(d.c)}\" will go back to showing just the number. The conversation and its messages are kept.", "Delete saved number",
            onDismiss = { dialog = null }, onConfirm = { vm.peerNumber(d.c)?.let { Repo.deleteContact(me, it) } }, onError = vm::say)
        is Dialog.DeleteConversation -> Confirm("Delete conversation?",
            "This clears the chat from your devices only. The saved contact stays, and other people keep their copy. New messages will bring it back.", "Delete conversation",
            onDismiss = { dialog = null }, onConfirm = { Repo.clearConversation(me, d.c.id); if (vm.openChatId == d.c.id) nav.popBackStack("home", false) }, onError = vm::say)
        is Dialog.Leave -> Confirm("Leave this group?", "You will stop receiving messages from it.", "Leave group",
            onDismiss = { dialog = null }, onConfirm = { Repo.leaveGroup(d.c, me); if (vm.openChatId == d.c.id) nav.popBackStack("home", false) }, onError = vm::say)
        null -> {}
    }
}
