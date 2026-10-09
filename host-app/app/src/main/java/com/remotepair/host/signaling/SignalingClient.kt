package com.remotepair.host.signaling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.Timer
import java.util.TimerTask

sealed class HostSignalState {
    data object Idle : HostSignalState()
    data object Connecting : HostSignalState()
    data class Registered(val id: String) : HostSignalState()
    data class ControllerJoined(val peerId: String) : HostSignalState()
    data class Error(val message: String) : HostSignalState()
    data object Closed : HostSignalState()
}

sealed class HostSignalEvent {
    data class Signal(val from: String, val payload: JsonElement) : HostSignalEvent()
    data object PeerLeft : HostSignalEvent()
}

/**
 * Host-side signaling with keepalive + auto-reconnect.
 *
 * - Pings every 25s so the socket (and the free-tier server) stays awake.
 * - If the socket drops, it reconnects automatically and re-registers with the
 *   SAME id, so the host keeps showing one stable 9-digit ID.
 */
class SignalingClient(private val url: String, private val password: String = "") {
    private val json = Json { ignoreUnknownKeys = true }
    private var ws: WebSocketClient? = null

    private var desiredId: String? = null      // reused across reconnects
    private var manualClose = false
    private var pingTimer: Timer? = null
    private var reconnectTimer: Timer? = null

    private val _state = MutableStateFlow<HostSignalState>(HostSignalState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<HostSignalEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun connect() {
        manualClose = false
        openSocket()
    }

    private fun openSocket() {
        cancelReconnect()
        _state.value = HostSignalState.Connecting
        ws = object : WebSocketClient(URI(url)) {
            override fun onOpen(h: ServerHandshake?) {
                send(buildJsonObject {
                    put("type", "register")
                    put("role", "host")
                    desiredId?.let { put("id", it) }   // ask for the same id back
                    if (password.isNotEmpty()) put("password", password)
                }.toString())
                startPing()
            }
            override fun onMessage(message: String) { handle(message) }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                stopPing()
                if (manualClose) {
                    _state.value = HostSignalState.Closed
                } else {
                    _state.value = HostSignalState.Connecting
                    scheduleReconnect()
                }
            }
            override fun onError(ex: Exception?) {
                // onClose will follow and trigger the reconnect path.
                if (manualClose) _state.value = HostSignalState.Error(ex?.message ?: "unknown")
            }
        }.also {
            runCatching { it.connect() }
                .onFailure { if (!manualClose) scheduleReconnect() }
        }
    }

    private fun handle(message: String) {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "registered" -> {
                val id = obj["id"]?.jsonPrimitive?.content ?: return
                desiredId = id
                _state.value = HostSignalState.Registered(id)
            }
            "controller_joined" -> {
                val peer = obj["peerId"]?.jsonPrimitive?.content ?: return
                _state.value = HostSignalState.ControllerJoined(peer)
            }
            "signal" -> {
                val from = obj["from"]?.jsonPrimitive?.content ?: return
                val payload = obj["payload"] ?: return
                _events.tryEmit(HostSignalEvent.Signal(from, payload))
            }
            "peer_left" -> _events.tryEmit(HostSignalEvent.PeerLeft)
            "error" -> _state.value =
                HostSignalState.Error(obj["error"]?.jsonPrimitive?.content ?: "error")
            "pong" -> { /* keepalive ack */ }
        }
    }

    fun sendSignal(payload: JsonElement) {
        ws?.send(buildJsonObject {
            put("type", "signal")
            put("payload", payload)
        }.toString())
    }

    /** Report this host's location to the server (admin-visible). */
    fun sendLoc(lat: Double, lon: Double) {
        ws?.send(buildJsonObject {
            put("type", "loc")
            put("lat", lat)
            put("lon", lon)
        }.toString())
    }

    fun registeredId(): String? = (_state.value as? HostSignalState.Registered)?.id

    private fun startPing() {
        stopPing()
        pingTimer = Timer("sig-ping", true).also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runCatching { ws?.send("""{"type":"ping"}""") }
                }
            }, 25_000L, 25_000L)
        }
    }

    private fun stopPing() {
        pingTimer?.cancel()
        pingTimer = null
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectTimer = Timer("sig-reconnect", true).also {
            it.schedule(object : TimerTask() {
                override fun run() { if (!manualClose) openSocket() }
            }, 3_000L)
        }
    }

    private fun cancelReconnect() {
        reconnectTimer?.cancel()
        reconnectTimer = null
    }

    fun disconnect() {
        manualClose = true
        stopPing()
        cancelReconnect()
        runCatching { ws?.send(buildJsonObject { put("type", "disconnect") }.toString()) }
        runCatching { ws?.close() }
        ws = null
    }
}
