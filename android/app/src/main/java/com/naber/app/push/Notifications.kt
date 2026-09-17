package com.naber.app.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.naber.app.MainActivity
import com.naber.app.NaberApp
import com.naber.app.R

object Notifications {

    const val CALL_NOTIFICATION_ID = 42
    // Sohbet bildirimleri conversationId + 1000 kullaniyor; durtme icin
    // ayri ve yeterince uzak bir taban degeri.
    private const val POKE_NOTIFICATION_BASE_ID = 900_000

    fun showMessage(context: Context, senderName: String, preview: String, conversationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NaberApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply {
                // Android 8 oncesinde ses kanaldan degil bildirimden gelir.
                SoundPlayer.uriFor(context, SoundPlayer.RECEIVED)?.let { setSound(it) }
            }
            .build()

        notify(context, conversationId + 1000, notification)
    }

    /** Durtme bildirimi. Tiklaninca sohbete degil, durten kisinin profiline gider. */
    fun showPoke(context: Context, fromName: String, fromUserId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_POKE_USER_ID, fromUserId)
        }
        val pending = PendingIntent.getActivity(
            context,
            POKE_NOTIFICATION_BASE_ID + fromUserId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NaberApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Naber")
            .setContentText("$fromName seni durttu!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply {
                SoundPlayer.uriFor(context, SoundPlayer.RECEIVED)?.let { setSound(it) }
            }
            .build()

        notify(context, POKE_NOTIFICATION_BASE_ID + fromUserId, notification)
    }

    /**
     * Gelen arama bildirimi.
     * Tam ekran niyet (full-screen intent) sayesinde uygulama kapaliyken ve
     * ekran kilitliyken de arama ekrani acilir.
     */
    fun showIncomingCall(context: Context, callerName: String, callId: Int, isGroup: Boolean) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
        }
        val open = PendingIntent.getActivity(
            context,
            callId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_CALL_ACCEPT, true)
        }
        val accept = PendingIntent.getActivity(
            context,
            callId + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reject = PendingIntent.getBroadcast(
            context,
            callId + 2,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_REJECT
                putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NaberApp.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName)
            .setContentText(if (isGroup) "Grup aramasi" else "Gelen sesli arama")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .addAction(0, "Reddet", reject)
            .addAction(0, "Kabul et", accept)
            .apply {
                SoundPlayer.uriFor(context, SoundPlayer.RINGTONE)?.let { setSound(it) }
            }
            .build()

        notify(context, CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCall(context: Context) {
        runCatching { manager(context).cancel(CALL_NOTIFICATION_ID) }
    }

    fun cancelConversation(context: Context, conversationId: Int) {
        runCatching { manager(context).cancel(conversationId + 1000) }
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching { manager(context).notify(id, notification) }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
