package com.naber.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.naber.app.push.SoundPlayer
import com.naber.app.work.NaberWork
import com.naber.app.call.CallManager
import com.naber.app.data.ApiClient
import com.naber.app.data.EventHub
import com.naber.app.data.Session

class NaberApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Naber.init(this)
        createNotificationChannels()
        // Temizlik ve kuyruk artik acilista degil, WorkManager'in uygun
        // gordugu bir anda yapiliyor; acilis diskle ugrasmiyor.
        NaberWork.schedule(this)
    }

    /**
     * Gorsel onbellegi.
     * Backblaze imzali adresleri her seferinde degistigi ve onbelleklenmeyi
     * yasaklayan basliklar dondugu icin onbellek anahtari medya kimligine
     * sabitlenir, basliklar yok sayilir. Boylece ayni gorsel tekrar inmez.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Hareketli cikartma ve ozel emojiler: Android 9+ sistemin
            // kendi cozucusunu kullanir, oncesinde Coil'in GIF cozucusu.
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("naber_images"))
                    .maxSizeBytes(192L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Mesajlar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Yeni mesaj bildirimleri"
                enableVibration(true)
                SoundPlayer.uriFor(this@NaberApp, SoundPlayer.RECEIVED)?.let {
                    setSound(it, notificationAttributes)
                }
            }
        )

        val ringtoneAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gelen sesli arama bildirimleri"
                setBypassDnd(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 900)
                SoundPlayer.uriFor(this@NaberApp, SoundPlayer.RINGTONE)?.let {
                    setSound(it, ringtoneAttributes)
                }
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Devam eden arama", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Arama surerken gorunen kalici bildirim"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        // Kanal sesleri olusturuldugu anda sabitlenir; ses eklendiginde
        // yeni kanal kimlikleri kullanilmali, yoksa eski kanal sessiz kalir.
        const val CHANNEL_MESSAGES = "naber_messages_v2"
        const val CHANNEL_CALLS = "naber_calls_v2"
        const val CHANNEL_ONGOING = "naber_call_ongoing"
    }
}

/** Kucuk proje oldugu icin agir bir DI yerine tek bir servis kaydi kullanilir. */
object Naber {
    lateinit var session: Session
        private set
    lateinit var api: ApiClient
        private set
    lateinit var events: EventHub
        private set
    lateinit var calls: CallManager
        private set

    private var started = false

    fun init(context: Context) {
        if (started) return
        session = Session(context.applicationContext)
        api = ApiClient(session)
        events = EventHub(context.applicationContext, api, session)
        calls = CallManager(context.applicationContext, api, events, session)
        started = true
    }
}
