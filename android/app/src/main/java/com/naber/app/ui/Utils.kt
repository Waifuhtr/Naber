package com.naber.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

fun formatPresence(user: User): String = when {
    user.online -> "cevrimici"
    user.lastSeen <= 0 -> ""
    else -> "son gorulme ${formatChatTime(user.lastSeen)} ${formatClock(user.lastSeen)}"
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

data class PreparedImage(val bytes: ByteArray, val width: Int, val height: Int, val mime: String = "image/jpeg")

/**
 * Secilen gorseli yuklemeden once kucultur (en fazla 1600 px, JPEG %85).
 * Hem yukleme suresini hem de Backblaze kullanimini dusurur.
 */
suspend fun prepareImage(context: Context, uri: Uri, maxSize: Int = 1600): PreparedImage? =
    withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val scale = minOf(1f, maxSize.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        PreparedImage(output.toByteArray(), bitmap.width, bitmap.height)
    }
