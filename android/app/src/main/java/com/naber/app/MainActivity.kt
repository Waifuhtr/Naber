package com.naber.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naber.app.call.CallStage
import com.naber.app.push.Notifications
import com.naber.app.ui.screens.AdminScreen
import com.naber.app.ui.screens.CallScreen
import com.naber.app.ui.screens.ChatScreen
import com.naber.app.ui.screens.ChatsScreen
import com.naber.app.ui.screens.LoginScreen
import com.naber.app.ui.screens.NewChatScreen
import com.naber.app.ui.screens.ProfileScreen
import com.naber.app.ui.theme.NaberTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pendingConversationId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        Naber.init(applicationContext)
        pendingConversationId = intent?.getIntExtra(EXTRA_CONVERSATION_ID, 0) ?: 0

        setContent {
            NaberTheme {
                NaberRoot(startConversationId = pendingConversationId)
            }
        }

        askNotificationPermission()
        registerPushToken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Naber.session.isLoggedIn) Naber.events.start()
    }

    override fun onPause() {
        super.onPause()
        // Arama surerken olay akisi acik kalmali.
        if (Naber.calls.state.value.stage == CallStage.IDLE) {
            Naber.events.stop()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    }
}

@Composable
private fun NaberRoot(startConversationId: Int) {
    val navController = rememberNavController()
    var loggedIn by remember { mutableStateOf(Naber.session.isLoggedIn) }
    val callState by Naber.calls.state.collectAsState()
    val incomingCall by Naber.events.incomingCall.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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

    // Sunucudan gelen arama bilgisi arama ekranini acar.
    LaunchedEffect(incomingCall?.id) {
        val call = incomingCall
        if (call != null && call.status == "ringing") {
            Naber.calls.onIncoming(call)
        }
    }

    LaunchedEffect(callState.stage) {
        if (callState.stage == CallStage.IDLE) Notifications.cancelCall(context)
    }

    if (!loggedIn) {
        LoginScreen(onLoggedIn = { loggedIn = true })
        return
    }

    NavHost(navController = navController, startDestination = "chats") {
        composable("chats") {
            ChatsScreen(
                startConversationId = startConversationId,
                onOpenChat = { conversationId, peerId -> navController.navigate("chat/$conversationId/$peerId") },
                onNewChat = { navController.navigate("users") },
                onProfile = { navController.navigate("profile") },
                onAdmin = { navController.navigate("admin") },
                onLoggedOut = { loggedIn = false }
            )
        }
        composable("users") {
            NewChatScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { conversationId, peerId ->
                    navController.popBackStack()
                    navController.navigate("chat/$conversationId/$peerId")
                }
            )
        }
        composable("chat/{conversationId}/{peerId}") { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getString("conversationId")?.toIntOrNull() ?: 0,
                peerId = entry.arguments?.getString("peerId")?.toIntOrNull() ?: 0,
                onBack = { navController.popBackStack() }
            )
        }
        composable("profile") {
            ProfileScreen(onBack = { navController.popBackStack() }, onLoggedOut = { loggedIn = false })
        }
        composable("admin") {
            AdminScreen(onBack = { navController.popBackStack() })
        }
    }

    if (callState.stage != CallStage.IDLE) {
        CallScreen(state = callState)
    }
}
