package com.naber.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * WordPress eklentisi ile konusan tek HTTP istemcisi.
 * Tum istekler Bearer token ile imzalanir.
 */
class ApiClient(private val session: Session) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Uzun yoklama istekleri icin daha uzun okuma suresi. */
    private val pollClient = client.newBuilder()
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun endpoint(path: String): String {
        val base = session.baseUrl
        if (base.isEmpty()) throw ApiException("Sunucu adresi girilmemis.")
        return "$base/wp-json/naber/v1$path"
    }

    private fun buildRequest(path: String, method: String, body: RequestBody?, query: Map<String, Any?> = emptyMap()): Request {
        val url = StringBuilder(endpoint(path))
        if (query.isNotEmpty()) {
            url.append("?")
            url.append(query.entries.filter { it.value != null }.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value.toString(), "UTF-8")}"
            })
        }
        val builder = Request.Builder().url(url.toString())
        if (session.token.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${session.token}")
        }
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
        longPoll: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = payload?.toString()?.toRequestBody(jsonType)
        val request = buildRequest(path, method, body, query)
        val httpClient = if (longPoll) pollClient else client
        try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (!response.isSuccessful) {
                    val message = json.optString("message").ifBlank {
                        "Sunucu hatasi (${response.code})"
                    }
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
        session.token = json.optString("token")
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Kullanici bilgisi alinamadi.")
        session.user = user
        return user
    }

    suspend fun register(username: String, password: String, displayName: String, email: String): User {
        val json = call(
            "/register", "POST",
            JSONObject()
                .put("username", username)
                .put("password", password)
                .put("display_name", displayName)
                .put("email", email)
                .put("device", android.os.Build.MODEL)
        )
        session.token = json.optString("token")
        val user = User.from(json.optJSONObject("user")) ?: throw ApiException("Kullanici bilgisi alinamadi.")
        session.user = user
        return user
    }

    suspend fun logout() {
        runCatching { call("/logout", "POST", JSONObject().put("device_token", session.pushToken)) }
        session.clear()
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

    suspend fun users(search: String = ""): List<User> {
        val json = call("/users", "GET", query = mapOf("search" to search.ifBlank { null }))
        return json.optJSONArray("users").toList { User.from(it) }
    }

    // ----------------------------------------------------------- sohbet

    suspend fun chats(): Pair<List<ChatSummary>, Int> {
        val json = call("/chats")
        val chats = json.optJSONArray("chats").toList { ChatSummary.from(it) }
        return chats to json.optInt("unread_total")
    }

    suspend fun openChat(userId: Int): Int {
        val json = call("/chats", "POST", JSONObject().put("user_id", userId))
        return json.optInt("id")
    }

    suspend fun messages(conversationId: Int, before: Int? = null, limit: Int = 50): Triple<List<Message>, User?, Boolean> {
        val json = call(
            "/chats/$conversationId/messages", "GET",
            query = mapOf("before" to before, "limit" to limit)
        )
        return Triple(
            json.optJSONArray("messages").toList { Message.from(it) },
            User.from(json.optJSONObject("peer")),
            json.optBoolean("typing")
        )
    }

    suspend fun sendText(conversationId: Int, receiverId: Int, body: String, clientId: String): Message {
        val json = call(
            "/messages", "POST",
            JSONObject()
                .put("conversation_id", conversationId)
                .put("receiver_id", receiverId)
                .put("type", "text")
                .put("body", body)
                .put("client_id", clientId)
        )
        return Message.from(json.optJSONObject("message")) ?: throw ApiException("Mesaj gonderilemedi.")
    }

    suspend fun sendImage(conversationId: Int, receiverId: Int, mediaId: Int, caption: String, clientId: String): Message {
        val json = call(
            "/messages", "POST",
            JSONObject()
                .put("conversation_id", conversationId)
                .put("receiver_id", receiverId)
                .put("type", "image")
                .put("media_id", mediaId)
                .put("body", caption)
                .put("client_id", clientId)
        )
        return Message.from(json.optJSONObject("message")) ?: throw ApiException("Gorsel gonderilemedi.")
    }

    suspend fun markRead(conversationId: Int) {
        runCatching { call("/chats/$conversationId/read", "POST") }
    }

    suspend fun sendTyping(conversationId: Int) {
        runCatching { call("/chats/$conversationId/typing", "POST") }
    }

    // ----------------------------------------------------------- medya

    /**
     * Gorsel gonderme akisi:
     *  1) sunucudan tek kullanimlik B2 upload adresi al
     *  2) dosyayi dogrudan Backblaze'e yukle (ilerleme geri bildirimli)
     *  3) sunucuda kaydi tamamla
     * Secret key uygulamada tutulmaz; yalnizca kisa omurlu upload jetonu kullanilir.
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        mime: String,
        width: Int,
        height: Int,
        onProgress: (Int) -> Unit
    ): Media = withContext(Dispatchers.IO) {
        val prepare = call(
            "/media/upload-url", "POST",
            JSONObject().put("mime", mime).put("size", bytes.size).put("width", width).put("height", height)
        )
        val mediaId = prepare.optInt("media_id")
        val uploadUrl = prepare.optString("upload_url")
        val uploadToken = prepare.optString("token")
        val fileName = prepare.optString("file_name")

        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }

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
                if (!response.isSuccessful) {
                    throw ApiException("Yukleme basarisiz (${response.code})", response.code)
                }
                runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Yukleme basarisiz: ${e.message ?: "baglanti hatasi"}")
        }

        val completed = call(
            "/media/complete", "POST",
            JSONObject()
                .put("media_id", mediaId)
                .put("file_id", uploadJson.optString("fileId"))
                .put("size", bytes.size)
        )
        Media.from(completed.optJSONObject("media")) ?: throw ApiException("Medya kaydi tamamlanamadi.")
    }

    // ----------------------------------------------------------- olaylar

    suspend fun events(sinceMessageId: Int, sinceSignalId: Int, conversationId: Int?, wait: Int = 25): EventBatch {
        val json = call(
            "/events", "GET",
            query = mapOf(
                "since_message_id" to sinceMessageId,
                "since_signal_id" to sinceSignalId,
                "conversation_id" to conversationId,
                "wait" to wait
            ),
            longPoll = true
        )
        val typing = json.optJSONObject("typing")
        return EventBatch(
            messages = json.optJSONArray("messages").toList { Message.from(it) },
            signals = json.optJSONArray("signals").toList {
                Signal(it.optInt("id"), it.optInt("call_id"), it.optInt("sender_id"), it.optString("type"), it.optString("payload"))
            },
            incomingCall = CallInfo.from(json.optJSONObject("incoming_call")),
            readMessageIds = json.optJSONArray("read_receipts").toList { it.optInt("message_id") },
            typing = typing?.optBoolean("typing") ?: false,
            typingConversationId = typing?.optInt("conversation_id") ?: 0,
            peerOnline = typing?.optBoolean("online"),
            unreadTotal = json.optInt("unread_total"),
            sinceMessageId = json.optInt("since_message_id", sinceMessageId),
            sinceSignalId = json.optInt("since_signal_id", sinceSignalId)
        )
    }

    // ----------------------------------------------------------- arama

    suspend fun iceServers(): List<IceServer> =
        IceServer.listFrom(call("/ice-servers").optJSONArray("ice_servers"))

    suspend fun startCall(userId: Int): Pair<CallInfo, List<IceServer>> {
        val json = call("/calls/start", "POST", JSONObject().put("user_id", userId))
        val info = CallInfo.from(json.optJSONObject("call")) ?: throw ApiException("Arama baslatilamadi.")
        return info to IceServer.listFrom(json.optJSONArray("ice_servers"))
    }

    suspend fun callAction(callId: Int, action: String): CallInfo? =
        CallInfo.from(call("/calls/$callId/$action", "POST").optJSONObject("call"))

    suspend fun sendSignal(callId: Int, type: String, payload: String) {
        call("/calls/$callId/signal", "POST", JSONObject().put("type", type).put("payload", payload))
    }

    suspend fun callSignals(callId: Int, since: Int): Pair<List<Signal>, CallInfo?> {
        val json = call("/calls/$callId/signals", "GET", query = mapOf("since" to since))
        val signals = json.optJSONArray("signals").toList {
            Signal(it.optInt("id"), it.optInt("call_id"), it.optInt("sender_id"), it.optString("type"), it.optString("payload"))
        }
        return signals to CallInfo.from(json.optJSONObject("call"))
    }

    suspend fun callHistory(): List<CallInfo> =
        call("/calls").optJSONArray("calls").toList { CallInfo.from(it) }

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
            totalMessages = messages.optInt("total"),
            todayMessages = messages.optInt("today"),
            conversations = json.optInt("conversations"),
            storageFiles = storage.optInt("files"),
            storageBytes = storage.optLong("bytes"),
            totalCalls = calls.optInt("total"),
            callSeconds = calls.optInt("seconds"),
            missedCalls = calls.optInt("missed"),
            storageReady = json.optBoolean("storage_ready"),
            pushReady = json.optBoolean("push_ready"),
            turnConfigured = json.optBoolean("turn_configured"),
            recentUsers = json.optJSONArray("recent_users").toList { User.from(it) }
        )
    }

    suspend fun adminUsers(): List<User> = call("/admin/users").optJSONArray("users").toList { User.from(it) }

    suspend fun adminSetDisabled(userId: Int, disabled: Boolean): User? =
        User.from(call("/admin/users/$userId", "POST", JSONObject().put("disabled", disabled)).optJSONObject("user"))

    suspend fun adminDeleteUser(userId: Int) {
        call("/admin/users/$userId", "DELETE")
    }

    /** Yonetici, bucket bilgilerinin dogru olup olmadigini uygulamadan da sinayabilir. */
    suspend fun adminStorageTest(writeTest: Boolean = true): StorageTestResult {
        val json = call("/admin/storage/test", "POST", JSONObject().put("write_test", writeTest))
        val steps = json.optJSONArray("steps").toList {
            StorageTestStep(it.optString("label"), it.optBoolean("ok"), it.optString("message"))
        }
        return StorageTestResult(
            ok = json.optBoolean("ok"),
            message = json.optString("message"),
            bucketId = json.optString("bucket_id"),
            steps = steps
        )
    }
}

/** JSONArray -> List<T> kisayolu. */
private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        mapper(item)?.let { out.add(it) }
    }
    return out
}
