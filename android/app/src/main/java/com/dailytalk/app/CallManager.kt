package com.dailytalk.app

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CallUi(
    val name: String,
    val video: Boolean,
    val incoming: Boolean,
    val status: String,             // calling | ringing | connecting | connected | reconnecting | ended | rejected | missed | failed
    val error: String? = null,
    val doc: CallDoc? = null,
)

/** Owns the single active call and its UI state. Survives configuration changes. */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow<CallUi?>(null)
    val ui: StateFlow<CallUi?> = _ui
    var session: RtcSession? = null
        private set
    private var timeout: Job? = null

    fun showIncoming(ctx: Context, call: CallDoc, name: String) {
        if (_ui.value != null) return
        _ui.value = CallUi(name, call.type == "video", true, "ringing", doc = call)
        Notifier.incomingCall(ctx, name, call.type == "video")
        timeout = scope.launch { delay(45_000); if (_ui.value?.status == "ringing") dismiss(ctx) }
    }

    fun start(ctx: Context, me: String, peer: String, cid: String, name: String, video: Boolean) {
        if (_ui.value != null) return
        _ui.value = CallUi(name, video, false, "calling")
        scope.launch {
            val s = RtcSession(ctx.applicationContext, Repo.newCallId(), me, video, ::onStatus)
            session = s
            audioMode(ctx, true, video)
            try {
                s.pendingAnswer = { sdp -> scope.launch { runCatching { s.applyAnswer(sdp) } } }
                s.startCall(peer, cid) { end -> finish(ctx, end) }
                timeout = scope.launch {
                    delay(45_000)
                    if (_ui.value?.status == "calling") { _ui.value = _ui.value?.copy(status = "missed"); s.end("missed"); finish(ctx, "missed") }
                }
            } catch (e: Exception) { fail(ctx, e) }
        }
    }

    fun accept(ctx: Context, me: String) {
        val cur = _ui.value ?: return
        val call = cur.doc ?: return
        timeout?.cancel()
        Notifier.cancelCall(ctx)
        _ui.value = cur.copy(status = "connecting")
        scope.launch {
            val s = RtcSession(ctx.applicationContext, call.id, me, call.type == "video", ::onStatus)
            session = s
            audioMode(ctx, true, call.type == "video")
            try { s.answerCall(call) { end -> finish(ctx, end) } } catch (e: Exception) { fail(ctx, e) }
        }
    }

    fun reject(ctx: Context) {
        val call = _ui.value?.doc ?: return
        scope.launch { runCatching { Repo.callRef(call.id).child("state").setValue("rejected").await() } }
        dismiss(ctx)
    }

    fun hangup(ctx: Context) {
        val s = session
        _ui.value = _ui.value?.copy(status = "ended")
        scope.launch { s?.end(); finish(ctx, "ended") }
    }

    fun dismiss(ctx: Context) {
        timeout?.cancel(); Notifier.cancelCall(ctx)
        session?.close(); session = null
        audioMode(ctx, false, false)
        _ui.value = null
    }

    private fun onStatus(s: String) {
        val cur = _ui.value ?: return
        if (s == "failed") { scope.launch { session?.end() }; _ui.value = cur.copy(status = "failed"); return }
        if (cur.status !in setOf("ended", "rejected", "missed", "failed")) _ui.value = cur.copy(status = s)
        if (s == "connected") timeout?.cancel()
    }

    private fun finish(ctx: Context, state: String) {
        timeout?.cancel(); session?.close(); session = null
        audioMode(ctx, false, false)
        _ui.value = _ui.value?.copy(status = state)
        scope.launch { delay(2500); if (_ui.value?.status == state) _ui.value = null }
    }

    private fun fail(ctx: Context, e: Exception) {
        session?.close(); session = null
        audioMode(ctx, false, false)
        _ui.value = _ui.value?.copy(status = "failed", error = "The call could not be started. Check permissions and your connection.")
        scope.launch { delay(3500); _ui.value = null }
    }

    private fun audioMode(ctx: Context, on: Boolean, speaker: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = on && speaker
    }
}
