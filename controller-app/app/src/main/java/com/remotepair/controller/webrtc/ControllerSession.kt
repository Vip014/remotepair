package com.remotepair.controller.webrtc

import android.content.Context
import com.remotepair.controller.signaling.SignalingClient
import com.remotepair.controller.signaling.SignalingEvent
import com.remotepair.controller.signaling.SignalingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.webrtc.*
import java.nio.charset.StandardCharsets

/**
 * Process-wide controller session: signaling + WebRTC offerer. Receives the
 * host's screen as a video track and sends input over the "input" DataChannel.
 */
object ControllerSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var signaling: SignalingClient? = null
    private var inputChannel: DataChannel? = null

    val remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val status = MutableStateFlow("Idle")
    val paired = MutableStateFlow(false)

    private var inited = false

    fun start(ctx: Context, url: String, hostId: String, password: String = "") {
        if (inited) return
        inited = true

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        val sc = SignalingClient(url)
        signaling = sc
        scope.launch {
            sc.state.collect { st ->
                when (st) {
                    SignalingState.Idle -> status.value = "Idle"
                    SignalingState.Connecting -> status.value = "Connecting…"
                    is SignalingState.Registered -> { sc.connectToHost(hostId, password); status.value = "Finding host $hostId…" }
                    is SignalingState.PeerConnected -> { paired.value = true; status.value = "Connected — negotiating…"; startCall() }
                    is SignalingState.Error -> status.value = when (st.message) {
                        "auth_failed" -> "Wrong password"
                        "host_not_found" -> "Host not online — check the ID"
                        else -> "Error: ${st.message}"
                    }
                    SignalingState.Closed -> status.value = "Disconnected"
                }
            }
        }
        scope.launch {
            sc.events.collect { ev ->
                when (ev) {
                    is SignalingEvent.Signal -> handleRemoteSignal(ev.payload)
                    SignalingEvent.PeerLeft -> { paired.value = false; status.value = "Host left" }
                }
            }
        }
        sc.connect()
    }

    private fun startCall() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory!!.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                signaling?.sendSignal(buildJsonObject {
                    put("candidate", JsonPrimitive(c.sdp))
                    put("sdpMid", JsonPrimitive(c.sdpMid))
                    put("sdpMLineIndex", JsonPrimitive(c.sdpMLineIndex))
                })
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val t = receiver?.track()
                if (t is VideoTrack) { remoteVideoTrack.value = t; status.value = "Live" }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        // Input channel (we create it; host receives via onDataChannel)
        inputChannel = pc!!.createDataChannel("input", DataChannel.Init().apply {
            ordered = true; maxRetransmits = 0
        })
        // We want to RECEIVE video from the host
        pc!!.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        pc!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(d: SessionDescription?) {
                d ?: return
                pc?.setLocalDescription(SimpleSdpObserver(), d)
                signaling?.sendSignal(buildJsonObject {
                    put("sdp", JsonPrimitive(d.description))
                    put("type", JsonPrimitive("offer"))
                })
            }
        }, MediaConstraints())
    }

    private fun handleRemoteSignal(payload: JsonElement) {
        val obj = payload.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content
        when {
            type == "answer" -> {
                val sdp = obj["sdp"]?.jsonPrimitive?.content ?: return
                pc?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            obj["candidate"] != null -> {
                pc?.addIceCandidate(
                    IceCandidate(
                        obj["sdpMid"]?.jsonPrimitive?.content ?: "",
                        obj["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0,
                        obj["candidate"]!!.jsonPrimitive.content
                    )
                )
            }
        }
    }

    fun sendInput(jsonStr: String) {
        val dc = inputChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        val bytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
        dc.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(bytes), false))
    }

    fun stop() {
        runCatching { pc?.close() }
        pc = null
        remoteVideoTrack.value = null
        signaling?.disconnect()
        signaling = null
        paired.value = false
        status.value = "Idle"
        inited = false
        runCatching { factory?.dispose() }
        factory = null
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}
