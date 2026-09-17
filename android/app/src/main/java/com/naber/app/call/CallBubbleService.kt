package com.naber.app.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.naber.app.MainActivity
import com.naber.app.Naber
import com.naber.app.ui.CallBubble
import com.naber.app.ui.theme.NaberTheme
import kotlin.math.roundToInt

/**
 * Arama baloncugunu ekranin uzerinde tutan servis.
 *
 * Uygulama arka plana atilinca baslatilir, one gelince durdurulur.
 * Baloncuk bir `ComposeView` olarak `WindowManager`'a eklenir; bu yuzden
 * servis Compose'un bekledigi sahipleri (yasam dongusu, ViewModel deposu,
 * kayitli durum) kendisi saglar.
 *
 * "Diger uygulamalarin uzerinde goster" izni yoksa sessizce kapanir;
 * izin istemek kullanicinin elinde (bkz. CallScreen'deki baglanti).
 */
class CallBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var windows: WindowManager? = null
    private var bubble: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) attach()
        return START_NOT_STICKY
    }

    private fun attach() {
        // Servis surecin ilk bileseni olarak ayaga kalkabilir.
        Naber.init(applicationContext)
        if (!canShow(this)) {
            stopSelf()
            return
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }
        windows = manager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = START_X
            y = START_Y
        }
        params = layout

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallBubbleService)
            setViewTreeViewModelStoreOwner(this@CallBubbleService)
            setViewTreeSavedStateRegistryOwner(this@CallBubbleService)
            setContent {
                NaberTheme {
                    val state by Naber.calls.state.collectAsState()
                    CallBubble(
                        state = state,
                        onDrag = { dx, dy -> moveBy(dx, dy) },
                        onToggleMute = { Naber.calls.toggleMute() },
                        onToggleDeafen = { Naber.calls.toggleDeafen() },
                        onHangUp = {
                            Naber.calls.hangUp()
                            stopSelf()
                        },
                        onOpen = { openApp() }
                    )
                }
            }
        }
        bubble = view

        registry.currentState = Lifecycle.State.RESUMED
        runCatching { manager.addView(view, layout) }
            .onFailure {
                bubble = null
                stopSelf()
            }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val layout = params ?: return
        val view = bubble ?: return
        layout.x += dx.roundToInt()
        layout.y += dy.roundToInt()
        // Ekran disina kacmasin.
        if (layout.x < 0) layout.x = 0
        if (layout.y < 0) layout.y = 0
        runCatching { windows?.updateViewLayout(view, layout) }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { startActivity(intent) }
        stopSelf()
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        bubble?.let { view -> runCatching { windows?.removeView(view) } }
        bubble = null
        params = null
        windows = null
        store.clear()
        super.onDestroy()
    }

    companion object {
        private const val START_X = 24
        private const val START_Y = 220

        /** Baloncuk icin "diger uygulamalarin uzerinde goster" izni var mi? */
        fun canShow(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** Izin ekranini acar; kullanici izni oradan verir. */
        fun permissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun start(context: Context) {
            if (!canShow(context)) return
            runCatching { context.startService(Intent(context, CallBubbleService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallBubbleService::class.java)) }
        }
    }
}
