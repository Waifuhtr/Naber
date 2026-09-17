package com.naber.app.data

/**
 * Surec boyunca yasayan sohbet onbellegi.
 *
 * Sohbetten cikip tekrar girildiginde ekran bos acilmasin diye: diskten
 * JSON okuyup cozmek de, sunucuyu beklemek de gorunur bir gecikme
 * yaratiyordu. Bellekteki kopya aninda hazir; disk ve ag guncellemeleri
 * ustune biner.
 *
 * Yalnizca onbellek: kaybi onemsiz oldugu icin uygulama kapaninca
 * silinir, kalici kayit LocalStore'da durur.
 */
object MemoryCache {

    /** Sohbet basina saklanan en fazla mesaj; LocalStore ile ayni sinir. */
    private const val MAX_MESSAGES = 300

    /** Onbellekte tutulan en fazla sohbet sayisi. */
    private const val MAX_CONVERSATIONS = 20

    private val messages = LinkedHashMap<Int, List<Message>>()
    private val chats = HashMap<Int, Chat>()

    @Synchronized
    fun messages(conversationId: Int): List<Message>? = messages[conversationId]

    @Synchronized
    fun putMessages(conversationId: Int, list: List<Message>) {
        if (conversationId <= 0) return
        // Son kullanilan sohbet sona tasinir; sinir asilinca en eski dusuruluyor.
        messages.remove(conversationId)
        messages[conversationId] = list.takeLast(MAX_MESSAGES)
        while (messages.size > MAX_CONVERSATIONS) {
            val oldest = messages.keys.firstOrNull() ?: break
            messages.remove(oldest)
        }
    }

    @Synchronized
    fun chat(conversationId: Int): Chat? = chats[conversationId]

    @Synchronized
    fun putChat(chat: Chat) {
        if (chat.id > 0) chats[chat.id] = chat
    }

    /** Sohbet listesi yenilendiginde baslik/uye bilgisi de tazelenir. */
    @Synchronized
    fun putChats(list: List<Chat>) {
        list.forEach { if (it.id > 0) chats[it.id] = it }
    }

    @Synchronized
    fun clear() {
        messages.clear()
        chats.clear()
    }
}
