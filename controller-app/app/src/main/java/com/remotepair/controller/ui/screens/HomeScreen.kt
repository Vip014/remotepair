package com.remotepair.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.remotepair.controller.storage.RecentHost
import com.remotepair.controller.storage.RecentHostsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefilledId: String?,
    onConnect: (id: String, passphrase: String) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RecentHostsStore(ctx) }
    val recents by store.flow.collectAsState(initial = emptyList())

    var id by remember { mutableStateOf(prefilledId ?: "") }
    var pass by remember { mutableStateOf("") }
    val canConnect = id.length == 9 && id.all { it.isDigit() } && pass.isNotBlank()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("RemotePair") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Connect to a host",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Ask the person being helped for their 9-digit host ID and session passphrase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = id,
                onValueChange = { if (it.length <= 9 && it.all(Char::isDigit)) id = it },
                label = { Text("Host ID") },
                placeholder = { Text("123456789") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Session passphrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch { store.add(RecentHost(id, System.currentTimeMillis())) }
                    onConnect(id, pass)
                },
                enabled = canConnect,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.titleMedium)
            }

            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recent hosts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.id }) { h ->
                        RecentRow(h, onClick = { id = h.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(h: RecentHost, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            h.id,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.weight(1f))
        Text(
            relativeTime(h.lastUsed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun relativeTime(t: Long): String {
    val diff = (System.currentTimeMillis() - t) / 1000
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}
