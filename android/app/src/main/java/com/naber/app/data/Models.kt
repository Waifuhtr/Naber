package com.naber.app.data

import org.json.JSONArray
import org.json.JSONObject

/** WordPress'ten donen kullanici. is_admin bilgisi sunucudan gelir, uygulama tahmin etmez. */
data class User(
    val id: Int,
    val username: String,
    val displayName: String,
    val avatar: String,
    val about: String,
    val lastSeen: Long,
    val online: Boolean,
    val isAdmin: Boolean,
    val disabled: Boolean = false
) {
    companion object {
        fun from(json: JSONObject?): User? {
            if (json == null) return null
            return User(
                id = json.optInt("id"),
                username = json.optString("username"),
                displayName = json.optString("display_name").ifBlank { json.optString("username") },
                avatar = json.optString("avatar"),
                about = json.optString("about"),
                lastSeen = json.optLong("last_seen"),
                online = json.optBoolean("online"),
                isAdmin = json.optBoolean("is_admin"),
                disabled = json.optBoolean("disabled")
            )
        }
    }
}

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

enum class SendState { SENDING, SENT, READ, FAILED }

data class Message(
    val id: Int,
    val conversationId: Int,
    val senderId: Int,
    val receiverId: Int,
    val type: String,
    val body: String,
    val clientId: String,
    val isRead: Boolean,
    val createdAt: Long,
    val media: Media?,
    val localImageUri: String? = null,
    val sendState: SendState = SendState.SENT,
    val uploadProgress: Int = 0
) {
    companion object {
        fun from(json: JSONObject?): Message? {
            if (json == null) return null
            return Message(
                id = json.optInt("id"),
                conversationId = json.optInt("conversation_id"),
                senderId = json.optInt("sender_id"),
                receiverId = json.optInt("receiver_id"),
                type = json.optString("type", "text"),
                body = json.optString("body"),
                clientId = json.optString("client_id"),
                isRead = json.optBoolean("is_read"),
                createdAt = json.optLong("created_at"),
                media = Media.from(json.optJSONObject("media"))
            )
        }
    }
}

data class ChatSummary(
    val id: Int,
    val peer: User,
    val unread: Int,
    val updatedAt: Long,
    val lastMessage: Message?
) {
    companion object {
        fun from(json: JSONObject): ChatSummary? {
            val peer = User.from(json.optJSONObject("peer")) ?: return null
            return ChatSummary(
                id = json.optInt("id"),
                peer = peer,
                unread = json.optInt("unread"),
                updatedAt = json.optLong("updated_at"),
                lastMessage = Message.from(json.optJSONObject("last_message"))
            )
        }
    }
}

data class CallInfo(
    val id: Int,
    val callerId: Int,
    val calleeId: Int,
    val status: String,
    val duration: Int,
    val createdAt: Long,
    val caller: User?,
    val callee: User?
) {
    companion object {
        fun from(json: JSONObject?): CallInfo? {
            if (json == null) return null
            return CallInfo(
                id = json.optInt("id"),
                callerId = json.optInt("caller_id"),
                calleeId = json.optInt("callee_id"),
                status = json.optString("status"),
                duration = json.optInt("duration"),
                createdAt = json.optLong("created_at"),
                caller = User.from(json.optJSONObject("caller")),
                callee = User.from(json.optJSONObject("callee"))
            )
        }
    }
}

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

data class Signal(val id: Int, val callId: Int, val senderId: Int, val type: String, val payload: String)

/** Uzun yoklama (long-polling) sonucu. */
data class EventBatch(
    val messages: List<Message>,
    val signals: List<Signal>,
    val incomingCall: CallInfo?,
    val readMessageIds: List<Int>,
    val typing: Boolean,
    val typingConversationId: Int,
    val peerOnline: Boolean?,
    val unreadTotal: Int,
    val sinceMessageId: Int,
    val sinceSignalId: Int
)

data class AdminStats(
    val totalUsers: Int,
    val onlineUsers: Int,
    val totalMessages: Int,
    val todayMessages: Int,
    val conversations: Int,
    val storageFiles: Int,
    val storageBytes: Long,
    val totalCalls: Int,
    val callSeconds: Int,
    val missedCalls: Int,
    val storageReady: Boolean,
    val pushReady: Boolean,
    val turnConfigured: Boolean,
    val recentUsers: List<User>
)

data class StorageTestStep(val label: String, val ok: Boolean, val message: String)

data class StorageTestResult(val ok: Boolean, val message: String, val bucketId: String, val steps: List<StorageTestStep>)
