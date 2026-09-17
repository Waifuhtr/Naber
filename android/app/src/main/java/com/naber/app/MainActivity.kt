package com.naber.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naber.app.call.CallStage
import com.naber.app.push.Notifications
import com.naber.app.ui.screens.AdminScreen
import com.naber.app.ui.screens.CallScreen
import com.naber.app.ui.screens.ChatScreen
import com.naber.app.ui.screens.GroupCreateScreen
import com.naber.app.ui.screens.GroupInfoScreen
import com.naber.app.ui.screens.HomeScreen
import com.naber.app.ui.screens.LoginScreen
import com.naber.app.ui.screens.UserProfileScreen
import com.naber.app.ui.theme.NaberColors
import com.naber.app.ui.theme.NaberTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pendingConversationId = 0
    private var pendingProfileUserId = 0
    private var pendingAccept by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Naber.init(applicationContext)
        // Arama yoneticisi surec boyunca yasar; onceki oturumdan kalan bitmis
        // arama durumu temizlenmezse uygulamaya her girildiginde hayalet bir
        // arama ekrani gorunur.
        Naber.calls.clearStaleState()
        pendingConversationId = intent?.getIntExtra(EXTRA_CONVERSATION_ID, 0) ?: 0
        pendingProfileUserId = intent?.getIntExtra(EXTRA_POKE_USER_ID, 0) ?: 0
        pendingAccept = intent?.getBooleanExtra(EXTRA_CALL_ACCEPT, false) ?: false

        setContent {
            NaberTheme {
                Box(modifier = Modifier.fillMaxSize().background(NaberColors.Background)) {
                    NaberRoot(
                        startConversationId = pendingConversationId,
                        startProfileUserId = pendingProfileUserId,
                        autoAcceptCall = pendingAccept,
                        onAcceptConsumed = { pendingAccept = false }
                    )
                }
            }
        }

        requestStartupPermissions()
        registerPushToken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CALL_ACCEPT, false)) {
            pendingAccept = true
        }
    }

    override fun onResume() {
        super.onResume()
        Naber.calls.clearStaleState()
        if (Naber.session.isLoggedIn) {
            Naber.events.start()
            Naber.events.launchInScope { Naber.api.setPresence(true) }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Naber.calls.state.value.stage == CallStage.IDLE) {
            Naber.events.stop()
            // Karsi taraf beklemeden "son gorulme" gorsun.
            if (Naber.session.isLoggedIn) {
                Naber.events.launchInScope { Naber.api.setPresence(false) }
            }
        }
    }

    /** Uygulamanin ihtiyac duydugu tum izinler ilk acilista birlikte istenir. */
    private fun requestStartupPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        launcher.launch(missing.toTypedArray())
    }

    /** Firebase yapilandirilmamissa sessizce gecilir. */
    private fun registerPushToken() {
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (Naber.session.isLoggedIn) {
                        Naber.events.launchInScope { Naber.api.registerDevice(token) }
                    } else {
                        Naber.session.pushToken = token
                    }
                }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALL_ACCEPT = "call_accept"
        const val EXTRA_POKE_USER_ID = "poke_user_id"
    }
}

@Composable
private fun NaberRoot(
    startConversationId: Int,
    startProfileUserId: Int,
    autoAcceptCall: Boolean,
    onAcceptConsumed: () -> Unit
) {
    val navController = rememberNavController()
    var loggedIn by remember { mutableStateOf(Naber.session.isLoggedIn) }
    val callState by Naber.calls.state.collectAsState()
    val incomingCall by Naber.events.incomingCall.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            Naber.events.start()
            withContext(Dispatchers.IO) {
                runCatching { Naber.api.me() }
                val token = Naber.session.pushToken
                if (token.isNotEmpty()) runCatching { Naber.api.registerDevice(token) }
            }
        }
    }

    LaunchedEffect(incomingCall?.id) {
        val call = incomingCall
        if (call != null && (call.status == "ringing" || call.status == "active")) {
            Naber.calls.onIncoming(call)
        }
    }

    // Bildirimdeki "Kabul et" dugmesi: ekran acilir acilmaz baglanir.
    LaunchedEffect(autoAcceptCall, callState.stage) {
        if (autoAcceptCall && callState.stage == CallStage.INCOMING) {
            Naber.calls.accept()
            onAcceptConsumed()
        }
    }

    LaunchedEffect(callState.stage) {
        if (callState.stage == CallStage.IDLE) Notifications.cancelCall(context)
    }

    if (!loggedIn) {
        LoginScreen(onLoggedIn = { loggedIn = true })
        return
    }

    // Durtme bildirimine dokununca dogrudan o kisinin profili acilir.
    LaunchedEffect(startProfileUserId) {
        if (startProfileUserId > 0) {
            navController.navigate("user/$startProfileUserId")
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                startConversationId = startConversationId,
                onOpenChat = { conversationId -> navController.navigate("chat/$conversationId") },
                onNewGroup = { navController.navigate("group/new") },
                onAdmin = { navController.navigate("admin") },
                onOpenProfile = { userId -> navController.navigate("user/$userId") },
                onLoggedOut = { loggedIn = false }
            )
        }

        composable("user/{userId}") { entry ->
            UserProfileScreen(
                userId = entry.arguments?.getString("userId")?.toIntOrNull() ?: 0,
                onBack = { navController.popBackStack() },
                onOpenChat = { conversationId ->
                    navController.popBackStack()
                    navController.navigate("chat/$conversationId")
                }
            )
        }

        composable("chat/{conversationId}") { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getString("conversationId")?.toIntOrNull() ?: 0,
                onBack = { navController.popBackStack() },
                onGroupInfo = { conversationId -> navController.navigate("group/$conversationId") },
                onOpenProfile = { userId -> navController.navigate("user/$userId") }
            )
        }

        composable("group/new") {
            GroupCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { conversationId ->
                    navController.popBackStack()
                    navController.navigate("chat/$conversationId")
                }
            )
        }

        composable("group/{conversationId}") { entry ->
            GroupInfoScreen(
                conversationId = entry.arguments?.getString("conversationId")?.toIntOrNull() ?: 0,
                onBack = { navController.popBackStack() },
                onLeft = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable("admin") {
            AdminScreen(onBack = { navController.popBackStack() })
        }
    }

    if (callState.stage != CallStage.IDLE) {
        CallScreen(state = callState)
    }
}
