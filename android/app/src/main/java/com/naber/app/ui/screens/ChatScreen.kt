package com.naber.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.naber.app.Naber
import com.naber.app.data.Chat
import com.naber.app.data.LocalFiles
import com.naber.app.data.LocalMedia
import com.naber.app.data.Message
import com.naber.app.data.SendState
import com.naber.app.data.TypingUser
import com.naber.app.ui.SenderAvatar
import com.naber.app.ui.ChatAvatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.MessageImage
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.formatClock
import com.naber.app.ui.formatPresence
import com.naber.app.ui.prepareImage
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(conversationId: Int, onBack: () -> Unit, onGroupInfo: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val myId = Naber.session.user?.id ?: 0

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var chat by remember { mutableStateOf<Chat?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf<List<TypingUser>>(emptyList()) }
    var typingAt by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    val presenceMap by Naber.events.presence.collectAsState()
    val revisions by Naber.events.revisions.collectAsState()
    var fullScreen by remember { mutableStateOf<Any?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Message?>(null) }
    // "Yaziyor" bilgisi her tusa basista degil, en fazla 3 saniyede bir gonderilir.
    val lastTypingSent = remember { longArrayOf(0L) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val current = chat ?: return@rememberLauncherForActivityResult
        if (!granted) return@rememberLauncherForActivityResult
        if (current.isGroup) {
            Naber.calls.startGroupCall(current.id, current.title, current.amAdmin)
        } else {
            current.peer?.let { Naber.calls.startOutgoing(it) }
        }
    }

    fun startCall() {
        val current = chat ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (current.isGroup) {
                Naber.calls.startGroupCall(current.id, current.title, current.amAdmin)
            } else {
                current.peer?.let { Naber.calls.startOutgoing(it) }
            }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    suspend fun load() {
        try {
            val (list, info, typingUsers) = Naber.api.messages(conversationId)
            messages = list
            chat = info
            typing = typingUsers
            Naber.api.markRead(conversationId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.activeConversationId = conversationId
        load()
    }

    LaunchedEffect(conversationId) {
        Naber.events.messages.collect { incoming ->
            if (incoming.conversationId != conversationId) return@collect
            messages = (messages.filterNot { it.clientId.isNotEmpty() && it.clientId == incoming.clientId } + incoming)
                .distinctBy { it.key }
                .sortedBy { it.createdAt }
            if (incoming.senderId != myId) Naber.api.markRead(conversationId)
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.readStates.collect { states ->
            val state = states.firstOrNull { it.conversationId == conversationId } ?: return@collect
            chat = chat?.copy(readWatermark = state.watermark)
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.typing.collect { state ->
            if (state.conversationId == conversationId) {
                typing = state.users
                typingAt = state.at
            }
        }
    }

    // "Yaziyor" bilgisi sunucudan gelmeyi birakirsa en gec 6 saniyede kaybolur.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    // Grup adi, uye listesi veya susturma degisirse ekran kendiliginden tazelenir.
    LaunchedEffect(revisions[conversationId]) {
        if (!loading) {
            runCatching { Naber.api.chatInfo(conversationId) }.onSuccess { chat = it }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendText() {
        val body = draft.trim()
        if (body.isEmpty()) return
        val clientId = UUID.randomUUID().toString()
        messages = messages + Message(
            id = 0,
            conversationId = conversationId,
            senderId = myId,
            type = "text",
            body = body,
            clientId = clientId,
            isRead = false,
            deleted = false,
            createdAt = System.currentTimeMillis() / 1000,
            media = null,
            sendState = SendState.SENDING
        )
        draft = ""
        lastTypingSent[0] = 0L
        Naber.events.launchInScope { Naber.api.sendTyping(conversationId, false) }
        scope.launch {
            try {
                val sent = Naber.api.sendText(conversationId, body, clientId)
                messages = messages.map { if (it.clientId == clientId) sent else it }
            } catch (e: Exception) {
                messages = messages.map { if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it }
            }
        }
    }

    fun sendImage(uri: Uri) {
        val clientId = UUID.randomUUID().toString()
        // Gorsel once yerel dosyadan gosterilir; yukleme arka planda surer.
        LocalMedia.remember(clientId, uri.toString())
        messages = messages + Message(
            id = 0,
            conversationId = conversationId,
            senderId = myId,
            type = "image",
            body = "",
            clientId = clientId,
            isRead = false,
            deleted = false,
            createdAt = System.currentTimeMillis() / 1000,
            media = null,
            localImageUri = uri.toString(),
            sendState = SendState.SENDING
        )

        scope.launch {
            try {
                val prepared = prepareImage(context, uri)
                if (prepared == null) {
                    error = "Gorsel okunamadi."
                    messages = messages.map { if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it }
                    return@launch
                }
                // Galeri adresi gecici oldugu icin kalici bir kopya alinir.
                val localCopy = LocalFiles.persist(context, prepared.bytes, "msg-$clientId.jpg") ?: uri.toString()
                LocalMedia.remember(clientId, localCopy)

                val media = Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) { percent ->
                    messages = messages.map { if (it.clientId == clientId) it.copy(uploadProgress = percent) else it }
                }
                LocalMedia.rememberMedia(media.id, localCopy)
                val sent = Naber.api.sendImage(conversationId, media.id, "", clientId)
                messages = messages.map {
                    if (it.clientId == clientId) sent.copy(localImageUri = localCopy) else it
                }
            } catch (e: Exception) {
                messages = messages.map { if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it }
                error = e.message
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { sendImage(it) }
    }

    val current = chat
    val typingActive = typing.isNotEmpty() && (tick - typingAt) < 6000
    val peerPresence = current?.peer?.id?.let { presenceMap[it] }
    val peerOnline = peerPresence?.online ?: (current?.peer?.online == true)
    val peerLastSeen = peerPresence?.lastSeen ?: (current?.peer?.lastSeen ?: 0L)
    val subtitle = when {
        typingActive && current?.isGroup == true -> "${typing.joinToString { it.name }} yaziyor..."
        typingActive -> "yaziyor..."
        current?.isGroup == true -> "${current.memberCount} uye"
        current?.peer != null -> formatPresence(peerOnline, peerLastSeen)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Ust cubuk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = NaberColors.TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        Naber.events.activeConversationId = 0
                        Naber.events.clearTyping()
                        if (draft.isNotEmpty()) {
                            Naber.events.launchInScope { Naber.api.sendTyping(conversationId, false) }
                        }
                        onBack()
                    }
            )
            Spacer(Modifier.width(10.dp))
            Box {
                current?.let { ChatAvatar(it, size = 40.dp) }
                if (current?.isGroup == false && peerOnline) {
                    OnlineDot(size = 11.dp, borderColor = NaberColors.TopBar, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = current?.isGroup == true) { current?.let { onGroupInfo(it.id) } }
            ) {
                Text(
                    current?.title.orEmpty(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NaberColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = if (typingActive) NaberColors.Accent else NaberColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.Filled.Call,
                contentDescription = "Sesli ara",
                tint = NaberColors.TextPrimary,
                modifier = Modifier.size(22.dp).clickable { startCall() }
            )
            Spacer(Modifier.width(14.dp))
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Secenekler",
                    tint = NaberColors.TextPrimary,
                    modifier = Modifier.size(22.dp).clickable { menuOpen = true }
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (current?.isGroup == true) {
                        DropdownMenuItem(
                            text = { Text("Grup bilgisi") },
                            onClick = { menuOpen = false; onGroupInfo(current.id) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (current?.notifyMuted == true) "Bildirimleri ac" else "Bildirimleri sessize al") },
                        onClick = {
                            menuOpen = false
                            val muted = current?.notifyMuted != true
                            scope.launch {
                                runCatching { Naber.api.setChatNotifications(conversationId, muted) }
                                chat = chat?.copy(notifyMuted = muted)
                            }
                        }
                    )
                }
            }
        }

        if (current?.chatMuted == true) {
            Text(
                "Bu grupta yonetici tarafindan susturuldunuz, mesaj gonderemezsiniz.",
                fontSize = 12.sp,
                color = NaberColors.Warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.SurfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaberColors.Accent)
                }

                messages.isEmpty() -> EmptyState(
                    "Sohbeti baslatin",
                    "Ilk mesaji gonderin, karsi taraf aninda gorecek.",
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.key }) { message ->
                        MessageRow(
                            message = message,
                            mine = message.senderId == myId,
                            isGroup = current?.isGroup == true,
                            seen = (chat?.readWatermark ?: 0) >= message.id && message.id > 0,
                            onImageClick = { fullScreen = it },
                            onLongPress = { deleteTarget = message }
                        )
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NaberColors.Danger)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                LaunchedEffect(it) {
                    delay(4000)
                    error = null
                }
            }
        }

        // Mesaj yazma alani — klavye acilinca yukari kayar.
        if (current?.chatMuted != true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.TopBar)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NaberColors.SurfaceHigh)
                        .clickable {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Gorsel ekle", tint = NaberColors.TextSecondary, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(NaberColors.SurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (draft.isEmpty()) {
                        Text("Mesaj yazin...", color = NaberColors.TextSecondary, fontSize = 14.5.sp)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { value ->
                            val wasEmpty = draft.isEmpty()
                            draft = value
                            val now = System.currentTimeMillis()
                            when {
                                // Ilk harfte hemen, sonra en fazla 3 saniyede bir bildir.
                                value.isNotEmpty() && (wasEmpty || now - lastTypingSent[0] > 3000) -> {
                                    lastTypingSent[0] = now
                                    Naber.events.launchInScope { Naber.api.sendTyping(conversationId, true) }
                                }
                                // Kutu bosaldiginda "yaziyor" hemen kalksin.
                                value.isEmpty() -> {
                                    lastTypingSent[0] = 0L
                                    Naber.events.launchInScope { Naber.api.sendTyping(conversationId, false) }
                                }
                            }
                        },
                        textStyle = TextStyle(color = NaberColors.TextPrimary, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(NaberColors.Accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (draft.isBlank()) NaberColors.SurfaceHigh else NaberColors.Bubble)
                        .clickable(enabled = draft.isNotBlank()) { sendText() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gonder",
                        tint = if (draft.isBlank()) NaberColors.TextSecondary else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    fullScreen?.let { model ->
        Dialog(onDismissRequest = { fullScreen = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable { fullScreen = null },
                contentAlignment = Alignment.Center
            ) {
                MessageImage(
                    media = null,
                    localUri = model,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    deleteTarget?.let { message ->
        val canDelete = message.senderId == myId || chat?.amAdmin == true
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (canDelete) "Mesaj silinsin mi?" else "Mesaj") },
            text = { Text(if (canDelete) "Mesaj herkesten silinecek." else "Bu mesaji silme yetkiniz yok.") },
            confirmButton = {
                if (canDelete) {
                    TextButton(onClick = {
                        val target = message
                        deleteTarget = null
                        scope.launch {
                            runCatching { Naber.api.deleteMessage(target.id) }
                                .onSuccess {
                                    messages = messages.map {
                                        if (it.id == target.id) it.copy(deleted = true, body = "", media = null) else it
                                    }
                                }
                                .onFailure { error = it.message }
                        }
                    }) { Text("Sil", color = NaberColors.Danger) }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Kapat", color = NaberColors.TextSecondary) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: Message,
    mine: Boolean,
    isGroup: Boolean,
    seen: Boolean,
    onImageClick: (Any) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine && isGroup) {
            SenderAvatar(
                name = message.senderName,
                url = message.senderAvatar,
                id = message.senderId,
                size = 28.dp,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .background(if (mine) NaberColors.Bubble else NaberColors.BubbleIn)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(if (message.type == "image" && !message.deleted) 4.dp else 10.dp)
        ) {
            if (isGroup && !mine && message.senderName.isNotBlank()) {
                Text(
                    message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NaberColors.Accent,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                )
            }

            if (message.deleted) {
                Text(
                    "Bu mesaj silindi",
                    fontSize = 14.sp,
                    color = if (mine) Color.White.copy(alpha = 0.7f) else NaberColors.TextSecondary
                )
            } else {
                if (message.type == "image") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { message.displayImage?.let(onImageClick) }
                    ) {
                        MessageImage(
                            media = message.media,
                            localUri = message.localImageUri ?: com.naber.app.data.LocalMedia.uriFor(message.media?.id ?: 0, message.clientId),
                            modifier = Modifier.widthIn(max = 280.dp).heightIn(max = 330.dp)
                        )
                        if (message.sendState == SendState.SENDING) {
                            Box(
                                modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LinearProgressIndicator(
                                        progress = { message.uploadProgress / 100f },
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.width(110.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("%${message.uploadProgress}", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                        if (message.sendState == SendState.FAILED) {
                            Box(
                                modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Yuklenemedi", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (message.body.isNotBlank()) {
                    Text(
                        message.body,
                        fontSize = 15.sp,
                        color = if (mine) Color.White else NaberColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = if (message.type == "image") 4.dp else 0.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 3.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    formatClock(message.createdAt),
                    fontSize = 10.5.sp,
                    color = if (mine) Color.White.copy(alpha = 0.75f) else NaberColors.TextSecondary
                )
                if (mine) {
                    when (message.sendState) {
                        SendState.SENDING -> CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.dp,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        SendState.FAILED -> Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Gonderilemedi",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )

                        else -> Icon(
                            if (seen) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (seen) "Okundu" else "Gonderildi",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
