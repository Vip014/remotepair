package com.remotepair.host.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.remotepair.host.HostApp
import com.remotepair.host.MainActivity

/**
 * Foreground service with mediaProjection type. Must be running BEFORE the
 * MediaProjection is created (Android 14+ requirement), so we start it with the
 * projection result, go foreground, then hand the projection to HostSession.
 */
class SessionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            HostSession.stopCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultCode != 0 && data != null) {
            // data already carries the result; ScreenCapturerAndroid uses it directly
            HostSession.startCapture(applicationContext, data)
        }
        return START_STICKY
    }

    private fun buildNotification() = NotificationCompat.Builder(this, HostApp.CHANNEL_SESSION)
        .setContentTitle("RemotePair sharing active")
        .setContentText("Your screen can be seen by a connected controller")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            PendingIntent.getService(
                this, 1, Intent(this, SessionService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "stop_session"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, SessionService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, SessionService::class.java).apply { action = ACTION_STOP })
        }
    }
}
