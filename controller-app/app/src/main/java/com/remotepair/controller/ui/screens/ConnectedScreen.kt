package com.remotepair.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.remotepair.controller.signaling.SignalingClient
import com.remotepair.controller.signaling.SignalingState
import com.remotepair.controller.storage.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedScreen(
    hostId: String,
    passphrase: String,
    onBack: () -> Unit,
    onLiveControl: () -> Unit,
    onFileBrowser: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Connecting to signaling server…") }
    var paired by remember { mutableStateOf(false) }

    DisposableEffect(hostId) {
        val client = ConnectionHolder.client ?: run {
            val url = kotlinx.coroutines.runBlocking { SettingsStore(ctx).signalingUrl.first() }
            SignalingClient(url).also { ConnectionHolder.client = it }
        }

        scope.launch {
            client.state.collect { s ->
                status = when (s) {
                    SignalingState.Idle -> "Idle"
                    SignalingState.Connecting -> "Connecting to signaling server…"
                    is SignalingState.Registered -> {
                        client.connectToHost(hostId)
                        "Finding host $hostId…"
                    }
                    is SignalingState.PeerConnected -> {
                        paired = true
                        "Paired with $hostId"
                    }
                    is SignalingState.Error -> "Error: ${s.message}"
                    SignalingState.Closed -> "Disconnected"
                }
            }
        }
        client.connect()

        onDispose { /* keep client alive for sub-screens */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Host $hostId", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = {
                        ConnectionHolder.client?.disconnect()
                        ConnectionHolder.client = null
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusPill(status, paired)
            Spacer(Modifier.height(8.dp))
            Text("Choose mode", style = MaterialTheme.typography.titleLarge)

            ModeCard(
                icon = Icons.Default.Visibility,
                title = "Live Control",
                subtitle = "See the host's screen and send taps/keystrokes.",
                enabled = paired,
                onClick = onLiveControl,
            )
            ModeCard(
                icon = Icons.Default.FolderOpen,
                title = "File Browser",
                subtitle = "Browse and copy files without disturbing the host's screen.",
                enabled = paired,
                onClick = onFileBrowser,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, paired: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(
                if (paired) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (paired) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Simple singleton to share the signaling client across screens for the MVP.
object ConnectionHolder {
    var client: SignalingClient? = null
}
