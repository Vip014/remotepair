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
 * Host-side signaling. Registers with role=host. The server assigns an ID,
 * but we also pass our preferred ID so both sides match (server is lenient).
 */
class SignalingClient(private val url: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private var ws: WebSocketClient? = null

    private val _state = MutableStateFlow<HostSignalState>(HostSignalState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<HostSignalEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun connect() {
        if (_state.value is HostSignalState.Connecting) return
        _state.value = HostSignalState.Connecting
        ws = object : WebSocketClient(URI(url)) {
            override fun onOpen(h: ServerHandshake?) {
                send(buildJsonObject {
                    put("type", "register")
                    put("role", "host")
                }.toString())
            }
            override fun onMessage(message: String) { handle(message) }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                _state.value = HostSignalState.Closed
            }
            override fun onError(ex: Exception?) {
                _state.value = HostSignalState.Error(ex?.message ?: "unknown")
            }
        }.also { it.connect() }
    }

    private fun handle(message: String) {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "registered" -> {
                val id = obj["id"]?.jsonPrimitive?.content ?: return
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
            "error" -> _state.value = HostSignalState.Error(obj["error"]?.jsonPrimitive?.content ?: "error")
        }
    }

    fun sendSignal(payload: JsonElement) {
        ws?.send(buildJsonObject {
            put("type", "signal")
            put("payload", payload)
        }.toString())
    }

    fun registeredId(): String? = (_state.value as? HostSignalState.Registered)?.id

    fun disconnect() {
        runCatching { ws?.send(buildJsonObject { put("type", "disconnect") }.toString()) }
        runCatching { ws?.close() }
        ws = null
    }
}
