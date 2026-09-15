package com.naber.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import com.naber.app.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val trLocale = Locale("tr", "TR")
private val hourFormat = SimpleDateFormat("HH:mm", trLocale)
private val dateFormat = SimpleDateFormat("dd.MM.yyyy", trLocale)
private val dayFormat = SimpleDateFormat("d MMMM", trLocale)

fun formatClock(epochSeconds: Long): String =
    if (epochSeconds <= 0) "" else hourFormat.format(Date(epochSeconds * 1000))

/** Sohbet listesinde: bugun saat, dun "Dun", oncesi tarih. */
fun formatChatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val date = Date(epochSeconds * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> hourFormat.format(date)
        sameYear && dayDiff == 1 -> "Dun"
        sameYear -> dayFormat.format(date)
        else -> dateFormat.format(date)
    }
}

/** "son gorulme bugun 14:32" / "son gorulme dun 09:10" / "son gorulme 12.03.2026" */
fun formatLastSeen(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val date = Date(epochSeconds * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    val clock = hourFormat.format(date)
    return when {
        sameYear && dayDiff == 0 -> "son gorulme bugun $clock"
        sameYear && dayDiff == 1 -> "son gorulme dun $clock"
        sameYear -> "son gorulme ${dayFormat.format(date)} $clock"
        else -> "son gorulme ${dateFormat.format(date)}"
    }
}

fun formatPresence(user: User): String = when {
    user.online -> "cevrimici"
    else -> formatLastSeen(user.lastSeen)
}

fun formatPresence(online: Boolean, lastSeen: Long): String = when {
    online -> "cevrimici"
    else -> formatLastSeen(lastSeen)
}

fun formatDuration(seconds: Int): String {
    val safe = maxOf(0, seconds)
    val minutes = safe / 60
    val remaining = safe % 60
    return if (minutes >= 60) {
        String.format(trLocale, "%d:%02d:%02d", minutes / 60, minutes % 60, remaining)
    } else {
        String.format(trLocale, "%d:%02d", minutes, remaining)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return String.format(trLocale, "%.1f %s", value, units[index])
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(trLocale)
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(trLocale)
    }
}

data class PreparedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mime: String = "image/jpeg",
    /** Mesajla birlikte tasinan cok kucuk base64 JPEG on izleme. */
    val preview: String = ""
)

/** On izleme karesinin uzun kenari; birkac kilobayti gecmemeli. */
private const val PREVIEW_EDGE = 48

/**
 * Secilen gorseli yuklemeden once kucultur.
 *
 * Boyut ve kalite baglanti turune gore secilir: olculu (mobil veri) baglantida
 * daha kucuk ve daha sikistirilmis gonderilir. Ayrica mesajla birlikte
 * gidecek kucucuk bir on izleme uretilir; alici asil dosya inmeden once
 * bulanik da olsa gorseli gorur.
 */
suspend fun prepareImage(context: Context, uri: Uri, maxSize: Int = 0): PreparedImage? =
    withContext(Dispatchers.IO) {
        val metered = isMeteredConnection(context)
        val edge = if (maxSize > 0) maxSize else if (metered) 1080 else 1600
        val quality = if (metered) 72 else 85

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (bounds.outWidth / sample > edge * 2 || bounds.outHeight / sample > edge * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val scale = minOf(1f, edge.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        PreparedImage(output.toByteArray(), bitmap.width, bitmap.height, preview = buildPreview(bitmap))
    }

/** Gorselin kucucuk halini base64 JPEG olarak uretir; uretilemezse bos doner. */
private fun buildPreview(bitmap: Bitmap): String = runCatching {
    val scale = minOf(1f, PREVIEW_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
    val width = maxOf(1, (bitmap.width * scale).toInt())
    val height = maxOf(1, (bitmap.height * scale).toInt())
    val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val output = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 55, output)
    if (small !== bitmap) small.recycle()
    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}.getOrDefault("")

/** Kullanici mobil veri gibi olculu bir baglantida mi? */
fun isMeteredConnection(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(false)

/** Base64 on izlemeyi cizilebilir bir bitmap'e cevirir. */
fun decodePreview(preview: String): Bitmap? {
    if (preview.isBlank()) return null
    return runCatching {
        val raw = Base64.decode(preview, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(raw, 0, raw.size)
    }.getOrNull()
}
