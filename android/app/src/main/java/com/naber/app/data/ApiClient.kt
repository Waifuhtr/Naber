package com.naber.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int = 0) : Exception(message)

/** WordPress eklentisi ile konusan tek HTTP istemcisi. */
class ApiClient(private val session: Session) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Uzun yoklama (long-poll) icin ayri istemci.
     *
     * Ayri Dispatcher ve ConnectionPool sart: newBuilder() bunlari
     * paylasir, o zaman 25 saniye acik duran yoklama istegi normal
     * isteklerle ayni kuyrugu ve baglanti havuzunu mesgul eder ve mesaj
     * gonderimi gecikir.
     */
    private val pollClient = client.newBuilder()
        .readTimeout(45, TimeUnit.SECONDS)
        .dispatcher(Dispatcher())
        .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
        .build()

    /**
     * Su an acik olan yoklama istegi.
     *
     * Kullanici bir sey gonderdiginde bu istek iptal edilir: sunucudaki
     * PHP isciligi boylece serbest kalir ve gonderim kuyrukta beklemez.
     * Paylasilan barindirmalarda PHP-FPM isci sayisi az oldugu icin bu,
     * gecikmenin en buyuk kaynagi.
     */
    @Volatile
    private var pollCall: okhttp3.Call? = null

    /** Acik yoklama istegini keser; yoklama dongusu hemen yenisini acar. */
    fun cancelPolling() {
        runCatching { pollCall?.cancel() }
        pollCall = null
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun endpoint(path: String) = "${session.baseUrl}/wp-json/naber/v1$path"

    private fun buildRequest(path: String, method: String, body: RequestBody?, query: Map<String, Any?>): Request {
        val url = StringBuilder(endpoint(path))
        if (query.isNotEmpty()) {
            val encoded = query.entries.filter { it.value != null }.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value.toString(), "UTF-8")}"
            }
            if (encoded.isNotEmpty()) url.append("?").append(encoded)
        }
        val builder = Request.Builder().url(url.toString())
        if (session.token.isNotEmpty()) builder.header("Authorization", "Bearer ${session.token}")
        builder.header("Accept", "application/json")
        return when (method) {
            "GET" -> builder.get().build()
            "DELETE" -> builder.delete(body ?: "{}".toRequestBody(jsonType)).build()
            else -> builder.post(body ?: "{}".toRequestBody(jsonType)).build()
        }
    }

    private suspend fun call(
        path: String,
        method: String = "GET",
        payload: JSONObject? = null,
        query: Map<String, Any?> = emptyMap(),
        longPoll: Boolean = false,
        /**
         * Kullanicinin ekranda bekledigi istekler (mesaj gonderme, silme,
         * duzenleme, reaksiyon) icin true. Bu durumda acik yoklama istegi
         * kesilir; sunucudaki PHP isciligi serbest kalir ve istek kuyrukta
         * beklemez. Arka plan istekleri (okundu, presence, yaziyor) bunu
         * kullanmaz, yoksa yoklama bosuna surekli yeniden acilir.
         */
        urgent: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        if (urgent) cancelPolling()
        val body = payload?.toString()?.toRequestBody(jsonType)
        val request = buildRequest(path, method, body, query)
        val httpClient = if (longPoll) pollClient else client
        try {
            val httpCall = httpClient.newCall(request)
            if (longPoll) pollCall = httpCall
            httpCall.execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (!response.isSuccessful) {
                    val message = json.optString("message").ifBlank { "Sunucu hatasi (${response.code})" }
                    throw ApiException(message, response.code)
                }
                json
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Baglanti kurulamadi: ${e.message ?: "bilinmeyen hata"}")
        }
    }

    // ----------------------------------------------------------- hesap

    suspend fun login(username: String, password: String): User {
        val json = call("/login", "POST", JSONObject().put("username", username).put("password", password).put("device", android.os.Build.MODEL))
        return storeSession(json)
    }

    suspend fun register(username: String, password: String, displayName: String): User {
        val json = call(
            "/register", "POST",
            JSONObject()
                .put("username", username)
                .put("password", password)
                .put("display_name", displayName)
                .put("device", android.os.Build.MODEL)
        )
        return storeSession(json)
    }

    private fun storeSession(json: JSONObject): User {
        session.token = json.optString("token")
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Kullanici bilgisi alinamadi.")
        session.user = user
        return user
    }

    suspend fun logout() {
        runCatching { call("/logout", "POST", JSONObject().put("device_token", session.pushToken)) }
        session.clear()
        LocalMedia.clear()
        MemoryCache.clear()
    }

    suspend fun me(): User {
        val json = call("/me")
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Oturum gecersiz.")
        session.user = user
        return user
    }

    suspend fun updateProfile(displayName: String?, about: String?, avatarMediaId: Int?): User {
        val payload = JSONObject()
        displayName?.let { payload.put("display_name", it) }
        about?.let { payload.put("about", it) }
        avatarMediaId?.let { payload.put("avatar_media_id", it) }
        val json = call("/me", "POST", payload)
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Profil guncellenemedi.")
        session.user = user
        return user
    }

    /** Gizlilik ayarlari; ikisi de simetriktir (gizleyen baskasininkini goremez). */
    suspend fun setPrivacy(hideLastSeen: Boolean, hideRead: Boolean): User {
        val json = call(
            "/me/privacy", "POST",
            JSONObject().put("hide_last_seen", hideLastSeen).put("hide_read", hideRead)
        )
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Ayar kaydedilemedi.")
        session.user = user
        return user
    }

    // ----------------------------------------------------------- kisiler

    suspend fun users(search: String = ""): List<User> =
        User.listFrom(call("/users", "GET", query = mapOf("search" to search.ifBlank { null })).optJSONArray("users"))

    suspend fun contacts(): List<User> = User.listFrom(call("/contacts").optJSONArray("users"))

    /** Naber adresi (kullaniciadi@naber.com) veya kullanici adi ile arama. */
    suspend fun lookup(query: String): User =
        User.from(call("/users/lookup", "GET", query = mapOf("q" to query)).optJSONObject("user"))
            ?: throw ApiException("Kullanici bulunamadi.")

    /** Baska bir kullanicinin profil ekrani icin guncel bilgisi. */
    suspend fun userProfile(userId: Int): UserProfile {
        val userJson = call("/users/$userId").optJSONObject("user")
        val user = User.from(userJson) ?: throw ApiException("Kullanici bulunamadi.")
        return UserProfile(
            user = user,
            pokeCooldown = userJson?.optInt("poke_cooldown") ?: 0,
            blocked = userJson?.optBoolean("blocked") == true
        )
    }

    /** Engeller veya engeli kaldirir. @return sunucunun dondugu son durum. */
    suspend fun setBlocked(userId: Int, blocked: Boolean): Boolean =
        call("/users/$userId/block", "POST", JSONObject().put("blocked", blocked))
            .optBoolean("blocked", blocked)

    /** Bir kullaniciyi durtme; sohbet acmaz, yalnizca bildirim gonderir. */
    suspend fun pokeUser(userId: Int): Int =
        call("/users/$userId/poke", "POST").optInt("next_allowed_in", 60)

    suspend fun addContact(userId: Int = 0, email: String = ""): Pair<User, Int> {
        val payload = JSONObject()
        if (userId > 0) payload.put("user_id", userId) else payload.put("email", email)
        val json = call("/contacts", "POST", payload)
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Kisi eklenemedi.")
        return user to json.optInt("conversation_id")
    }

    suspend fun removeContact(userId: Int) {
        call("/contacts/$userId", "DELETE")
    }

    // ----------------------------------------------------------- sohbetler

    /**
     * Sohbet listesi.
     *
     * [since] sifirdan buyukse sunucu yalnizca o zamandan sonra degisen
     * sohbetleri doner; liste her yoklamada bastan indirilmez.
     */
    suspend fun chats(since: Long = 0L): ChatSync {
        val json = call("/chats", "GET", query = mapOf("since" to since.takeIf { it > 0L }))
        return ChatSync(
            chats = Chat.listFrom(json.optJSONArray("chats")),
            unreadTotal = json.optInt("unread_total"),
            partial = json.optBoolean("partial"),
            syncTime = json.optLong("sync_time")
        )
    }

    suspend fun openChat(userId: Int): Int = call("/chats", "POST", JSONObject().put("user_id", userId)).optInt("id")

    suspend fun createGroup(title: String, memberIds: List<Int>, avatarMediaId: Int?, about: String = ""): Chat {
        val payload = JSONObject()
            .put("title", title)
            .put("about", about)
            .put("members", JSONArray(memberIds))
        avatarMediaId?.let { payload.put("avatar_media_id", it) }
        return Chat.from(call("/groups", "POST", payload).optJSONObject("chat")) ?: throw ApiException("Grup olusturulamadi.")
    }

    suspend fun chatInfo(conversationId: Int): Chat =
        Chat.from(call("/chats/$conversationId").optJSONObject("chat")) ?: throw ApiException("Sohbet bulunamadi.")

    /** Davet koduyla gruba katilir. */
    suspend fun joinGroupByCode(code: String): Chat =
        Chat.from(call("/groups/join", "POST", JSONObject().put("code", code)).optJSONObject("chat"))
            ?: throw ApiException("Bu koda sahip bir grup bulunamadi.")

    /** Grup yoneticisi eski kodu gecersiz kilip yenisini uretir. */
    suspend fun regenerateInviteCode(conversationId: Int): String =
        call("/groups/$conversationId/invite-code/regenerate", "POST").optString("invite_code")

    suspend fun updateGroup(
        conversationId: Int,
        title: String?,
        about: String?,
        avatarMediaId: Int?,
        mentionAllAdmins: Boolean? = null
    ): Chat {
        val payload = JSONObject()
        title?.let { payload.put("title", it) }
        about?.let { payload.put("about", it) }
        avatarMediaId?.let { payload.put("avatar_media_id", it) }
        mentionAllAdmins?.let { payload.put("mention_all_admins", it) }
        return Chat.from(call("/chats/$conversationId", "POST", payload).optJSONObject("chat"))
            ?: throw ApiException("Grup guncellenemedi.")
    }

    /** Grubu tamamen siler; sunucu yalnizca grup sahibine izin verir. */
    suspend fun deleteGroup(conversationId: Int) {
        call("/chats/$conversationId", "DELETE")
    }

    suspend fun addGroupMembers(conversationId: Int, memberIds: List<Int>): Chat =
        Chat.from(call("/chats/$conversationId/members", "POST", JSONObject().put("members", JSONArray(memberIds))).optJSONObject("chat"))
            ?: throw ApiException("Uye eklenemedi.")

    /**
     * Uye cikarma.
     *
     * Grup sahibini cikarmaya kalkisilirsa sunucu "saka savunmasi"
     * yaniti dondurur: cikaran kisi gecici olarak atilir. O yuzden
     * sonuc ya guncel sohbet ya da saka bilgisidir.
     */
    suspend fun removeGroupMember(conversationId: Int, userId: Int): RemoveMemberResult {
        val json = call("/chats/$conversationId/members/$userId", "DELETE")
        json.optJSONObject("prank")?.let { prank ->
            return RemoveMemberResult(
                prank = GroupPrank(
                    message = prank.optString("message"),
                    restoreSeconds = prank.optInt("restore_seconds", 10),
                    victimId = prank.optInt("victim_id")
                )
            )
        }
        val chat = Chat.from(json.optJSONObject("chat")) ?: throw ApiException("Uye cikarilamadi.")
        return RemoveMemberResult(chat = chat)
    }

    /**
     * Rol ve ayrintili yetkileri birlikte ayarlar.
     * [role] "owner" verilirse sahiplik devredilir.
     * [perms] null ise yetkiler degistirilmez.
     */
    suspend fun setGroupRole(
        conversationId: Int,
        userId: Int,
        role: String,
        perms: List<String>? = null
    ): Chat {
        val body = JSONObject().put("role", role)
        if (perms != null) body.put("perms", JSONArray(perms))
        return Chat.from(call("/chats/$conversationId/members/$userId/role", "POST", body).optJSONObject("chat"))
            ?: throw ApiException("Rol degistirilemedi.")
    }

    /** Mesaji sohbetin en ustune sabitler ya da sabitlemeyi kaldirir. */
    suspend fun pinMessage(messageId: Int, pinned: Boolean): Chat? =
        Chat.from(
            call("/messages/$messageId/pin", "POST", JSONObject().put("pinned", pinned), urgent = true)
                .optJSONObject("chat")
        )

    /** Grup yoneticisi bir uyeyi sohbette susturur veya susturmayi kaldirir. */
    suspend fun setMemberMuted(conversationId: Int, userId: Int, muted: Boolean): Chat =
        Chat.from(call("/chats/$conversationId/members/$userId/mute", "POST", JSONObject().put("muted", muted)).optJSONObject("chat"))
            ?: throw ApiException("Susturma degistirilemedi.")

    suspend fun leaveGroup(conversationId: Int) {
        call("/chats/$conversationId/leave", "POST")
    }

    /** [durationSeconds] 0 ise suresiz sessize alir. */
    suspend fun setChatNotifications(conversationId: Int, muted: Boolean, durationSeconds: Int = 0): Chat? {
        val json = call(
            "/chats/$conversationId/notifications", "POST",
            JSONObject().put("muted", muted).put("duration_seconds", durationSeconds)
        )
        return Chat.from(json.optJSONObject("chat"))
    }

    /** Sohbeti kullanicinin kendi listesinde sabitler/kaldirir. */
    suspend fun setChatPinned(conversationId: Int, pinned: Boolean): Chat? {
        val json = call("/chats/$conversationId/pin", "POST", JSONObject().put("pinned", pinned))
        return Chat.from(json.optJSONObject("chat"))
    }

    /** Kaybolan mesajlar: 0 kapatir, digerleri mesaj omrunu saniye olarak belirler. */
    suspend fun setDisappearing(conversationId: Int, seconds: Int): Chat? {
        val json = call("/chats/$conversationId/disappearing", "POST", JSONObject().put("seconds", seconds))
        return Chat.from(json.optJSONObject("chat"))
    }

    suspend fun messages(conversationId: Int, before: Int? = null, limit: Int = 50): Triple<List<Message>, Chat?, List<TypingUser>> {
        val json = call("/chats/$conversationId/messages", "GET", query = mapOf("before" to before, "limit" to limit))
        val typing = json.optJSONArray("typing").mapObjects { TypingUser(it.optInt("id"), it.optString("name")) }
        return Triple(Message.listFrom(json.optJSONArray("messages")), Chat.from(json.optJSONObject("chat")), typing)
    }

    /** Anket mesaji olusturur. */
    suspend fun sendPoll(
        conversationId: Int,
        question: String,
        options: List<String>,
        multiple: Boolean,
        clientId: String
    ): Message = Message.from(
        call(
            "/messages", "POST",
            JSONObject()
                .put("conversation_id", conversationId)
                .put("type", "poll")
                .put("question", question)
                .put("options", JSONArray(options))
                .put("multiple", multiple)
                .put("client_id", clientId),
            urgent = true
        ).optJSONObject("message")
    ) ?: throw ApiException("Anket olusturulamadi.")

    /** Ankette oy verir; ayni secenege tekrar basmak oyu geri ceker. */
    suspend fun votePoll(pollId: Int, optionIndex: Int): Poll =
        Poll.from(
            call("/polls/$pollId/vote", "POST", JSONObject().put("option", optionIndex)).optJSONObject("poll")
        ) ?: throw ApiException("Oy kaydedilemedi.")

    /** Sesli mesaj; govdesi saniye cinsinden suredir. */
    suspend fun sendAudio(conversationId: Int, mediaId: Int, seconds: Int, clientId: String, replyTo: Int = 0): Message =
        Message.from(
            call(
                "/messages", "POST",
                JSONObject()
                    .put("conversation_id", conversationId)
                    .put("type", "audio")
                    .put("media_id", mediaId)
                    .put("body", seconds.toString())
                    .put("client_id", clientId)
                    .put("reply_to", replyTo),
                urgent = true
            ).optJSONObject("message")
        ) ?: throw ApiException("Sesli mesaj gonderilemedi.")

    /** Konum mesaji; govdesi "enlem,boylam" bicimindedir. */
    suspend fun sendLocation(conversationId: Int, latitude: Double, longitude: Double, clientId: String): Message =
        Message.from(
            call(
                "/messages", "POST",
                JSONObject()
                    .put("conversation_id", conversationId)
                    .put("type", "location")
                    .put("body", formatLocation(latitude, longitude))
                    .put("client_id", clientId),
                urgent = true
            ).optJSONObject("message")
        ) ?: throw ApiException("Konum gonderilemedi.")

    suspend fun sendText(conversationId: Int, body: String, clientId: String, replyTo: Int = 0): Message =
        Message.from(
            call(
                "/messages", "POST",
                JSONObject()
                    .put("conversation_id", conversationId)
                    .put("type", "text")
                    .put("body", body)
                    .put("client_id", clientId)
                    .put("reply_to", replyTo),
                urgent = true
            ).optJSONObject("message")
        ) ?: throw ApiException("Mesaj gonderilemedi.")

    /**
     * [preview] gorselin cok kucuk base64 JPEG on izlemesidir; mesajla birlikte
     * tasinir, boylece alici asil dosya inmeden once bulanik bir goruntu gorur.
     */
    suspend fun sendImage(
        conversationId: Int,
        mediaId: Int,
        caption: String,
        clientId: String,
        preview: String = "",
        replyTo: Int = 0
    ): Message =
        Message.from(
            call(
                "/messages", "POST",
                JSONObject()
                    .put("conversation_id", conversationId)
                    .put("type", "image")
                    .put("media_id", mediaId)
                    .put("body", caption)
                    .put("client_id", clientId)
                    .put("preview", preview)
                    .put("reply_to", replyTo),
                urgent = true
            ).optJSONObject("message")
        ) ?: throw ApiException("Gorsel gonderilemedi.")

    /** scope = "all" (herkesten sil) veya "me" (yalnizca bende gizle). */
    suspend fun deleteMessage(messageId: Int, scope: String = "all") {
        call("/messages/$messageId", "DELETE", JSONObject().put("scope", scope), query = mapOf("scope" to scope), urgent = true)
    }

    /** Yalnizca metin mesajlari, yalnizca gonderen ve gonderimden sonraki 15 dakika icinde. */
    suspend fun editMessage(messageId: Int, body: String): Message =
        Message.from(
            call("/messages/$messageId", "POST", JSONObject().put("body", body), urgent = true).optJSONObject("message")
        ) ?: throw ApiException("Mesaj duzenlenemedi.")

    /**
     * Mesaja emoji reaksiyonu birakir/kaldirir. Ayni emojiye tekrar basmak
     * kaldirir, farkli emoji basmak degistirir (WhatsApp'taki gibi).
     * @return guncel reaksiyon ozeti.
     */
    suspend fun reactToMessage(messageId: Int, emoji: String): List<MessageReaction> {
        val json = call("/messages/$messageId/react", "POST", JSONObject().put("emoji", emoji), urgent = true)
        return json.optJSONArray("reactions").mapObjects { MessageReaction.from(it) }
    }

    /** Bir mesaji baska bir sohbete iletir; gorsel varsa tekrar yuklenmez. */
    suspend fun forwardMessage(messageId: Int, targetConversationId: Int): Message =
        Message.from(
            call(
                "/messages/$messageId/forward", "POST",
                JSONObject().put("conversation_id", targetConversationId)
            ).optJSONObject("message")
        ) ?: throw ApiException("Mesaj iletilemedi.")

    suspend fun messageInfo(messageId: Int): MessageInfo {
        val json = call("/messages/$messageId/info").optJSONObject("info")
            ?: throw ApiException("Mesaj bilgisi alinamadi.")
        val media = json.optJSONObject("media")
        return MessageInfo(
            id = json.optInt("id"),
            type = json.optString("type"),
            senderName = json.optJSONObject("sender")?.optString("display_name").orEmpty(),
            createdAt = json.optLong("created_at"),
            deliveredAt = json.optLong("delivered_at"),
            readAt = json.optLong("read_at"),
            deleted = json.optBoolean("deleted"),
            own = json.optBoolean("own"),
            recipients = json.optJSONArray("recipients").mapObjects {
                MessageRecipient(
                    id = it.optInt("id"),
                    name = it.optString("name"),
                    delivered = it.optBoolean("delivered"),
                    read = it.optBoolean("read")
                )
            },
            mediaSize = media?.optLong("size") ?: 0L,
            mediaWidth = media?.optInt("width") ?: 0,
            mediaHeight = media?.optInt("height") ?: 0,
            mediaMime = media?.optString("mime").orEmpty()
        )
    }

    /** Gorsel acilmazsa tazelenmis adres almak icin. */
    suspend fun mediaUrl(mediaId: Int): Media =
        Media.from(call("/media/$mediaId/url").optJSONObject("media"))
            ?: throw ApiException("Medya adresi alinamadi.")

    suspend fun markRead(conversationId: Int) {
        runCatching { call("/chats/$conversationId/read", "POST") }
    }

    suspend fun sendTyping(conversationId: Int, typing: Boolean = true) {
        runCatching { call("/chats/$conversationId/typing", "POST", JSONObject().put("typing", typing)) }
    }

    // ----------------------------------------------------------- medya

    /**
     * 1) sunucudan tek kullanimlik B2 upload adresi al
     * 2) dosyayi dogrudan Backblaze'e yukle (ilerleme geri bildirimli)
     * 3) sunucuda kaydi tamamla
     */
    /**
     * Medya yukler (gorsel ya da ses).
     *
     * Ses kayitlarinda genislik/yukseklik anlamsizdir, 0 gecilir; yukleme
     * yolu ikisinde de ayni oldugu icin ayri bir islev yazilmadi.
     */
    suspend fun uploadMedia(
        bytes: ByteArray,
        mime: String,
        width: Int,
        height: Int,
        onProgress: (Int) -> Unit
    ): Media = withContext(Dispatchers.IO) {
        // Dosya ozeti: ayni gorsel daha once yuklendiyse sunucu var olan kaydi
        // doner ve tekrar yukleme yapilmaz (hem hizli hem de kotayi korur).
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        val prepare = call(
            "/media/upload-url", "POST",
            JSONObject()
                .put("mime", mime)
                .put("size", bytes.size)
                .put("width", width)
                .put("height", height)
                .put("hash", hash)
        )

        if (prepare.optBoolean("duplicate")) {
            Media.from(prepare.optJSONObject("media"))?.let {
                onProgress(100)
                return@withContext it
            }
        }

        val mediaId = prepare.optInt("media_id")
        val uploadUrl = prepare.optString("upload_url")
        val uploadToken = prepare.optString("token")
        val fileName = prepare.optString("file_name")

        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

        val progressBody = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                val chunk = 32 * 1024
                var written = 0
                while (written < bytes.size) {
                    val size = minOf(chunk, bytes.size - written)
                    sink.write(bytes, written, size)
                    written += size
                    onProgress((written * 100) / bytes.size)
                }
            }
        }

        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadToken)
            .header("X-Bz-File-Name", java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"))
            .header("Content-Type", mime)
            .header("X-Bz-Content-Sha1", sha1)
            .post(progressBody)
            .build()

        val uploadJson = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException("Yukleme basarisiz (${response.code})", response.code)
                runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Yukleme basarisiz: ${e.message ?: "baglanti hatasi"}")
        }

        val completed = call(
            "/media/complete", "POST",
            JSONObject().put("media_id", mediaId).put("file_id", uploadJson.optString("fileId")).put("size", bytes.size)
        )
        Media.from(completed.optJSONObject("media")) ?: throw ApiException("Medya kaydi tamamlanamadi.")
    }

    // ----------------------------------------------------------- olaylar

    /**
     * Uzun yoklama. Istemci elindeki durumun imzalarini gonderir; sunucu farkli
     * bir durum gorurse (yeni mesaj, cevrimici degisikligi, yaziyor, grup
     * degisikligi) hemen doner.
     */
    suspend fun events(
        sinceMessageId: Int,
        sinceSignalId: Int,
        conversationId: Int?,
        typingSignature: String,
        conversationCallSignature: String,
        presenceSignature: String,
        revisionSignature: String,
        wait: Int = 25
    ): EventBatch {
        val json = call(
            "/events", "GET",
            query = mapOf(
                "since_message_id" to sinceMessageId,
                "since_signal_id" to sinceSignalId,
                "conversation_id" to conversationId,
                "typing_signature" to typingSignature,
                "conversation_call_signature" to conversationCallSignature,
                "presence_signature" to presenceSignature,
                "revision_signature" to revisionSignature,
                "wait" to wait
            ),
            longPoll = true
        )
        return EventBatch(
            messages = Message.listFrom(json.optJSONArray("messages")),
            signals = json.optJSONArray("signals").mapObjects {
                Signal(it.optInt("id"), it.optInt("call_id"), it.optInt("sender_id"), it.optString("type"), it.optString("payload"))
            },
            incomingCall = CallInfo.from(json.optJSONObject("incoming_call")),
            conversationCall = CallInfo.from(json.optJSONObject("conversation_call")),
            readStates = json.optJSONArray("read_states").mapObjects {
                ReadState(it.optInt("conversation_id"), it.optInt("watermark"), it.optInt("delivered"))
            },
            typing = json.optJSONArray("typing").mapObjects { TypingUser(it.optInt("id"), it.optString("name")) },
            typingConversationId = json.optInt("typing_conversation_id"),
            presence = json.optJSONArray("presence").mapObjects {
                Presence(it.optInt("id"), it.optBoolean("online"), it.optLong("last_seen"))
            },
            revisions = json.optJSONArray("chat_revisions").mapObjects {
                ChatRevision(it.optInt("id"), it.optLong("updated_at"))
            },
            typingSignature = json.optString("typing_signature"),
            conversationCallSignature = json.optString("conversation_call_signature"),
            presenceSignature = json.optString("presence_signature"),
            revisionSignature = json.optString("revision_signature"),
            unreadTotal = json.optInt("unread_total"),
            sinceMessageId = json.optInt("since_message_id", sinceMessageId),
            sinceSignalId = json.optInt("since_signal_id", sinceSignalId)
        )
    }

    /** Uygulama on planda mi arka planda mi; karsi taraf "son gorulme"yi aninda gorur. */
    suspend fun setPresence(online: Boolean) {
        runCatching { call("/presence", "POST", JSONObject().put("online", online)) }
    }

    // ----------------------------------------------------------- arama

    // Metered kimlik bilgileri kisa surede degismez; sunucu zaten kendi
    // tarafinda onbellekliyor ama her arama kabulunde ayri bir HTTP istegi
    // gereksiz gecikme yaratir. Istemci tarafinda da kisa bir sure saklanir.
    private var cachedIceServers: List<IceServer>? = null
    private var iceServersCachedAt = 0L
    private val iceServersCacheMs = 5 * 60 * 1000L

    /**
     * ICE sunucu listesi. Son bes dakika icinde alindiysa tekrar sorulmaz;
     * [forceRefresh] ile onbellek atlanip taze veri istenebilir.
     */
    suspend fun iceServers(forceRefresh: Boolean = false): List<IceServer> {
        val cached = cachedIceServers
        val fresh = System.currentTimeMillis() - iceServersCachedAt < iceServersCacheMs
        if (!forceRefresh && cached != null && fresh) return cached

        val servers = IceServer.listFrom(call("/ice-servers").optJSONArray("ice_servers"))
        cachedIceServers = servers
        iceServersCachedAt = System.currentTimeMillis()
        return servers
    }

    suspend fun startCall(userId: Int = 0, conversationId: Int = 0): Triple<CallInfo, List<Int>, List<IceServer>> {
        val payload = JSONObject()
        if (conversationId > 0) payload.put("conversation_id", conversationId) else payload.put("user_id", userId)
        val json = call("/calls/start", "POST", payload)
        val info = CallInfo.from(json.optJSONObject("call")) ?: throw ApiException("Arama baslatilamadi.")
        val servers = IceServer.listFrom(json.optJSONArray("ice_servers"))
        // Arama baslatirken zaten taze ICE bilgisi geldi; onbellek buradan da beslenir.
        if (servers.isNotEmpty()) {
            cachedIceServers = servers
            iceServersCachedAt = System.currentTimeMillis()
        }
        return Triple(info, json.optJSONArray("peers").toIntList(), servers)
    }

    suspend fun callAction(callId: Int, action: String): Pair<CallInfo?, List<Int>> {
        val json = call("/calls/$callId/$action", "POST")
        return CallInfo.from(json.optJSONObject("call")) to json.optJSONArray("peers").toIntList()
    }

    suspend fun sendSignal(callId: Int, to: Int, type: String, payload: String) {
        call("/calls/$callId/signal", "POST", JSONObject().put("type", type).put("payload", payload).put("to", to))
    }

    suspend fun callSignals(callId: Int, since: Int): Triple<List<Signal>, CallInfo?, List<Int>> {
        val json = call("/calls/$callId/signals", "GET", query = mapOf("since" to since))
        val signals = json.optJSONArray("signals").mapObjects {
            Signal(it.optInt("id"), it.optInt("call_id"), it.optInt("sender_id"), it.optString("type"), it.optString("payload"))
        }
        return Triple(signals, CallInfo.from(json.optJSONObject("call")), json.optJSONArray("peers").toIntList())
    }

    /** Grup aramasinda yonetici islemleri. */
    suspend fun muteParticipant(callId: Int, userId: Int, muted: Boolean): CallInfo? =
        CallInfo.from(call("/calls/$callId/participants/$userId/mute", "POST", JSONObject().put("muted", muted)).optJSONObject("call"))

    suspend fun kickParticipant(callId: Int, userId: Int): CallInfo? =
        CallInfo.from(call("/calls/$callId/participants/$userId/kick", "POST").optJSONObject("call"))

    suspend fun callHistory(): List<CallInfo> = CallInfo.listFrom(call("/calls").optJSONArray("calls"))

    // ----------------------------------------------------------- cihaz

    suspend fun registerDevice(token: String) {
        session.pushToken = token
        runCatching { call("/devices", "POST", JSONObject().put("token", token).put("platform", "android")) }
    }

    // ----------------------------------------------------------- yonetim

    suspend fun adminStats(): AdminStats {
        val json = call("/admin/stats")
        val users = json.optJSONObject("users") ?: JSONObject()
        val messages = json.optJSONObject("messages") ?: JSONObject()
        val storage = json.optJSONObject("storage") ?: JSONObject()
        val calls = json.optJSONObject("calls") ?: JSONObject()
        return AdminStats(
            totalUsers = users.optInt("total"),
            onlineUsers = users.optInt("online"),
            bannedUsers = users.optInt("banned"),
            totalMessages = messages.optInt("total"),
            todayMessages = messages.optInt("today"),
            conversations = json.optInt("conversations"),
            groups = json.optInt("groups"),
            storageFiles = storage.optInt("files"),
            storageBytes = storage.optLong("bytes"),
            totalCalls = calls.optInt("total"),
            callSeconds = calls.optInt("seconds"),
            missedCalls = calls.optInt("missed"),
            groupCalls = calls.optInt("group"),
            storageReady = json.optBoolean("storage_ready"),
            pushReady = json.optBoolean("push_ready"),
            turnConfigured = json.optBoolean("turn_configured"),
            server = json.optString("server"),
            pluginVersion = json.optString("plugin_version"),
            emailDomain = json.optString("email_domain"),
            recentUsers = User.listFrom(json.optJSONArray("recent_users"))
        )
    }

    suspend fun adminUsers(search: String = ""): List<User> =
        User.listFrom(call("/admin/users", "GET", query = mapOf("search" to search.ifBlank { null })).optJSONArray("users"))

    suspend fun adminUpdateUser(
        userId: Int,
        banned: Boolean? = null,
        reason: String? = null,
        disabled: Boolean? = null,
        admin: Boolean? = null,
        displayName: String? = null,
        password: String? = null,
        logout: Boolean? = null
    ): User? {
        val payload = JSONObject()
        banned?.let { payload.put("banned", it) }
        reason?.let { payload.put("reason", it) }
        disabled?.let { payload.put("disabled", it) }
        admin?.let { payload.put("admin", it) }
        displayName?.let { payload.put("display_name", it) }
        password?.let { payload.put("password", it) }
        logout?.let { payload.put("logout", it) }
        return User.from(call("/admin/users/$userId", "POST", payload).optJSONObject("user"))
    }

    suspend fun adminDeleteUser(userId: Int) {
        call("/admin/users/$userId", "DELETE")
    }

    suspend fun adminChats(): List<AdminChat> =
        call("/admin/chats").optJSONArray("chats").mapObjects {
            AdminChat(
                id = it.optInt("id"),
                type = it.optString("type"),
                title = it.optString("title"),
                memberCount = it.optInt("member_count"),
                messageCount = it.optInt("message_count"),
                updatedAt = it.optLong("updated_at")
            )
        }

    suspend fun adminDeleteChat(conversationId: Int) {
        call("/admin/chats/$conversationId", "DELETE")
    }

    suspend fun adminSettings(): ServerSettings {
        val json = call("/admin/settings")
        return ServerSettings(
            server = json.optString("server"),
            pluginVersion = json.optString("plugin_version"),
            bucketName = json.optString("bucket_name"),
            bucketId = json.optString("bucket_id"),
            keyId = json.optString("key_id"),
            pathPrefix = json.optString("path_prefix"),
            maxUploadMb = json.optInt("max_upload_mb"),
            linkTtl = json.optInt("link_ttl"),
            publicBaseUrl = json.optString("public_base_url"),
            turnUrls = json.optString("turn_urls"),
            stunUrls = json.optString("stun_urls"),
            fcmProjectId = json.optString("fcm_project_id"),
            emailDomain = json.optString("email_domain"),
            allowRegistration = json.optBoolean("allow_registration"),
            storageReady = json.optBoolean("storage_ready"),
            pushReady = json.optBoolean("push_ready")
        )
    }

    suspend fun adminStorageTest(writeTest: Boolean = true): StorageTestResult {
        val json = call("/admin/storage/test", "POST", JSONObject().put("write_test", writeTest))
        return StorageTestResult(
            ok = json.optBoolean("ok"),
            message = json.optString("message"),
            bucketId = json.optString("bucket_id"),
            steps = json.optJSONArray("steps").mapObjects {
                StorageTestStep(it.optString("label"), it.optBoolean("ok"), it.optString("message"))
            }
        )
    }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) out.add(optInt(i))
    return out
}
