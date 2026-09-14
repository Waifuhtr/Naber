package com.naber.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.naber.app.Naber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Uygulama kapaliyken bildirimler buradan gelir.
 * Firebase yapilandirilmamissa (google-services.json yoksa) bu servis hic
 * calismaz; uygulama acikken mesajlar zaten olay akisindan gelir.
 */
class NaberMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Naber.init(applicationContext)
        if (!Naber.session.isLoggedIn) {
            Naber.session.pushToken = token
            return
        }
        scope.launch { runCatching { Naber.api.registerDevice(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Naber.init(applicationContext)
        val data = message.data

        when (data["type"]) {
            "call" -> {
                val callId = data["call_id"]?.toIntOrNull() ?: return
                Notifications.showIncomingCall(
                    this,
                    data["caller_name"] ?: "Bilinmeyen",
                    callId
                )
            }

            else -> {
                val conversationId = data["conversation_id"]?.toIntOrNull() ?: 0
                val sender = data["sender_name"] ?: message.notification?.title ?: "Yeni mesaj"
                val preview = data["preview"] ?: message.notification?.body ?: ""
                Notifications.showMessage(this, sender, preview, conversationId)
            }
        }
    }
}
