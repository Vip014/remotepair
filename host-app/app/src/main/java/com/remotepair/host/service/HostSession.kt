package com.remotepair.host.service

import android.content.Context
import android.content.Intent
import com.remotepair.host.signaling.HostSignalEvent
import com.remotepair.host.signaling.HostSignalState
import com.remotepair.host.signaling.SignalingClient
import com.remotepair.host.webrtc.WebRtcHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the host's signaling connection and WebRTC engine.
 * The UI observes [uiState]; the foreground service drives capture start/stop.
 */
object HostSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var signaling: SignalingClient? = null
    private var webrtc: WebRtcHost? = null
    private var currentPeer: String? = null

    data class UiState(
        val hostId: String = "---------",
        val status: String = "Offline",
        val sharing: Boolean = false,
        val controllerConnected: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    /** Connect to signaling and register as a host (shows the server-assigned ID). */
    fun ensureSignaling(url: String) {
        if (signaling != null) return
        val sc = SignalingClient(url)
        signaling = sc
        scope.launch {
            sc.state.collect { st ->
                when (st) {
                    HostSignalState.Idle -> update { it.copy(status = "Offline") }
                    HostSignalState.Connecting -> update { it.copy(status = "Connecting…") }
                    is HostSignalState.Registered ->
                        update { it.copy(hostId = st.id, status = "Ready — share your ID") }
                    is HostSignalState.ControllerJoined -> {
                        currentPeer = st.peerId
                        update { it.copy(controllerConnected = true, status = "Controller connected") }
                    }
                    is HostSignalState.Error -> update { it.copy(status = "Error: ${st.message}") }
                    HostSignalState.Closed -> update { it.copy(status = "Disconnected") }
                }
            }
        }
        scope.launch {
            sc.events.collect { ev ->
                when (ev) {
                    is HostSignalEvent.Signal -> webrtc?.handleRemoteSignal(ev.payload)
                    HostSignalEvent.PeerLeft -> {
                        currentPeer = null
                        update { it.copy(controllerConnected = false, status = "Controller left") }
                    }
                }
            }
        }
        sc.connect()
    }

    /** Called by the foreground service once it is running with mediaProjection type. */
    fun startCapture(appCtx: Context, projectionData: Intent) {
        val sc = signaling ?: return
        if (webrtc != null) return
        webrtc = WebRtcHost(appCtx, sc, projectionData).also { it.start() }
        update { it.copy(sharing = true, status = "Sharing — waiting for controller") }
    }

    fun stopCapture() {
        webrtc?.stop()
        webrtc = null
        update { it.copy(sharing = false, controllerConnected = false, status = "Ready — share your ID") }
    }

    fun shutdown() {
        stopCapture()
        signaling?.disconnect()
        signaling = null
        update { UiState() }
    }

    private inline fun update(block: (UiState) -> UiState) {
        _uiState.value = block(_uiState.value)
    }
}
