package com.remotepair.host.ui.screens

import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.remotepair.host.network.IpDetector
import com.remotepair.host.service.HostSession
import com.remotepair.host.service.SessionService
import com.remotepair.host.storage.PasswordStore
import com.remotepair.host.storage.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val ui by HostSession.uiState.collectAsState()
    var ip by remember { mutableStateOf<String?>(null) }

    val pwStore = remember { PasswordStore(ctx) }
    var password by remember { mutableStateOf("") }
    var savedPassword by remember { mutableStateOf("") }
    var signalingUrl by remember { mutableStateOf("") }

    // Connect to signaling on first load so we get the server-assigned ID.
    LaunchedEffect(Unit) {
        val url = SettingsStore(ctx).signalingUrl.first()
        signalingUrl = url
        val pw = pwStore.password.first()
        password = pw
        savedPassword = pw
        HostSession.ensureSignaling(url, pw)
        ip = IpDetector.localIp()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            SessionService.start(ctx, result.resultCode, result.data!!)
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
            // Status pill
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(
                        if (ui.controllerConnected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ui.status, style = MaterialTheme.typography.bodyMedium)
            }

            // Hero ID card
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("YOUR HOST ID", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    formatId(ui.hostId),
                    fontSize = 32.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(ui.hostId)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy ID")
                }
            }

            if (ip != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LOCAL IP", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(ip!!, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
            }

            // Connect password
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect password", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    if (savedPassword.isEmpty())
                        "No password set — anyone with your ID can connect."
                    else "A controller must enter this password to connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Leave blank for open") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            pwStore.set(password)
                            savedPassword = password
                            HostSession.restartSignaling(signalingUrl, password)
                        }
                    },
                    enabled = password != savedPassword,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Save password") }
            }

            Spacer(Modifier.weight(1f))

            if (ui.sharing) {
                Button(
                    onClick = { SessionService.stop(ctx) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Stop sharing", style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    onClick = {
                        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    enabled = ui.hostId.any { it.isDigit() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Start sharing", style = MaterialTheme.typography.titleMedium) }
            }

            Text(
                "To let the controller tap and swipe, also enable RemotePair Host in Android Settings → Accessibility.",
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
