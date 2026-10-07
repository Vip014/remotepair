package com.remotepair.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remotepair.controller.ui.screens.*
import com.remotepair.controller.ui.theme.RemotePairTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Deep link: remotepair://connect?id=123456789
        val deepLinkId = intent?.data?.takeIf { it.scheme == "remotepair" && it.host == "connect" }
            ?.getQueryParameter("id")

        setContent {
            RemotePairTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            prefilledId = deepLinkId,
                            onConnect = { id, pass -> nav.navigate("connected/$id/$pass") },
                            onSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("connected/{id}/{pass}") { entry ->
                        val id = entry.arguments?.getString("id") ?: ""
                        val pass = entry.arguments?.getString("pass") ?: ""
                        ConnectedScreen(
                            hostId = id,
                            passphrase = pass,
                            onBack = { nav.popBackStack() },
                            onLiveControl = { nav.navigate("live/$id") },
                            onFileBrowser = { nav.navigate("files/$id") }
                        )
                    }
                    composable("live/{id}") { entry ->
                        LiveControlScreen(
                            hostId = entry.arguments?.getString("id") ?: "",
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("files/{id}") { entry ->
                        FileBrowserScreen(
                            hostId = entry.arguments?.getString("id") ?: "",
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
