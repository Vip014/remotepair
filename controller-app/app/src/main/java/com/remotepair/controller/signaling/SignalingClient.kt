package com.remotepair.controller.signaling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

sealed class SignalingState {
    data object Idle : SignalingState()
    data object Connecting : SignalingState()
    data class Registered(val id: String) : SignalingState()
    data class PeerConnected(val peerId: String) : SignalingState()
    data class Error(val message: String) : SignalingState()
    data object Closed : SignalingState()
}

sealed class SignalingEvent {
    data class Signal(val from: String, val payload: JsonElement) : SignalingEvent()
    data object PeerLeft : SignalingEvent()
}

class SignalingClient(private val url: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private var ws: WebSocketClient? = null

    private val _state = MutableStateFlow<SignalingState>(SignalingState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun connect() {
        if (_state.value is SignalingState.Connecting) return
        _state.value = SignalingState.Connecting
        ws = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                // Register as controller.
                send(buildJsonObject {
                    put("type", "register")
                    put("role", "controller")
                }.toString())
            }

            override fun onMessage(message: String) {
                handleMessage(message)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                _state.value = SignalingState.Closed
            }

            override fun onError(ex: Exception?) {
                _state.value = SignalingState.Error(ex?.message ?: "unknown")
            }
        }.also { it.connect() }
    }

    private fun handleMessage(message: String) {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "registered" -> {
                val id = obj["id"]?.jsonPrimitive?.content ?: return
                _state.value = SignalingState.Registered(id)
            }
            "connected" -> {
                val peer = obj["peerId"]?.jsonPrimitive?.content ?: return
                _state.value = SignalingState.PeerConnected(peer)
            }
            "signal" -> {
                val from = obj["from"]?.jsonPrimitive?.content ?: return
                val payload = obj["payload"] ?: return
                _events.tryEmit(SignalingEvent.Signal(from, payload))
            }
            "peer_left" -> _events.tryEmit(SignalingEvent.PeerLeft)
            "error" -> {
                val err = obj["error"]?.jsonPrimitive?.content ?: "error"
                _state.value = SignalingState.Error(err)
            }
        }
    }

    fun connectToHost(hostId: String) {
        ws?.send(buildJsonObject {
            put("type", "connect")
            put("id", hostId)
        }.toString())
    }

    fun sendSignal(payload: JsonElement) {
        ws?.send(buildJsonObject {
            put("type", "signal")
            put("payload", payload)
        }.toString())
    }

    fun disconnect() {
        runCatching { ws?.send(buildJsonObject { put("type", "disconnect") }.toString()) }
        runCatching { ws?.close() }
        ws = null
    }
}
