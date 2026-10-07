package com.remotepair.host.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.remotepair.host.HostApp
import com.remotepair.host.MainActivity
import com.remotepair.host.R

/**
 * Foreground service that keeps the host session alive and shows a persistent
 * notification. In a full build this would also hold the MediaProjection and
 * WebRTC peer connection.
 */
class SessionService : Service() {

    override fun onCreate() {
        super.onCreate()
        val tapIntent = Intent(this, MainActivity::class.java)
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, SessionService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, HostApp.CHANNEL_SESSION)
            .setContentTitle("RemotePair session active")
            .setContentText("Tap to view · controller can see your screen")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setContentIntent(tapPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End session", stopPending)
            .build()
        startForeground(1, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "stop_session"
        fun start(ctx: Context) {
            val i = Intent(ctx, SessionService::class.java)
            ctx.startForegroundService(i)
        }
        fun stop(ctx: Context) {
            val i = Intent(ctx, SessionService::class.java).apply { action = ACTION_STOP }
            ctx.startService(i)
        }
    }
}
