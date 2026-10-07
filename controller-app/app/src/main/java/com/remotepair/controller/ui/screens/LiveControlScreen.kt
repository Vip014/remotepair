package com.remotepair.controller.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack as BackGesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.remotepair.controller.webrtc.ControllerSession
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveControlScreen(hostId: String, onBack: () -> Unit) {
    val track by ControllerSession.remoteVideoTrack.collectAsState()
    val status by ControllerSession.status.collectAsState()

    // Build the renderer once; bind/unbind the track as it comes and goes.
    val renderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(renderer.value, track) {
        val r = renderer.value
        val t = track
        if (r != null && t != null) {
            runCatching { t.addSink(r) }
        }
        onDispose {
            if (r != null && t != null) runCatching { t.removeSink(r) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { renderer.value?.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (track != null) "Live" else status) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ControllerSession.sendInput("""{"t":"back"}""") }) {
                    Icon(BackGesture, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ControllerSession.sendInput("""{"t":"home"}""") }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ControllerSession.sendInput("""{"t":"recents"}""") }) {
                    Icon(Icons.Default.Menu, contentDescription = "Recents")
                }
                Spacer(Modifier.weight(1f))
            }
        }
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(ControllerSession.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                        renderer.value = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            sendTap(off, size.width, size.height)
                        }
                    }
                    .pointerInput(Unit) {
                        var start = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start = it },
                            onDragEnd = { }
                        ) { change, _ ->
                            // Fire a swipe from start to the current position.
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0 && h > 0) {
                                ControllerSession.sendInput(
                                    """{"t":"swipe","x1":${start.x / w},"y1":${start.y / h},""" +
                                        """"x2":${change.position.x / w},"y2":${change.position.y / h}}"""
                                )
                            }
                            change.consume()
                        }
                    }
            )

            if (track == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(status, color = Color.White)
                }
            }
        }
    }
}

private fun sendTap(off: Offset, width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    val x = off.x / width
    val y = off.y / height
    ControllerSession.sendInput("""{"t":"tap","x":$x,"y":$y}""")
}
