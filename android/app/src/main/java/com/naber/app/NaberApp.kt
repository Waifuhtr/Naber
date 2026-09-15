package com.naber.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.naber.app.data.LocalFiles
import com.naber.app.call.CallManager
import com.naber.app.data.ApiClient
import com.naber.app.data.EventHub
import com.naber.app.data.Session

class NaberApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Naber.init(this)
        createNotificationChannels()
        LocalFiles.cleanup(this)
    }

    /**
     * Gorsel onbellegi.
     * Backblaze imzali adresleri her seferinde degistigi ve onbelleklenmeyi
     * yasaklayan basliklar dondugu icin onbellek anahtari medya kimligine
     * sabitlenir, basliklar yok sayilir. Boylece ayni gorsel tekrar inmez.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
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
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Mesajlar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Yeni mesaj bildirimleri"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gelen sesli arama bildirimleri"
                setBypassDnd(true)
            }
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "naber_messages"
        const val CHANNEL_CALLS = "naber_calls"
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
        events = EventHub(api)
        calls = CallManager(context.applicationContext, api, events, session)
        started = true
    }
}
