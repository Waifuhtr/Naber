package com.naber.app.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.naber.app.MainActivity
import com.naber.app.NaberApp
import com.naber.app.R

/**
 * Arama suren surece calisan on plan servisi.
 * Uygulamadan cikildiginda bile ses baglantisi yasar, bildirim cubugunda
 * "Devam eden arama" gorunur ve dokununca aramaya geri donulur.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Arama"
        startAsForeground(buildNotification(title))
        return START_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // Izin yoksa uygulama yine calisir, yalnizca kalici bildirim olmaz.
            stopSelf()
        }
    }

    private fun buildNotification(title: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NaberApp.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Devam eden arama - geri donmek icin dokunun")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 77
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            runCatching {
                val intent = Intent(context, CallService::class.java).putExtra(EXTRA_TITLE, title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
