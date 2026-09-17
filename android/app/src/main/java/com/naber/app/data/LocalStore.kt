package com.naber.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sohbetlerin ve mesajlarin cihazdaki kopyasi.
 *
 * Uygulama acilir acilmaz once buradan okunur: liste ve gecmis aninda ekrana
 * gelir, sunucu yaniti sonra ustune biner. Boylece
 *  - acilista bos ekran ve donen tekerlek gorulmez,
 *  - internet yokken de eski mesajlar okunabilir,
 *  - her acilista tum gecmis yeniden indirilmez.
 *
 * Veri, sunucudan gelen JSON bicimiyle ayni sekilde saklanir; bu yuzden ayri
 * bir surum donusumu gerekmez. Kullanici cikis yaptiginda [clear] ile silinir.
 */
object LocalStore {

    private const val DIR = "naber-store"
    private const val CHATS_FILE = "chats.json"
    private const val CONTACTS_FILE = "contacts.json"
    /** Sohbet basina saklanan en fazla mesaj sayisi. */
    private const val MAX_MESSAGES = 300

    private val lock = Mutex()

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun chatsFile(context: Context): File = File(dir(context), CHATS_FILE)
    private fun contactsFile(context: Context): File = File(dir(context), CONTACTS_FILE)

    private fun messagesFile(context: Context, conversationId: Int): File =
        File(dir(context), "messages-$conversationId.json")

    // ------------------------------------------------------------ sohbetler

    suspend fun saveChats(context: Context, chats: List<Chat>, unreadTotal: Int) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val payload = JSONObject()
                        .put("unread_total", unreadTotal)
                        .put("chats", JSONArray(chats.map { it.toJson() }))
                    writeAtomic(chatsFile(context), payload.toString())
                }
                Unit
            }
        }

    /** @return sohbet listesi ve okunmamis toplami; kayit yoksa bos liste. */
    suspend fun loadChats(context: Context): Pair<List<Chat>, Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = chatsFile(context).takeIf { it.exists() }?.readText() ?: return@runCatching null
            val json = JSONObject(text)
            Chat.listFrom(json.optJSONArray("chats")) to json.optInt("unread_total")
        }.getOrNull() ?: (emptyList<Chat>() to 0)
    }

    // -------------------------------------------------------------- kisiler

    /** Kisiler sekmesi de aninda dolu acilsin diye kisi listeleri saklanir. */
    suspend fun saveContacts(context: Context, contacts: List<User>, directory: List<User>) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val payload = JSONObject()
                        .put("contacts", JSONArray(contacts.map { it.toJson() }))
                        .put("directory", JSONArray(directory.map { it.toJson() }))
                    writeAtomic(contactsFile(context), payload.toString())
                }
                Unit
            }
        }

    /** @return kisilerim ve tum kullanicilar listesi; kayit yoksa ikisi de bos. */
    suspend fun loadContacts(context: Context): Pair<List<User>, List<User>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = contactsFile(context).takeIf { it.exists() }?.readText() ?: return@runCatching null
            val json = JSONObject(text)
            User.listFrom(json.optJSONArray("contacts")) to User.listFrom(json.optJSONArray("directory"))
        }.getOrNull() ?: (emptyList<User>() to emptyList())
    }

    // -------------------------------------------------------------- mesajlar

    /**
     * Sohbetin mesajlarini saklar. Gonderilememis mesajlar da yazilir; boylece
     * uygulama kapatilsa bile kuyrukta kalirlar.
     */
    suspend fun saveMessages(context: Context, conversationId: Int, messages: List<Message>) =
        withContext(Dispatchers.IO) {
            if (conversationId <= 0) return@withContext
            lock.withLock {
                runCatching {
                    val trimmed = messages.takeLast(MAX_MESSAGES)
                    val payload = JSONObject()
                        .put("conversation_id", conversationId)
                        .put("saved_at", System.currentTimeMillis())
                        .put("messages", JSONArray(trimmed.map { it.toJson() }))
                        .put("outbox", JSONArray(pendingOf(trimmed).map { it.toJson() }))
                    writeAtomic(messagesFile(context, conversationId), payload.toString())
                }
                Unit
            }
        }

    /**
     * Saklanan mesajlar. Gonderilemeyenler [SendState.FAILED] olarak geri
     * yuklenir; kullanici uygulamayi yeniden acinca kuyrukta durur ve
     * yeniden denenebilir.
     */
    suspend fun loadMessages(context: Context, conversationId: Int): List<Message> =
        withContext(Dispatchers.IO) {
            if (conversationId <= 0) return@withContext emptyList()
            runCatching {
                val file = messagesFile(context, conversationId)
                val text = file.takeIf { it.exists() }?.readText() ?: return@runCatching emptyList<Message>()
                val json = JSONObject(text)
                val stored = Message.listFrom(json.optJSONArray("messages"))
                val outbox = Message.listFrom(json.optJSONArray("outbox")).map {
                    it.copy(sendState = SendState.FAILED)
                }

                // Kuyruktaki mesaj listede de olabilir; kimlige gore tekillenir.
                val keys = stored.map { it.key }.toHashSet()
                stored.map { message ->
                    if (message.id <= 0) message.copy(sendState = SendState.FAILED) else message
                } + outbox.filter { it.key !in keys }
            }.getOrDefault(emptyList())
        }

    /** Global aramada bulunan bir mesaj ve ait oldugu sohbet. */
    data class SearchHit(val conversationId: Int, val message: Message)

    /**
     * Tum sohbetlerin cihazdaki kopyalarinda metin arar.
     *
     * Arama sunucuya gitmez: saklanan JSON dosyalari taranir, boylece
     * cevrimdisiyken de calisir ve sunucuya yuk binmez. Sohbet basina en
     * fazla [MAX_MESSAGES] mesaj saklandigi icin taranan veri kucuktur.
     */
    suspend fun searchMessages(
        context: Context,
        query: String,
        limit: Int = 80
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val needle = searchKey(query)
        if (needle.isBlank()) return@withContext emptyList()

        val files = dir(context).listFiles { file -> file.name.startsWith("messages-") }
            ?: return@withContext emptyList()

        val hits = mutableListOf<SearchHit>()
        for (file in files) {
            runCatching {
                val json = JSONObject(file.readText())
                val conversationId = json.optInt("conversation_id")
                if (conversationId <= 0) return@runCatching
                Message.listFrom(json.optJSONArray("messages")).forEach { message ->
                    if (!message.deleted && searchKey(message.body).contains(needle)) {
                        hits += SearchHit(conversationId, message)
                    }
                }
            }
        }
        hits.sortByDescending { it.message.createdAt }
        hits.take(limit)
    }

    /**
     * Aramayi buyuk/kucuk harfe ve Turkce harflere karsi duyarsizlastirir.
     *
     * Turkce'de I/i ve İ/ı ayrimi dogru yapilinca "ISTANBUL" yazan birisi
     * "istanbul" mesajini bulamiyor. Arama icin ikisi de ayni kabul edilir;
     * amac dilbilgisi degil, kullanicinin aradigini bulmasi.
     */
    private fun searchKey(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            builder.append(
                when (char) {
                    'I', 'İ', 'ı' -> 'i'
                    'Ş', 'ş' -> 's'
                    'Ğ', 'ğ' -> 'g'
                    'Ü', 'ü' -> 'u'
                    'Ö', 'ö' -> 'o'
                    'Ç', 'ç' -> 'c'
                    else -> char.lowercaseChar()
                }
            )
        }
        return builder.toString().trim()
    }

    /** Cihazda mesaji saklanan sohbetlerin kimlikleri. */
    fun conversationIds(context: Context): List<Int> =
        dir(context).listFiles { file -> file.name.startsWith("messages-") }
            ?.mapNotNull { file ->
                file.name.removePrefix("messages-").removeSuffix(".json").toIntOrNull()
            }
            ?: emptyList()

    /** Henuz sunucuya ulasmamis (kimligi olmayan) mesajlar. */
    private fun pendingOf(messages: List<Message>): List<Message> =
        messages.filter { it.id <= 0 && it.clientId.isNotBlank() }

    // ------------------------------------------------------------- temizlik

    fun totalBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /**
     * Once gecici dosyaya yazip sonra tasir.
     * Yazma sirasinda uygulama olurse yarim JSON kalmaz.
     */
    private fun writeAtomic(file: File, content: String) {
        val temp = File(file.parentFile, "${file.name}.part")
        temp.writeText(content)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            file.writeText(content)
            temp.delete()
        }
    }
}
