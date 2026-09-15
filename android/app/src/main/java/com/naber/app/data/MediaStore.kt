package com.naber.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gorsellerin cihazdaki kalici deposu.
 *
 * Bir gorsel ilk goruldugunde indirilip telefona yazilir; sonraki acilislarda
 * hep bu dosya kullanilir. Boylece:
 *  - imzali adres degisse de gorsel yeniden indirilmez ("surekli yukleniyor" biter),
 *  - internet olmadan da gecmis gorseller acilir,
 *  - onizleme aninda gorunur.
 *
 * Konum: /Android/media/com.naber.app/Naber/Media/Naber Images
 * (uygulamaya ait klasor oldugu icin izin gerekmez, galeriyi kirletmez)
 */
object MediaStore {

    private const val FOLDER = "Naber/Media/Naber Images"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val locks = HashMap<Int, Mutex>()
    private val globalLock = Mutex()

    private fun directory(context: Context): File {
        val base = context.externalMediaDirs.firstOrNull() ?: context.filesDir
        return File(base, FOLDER).apply { if (!exists()) mkdirs() }
    }

    fun fileFor(context: Context, mediaId: Int): File =
        File(directory(context), "NABER-IMG-$mediaId.jpg")

    /** Indirilmis dosyanin adresi; yoksa null. */
    fun cachedUri(context: Context, mediaId: Int): String? {
        if (mediaId <= 0) return null
        val file = fileFor(context, mediaId)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    /** Yerel kopyayi dogrudan kaydeder (gonderdigimiz gorseller icin). */
    fun store(context: Context, mediaId: Int, bytes: ByteArray): String? = runCatching {
        if (mediaId <= 0) return null
        val file = fileFor(context, mediaId)
        file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()

    /**
     * Gorseli bir kez indirir ve yerel adresini doner.
     * Ayni gorsel icin es zamanli cagrilar tek indirmeye dusurulur.
     */
    suspend fun ensure(context: Context, mediaId: Int, url: String): String? {
        if (mediaId <= 0 || url.isBlank()) return null
        cachedUri(context, mediaId)?.let { return it }

        val lock = globalLock.withLock { locks.getOrPut(mediaId) { Mutex() } }

        return lock.withLock {
            cachedUri(context, mediaId)?.let { return@withLock it }

            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val bytes = response.body?.bytes() ?: return@use null
                        if (bytes.isEmpty()) return@use null

                        val file = fileFor(context, mediaId)
                        val temp = File(file.parentFile, "${file.name}.part")
                        temp.writeBytes(bytes)
                        if (file.exists()) file.delete()
                        temp.renameTo(file)
                        Uri.fromFile(file).toString()
                    }
                }.getOrNull()
            }
        }
    }

    /** Toplam boyut (yonetim/temizlik icin). */
    fun totalBytes(context: Context): Long =
        directory(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        runCatching { directory(context).listFiles()?.forEach { it.delete() } }
    }
}
