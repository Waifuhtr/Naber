package com.naber.app.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Secilen gorselleri uygulamaya ait klasore kopyalar.
 *
 * Galeriden gelen content:// adresleri yalnizca o anki oturumda gecerlidir;
 * uygulama kapanip acilinca okunamaz. Bu yuzden profil fotografi ve
 * gonderilen gorseller kalici bir kopyaya alinir.
 */
object LocalFiles {

    private const val DIR = "naber-media"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** @return "file://..." bicimindeki kalici adres, basarisizsa null. */
    fun persist(context: Context, bytes: ByteArray, name: String): String? = runCatching {
        val file = File(dir(context), name)
        file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()

    fun persist(context: Context, uri: Uri, name: String): String? = runCatching {
        val file = File(dir(context), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(file).toString()
    }.getOrNull()

    /**
     * Kamerayla cekilecek fotograf icin bos bir hedef dosya ve onun
     * FileProvider adresi.
     *
     * Kamera uygulamasi baska bir uygulamadir; kendi klasorumuze dogrudan
     * yazamaz. FileProvider ile gecici yazma izni verilen bir content://
     * adresi uretilir.
     */
    fun cameraTarget(context: Context): Pair<File, Uri>? = runCatching {
        val file = File(dir(context), "cam-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        file to uri
    }.getOrNull()

    fun exists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return false
        val file = uri.path?.let { File(it) } ?: return false
        return file.exists() && file.length() > 0
    }

    /** Cok eski yerel kopyalari temizler (varsayilan 30 gun). */
    fun cleanup(context: Context, maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000) {
        runCatching {
            val threshold = System.currentTimeMillis() - maxAgeMillis
            dir(context).listFiles()?.forEach { file ->
                if (file.lastModified() < threshold) file.delete()
            }
        }
    }
}
