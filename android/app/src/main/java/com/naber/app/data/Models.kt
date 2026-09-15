package com.naber.app.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** WordPress'ten donen kullanici. is_admin bilgisi sunucudan gelir, uygulama tahmin etmez. */
@Immutable
data class User(
    val id: Int,
    val username: String,
    val displayName: String,
    val naberEmail: String,
    val avatar: String,
    /** Profil fotografinin medya kimligi; cihazda saklamak icin kullanilir. */
    val avatarId: Int = 0,
    val about: String,
    val lastSeen: Long,
    val online: Boolean,
    val isAdmin: Boolean,
    val isContact: Boolean = false,
    val disabled: Boolean = false,
    val banned: Boolean = false,
    val banReason: String = "",
    val registered: Long = 0,
    // Grup ve arama baglaminda dolan alanlar
    val role: String = "",
    val chatMuted: Boolean = false,
    val callStatus: String = "",
    val muted: Boolean = false
) {
    val isGroupAdmin: Boolean get() = role == "owner" || role == "admin"

    val roleLabel: String
        get() = when (role) {
            "owner" -> "Grup sahibi"
            "admin" -> "Yonetici"
            else -> ""
        }

    companion object {
        fun from(json: JSONObject?): User? {
            if (json == null) return null
            return User(
                id = json.optInt("id"),
                username = json.optString("username"),
                displayName = json.optString("display_name").ifBlank { json.optString("username") },
                naberEmail = json.optString("naber_email"),
                avatar = json.optString("avatar"),
                avatarId = json.optInt("avatar_id"),
                about = json.optString("about"),
                lastSeen = json.optLong("last_seen"),
                online = json.optBoolean("online"),
                isAdmin = json.optBoolean("is_admin"),
                isContact = json.optBoolean("is_contact"),
                disabled = json.optBoolean("disabled"),
                banned = json.optBoolean("banned"),
                banReason = json.optString("ban_reason"),
                registered = json.optLong("registered"),
                role = json.optString("role"),
                chatMuted = json.optBoolean("chat_muted"),
                callStatus = json.optString("call_status"),
                muted = json.optBoolean("muted")
            )
        }

        fun listFrom(array: JSONArray?): List<User> = array.mapObjects { from(it) }
    }
}

@Immutable
data class Media(
    val id: Int,
    val url: String,
    val mime: String,
    val width: Int,
    val height: Int
) {
    companion object {
        fun from(json: JSONObject?): Media? {
            if (json == null) return null
            return Media(
                id = json.optInt("id"),
                url = json.optString("url"),
                mime = json.optString("mime"),
                width = json.optInt("width"),
                height = json.optInt("height")
            )
        }
    }
}

enum class SendState { SENDING, SENT, FAILED }

@Immutable
data class Message(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val type: String,
    val body: String,
    val clientId: String,
    val isRead: Boolean,
    val deleted: Boolean,
    val createdAt: Long,
    val media: Media?,
    val senderName: String = "",
    val senderAvatar: String = "",
    val localImageUri: String? = null,
    val sendState: SendState = SendState.SENT,
    val uploadProgress: Int = 0,
    /** Cok kucuk base64 JPEG. Asil dosya inene kadar bulanik on izleme cizilir. */
    val preview: String = ""
) {
    val key: String get() = if (id > 0) "id-$id" else "c-$clientId"

    /** Once yerel dosya gosterilir; boylece gorsel aninda ekranda olur. */
    val displayImage: Any?
        get() = localImageUri
            ?: LocalMedia.uriFor(media?.id ?: 0, clientId)
            ?: media?.url?.takeIf { it.isNotBlank() }

    companion object {
        fun from(json: JSONObject?): Message? {
            if (json == null) return null
            return Message(
                id = json.optInt("id"),
                conversationId = json.optInt("conversation_id"),
                senderId = json.optInt("sender_id"),
                type = json.optString("type", "text"),
                body = json.optString("body"),
                clientId = json.optString("client_id"),
                isRead = json.optBoolean("is_read"),
                deleted = json.optBoolean("deleted"),
                createdAt = json.optLong("created_at"),
                media = Media.from(json.optJSONObject("media")),
                senderName = json.optString("sender_name"),
                senderAvatar = json.optString("sender_avatar"),
                preview = json.optString("preview"),
                // Sunucu bu alani gondermez; yalnizca cihazdaki kopyada bulunur.
                localImageUri = json.optString("local_image_uri").ifBlank { null }
            )
        }

        fun listFrom(array: JSONArray?): List<Message> = array.mapObjects { from(it) }
    }
}

@Immutable
data class Chat(
    val id: Int,
    val type: String,
    val title: String,
    val about: String,
    val avatar: String,
    val avatarId: Int,
    val peer: User?,
    val ownerId: Int,
    val memberCount: Int,
    val role: String,
    val chatMuted: Boolean,
    val notifyMuted: Boolean,
    val unread: Int,
    val updatedAt: Long,
    val lastMessage: Message?,
    val readWatermark: Int,
    val deliveredWatermark: Int,
    val members: List<User> = emptyList()
) {
    val isGroup: Boolean get() = type == "group"
    val amAdmin: Boolean get() = role == "owner" || role == "admin"
    val amOwner: Boolean get() = role == "owner"

    companion object {
        fun from(json: JSONObject?): Chat? {
            if (json == null) return null
            return Chat(
                id = json.optInt("id"),
                type = json.optString("type", "direct"),
                title = json.optString("title"),
                about = json.optString("about"),
                avatar = json.optString("avatar"),
                avatarId = json.optInt("avatar_id"),
                peer = User.from(json.optJSONObject("peer")),
                ownerId = json.optInt("owner_id"),
                memberCount = json.optInt("member_count"),
                role = json.optString("role"),
                chatMuted = json.optBoolean("chat_muted"),
                notifyMuted = json.optBoolean("notify_muted"),
                unread = json.optInt("unread"),
                updatedAt = json.optLong("updated_at"),
                lastMessage = Message.from(json.optJSONObject("last_message")),
                readWatermark = json.optInt("read_watermark"),
                deliveredWatermark = json.optInt("delivered_watermark"),
                members = User.listFrom(json.optJSONArray("members"))
            )
        }

        fun listFrom(array: JSONArray?): List<Chat> = array.mapObjects { from(it) }
    }
}

@Immutable
data class CallInfo(
    val id: Int,
    val type: String,
    val conversationId: Int,
    val groupTitle: String,
    val caller: User?,
    val callee: User?,
    val callerId: Int,
    val calleeId: Int,
    val status: String,
    val duration: Int,
    val createdAt: Long,
    val endReason: String,
    val participants: List<User>
) {
    val isGroup: Boolean get() = type == "group"

    companion object {
        fun from(json: JSONObject?): CallInfo? {
            if (json == null) return null
            return CallInfo(
                id = json.optInt("id"),
                type = json.optString("type", "direct"),
                conversationId = json.optInt("conversation_id"),
                groupTitle = json.optString("group_title"),
                caller = User.from(json.optJSONObject("caller")),
                callee = User.from(json.optJSONObject("callee")),
                callerId = json.optInt("caller_id"),
                calleeId = json.optInt("callee_id"),
                status = json.optString("status"),
                duration = json.optInt("duration"),
                createdAt = json.optLong("created_at"),
                endReason = json.optString("end_reason"),
                participants = User.listFrom(json.optJSONArray("participants"))
            )
        }

        fun listFrom(array: JSONArray?): List<CallInfo> = array.mapObjects { from(it) }
    }
}

@Immutable
data class IceServer(val urls: List<String>, val username: String, val credential: String) {
    companion object {
        fun listFrom(array: JSONArray?): List<IceServer> {
            if (array == null) return emptyList()
            val out = mutableListOf<IceServer>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val urls = mutableListOf<String>()
                when (val raw = item.opt("urls")) {
                    is String -> urls.add(raw)
                    is JSONArray -> for (j in 0 until raw.length()) urls.add(raw.optString(j))
                }
                if (urls.isEmpty()) continue
                out.add(IceServer(urls, item.optString("username"), item.optString("credential")))
            }
            return out
        }
    }
}

@Immutable
data class Signal(val id: Int, val callId: Int, val senderId: Int, val type: String, val payload: String)

@Immutable
data class ReadState(val conversationId: Int, val watermark: Int, val delivered: Int)

/** Mesaj durumu: tek tik, gri cift tik, mavi cift tik. */
enum class TickState { SENDING, SENT, DELIVERED, READ, FAILED }

@Immutable
data class MessageRecipient(val id: Int, val name: String, val delivered: Boolean, val read: Boolean)

@Immutable
data class MessageInfo(
    val id: Int,
    val type: String,
    val senderName: String,
    val createdAt: Long,
    val deliveredAt: Long,
    val readAt: Long,
    val deleted: Boolean,
    val own: Boolean,
    val recipients: List<MessageRecipient>,
    val mediaSize: Long,
    val mediaWidth: Int,
    val mediaHeight: Int,
    val mediaMime: String
)

@Immutable
data class Presence(val id: Int, val online: Boolean, val lastSeen: Long)

@Immutable
data class ChatRevision(val id: Int, val updatedAt: Long)

/** "Yaziyor" durumu; her yanitla tazelenir, 6 saniye sonra kendiliginden duser. */
@Immutable
data class TypingState(
    val conversationId: Int = 0,
    val users: List<TypingUser> = emptyList(),
    val at: Long = 0L
)

@Immutable
data class TypingUser(val id: Int, val name: String)

@Immutable
data class EventBatch(
    val messages: List<Message>,
    val signals: List<Signal>,
    val incomingCall: CallInfo?,
    val readStates: List<ReadState>,
    val typing: List<TypingUser>,
    val typingConversationId: Int,
    val presence: List<Presence>,
    val revisions: List<ChatRevision>,
    val typingSignature: String,
    val presenceSignature: String,
    val revisionSignature: String,
    val unreadTotal: Int,
    val sinceMessageId: Int,
    val sinceSignalId: Int
)

@Immutable
data class AdminStats(
    val totalUsers: Int,
    val onlineUsers: Int,
    val bannedUsers: Int,
    val totalMessages: Int,
    val todayMessages: Int,
    val conversations: Int,
    val groups: Int,
    val storageFiles: Int,
    val storageBytes: Long,
    val totalCalls: Int,
    val callSeconds: Int,
    val missedCalls: Int,
    val groupCalls: Int,
    val storageReady: Boolean,
    val pushReady: Boolean,
    val turnConfigured: Boolean,
    val server: String,
    val pluginVersion: String,
    val emailDomain: String,
    val recentUsers: List<User>
)

@Immutable
data class AdminChat(
    val id: Int,
    val type: String,
    val title: String,
    val memberCount: Int,
    val messageCount: Int,
    val updatedAt: Long
)

@Immutable
data class ServerSettings(
    val server: String,
    val pluginVersion: String,
    val bucketName: String,
    val bucketId: String,
    val keyId: String,
    val pathPrefix: String,
    val maxUploadMb: Int,
    val linkTtl: Int,
    val publicBaseUrl: String,
    val turnUrls: String,
    val stunUrls: String,
    val fcmProjectId: String,
    val emailDomain: String,
    val allowRegistration: Boolean,
    val storageReady: Boolean,
    val pushReady: Boolean
)

@Immutable
data class StorageTestStep(val label: String, val ok: Boolean, val message: String)

@Immutable
data class StorageTestResult(val ok: Boolean, val message: String, val bucketId: String, val steps: List<StorageTestStep>)

// ---------------------------------------------------------------- cevrimdisi
//
// Asagidaki donusturucular sohbet listesini ve mesaj gecmisini cihaza yazmak
// icin kullanilir. Sunucudan gelen bicimin aynisi yazildigi icin okuma
// tarafinda ayri bir cozumleyiciye gerek kalmaz.

fun Media.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("url", url)
    .put("mime", mime)
    .put("width", width)
    .put("height", height)

fun User.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("username", username)
    .put("display_name", displayName)
    .put("naber_email", naberEmail)
    .put("avatar", avatar)
    .put("avatar_id", avatarId)
    .put("about", about)
    .put("last_seen", lastSeen)
    .put("online", online)
    .put("is_admin", isAdmin)
    .put("is_contact", isContact)
    .put("disabled", disabled)
    .put("banned", banned)
    .put("ban_reason", banReason)
    .put("registered", registered)
    .put("role", role)
    .put("chat_muted", chatMuted)
    .put("call_status", callStatus)
    .put("muted", muted)

fun Message.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("conversation_id", conversationId)
    .put("sender_id", senderId)
    .put("type", type)
    .put("body", body)
    .put("client_id", clientId)
    .put("is_read", isRead)
    .put("deleted", deleted)
    .put("created_at", createdAt)
    .put("media", media?.toJson())
    .put("sender_name", senderName)
    .put("sender_avatar", senderAvatar)
    .put("preview", preview)
    .put("local_image_uri", localImageUri.orEmpty())

fun Chat.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("type", type)
    .put("title", title)
    .put("about", about)
    .put("avatar", avatar)
    .put("avatar_id", avatarId)
    .put("peer", peer?.toJson())
    .put("owner_id", ownerId)
    .put("member_count", memberCount)
    .put("role", role)
    .put("chat_muted", chatMuted)
    .put("notify_muted", notifyMuted)
    .put("unread", unread)
    .put("updated_at", updatedAt)
    .put("last_message", lastMessage?.toJson())
    .put("read_watermark", readWatermark)
    .put("delivered_watermark", deliveredWatermark)
    .put("members", JSONArray(members.map { it.toJson() }))

/** JSONArray -> List<T> kisayolu. */
internal fun <T> JSONArray?.mapObjects(mapper: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        mapper(item)?.let { out.add(it) }
    }
    return out
}
