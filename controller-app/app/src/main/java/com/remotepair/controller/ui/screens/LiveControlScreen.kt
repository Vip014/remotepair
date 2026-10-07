package com.remotepair.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shows the host's screen (video track) and sends input back when "Start Input" is toggled on.
 *
 * MVP note: the actual WebRTC video track rendering and input DataChannel are wired up in
 * webrtc/PeerConnectionManager.kt. Since the real Host APK does not exist yet, this screen
 * paints a placeholder until a video frame arrives from the paired host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveControlScreen(
    hostId: String,
    onBack: () -> Unit,
) {
    var inputEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // Video surface placeholder. Replaced by a SurfaceViewRenderer bound to the host track.
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Waiting for host video…\nHost $hostId",
                    color = Color(0xFF8A94A6),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Floating control bar.
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { inputEnabled = !inputEnabled },
                    colors = if (inputEnabled)
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    else ButtonDefaults.filledTonalButtonColors()
                ) {
                    Icon(Icons.Default.Mouse, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (inputEnabled) "Input ON" else "Start Input")
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { /* open IME in future */ }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                }
            }
        }
    }
}
