package com.naber.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.naber.app.Naber
import com.naber.app.data.LocalStore
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
                val isGroup = data["call_type"] == "group"
                Notifications.showIncomingCall(
                    context = this,
                    callerName = if (isGroup) (data["group_title"] ?: "Grup aramasi") else (data["caller_name"] ?: "Bilinmeyen"),
                    callId = callId,
                    isGroup = isGroup
                )
            }

            "test" -> {
                Notifications.showMessage(this, "Naber", "Bildirim testi basarili.", 0)
            }

            "poke" -> {
                val fromId = data["from_id"]?.toIntOrNull() ?: 0
                val fromName = data["from_name"] ?: "Bir kullanici"
                if (fromId > 0) Notifications.showPoke(this, fromName, fromId)
            }

            else -> {
                val conversationId = data["conversation_id"]?.toIntOrNull() ?: 0
                val sender = data["sender_name"] ?: message.notification?.title ?: "Yeni mesaj"
                val preview = data["preview"] ?: message.notification?.body ?: ""
                Notifications.showMessage(this, sender, preview, conversationId)
                cacheInBackground(conversationId)
            }
        }
    }

    /**
     * Bildirim geldiginde sohbeti arka planda cihaza yazar.
     *
     * Boylece kullanici bildirime dokundugunda sohbet zaten dolu acilir;
     * mesajin gelmesini beklemez. Basarisiz olursa sessizce gecilir, bildirim
     * yine de gosterilmistir.
     */
    private fun cacheInBackground(conversationId: Int) {
        if (conversationId <= 0 || !Naber.session.isLoggedIn) return
        val context = applicationContext
        scope.launch {
            runCatching {
                val (list, _, _) = Naber.api.messages(conversationId)
                if (list.isNotEmpty()) {
                    // Gonderilemeyen mesajlar kuyrukta kalsin diye once mevcut
                    // kayit okunur, sunucudan gelenlerle birlestirilir.
                    val pending = LocalStore.loadMessages(context, conversationId).filter { it.id <= 0 }
                    LocalStore.saveMessages(
                        context,
                        conversationId,
                        (list + pending).distinctBy { it.key }.sortedBy { it.createdAt }
                    )
                }
                val sync = Naber.api.chats()
                LocalStore.saveChats(context, sync.chats, sync.unreadTotal)
            }
        }
    }
}
