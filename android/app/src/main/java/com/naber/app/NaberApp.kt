package com.naber.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.naber.app.call.CallManager
import com.naber.app.data.ApiClient
import com.naber.app.data.EventHub
import com.naber.app.data.Session

class NaberApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Naber.init(this)
        createNotificationChannels()
    }

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
        calls = CallManager(context.applicationContext, api, events)
        started = true
    }
}
