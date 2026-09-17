package com.naber.app.data

import org.json.JSONObject

/**
 * Cikartma ya da ozel emoji kaydi.
 *
 * Ikisi de ayni yapida tutulur, [kind] ayirir:
 * - "sticker": mesaj olarak gonderilen buyuk gorsel, paketlere ayrilir.
 * - "emoji":   metin icinde ":ad:" seklinde yazilan kucuk gorsel;
 *              reaksiyon olarak da kullanilabilir.
 */
data class Sticker(
    val id: Int,
    val kind: String,
    val pack: String,
    val name: String,
    val mediaId: Int,
    val url: String,
    val animated: Boolean,
    val uploaderId: Int
) {
    val isEmoji: Boolean get() = kind == "emoji"

    /** Metin icinde yazildigi bicim. */
    val token: String get() = ":$name:"

    companion object {
        fun from(json: JSONObject?): Sticker? {
            if (json == null) return null
            val id = json.optInt("id")
            if (id <= 0) return null
            return Sticker(
                id = id,
                kind = json.optString("kind", "sticker"),
                pack = json.optString("pack"),
                name = json.optString("name"),
                mediaId = json.optInt("media_id"),
                url = json.optString("url"),
                animated = json.optBoolean("animated"),
                uploaderId = json.optInt("uploader_id")
            )
        }
    }
}

/**
 * Cikartma ve ozel emoji listesinin surec ici onbellegi.
 *
 * Liste kucuk ve nadiren degisir; her sohbet acilisinda yeniden
 * indirmek yerine bir kez cekilip burada tutulur.
 */
object StickerStore {

    @Volatile
    private var cache: List<Sticker> = emptyList()

    @Volatile
    private var loadedAt: Long = 0L

    /** Bu sureden eski onbellek tazelenir. */
    private const val TTL_MS = 10 * 60 * 1000L

    @Synchronized
    fun put(list: List<Sticker>) {
        cache = list
        loadedAt = System.currentTimeMillis()
    }

    fun cached(): List<Sticker> = cache

    fun isStale(): Boolean = cache.isEmpty() || System.currentTimeMillis() - loadedAt > TTL_MS

    fun stickers(): List<Sticker> = cache.filter { !it.isEmoji }

    fun emojis(): List<Sticker> = cache.filter { it.isEmoji }

    /** Paket adina gore gruplanmis cikartmalar; adsiz paket sona duser. */
    fun packs(): List<Pair<String, List<Sticker>>> =
        stickers()
            .groupBy { it.pack.ifBlank { "Digerleri" } }
            .toList()
            .sortedBy { if (it.first == "Digerleri") "zzz" else it.first.lowercase() }

    fun emoji(name: String): Sticker? = cache.firstOrNull { it.isEmoji && it.name == name }

    @Synchronized
    fun add(sticker: Sticker) {
        cache = cache + sticker
    }

    @Synchronized
    fun remove(id: Int) {
        cache = cache.filterNot { it.id == id }
    }

    @Synchronized
    fun clear() {
        cache = emptyList()
        loadedAt = 0L
    }
}

/** Metindeki ":ad:" bicimindeki ozel emoji adlari. */
private val EMOJI_TOKEN = Regex(":([a-zA-Z0-9_]{1,30}):")

/**
 * Metinde gecen ve katalogda karsiligi olan ozel emojiler.
 * Mesaj cizilirken bunlar kucuk gorsele donusturulur.
 */
fun customEmojisIn(text: String): List<Sticker> {
    if (text.isEmpty() || !text.contains(':')) return emptyList()
    return EMOJI_TOKEN.findAll(text)
        .mapNotNull { StickerStore.emoji(it.groupValues[1].lowercase()) }
        .distinctBy { it.id }
        .toList()
}
