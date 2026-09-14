package com.naber.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naber.app.Naber
import com.naber.app.data.ChatSummary
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.UnreadBadge
import com.naber.app.ui.formatChatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    startConversationId: Int,
    onOpenChat: (Int, Int) -> Unit,
    onNewChat: () -> Unit,
    onProfile: () -> Unit,
    onAdmin: () -> Unit,
    onLoggedOut: () -> Unit
) {
    var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val me = Naber.session.user
    val connected by Naber.events.connected.collectAsState()

    suspend fun reload() {
        try {
            chats = Naber.api.chats().first
            error = null
        } catch (e: Exception) {
            error = e.message
            if (e.message?.contains("401") == true) onLoggedOut()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        Naber.events.activeConversationId = 0
        reload()
        // Bildirimden acildiysa dogrudan sohbete gec.
        if (startConversationId > 0) {
            chats.firstOrNull { it.id == startConversationId }?.let { onOpenChat(it.id, it.peer.id) }
        }
    }

    // Yeni mesaj gelince liste tazelenir.
    LaunchedEffect(Unit) {
        Naber.events.messages.collect { reload() }
    }

    val filtered = remember(chats, search) {
        if (search.isBlank()) chats
        else chats.filter { it.peer.displayName.contains(search, ignoreCase = true) || it.peer.username.contains(search, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Naber", fontWeight = FontWeight.Bold)
                        if (!connected) {
                            Text(
                                "baglanti bekleniyor...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "Ara", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    if (me?.isAdmin == true) {
                        IconButton(onClick = onAdmin) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = "Yonetim",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = onProfile) {
                        Avatar(me, size = 32.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Filled.Chat, contentDescription = "Yeni sohbet")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (searchOpen) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Sohbetlerde ara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> EmptyState(
                    title = "Baglanti sorunu",
                    description = error ?: "",
                    modifier = Modifier.padding(top = 40.dp)
                )

                filtered.isEmpty() -> EmptyState(
                    title = "Henuz sohbet yok",
                    description = "Sag alttaki butona dokunarak yeni bir sohbet baslatin.",
                    modifier = Modifier.padding(top = 40.dp)
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRow(chat = chat, onClick = { onOpenChat(chat.id, chat.peer.id) })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(chat.peer, size = 52.dp)
                if (chat.peer.online) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
                    ) {}
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    chat.peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val last = chat.lastMessage
                val preview = when {
                    last == null -> "Sohbeti baslatin"
                    last.type == "image" -> "Fotograf"
                    else -> last.body
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatChatTime(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                UnreadBadge(chat.unread)
                if (chat.unread == 0) Spacer(Modifier.height(1.dp))
            }
        }
    }
}
