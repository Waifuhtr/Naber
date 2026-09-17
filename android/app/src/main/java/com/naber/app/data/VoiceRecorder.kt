package com.naber.app.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Sesli mesaj kaydi ve calma.
 *
 * Kayit AAC olarak MP4 kabina yazilir (.m4a): Android'in her surumunde
 * bulunur, dosya kucuktur ve sunucuya yuklenip her telefonda calinabilir.
 */
object VoiceRecorder {

    /** Kaydin ust siniri; sunucu da ayni siniri uygular. */
    const val MAX_SECONDS = 600

    private const val DIR = "naber-ses"

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    val isRecording: Boolean get() = recorder != null

    /**
     * Kaydi baslatir.
     *
     * Izin kontrolu cagiran tarafta yapilir.
     *
     * @return basarisiz olursa false.
     */
    fun start(context: Context): Boolean {
        if (recorder != null) return false

        val file = File(dir(context), "ses-${System.currentTimeMillis()}.m4a")
        val created = runCatching {
            @Suppress("DEPRECATION")
            val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Konusma icin yeterli, dosyayi gereksiz buyutmez.
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()

        if (created == null) {
            runCatching { file.delete() }
            return false
        }

        recorder = created
        target = file
        startedAt = System.currentTimeMillis()
        return true
    }

    /**
     * Kaydi bitirir.
     *
     * @return dosya ve saniye cinsinden sure; kayit cok kisaysa ya da
     *         bir hata olustuysa null (dosya silinir).
     */
    fun stop(): Pair<File, Int>? {
        val active = recorder ?: return null
        val file = target
        recorder = null
        target = null

        val stopped = runCatching {
            active.stop()
        }.isSuccess
        runCatching { active.release() }

        if (file == null) return null

        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        // MediaRecorder cok kisa kayitlarda bozuk dosya uretir; 1 saniyenin
        // altindaki kayitlar kazara basma sayilir ve atilir.
        if (!stopped || seconds < 1 || !file.exists() || file.length() <= 0) {
            runCatching { file.delete() }
            return null
        }

        return file to seconds.coerceAtMost(MAX_SECONDS)
    }

    /** Kaydi iptal eder ve dosyayi siler. */
    fun cancel() {
        val active = recorder ?: return
        recorder = null
        val file = target
        target = null
        runCatching { active.stop() }
        runCatching { active.release() }
        runCatching { file?.delete() }
    }

    /** Kayit suresi (saniye). */
    fun elapsedSeconds(): Int =
        if (recorder == null) 0 else ((System.currentTimeMillis() - startedAt) / 1000).toInt()
}

/**
 * Sesli mesaj calar.
 *
 * Ayni anda tek bir kayit calar: ikinci bir mesaja basildiginda oncekini
 * durdurur, yoksa iki ses ust uste binerdi.
 */
object VoicePlayer {

    private var player: MediaPlayer? = null
    private var playingKey: String = ""

    /** Su an calan mesajin anahtari; hicbiri calmiyorsa bos. */
    val currentKey: String get() = playingKey

    fun toggle(source: String, key: String, onFinished: () -> Unit): Boolean {
        if (playingKey == key) {
            stop()
            return false
        }

        stop()

        val created = runCatching {
            MediaPlayer().apply {
                setDataSource(source)
                setOnCompletionListener {
                    stop()
                    onFinished()
                }
                prepare()
                start()
            }
        }.getOrNull() ?: return false

        player = created
        playingKey = key
        return true
    }

    fun stop() {
        val active = player ?: return
        player = null
        playingKey = ""
        runCatching { active.stop() }
        runCatching { active.release() }
    }
}
