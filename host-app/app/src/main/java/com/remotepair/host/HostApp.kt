package com.remotepair.host

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "remotepair_host_prefs")

class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SESSION,
                    "Session active",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows while a controller is connected to your device"
                }
            )
        }
    }
    companion object {
        const val CHANNEL_SESSION = "session_active"
        lateinit var instance: HostApp
            private set
    }
}
