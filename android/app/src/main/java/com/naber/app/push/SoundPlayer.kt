package com.naber.app.push

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Uygulama sesleri.
 *
 * Dosyalar res/raw altinda aranir; yoksa sessizce gecilir (derleme bozulmaz):
 *  - message_sent      : mesaj gonderilince
 *  - message_received  : mesaj gelince
 *  - call_ringtone     : gelen arama zili
 */
object SoundPlayer {

    const val SENT = "message_sent"
    const val RECEIVED = "message_received"
    const val RINGTONE = "call_ringtone"

    private var pool: SoundPool? = null
    private val loaded = HashMap<String, Int>()
    private var ringtone: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun rawId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)

    /** Bildirim kanallarinda kullanilacak ses adresi. */
    fun uriFor(context: Context, name: String): Uri? {
        val id = rawId(context, name)
        if (id == 0) return null
        return Uri.parse("android.resource://${context.packageName}/$id")
    }

    private fun ensurePool(context: Context): SoundPool {
        pool?.let { return it }
        val created = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = created
        return created
    }

    private fun play(context: Context, name: String) {
        runCatching {
            val resource = rawId(context, name)
            if (resource == 0) return
            val soundPool = ensurePool(context)
            val existing = loaded[name]
            if (existing != null) {
                soundPool.play(existing, 0.6f, 0.6f, 1, 0, 1f)
                return
            }
            val id = soundPool.load(context, resource, 1)
            loaded[name] = id
            soundPool.setOnLoadCompleteListener { p, sampleId, status ->
                if (status == 0) p.play(sampleId, 0.6f, 0.6f, 1, 0, 1f)
            }
        }
    }

    fun playSent(context: Context) = play(context, SENT)

    fun playReceived(context: Context) = play(context, RECEIVED)

    /** Gelen arama zili; ses yoksa yalnizca titresim calisir. */
    fun startRingtone(context: Context, vibrate: Boolean = true) {
        stopRingtone()
        runCatching {
            val resource = rawId(context, RINGTONE)
            if (resource != 0) {
                val descriptor = context.resources.openRawResourceFd(resource)
                if (descriptor != null) {
                    val player = MediaPlayer()
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    player.isLooping = true
                    player.prepare()
                    player.start()
                    descriptor.close()
                    ringtone = player
                }
            }
        }

        if (vibrate) {
            runCatching {
                val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator = manager
                val pattern = longArrayOf(0, 700, 900)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    manager?.vibrate(pattern, 0)
                }
            }
        }
    }

    fun stopRingtone() {
        runCatching {
            ringtone?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    /** Arama sirasinda zil sesi hoparlorden cikmasin diye ses modu kontrolu. */
    fun ringtoneAllowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        return manager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}
