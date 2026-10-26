package com.dailytalk.app

import android.content.Context
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One 1:1 WebRTC call. Signaling uses the same Realtime Database nodes as the website (calls/{id}). */
class RtcSession(
    private val ctx: Context,
    val callId: String,
    private val me: String,
    private val video: Boolean,
    private val onStatus: (String) -> Unit,
) {
    companion object {
        val egl: EglBase by lazy { EglBase.create() }
        private var factory: PeerConnectionFactory? = null
        private fun factory(ctx: Context): PeerConnectionFactory = factory ?: run {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions())
            PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory().also { factory = it }
        }

        fun iceServers(): List<PeerConnection.IceServer> {
            val list = mutableListOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            )
            if (BuildConfig.TURN_URL.isNotBlank()) {
                list += PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                    .setUsername(BuildConfig.TURN_USERNAME).setPassword(BuildConfig.TURN_CREDENTIAL).createIceServer()
            }
            return list
        }
    }

    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo = MutableStateFlow<VideoTrack?>(null)

    private val f = factory(ctx)
    private var capturer: CameraVideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var audio: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private val regs = mutableListOf<() -> Unit>()
    private val queued = mutableListOf<IceCandidate>()
    private var remoteSet = false
    private var closed = false

    private val pc: PeerConnection = f.createPeerConnection(
        PeerConnection.RTCConfiguration(iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN },
        object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(d: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidate(c: IceCandidate) {
                Repo.candidates(callId).push().setValue(mapOf(
                    "from" to me, "candidate" to c.sdp, "sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex,
                ))
            }
            override fun onTrack(t: RtpTransceiver) {
                (t.receiver.track() as? VideoTrack)?.let { remoteVideo.value = it }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                when (s) {
                    PeerConnection.PeerConnectionState.CONNECTED -> onStatus("connected")
                    PeerConnection.PeerConnectionState.DISCONNECTED -> onStatus("reconnecting")
                    PeerConnection.PeerConnectionState.FAILED -> onStatus("failed")
                    else -> {}
                }
            }
        },
    ) ?: error("Could not create the call connection.")

    private fun attachMedia() {
        val src = f.createAudioSource(MediaConstraints())
        audio = f.createAudioTrack("a0", src).also { pc.addTrack(it, listOf("s0")) }
        if (video) {
            val enumerator = Camera2Enumerator(ctx)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
                ?: error("No camera found.")
            val cap = enumerator.createCapturer(name, null) ?: error("Could not open the camera.")
            helper = SurfaceTextureHelper.create("capture", egl.eglBaseContext)
            val vs = f.createVideoSource(cap.isScreencast)
            cap.initialize(helper, ctx, vs.capturerObserver)
            cap.startCapture(640, 480, 24)
            capturer = cap
            videoTrack = f.createVideoTrack("v0", vs).also { pc.addTrack(it, listOf("s0")); localVideo.value = it }
        }
    }

    private suspend fun offer(): SessionDescription = suspendCancellableCoroutine { c ->
        pc.createOffer(object : Sdp() {
            override fun onCreateSuccess(d: SessionDescription) = c.resume(d)
            override fun onCreateFailure(e: String?) = c.resumeWithException(Exception(e))
        }, MediaConstraints())
    }
    private suspend fun answer(): SessionDescription = suspendCancellableCoroutine { c ->
        pc.createAnswer(object : Sdp() {
            override fun onCreateSuccess(d: SessionDescription) = c.resume(d)
            override fun onCreateFailure(e: String?) = c.resumeWithException(Exception(e))
        }, MediaConstraints())
    }
    private suspend fun setLocal(d: SessionDescription) = suspendCancellableCoroutine<Unit> { c ->
        pc.setLocalDescription(object : Sdp() {
            override fun onSetSuccess() = c.resume(Unit)
            override fun onSetFailure(e: String?) = c.resumeWithException(Exception(e))
        }, d)
    }
    private suspend fun setRemote(d: SessionDescription) = suspendCancellableCoroutine<Unit> { c ->
        pc.setRemoteDescription(object : Sdp() {
            override fun onSetSuccess() = c.resume(Unit)
            override fun onSetFailure(e: String?) = c.resumeWithException(Exception(e))
        }, d)
        remoteSet = true
    }
    private abstract class Sdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    private fun listenCandidates() {
        val r = Repo.candidates(callId)
        val l = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child("from").getValue(String::class.java) == me) return
                val c = IceCandidate(
                    snapshot.child("sdpMid").getValue(String::class.java),
                    (snapshot.child("sdpMLineIndex").getValue(Long::class.java) ?: 0L).toInt(),
                    snapshot.child("candidate").getValue(String::class.java) ?: return,
                )
                if (remoteSet) pc.addIceCandidate(c) else synchronized(queued) { queued += c }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        r.addChildEventListener(l)
        regs += { r.removeEventListener(l) }
    }
    private fun flush() { synchronized(queued) { queued.forEach { pc.addIceCandidate(it) }; queued.clear() } }

    private fun listenValue(path: com.google.firebase.database.DatabaseReference, onValue: (DataSnapshot) -> Unit) {
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onValue(snapshot) }
            override fun onCancelled(error: DatabaseError) {}
        }
        path.addValueEventListener(l)
        regs += { path.removeEventListener(l) }
    }

    /** Caller side. Throws if media/permissions fail. */
    suspend fun startCall(peer: String, conversationId: String, onEnded: (String) -> Unit) {
        attachMedia()
        val offer = offer(); setLocal(offer)
        val type = if (video) "video" else "voice"
        Repo.callRef(callId).setValue(mapOf(
            "callerId" to me, "calleeId" to peer, "conversationId" to conversationId, "type" to type, "state" to "ringing",
            "offer" to mapOf("type" to "offer", "sdp" to offer.description), "createdAt" to ServerValue.TIMESTAMP,
        )).await()
        Repo.inbox(peer, callId).setValue(mapOf(
            "callerId" to me, "conversationId" to conversationId, "type" to type, "createdAt" to ServerValue.TIMESTAMP,
        )).await()
        listenCandidates()
        listenValue(Repo.callRef(callId)) { d ->
            val sdp = d.child("answer").child("sdp").getValue(String::class.java)
            if (sdp != null && !remoteSet) pendingAnswer?.invoke(sdp)
            val st = d.child("state").getValue(String::class.java)
            if (st == "rejected" || st == "ended" || st == "missed") onEnded(st)
        }
    }

    var pendingAnswer: ((String) -> Unit)? = null

    /** Applies the callee's answer (called from the coroutine that owns this session). */
    suspend fun applyAnswer(sdp: String) {
        if (remoteSet) return
        setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp)); flush(); onStatus("connecting")
    }

    /** Callee side. */
    suspend fun answerCall(call: CallDoc, onEnded: (String) -> Unit) {
        attachMedia()
        setRemote(SessionDescription(SessionDescription.Type.OFFER, call.offerSdp ?: error("Missing offer")))
        val ans = answer(); setLocal(ans)
        Repo.callRef(callId).updateChildren(mapOf("answer" to mapOf("type" to "answer", "sdp" to ans.description), "state" to "accepted")).await()
        listenCandidates(); flush()
        listenValue(Repo.callRef(callId).child("state")) { s ->
            val st = s.getValue(String::class.java)
            if (st == "ended" || st == "missed") onEnded(st)
        }
    }

    fun setMuted(m: Boolean) { audio?.setEnabled(!m) }
    fun setCameraOff(off: Boolean) { videoTrack?.setEnabled(!off) }

    suspend fun end(state: String = "ended") {
        runCatching { Repo.callRef(callId).child("state").setValue(state).await() }
        close()
    }

    fun close() {
        if (closed) return; closed = true
        regs.forEach { it() }
        runCatching { capturer?.stopCapture() }
        capturer?.dispose(); helper?.dispose()
        audio?.dispose(); videoTrack?.dispose()
        pc.close()
        remoteVideo.value = null; localVideo.value = null
    }
}
