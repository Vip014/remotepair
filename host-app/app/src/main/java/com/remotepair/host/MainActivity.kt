package com.remotepair.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remotepair.host.ui.screens.MainScreen
import com.remotepair.host.ui.screens.SettingsScreen
import com.remotepair.host.ui.theme.RemotePairTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemotePairTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(onSettings = { nav.navigate("settings") })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
