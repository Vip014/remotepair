package com.remotepair.controller.webrtc

import android.content.Context
import com.remotepair.controller.signaling.SignalingClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.webrtc.*

/**
 * Thin wrapper around a PeerConnection for the Controller side.
 *
 * Controller is the initiator: it creates DataChannels "input" and "files",
 * creates an SDP offer, sends it to the host via signaling, and applies the
 * host's answer. The host publishes a video track which arrives on
 * onAddTrack / onTrack.
 *
 * This file is the skeleton — Live Control and File Browser screens attach to
 * the channels exposed here.
 */
class PeerConnectionManager(
    private val ctx: Context,
    private val signaling: SignalingClient,
) {
    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null

    var inputChannel: DataChannel? = null
        private set
    var filesChannel: DataChannel? = null
        private set

    val remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            // Add your TURN server(s) here for strict NATs:
            // PeerConnection.IceServer.builder("turn:your.turn:3478")
            //     .setUsername("user").setPassword("pass").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory!!.createPeerConnection(rtcConfig, PcObserver())!!

        inputChannel = pc!!.createDataChannel("input", DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 0 // low-latency input, drop stale
        })
        filesChannel = pc!!.createDataChannel("files", DataChannel.Init().apply {
            ordered = true // reliable for files
        })
    }

    suspend fun createOfferAndSend() {
        val offer = createOffer() ?: return
        pc?.setLocalDescription(SimpleSdpObserver(), offer)
        signaling.sendSignal(buildJsonObject {
            put("sdp", JsonPrimitive(offer.description))
            put("type", JsonPrimitive(offer.type.canonicalForm()))
        })
    }

    fun handleRemoteSignal(payload: JsonElement) {
        val obj = payload.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content
        if (type == "answer" || type == "offer") {
            val sdp = obj["sdp"]?.jsonPrimitive?.content ?: return
            val sd = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
            pc?.setRemoteDescription(SimpleSdpObserver(), sd)
        } else if (obj["candidate"] != null) {
            val c = IceCandidate(
                obj["sdpMid"]?.jsonPrimitive?.content ?: "",
                obj["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0,
                obj["candidate"]!!.jsonPrimitive.content
            )
            pc?.addIceCandidate(c)
        }
    }

    private suspend fun createOffer(): SessionDescription? {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            pc?.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(d: SessionDescription?) { cont.resumeWith(Result.success(d)) }
                override fun onCreateFailure(e: String?) { cont.resumeWith(Result.success(null)) }
            }, MediaConstraints())
        }
    }

    fun close() {
        runCatching { pc?.close() }
        pc = null
        factory?.dispose()
        eglBase.release()
    }

    private inner class PcObserver : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            signaling.sendSignal(buildJsonObject {
                put("candidate", JsonPrimitive(c.sdp))
                put("sdpMid", JsonPrimitive(c.sdpMid))
                put("sdpMLineIndex", JsonPrimitive(c.sdpMLineIndex))
            })
        }
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>?) {
            val track = receiver.track()
            if (track is VideoTrack) remoteVideoTrack.value = track
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
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}
