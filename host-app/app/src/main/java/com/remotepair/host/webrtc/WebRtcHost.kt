package com.remotepair.host.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import com.remotepair.host.input.InputInjector
import com.remotepair.host.signaling.SignalingClient
import org.webrtc.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets

/**
 * Host-side WebRTC. Captures the screen via MediaProjection and publishes it as
 * a video track. The controller is the offerer; we answer. Input arrives on the
 * controller-created "input" DataChannel and is injected via InputInjector.
 */
class WebRtcHost(
    private val ctx: Context,
    private val signaling: SignalingClient,
    private val projectionData: Intent,
) {
    private val eglBase: EglBase = EglBase.create()
    private lateinit var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var capturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var screenW = 1080
    private var screenH = 1920

    fun start() {
        val metrics = DisplayMetrics()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        createPeerConnection()
        startScreenCapture()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                signaling.sendSignal(buildJsonObject {
                    put("candidate", JsonPrimitive(c.sdp))
                    put("sdpMid", JsonPrimitive(c.sdpMid))
                    put("sdpMLineIndex", JsonPrimitive(c.sdpMLineIndex))
                })
            }
            override fun onDataChannel(dc: DataChannel) {
                when (dc.label()) {
                    "input" -> attachInputChannel(dc)
                    "files" -> com.remotepair.host.files.FileResponder(dc).attach()
                }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
        })
    }

    private fun startScreenCapture() {
        val cap = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {
            override fun onStop() {}
        })
        capturer = cap
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val source = factory.createVideoSource(cap.isScreencast)
        videoSource = source
        cap.initialize(surfaceHelper, ctx, source.capturerObserver)
        cap.startCapture(screenW, screenH, 20)
        val track = factory.createVideoTrack("screen0", source)
        pc?.addTrack(track, listOf("stream0"))
    }

    private fun attachInputChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val msg = String(bytes, StandardCharsets.UTF_8)
                InputInjector.handle(msg, screenW, screenH)
            }
        })
    }

    /** Called when a signal (offer / ICE) arrives from the controller. */
    fun handleRemoteSignal(payload: JsonElement) {
        val obj = payload.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content
        when {
            type == "offer" -> {
                val sdp = obj["sdp"]?.jsonPrimitive?.content ?: return
                pc?.setRemoteDescription(object : SimpleSdp() {
                    override fun onSetSuccess() { createAnswer() }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
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

    private fun createAnswer() {
        pc?.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(d: SessionDescription?) {
                d ?: return
                pc?.setLocalDescription(SimpleSdp(), d)
                signaling.sendSignal(buildJsonObject {
                    put("sdp", JsonPrimitive(d.description))
                    put("type", JsonPrimitive("answer"))
                })
            }
        }, MediaConstraints())
    }

    fun stop() {
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { pc?.close() }
        runCatching { factory.dispose() }
        runCatching { eglBase.release() }
    }
}

open class SimpleSdp : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}
