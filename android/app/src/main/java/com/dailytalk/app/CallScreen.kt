package com.dailytalk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
private fun RoundAction(label: String, glyph: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(68.dp).background(color, CircleShape).semantics { contentDescription = label }
            .then(Modifier.androidClickable(onClick)),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, style = MaterialTheme.typography.headlineSmall) }
}
private fun Modifier.androidClickable(onClick: () -> Unit) = this.then(Modifier.clickableNoRipple(onClick))
private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
)

@Composable
private fun VideoView(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    if (track == null) return
    AndroidView(
        factory = { c -> SurfaceViewRenderer(c).apply {
            init(RtcSession.egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL); setMirror(mirror); setEnableHardwareScaler(true)
            track.addSink(this)
        } },
        onRelease = { r -> track.removeSink(r); r.release() },
        modifier = modifier,
    )
}

@Composable
fun CallScreen(call: CallUi, vm: AppViewModel) {
    val ctx = LocalContext.current
    val session = CallManager.session
    val remote by (session?.remoteVideo ?: remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }).collectAsStateCompat()
    val local by (session?.localVideo ?: remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }).collectAsStateCompat()
    var muted by remember { mutableStateOf(false) }; var camOff by remember { mutableStateOf(false) }; var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(call.status) { if (call.status == "connected") { secs = 0; while (true) { delay(1000); secs++ } } }
    val over = call.status in setOf("ended", "rejected", "missed", "failed")
    val label = call.error ?: when (call.status) {
        "calling" -> "Calling…"; "ringing" -> "Incoming call…"; "connecting" -> "Connecting…"; "reconnecting" -> "Reconnecting…"
        "connected" -> "%02d:%02d".format(secs / 60, secs % 60)
        "ended" -> "Call ended"; "rejected" -> "Call declined"; "missed" -> "No answer"; else -> "Connection lost"
    }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Palette.BrownDeep, Palette.Ink)))) {
        if (call.video) VideoView(remote, false, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Avatar(call.name, 96.dp)
                Text(call.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(label, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.semantics { contentDescription = "Call status: $label" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (call.incoming && call.status == "ringing") {
                    RoundAction("Decline", "✕", Color(0xFFD6402B)) { CallManager.reject(ctx) }
                    RoundAction("Accept", "✆", Color(0xFF3F9A3A)) { vm.requestCallPermissions?.invoke(call.video) { CallManager.accept(ctx, vm.user.value?.uid ?: "") } }
                } else if (!over) {
                    RoundAction(if (muted) "Unmute" else "Mute", if (muted) "🔇" else "🎙", if (muted) Palette.Brown else Color.White.copy(alpha = 0.25f)) { muted = !muted; session?.setMuted(muted) }
                    if (call.video) RoundAction(if (camOff) "Camera on" else "Camera off", "🎥", if (camOff) Palette.Brown else Color.White.copy(alpha = 0.25f)) { camOff = !camOff; session?.setCameraOff(camOff) }
                    RoundAction("End call", "✕", Color(0xFFD6402B)) { CallManager.hangup(ctx) }
                } else TextButton({ CallManager.dismiss(ctx) }) { Text("Close", color = Color.White) }
            }
        }
        if (call.video) VideoView(local, true, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(104.dp, 140.dp))
    }
}
