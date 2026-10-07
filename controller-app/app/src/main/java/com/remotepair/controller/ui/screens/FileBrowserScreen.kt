package com.remotepair.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.remotepair.controller.files.FileEntry

/**
 * File browser against the host via the DataChannel "files" protocol.
 * In the MVP, we display a stub list; wiring to FileProtocol happens once Host APK is built.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    hostId: String,
    onBack: () -> Unit,
) {
    var path by remember { mutableStateOf("/storage/emulated/0") }

    // Stub listing until the DataChannel is wired to the real host.
    val entries = remember(path) {
        listOf(
            FileEntry("DCIM", true, 0, 0),
            FileEntry("Download", true, 0, 0),
            FileEntry("Documents", true, 0, 0),
            FileEntry("notes.txt", false, 1234, 0),
            FileEntry("resume.pdf", false, 182340, 0),
        )
    }

    val selected = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(enabled = selected.isNotEmpty(), onClick = { /* download */ }) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
                IconButton(onClick = { /* upload */ }) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload")
                }
                IconButton(enabled = selected.isNotEmpty(), onClick = { /* delete */ }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Divider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.name }) { e ->
                    val isSel = selected.contains(e.name)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isSel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                            .clickable {
                                if (e.isDir) path = "$path/${e.name}"
                                else {
                                    if (isSel) selected.remove(e.name) else selected.add(e.name)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (e.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            if (!e.isDir) {
                                Text(
                                    formatSize(e.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
