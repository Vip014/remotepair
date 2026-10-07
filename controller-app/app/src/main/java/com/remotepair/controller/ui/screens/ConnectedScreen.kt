package com.remotepair.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotepair.controller.storage.SettingsStore
import com.remotepair.controller.webrtc.ControllerSession
import kotlinx.coroutines.flow.first

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
    val status by ControllerSession.status.collectAsState()
    val paired by ControllerSession.paired.collectAsState()

    // Start (or reuse) the session as soon as we land here.
    LaunchedEffect(hostId) {
        val url = SettingsStore(ctx).signalingUrl.first()
        ControllerSession.start(ctx, url, hostId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Host ${formatId(hostId)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(
                        if (paired) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))

            ActionTile(
                icon = Icons.Default.ScreenShare,
                title = "Live Control",
                subtitle = "See the host screen and tap, swipe, go back/home.",
                enabled = paired,
                onClick = onLiveControl
            )
            ActionTile(
                icon = Icons.Default.Folder,
                title = "File Browser",
                subtitle = "Browse and copy files without disturbing the host.",
                enabled = paired,
                onClick = onFileBrowser
            )

            Spacer(Modifier.weight(1f))
            Text(
                if (paired) "Connected to the host."
                else "Waiting for the host to be online and sharing…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatId(id: String): String {
    if (id.length != 9) return id
    return "${id.substring(0, 3)} ${id.substring(3, 6)} ${id.substring(6, 9)}"
}
