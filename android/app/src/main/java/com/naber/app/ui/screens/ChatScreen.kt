package com.naber.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.naber.app.Naber
import com.naber.app.data.Media
import com.naber.app.data.Message
import com.naber.app.data.SendState
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.formatClock
import com.naber.app.ui.prepareImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(conversationId: Int, peerId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var peer by remember { mutableStateOf<User?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var peerTyping by remember { mutableStateOf(false) }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = peer
        if (granted && target != null) Naber.calls.startOutgoing(target)
    }

    fun startCall() {
        val target = peer ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Naber.calls.startOutgoing(target)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.activeConversationId = conversationId
        try {
            val (list, loadedPeer, typing) = Naber.api.messages(conversationId)
            messages = list
            peer = loadedPeer
            peerTyping = typing
            Naber.api.markRead(conversationId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // Yeni mesajlar
    LaunchedEffect(conversationId) {
        Naber.events.messages.collect { incoming ->
            if (incoming.conversationId != conversationId) return@collect
            messages = (messages.filterNot { it.clientId.isNotEmpty() && it.clientId == incoming.clientId } + incoming)
                .distinctBy { if (it.id > 0) "id-${it.id}" else "c-${it.clientId}" }
                .sortedBy { it.createdAt }
            if (incoming.senderId != Naber.session.user?.id) {
                Naber.api.markRead(conversationId)
            }
        }
    }

    // Okundu bilgisi
    LaunchedEffect(conversationId) {
        Naber.events.readReceipts.collect { ids ->
            if (ids.isEmpty()) return@collect
            messages = messages.map { if (ids.contains(it.id)) it.copy(isRead = true) else it }
        }
    }

    // Yaziyor gostergesi
    LaunchedEffect(conversationId) {
        Naber.events.typing.collect { (convId, typing) ->
            if (convId == conversationId) peerTyping = typing
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendText() {
        val body = draft.trim()
        if (body.isEmpty()) return
        val clientId = UUID.randomUUID().toString()
        val optimistic = Message(
            id = 0,
            conversationId = conversationId,
            senderId = Naber.session.user?.id ?: 0,
            receiverId = peerId,
            type = "text",
            body = body,
            clientId = clientId,
            isRead = false,
            createdAt = System.currentTimeMillis() / 1000,
            media = null,
            sendState = SendState.SENDING
        )
        messages = messages + optimistic
        draft = ""
        scope.launch {
            try {
                val sent = Naber.api.sendText(conversationId, peerId, body, clientId)
                messages = messages.map { if (it.clientId == clientId) sent.copy(sendState = SendState.SENT) else it }
            } catch (e: Exception) {
                messages = messages.map { if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it }
            }
        }
    }

    fun sendImage(uri: android.net.Uri) {
        val clientId = UUID.randomUUID().toString()
        scope.launch {
            val prepared = prepareImage(context, uri)
            if (prepared == null) {
                error = "Gorsel okunamadi."
                return@launch
            }
            val placeholder = Message(
                id = 0,
                conversationId = conversationId,
                senderId = Naber.session.user?.id ?: 0,
                receiverId = peerId,
                type = "image",
                body = "",
                clientId = clientId,
                isRead = false,
                createdAt = System.currentTimeMillis() / 1000,
                media = null,
                localImageUri = uri.toString(),
                sendState = SendState.SENDING
            )
            messages = messages + placeholder
            try {
                val media = Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) { percent ->
                    messages = messages.map { if (it.clientId == clientId) it.copy(uploadProgress = percent) else it }
                }
                val sent = Naber.api.sendImage(conversationId, peerId, media.id, "", clientId)
                messages = messages.map { if (it.clientId == clientId) sent.copy(sendState = SendState.SENT) else it }
            } catch (e: Exception) {
                messages = messages.map { if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it }
                error = e.message
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { sendImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        Naber.events.activeConversationId = 0
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(peer, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                peer?.displayName ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    peerTyping -> "yaziyor..."
                                    peer?.online == true -> "cevrimici"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { startCall() }) {
                        Icon(Icons.Filled.Call, contentDescription = "Sesli ara", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = "Galeri")
                    }
                    TextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            Naber.events.launchInScope { Naber.api.sendTyping(conversationId) }
                        },
                        placeholder = { Text("Mesaj yazin") },
                        modifier = Modifier.weight(1f).heightIn(max = 140.dp),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(onClick = { sendText() }, enabled = draft.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gonder",
                            tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                messages.isEmpty() -> EmptyState(
                    title = "Sohbeti baslatin",
                    description = "Ilk mesaji gonderin, karsi taraf aninda gorecek.",
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { if (it.id > 0) "id-${it.id}" else "c-${it.clientId}" }) { message ->
                        MessageBubble(
                            message = message,
                            mine = message.senderId == Naber.session.user?.id,
                            onImageClick = { fullScreenImage = it }
                        )
                    }
                }
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                ) {
                    Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                }
                LaunchedEffect(it) {
                    delay(4000)
                    error = null
                }
            }
        }
    }

    fullScreenImage?.let { url ->
        Dialog(onDismissRequest = { fullScreenImage = null }) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable { fullScreenImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean, onImageClick: (String) -> Unit) {
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(color = bubbleColor, shape = shape, tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 300.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (message.type == "image") {
                    ImageContent(message, onImageClick)
                }
                if (message.body.isNotBlank()) {
                    Text(
                        message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatClock(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        when (message.sendState) {
                            SendState.SENDING -> CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SendState.FAILED -> Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Gonderilemedi",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                            else -> Icon(
                                if (message.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = if (message.isRead) "Okundu" else "Gonderildi",
                                tint = if (message.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageContent(message: Message, onImageClick: (String) -> Unit) {
    val model: Any? = message.media?.url?.takeIf { it.isNotBlank() } ?: message.localImageUri
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                val url = message.media?.url
                if (!url.isNullOrBlank()) onImageClick(url)
            }
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Gorsel",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
            )
        }
        if (message.sendState == SendState.SENDING) {
            Box(
                modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { message.uploadProgress / 100f },
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("%${message.uploadProgress}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (message.sendState == SendState.FAILED) {
            Box(
                modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text("Yuklenemedi - tekrar deneyin", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Kullanilmayan ama ileride medya listesi icin faydali kisayol. */
@Suppress("unused")
private fun Media.isImage(): Boolean = mime.startsWith("image/")
