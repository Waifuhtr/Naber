package com.naber.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import androidx.compose.ui.platform.LocalContext
import com.naber.app.data.Chat
import com.naber.app.data.LocalStore
import com.naber.app.ui.Avatar
import com.naber.app.ui.ChatAvatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.NaberChip
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.UnreadBadge
import com.naber.app.ui.formatChatTime
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay

@Composable
fun ChatsTab(
    startConversationId: Int,
    onOpenChat: (Int) -> Unit,
    onNewGroup: () -> Unit,
    onAdmin: () -> Unit,
    onContacts: () -> Unit
) {
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }

    val me = Naber.session.user
    val connected by Naber.events.connected.collectAsState()
    val presenceMap by Naber.events.presence.collectAsState()
    val revisions by Naber.events.revisions.collectAsState()

    val context = LocalContext.current

    suspend fun reload() {
        try {
            val (fresh, unreadTotal) = Naber.api.chats()
            chats = fresh
            error = null
            LocalStore.saveChats(context, fresh, unreadTotal)
        } catch (e: Exception) {
            // Cevrimdisiyken elimizdeki kopya ekranda kalir; hata yalnizca
            // hicbir sey gosteremiyorsak anlamli olur.
            if (chats.isEmpty()) error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        Naber.events.activeConversationId = 0
        // Once cihazdaki kopya: liste aninda gorunur, ag beklenmez.
        val (cached, _) = LocalStore.loadChats(context)
        if (cached.isNotEmpty() && chats.isEmpty()) {
            chats = cached
            loading = false
        }
        reload()
        if (startConversationId > 0) onOpenChat(startConversationId)
    }

    // Liste degistiginde cihazdaki kopya tazelenir; art arda gelen
    // degisikliklerde tek yazma yapmak icin kisa bir bekleme konur.
    LaunchedEffect(chats) {
        if (chats.isEmpty()) return@LaunchedEffect
        delay(400)
        LocalStore.saveChats(context, chats, chats.sumOf { it.unread })
    }

    // Yeni mesajda tum liste yeniden cekilmez; yalnizca ilgili satir guncellenir.
    LaunchedEffect(Unit) {
        Naber.events.messages.collect { message ->
            val myId = Naber.session.user?.id ?: 0
            val existing = chats.firstOrNull { it.id == message.conversationId }
            if (existing == null) {
                reload()
                return@collect
            }
            val updated = existing.copy(
                lastMessage = message,
                updatedAt = message.createdAt,
                unread = if (message.senderId == myId) existing.unread else existing.unread + 1
            )
            chats = (listOf(updated) + chats.filterNot { it.id == updated.id })
                .sortedByDescending { it.updatedAt }
        }
    }

    // Yeni grup, uye degisikligi veya grup adi degisiminde liste kendiliginden tazelenir.
    LaunchedEffect(revisions) {
        if (revisions.isNotEmpty() && !loading) reload()
    }

    // Okundu bilgisi degistiginde tikleri guncelle.
    LaunchedEffect(Unit) {
        Naber.events.readStates.collect { states ->
            if (states.isEmpty()) return@collect
            val map = states.associate { it.conversationId to it.watermark }
            chats = chats.map { chat ->
                val watermark = map[chat.id] ?: return@map chat
                if (watermark == chat.readWatermark) chat else chat.copy(readWatermark = watermark)
            }
        }
    }

    val filtered = remember(chats, search, filter) {
        chats.filter { chat ->
            val matchesFilter = when (filter) {
                1 -> !chat.isGroup
                2 -> chat.isGroup
                else -> true
            }
            val matchesSearch = search.isBlank() || chat.title.contains(search, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NaberColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Ust baslik
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.TopBar)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(me, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Naber", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
                    Text(
                        if (connected) "cevrimici" else "Baglanti bekleniyor...",
                        fontSize = 11.5.sp,
                        color = if (connected) NaberColors.Accent else NaberColors.TextSecondary
                    )
                }
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Ara",
                    tint = NaberColors.TextSecondary,
                    modifier = Modifier.size(22.dp).clickable { searchOpen = !searchOpen }
                )
                Spacer(Modifier.width(18.dp))
                Box {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Menu",
                        tint = NaberColors.TextSecondary,
                        modifier = Modifier.size(22.dp).clickable { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Yeni grup") },
                            onClick = { menuOpen = false; onNewGroup() }
                        )
                        DropdownMenuItem(
                            text = { Text("Kisi ekle") },
                            onClick = { menuOpen = false; onContacts() }
                        )
                        if (me?.isAdmin == true) {
                            DropdownMenuItem(
                                text = { Text("Yonetim paneli") },
                                onClick = { menuOpen = false; onAdmin() }
                            )
                        }
                    }
                }
                if (me?.isAdmin == true) {
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        Icons.Filled.AdminPanelSettings,
                        contentDescription = "Yonetim",
                        tint = NaberColors.Accent,
                        modifier = Modifier.size(22.dp).clickable { onAdmin() }
                    )
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Sohbetlerde ara") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NaberColors.Surface,
                        unfocusedContainerColor = NaberColors.Surface,
                        focusedBorderColor = NaberColors.Accent,
                        unfocusedBorderColor = NaberColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // Filtre secenekleri
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NaberChip("Tumu", filter == 0) { filter = 0 }
                NaberChip("Kisiler", filter == 1) { filter = 1 }
                NaberChip("Gruplar", filter == 2) { filter = 2 }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaberColors.Accent)
                }

                error != null -> EmptyState("Baglanti sorunu", error ?: "")

                filtered.isEmpty() -> EmptyState(
                    title = if (chats.isEmpty()) "Henuz sohbet yok" else "Sonuc bulunamadi",
                    description = if (chats.isEmpty()) {
                        "Sag alttaki butondan bir kisi ekleyin ya da grup olusturun."
                    } else {
                        "Baska bir filtre veya arama deneyin."
                    }
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            online = chat.peer?.id?.let { presenceMap[it]?.online } ?: (chat.peer?.online == true),
                            onClick = { onOpenChat(chat.id) }
                        )
                    }
                }
            }
        }

        // Yeni sohbet / grup
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(NaberColors.Accent)
                    .clickable { fabMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Yeni", tint = NaberColors.Background)
            }
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Yeni sohbet") },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                    onClick = { fabMenu = false; onContacts() }
                )
                DropdownMenuItem(
                    text = { Text("Yeni grup") },
                    leadingIcon = { Icon(Icons.Filled.Group, null) },
                    onClick = { fabMenu = false; onNewGroup() }
                )
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, online: Boolean, onClick: () -> Unit) {
    val myId = Naber.session.user?.id ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ChatAvatar(chat, size = 52.dp)
            if (!chat.isGroup && online) {
                OnlineDot(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                chat.title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NaberColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val last = chat.lastMessage
            val preview = when {
                last == null -> "Sohbeti baslatin"
                last.deleted -> "Bu mesaj silindi"
                last.type == "image" -> "Fotograf"
                else -> last.body
            }
            val prefix = when {
                last == null -> ""
                last.senderId == myId -> "Sen: "
                chat.isGroup && last.senderName.isNotBlank() -> "${last.senderName}: "
                else -> ""
            }
            Text(
                prefix + preview,
                fontSize = 13.sp,
                color = NaberColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                formatChatTime(chat.updatedAt),
                fontSize = 11.sp,
                color = if (chat.unread > 0) NaberColors.Accent else NaberColors.TextSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chat.notifyMuted) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = "Sessiz",
                        tint = NaberColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (chat.unread > 0) {
                    UnreadBadge(chat.unread)
                } else if (chat.lastMessage?.senderId == myId && chat.lastMessage != null) {
                    val seen = chat.readWatermark >= (chat.lastMessage?.id ?: 0)
                    Icon(
                        if (seen) Icons.Filled.DoneAll else Icons.Filled.Done,
                        contentDescription = if (seen) "Okundu" else "Gonderildi",
                        tint = if (seen) NaberColors.Accent else NaberColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
