package com.naber.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naber.app.Naber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Bildirim uzerindeki "Reddet" dugmesi; uygulamayi acmadan aramayi kapatir. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REJECT) return
        val callId = intent.getIntExtra(EXTRA_CALL_ID, 0)
        if (callId <= 0) return

        Naber.init(context.applicationContext)
        SoundPlayer.stopRingtone()
        Notifications.cancelCall(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { Naber.api.callAction(callId, "reject") }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_REJECT = "com.naber.app.CALL_REJECT"
        const val EXTRA_CALL_ID = "call_id"
    }
}
