package com.remotepair.host.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.remotepair.host.network.IpDetector
import com.remotepair.host.service.SessionService
import com.remotepair.host.storage.HostIdentity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val identity = remember { HostIdentity(ctx) }
    val clipboard = LocalClipboardManager.current

    var id by remember { mutableStateOf("---------") }
    var pass by remember { mutableStateOf("---") }
    var ip by remember { mutableStateOf<String?>(null) }
    var sessionActive by remember { mutableStateOf(false) }

    // Load identity once (generate if first launch) and detect IP
    LaunchedEffect(Unit) {
        identity.ensureInitialized()
        identity.id.collect { id = it.ifEmpty { "---------" } }
    }
    LaunchedEffect(Unit) { identity.passphrase.collect { pass = it.ifEmpty { "---" } } }
    LaunchedEffect(Unit) { ip = IpDetector.localIp() }

    // MediaProjection permission launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            SessionService.start(ctx)
            sessionActive = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("RemotePair Host") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Share this with your helper",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hero: 9-digit ID card
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "YOUR HOST ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    formatId(id),
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "PASSPHRASE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        pass,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString("$id\n$pass"))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = {
                        scope.launch { identity.rotate() }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New ID")
                    }
                }
            }

            // Local IP info
            if (ip != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LOCAL IP", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(ip!!, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Session status + action
            if (sessionActive) {
                Button(
                    onClick = { SessionService.stop(ctx); sessionActive = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("End session", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = {
                        val mpm = ctx.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                                as android.media.projection.MediaProjectionManager
                        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Start sharing", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Nothing is shared until you tap Start sharing. Android will ask you to confirm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatId(id: String): String {
    if (id.length != 9) return id
    return "${id.substring(0,3)} ${id.substring(3,6)} ${id.substring(6,9)}"
}
