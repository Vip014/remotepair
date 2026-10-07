package com.remotepair.controller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.remotepair.controller.storage.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(ctx) }
    var url by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { url = store.signalingUrl.first() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Signaling server", style = MaterialTheme.typography.titleMedium)
            Text(
                "WebSocket URL of the server that pairs you with hosts. " +
                        "Use ws:// on local Wi-Fi, wss:// in production.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ws:// or wss:// URL") }
            )
            Button(
                onClick = { scope.launch { store.setSignalingUrl(url); onBack() } },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save") }
        }
    }
}
