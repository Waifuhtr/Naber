package com.naber.app.data

import android.content.Context

/**
 * Emoji secici icin katalog ve "sik kullanilanlar" listesi.
 *
 * Android'de sistemin emoji listesini veren bir API yok; bu yuzden
 * kategorilere ayrilmis bir set burada tutuluyor. Amac eksiksiz bir
 * emoji sozlugu degil, gunluk yazismada kullanilanlari kapsamak.
 */
object EmojiCatalog {

    private const val PREFS = "naber_emoji"
    private const val KEY_RECENT = "sik_kullanilanlar"
    private const val MAX_RECENT = 24

    data class Category(val title: String, val icon: String, val emojis: List<String>)

    val CATEGORIES: List<Category> = listOf(
        Category(
            "Yuzler", "🙂",
            "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖".split(" ")
        ),
        Category(
            "El ve beden", "👍",
            "👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 ✍️ 💪 🦾 🦵 🦶 👂 👃 🧠 🫀 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 👨 👩 🧔 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 🥷 👰 🤵 🎅 🦸 🦹".split(" ")
        ),
        Category(
            "Kalpler", "❤️",
            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💯 💢 💥 💫 💦 💨 🕳️ 💣 💬 💭 🗯️ 💤".split(" ")
        ),
        Category(
            "Hayvanlar", "🐶",
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🐪 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦡 🐁 🐀 🐿️ 🦔".split(" ")
        ),
        Category(
            "Yiyecek", "🍕",
            "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🥙 🧆 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 ☕ 🍵 🧃 🥤 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉".split(" ")
        ),
        Category(
            "Etkinlik", "⚽",
            "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩".split(" ")
        ),
        Category(
            "Nesneler", "💡",
            "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📟 📺 📻 ⏰ ⏱️ ⏲️ 🕰️ 🔋 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💳 🧾 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧲 🔫 💊 💉 🩹 🩺 🚪 🪑 🚽 🚿 🛁 🧴 🧷 🧹 🧺 🧻 🧼 🪒 🧽 🛒 🎁 🎈 🎀 🎊 🎉 🪄 🔮 📦 📫 ✉️ 📝 ✏️ 📚 📖 🔖 🔑 🔒 🔓".split(" ")
        ),
        Category(
            "Semboller", "✨",
            "✨ ⭐ 🌟 💫 ⚡ 🔥 🌈 ☀️ 🌤️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🌊 🌪️ 🌙 🌛 🌝 🌞 ⛄ 🎄 🎆 🎇 🧨 ✅ ❌ ⭕ ❗ ❓ ⚠️ 🚫 💤 ♻️ 🔔 🔕 🎵 🎶 ➕ ➖ ✖️ ➗ 🟢 🔴 🟡 🔵 ⚫ ⚪ 🟠 🟣 🔺 🔻 🔶 🔷 ⬛ ⬜".split(" ")
        )
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** En son kullanilan emojiler, yenisi basta. */
    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, null)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun remember(context: Context, emoji: String) {
        if (emoji.isBlank()) return
        val updated = (listOf(emoji) + recent(context).filterNot { it == emoji }).take(MAX_RECENT)
        prefs(context).edit().putString(KEY_RECENT, updated.joinToString(" ")).apply()
    }

    /**
     * Arama: emoji'nin kendisi yazilabilir ya da Turkce bir anahtar
     * kelime. Butun emojiler icin sozluk tutmak yerine gunluk kullanilan
     * kelimeler eslestirilir; eslesme yoksa tum liste doner.
     */
    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "gul" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😊"),
        "aglama" to listOf("😢", "😭", "🥺", "😥", "😪"),
        "kalp" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖", "💘"),
        "ates" to listOf("🔥", "💥", "⚡"),
        "alkis" to listOf("👏", "🙌", "👍"),
        "tamam" to listOf("👍", "👌", "✅", "🆗"),
        "hayir" to listOf("👎", "❌", "🚫"),
        "dua" to listOf("🙏"),
        "kizgin" to listOf("😠", "😡", "🤬", "👿"),
        "uzgun" to listOf("😔", "😞", "🙁", "😟"),
        "saskin" to listOf("😮", "😯", "😲", "🤯", "😳"),
        "uyku" to listOf("😴", "😪", "💤"),
        "parti" to listOf("🥳", "🎉", "🎊", "🎈", "🎁"),
        "yemek" to listOf("🍕", "🍔", "🍟", "🌭", "🍝", "🍜", "🍣"),
        "kahve" to listOf("☕", "🍵"),
        "futbol" to listOf("⚽", "🏆", "🥅"),
        "kopek" to listOf("🐶", "🐕"),
        "kedi" to listOf("🐱", "🐈"),
        "para" to listOf("💸", "💵", "💳", "🤑"),
        "muzik" to listOf("🎵", "🎶", "🎧", "🎤"),
        "selam" to listOf("👋", "🤝"),
        "yildiz" to listOf("⭐", "🌟", "✨", "💫")
    )

    /** Tum kategorilerdeki emojiler tek liste halinde. */
    val ALL: List<String> = CATEGORIES.flatMap { it.emojis }.distinct()

    fun search(query: String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return ALL
        // Dogrudan emoji yazildiysa onu don.
        if (ALL.contains(query.trim())) return listOf(query.trim())
        val hits = KEYWORDS.entries
            .filter { it.key.startsWith(needle) || needle.startsWith(it.key) }
            .flatMap { it.value }
            .distinct()
        return hits
    }
}
