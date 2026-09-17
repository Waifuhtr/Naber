package com.naber.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.naber.app.Naber
import com.naber.app.data.Chat
import com.naber.app.data.ChatWallpaper
import com.naber.app.data.LocalFiles
import com.naber.app.data.LocalMedia
import com.naber.app.data.LocalStore
import com.naber.app.data.Locations
import com.naber.app.data.formatLocation
import com.naber.app.data.mapsUri
import com.naber.app.data.parseLocation
import com.naber.app.data.MediaStore
import com.naber.app.data.Message
import com.naber.app.data.MessageReplySummary
import com.naber.app.data.Poll
import com.naber.app.data.SendState
import com.naber.app.data.MessageInfo
import com.naber.app.data.TickState
import com.naber.app.data.TypingUser
import com.naber.app.data.User
import com.naber.app.ui.SenderAvatar
import com.naber.app.ui.ChatAvatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.MessageImage
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.formatBytes
import com.naber.app.ui.formatChatTime
import com.naber.app.ui.formatClock
import com.naber.app.ui.formatPresence
import com.naber.app.ui.prepareImage
import com.naber.app.push.Notifications
import com.naber.app.push.SoundPlayer
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Basili tutunca gosterilen hizli reaksiyon secenekleri. */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** Kaybolan mesaj sureleri; sunucudaki DISAPPEAR_OPTIONS ile ayni olmali. */
private val DISAPPEAR_OPTIONS = listOf(0, 3600, 86400, 604800, 2592000)

/** Anket secenek sinirlari; sunucudaki Naber_Polls sabitleriyle ayni. */
private const val POLL_MIN_OPTIONS = 2
private const val POLL_MAX_OPTIONS = 6

/** Grubun tamamini kapsayan bahsetme sozcukleri; sunucu ile ayni olmali. */
private val MENTION_ALL_TOKENS = listOf("herkes", "hepsi", "everyone")

/**
 * "@isim" gecen yerleri vurgular.
 *
 * Isimler sohbetin uye listesinden gelir; boylece metindeki bir e-posta
 * adresi yanlislikla bahsetme gibi gorunmez.
 */
private fun mentionText(body: String, names: List<String>, highlight: Color): AnnotatedString {
    if (names.isEmpty() || !body.contains('@')) return AnnotatedString(body)
    return buildAnnotatedString {
        append(body)
        val style = SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)
        // Uzun isimler once: "@Ali Veli" yazilmisken yalnizca "Ali" kismi
        // vurgulanip geri kalani duz kalmasin.
        names.sortedByDescending { it.length }.forEach { name ->
            val token = "@" + name
            var index = body.indexOf(token, ignoreCase = true)
            while (index >= 0) {
                addStyle(style, index, index + token.length)
                index = body.indexOf(token, index + token.length, ignoreCase = true)
            }
        }
    }
}

private fun disappearLabel(seconds: Int): String = when (seconds) {
    3600 -> "1 saat"
    86400 -> "24 saat"
    604800 -> "7 gun"
    2592000 -> "30 gun"
    else -> "Kapali"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(conversationId: Int, onBack: () -> Unit, onGroupInfo: (Int) -> Unit, onOpenProfile: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val myId = Naber.session.user?.id ?: 0

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var chat by remember { mutableStateOf<Chat?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingOlder by remember { mutableStateOf(false) }
    // Sunucuda daha eski mesaj kalmadiginda bir daha istenmez.
    var hasOlder by remember(conversationId) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf<List<TypingUser>>(emptyList()) }
    var typingAt by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    val presenceMap by Naber.events.presence.collectAsState()
    val revisions by Naber.events.revisions.collectAsState()
    val connected by Naber.events.connected.collectAsState()
    var fullScreen by remember { mutableStateOf<Any?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Message?>(null) }
    var replyTarget by remember { mutableStateOf<Message?>(null) }
    var editTarget by remember { mutableStateOf<Message?>(null) }
    var forwardTarget by remember { mutableStateOf<Message?>(null) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var muteDialogOpen by remember { mutableStateOf(false) }
    var disappearDialogOpen by remember { mutableStateOf(false) }
    var wallpaperDialogOpen by remember { mutableStateOf(false) }
    var locationBusy by remember { mutableStateOf(false) }
    var pollDialogOpen by remember { mutableStateOf(false) }
    // Izin penceresinden donunce konumu gondermek icin kullanilir; izin
    // sonucu, gondermeyi yapan fonksiyon tanimlanmadan once gelir.
    val sendLocationRequest = remember { mutableStateOf(false) }
    var wallpaperId by remember(conversationId) { mutableStateOf(ChatWallpaper.idFor(context, conversationId)) }
    var infoTarget by remember { mutableStateOf<MessageInfo?>(null) }
    val retriedImages = remember { mutableStateListOf<Int>() }
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

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sendLocationRequest.value = true else error = "Konum izni verilmedi."
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
            // Gonderilememis mesajlar kuyrukta kalmali; sunucu listesi onlari bilmez.
            val pending = messages.filter { it.id <= 0 }
            messages = (list + pending).distinctBy { it.key }.sortedBy { it.createdAt }
            chat = info
            typing = typingUsers
            error = null
            Naber.api.markRead(conversationId)
        } catch (e: Exception) {
            // Cevrimdisiyken cihazdaki gecmis ekranda kalir.
            if (messages.isEmpty()) error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.activeConversationId = conversationId
        Notifications.cancelConversation(context, conversationId)
        // Once cihazdaki gecmis: sohbet aninda dolu acilir, ag beklenmez.
        val cached = LocalStore.loadMessages(context, conversationId)
        if (cached.isNotEmpty()) {
            messages = cached
            loading = false
        }
        load()
    }

    // Gecmis degistiginde cihazdaki kopya tazelenir (kuyruktakiler dahil).
    // Yukleme ilerlemesi gibi hizli degisikliklerde her seferinde diske
    // yazmamak icin kisa bir bekleme konur; LaunchedEffect yeni degisiklikte
    // onceki beklemeyi iptal eder.
    LaunchedEffect(messages) {
        if (messages.isEmpty()) return@LaunchedEffect
        delay(400)
        LocalStore.saveMessages(context, conversationId, messages)
    }

    // Art arda gelen birden fazla mesaj icin "okundu" tek istekte toplanir;
    // her mesaja ayri istek atmak yerine kisa bir sessizlik beklenir.
    var markReadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(conversationId) {
        Naber.events.messages.collect { incoming ->
            if (incoming.conversationId != conversationId) return@collect
            messages = (messages.filterNot { it.clientId.isNotEmpty() && it.clientId == incoming.clientId } + incoming)
                .distinctBy { it.key }
                .sortedBy { it.createdAt }
            if (incoming.senderId != myId) {
                markReadJob?.cancel()
                markReadJob = scope.launch {
                    delay(300)
                    Naber.api.markRead(conversationId)
                }
            }
        }
    }

    LaunchedEffect(conversationId) {
        Naber.events.readStates.collect { states ->
            val state = states.firstOrNull { it.conversationId == conversationId } ?: return@collect
            chat = chat?.copy(readWatermark = state.watermark, deliveredWatermark = state.delivered)
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

    // Grup bilgisi, susturma veya silinen mesaj gibi degisikliklerde ekran
    // kendiliginden tazelenir (sohbetten cikip girmeye gerek yok).
    LaunchedEffect(revisions[conversationId]) {
        if (!loading) {
            runCatching { Naber.api.chatInfo(conversationId) }.onSuccess { chat = it }
            runCatching { Naber.api.messages(conversationId) }.onSuccess { (fresh, info, _) ->
                if (fresh.isNotEmpty()) {
                    val pending = messages.filter { it.id <= 0 }
                    messages = (fresh + pending).distinctBy { it.key }.sortedBy { it.createdAt }
                }
                info?.let { chat = it }
            }
        }
    }

    // En alta yalnizca yeni mesaj eklendiginde kaydirilir. Eski mesajlar
    // yukari eklendiginde son mesaj degismedigi icin ekran yerinde kalir.
    LaunchedEffect(messages.lastOrNull()?.key) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun mark(clientId: String, state: SendState) {
        messages = messages.map { if (it.clientId == clientId) it.copy(sendState = state) else it }
    }

    /**
     * Sunucuya metin mesajini ulastirir. Basarisiz olursa kuyrukta kalir.
     * Yanit bilgisi yerel taslaktan okunur; boylece yeniden denemede de korunur.
     */
    suspend fun deliverText(clientId: String, body: String) {
        try {
            mark(clientId, SendState.SENDING)
            val replyId = messages.firstOrNull { it.clientId == clientId }?.replyTo?.id ?: 0
            val sent = Naber.api.sendText(conversationId, body, clientId, replyId)
            messages = messages.map { if (it.clientId == clientId) sent else it }
            SoundPlayer.playSent(context)
        } catch (e: Exception) {
            mark(clientId, SendState.FAILED)
        }
    }

    /** Gorseli hazirlar, yukler ve mesaji gonderir. */
    suspend fun deliverImage(clientId: String, uri: Uri) {
        try {
            mark(clientId, SendState.SENDING)
            val prepared = prepareImage(context, uri)
            if (prepared == null) {
                error = "Gorsel okunamadi."
                mark(clientId, SendState.FAILED)
                return
            }
            // Galeri adresi gecici oldugu icin kalici bir kopya alinir.
            val localCopy = LocalFiles.persist(context, prepared.bytes, "msg-$clientId.jpg") ?: uri.toString()
            LocalMedia.remember(clientId, localCopy)
            messages = messages.map { if (it.clientId == clientId) it.copy(localImageUri = localCopy) else it }

            // Depolama ucundan gecici hata gelebiliyor; bir kez sessizce tekrar denenir.
            val media = try {
                Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) { percent ->
                    messages = messages.map { if (it.clientId == clientId) it.copy(uploadProgress = percent) else it }
                }
            } catch (first: Exception) {
                delay(700)
                messages = messages.map { if (it.clientId == clientId) it.copy(uploadProgress = 0) else it }
                Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) { percent ->
                    messages = messages.map { if (it.clientId == clientId) it.copy(uploadProgress = percent) else it }
                }
            }
            LocalMedia.rememberMedia(media.id, localCopy)
            // Gonderilen gorsel de ortak depoya yazilir; ileride ayni
            // kayittan okunur, tekrar indirilmez.
            MediaStore.store(context, media.id, prepared.bytes)
            val replyId = messages.firstOrNull { it.clientId == clientId }?.replyTo?.id ?: 0
            val sent = Naber.api.sendImage(conversationId, media.id, "", clientId, prepared.preview, replyId)
            messages = messages.map {
                if (it.clientId == clientId) sent.copy(localImageUri = localCopy) else it
            }
            SoundPlayer.playSent(context)
        } catch (e: Exception) {
            mark(clientId, SendState.FAILED)
            error = e.message
        }
    }

    /**
     * Bir mesaja emoji reaksiyonu birakir/kaldirir/degistirir.
     * Sunucu son durumu dondurur; ekran ona gore guncellenir.
     */
    fun react(target: Message, emoji: String) {
        if (target.id <= 0 || target.deleted) return
        scope.launch {
            runCatching { Naber.api.reactToMessage(target.id, emoji) }
                .onSuccess { reactions ->
                    messages = messages.map { if (it.id == target.id) it.copy(reactions = reactions) else it }
                }
                .onFailure { error = it.message }
        }
    }

    /** Yanitlanan mesajin balonun ustunde gosterilecek kisa ozetini kurar. */
    fun buildReplySummary(target: Message): MessageReplySummary = MessageReplySummary(
        id = target.id,
        senderId = target.senderId,
        senderName = when {
            target.senderId == myId -> "Siz"
            target.senderName.isNotBlank() -> target.senderName
            else -> chat?.peer?.displayName.orEmpty()
        },
        type = target.type,
        body = when (target.type) {
            "image" -> "Fotograf"
            "location" -> "Konum"
            else -> target.body
        },
        deleted = target.deleted
    )

    fun sendText() {
        val body = draft.trim()
        if (body.isEmpty()) return
        val clientId = UUID.randomUUID().toString()
        val reply = replyTarget?.let { buildReplySummary(it) }
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
            sendState = SendState.SENDING,
            replyTo = reply
        )
        draft = ""
        replyTarget = null
        lastTypingSent[0] = 0L
        Naber.events.launchInScope { Naber.api.sendTyping(conversationId, false) }
        scope.launch { deliverText(clientId, body) }
    }

    fun sendLocation() {
        if (locationBusy) return
        locationBusy = true
        scope.launch {
            try {
                val point = Locations.current(context)
                if (point == null) {
                    error = "Konum alinamadi. Konum servisini acip tekrar deneyin."
                    return@launch
                }
                val clientId = UUID.randomUUID().toString()
                val body = formatLocation(point.first, point.second)
                messages = messages + Message(
                    id = 0,
                    conversationId = conversationId,
                    senderId = myId,
                    type = "location",
                    body = body,
                    clientId = clientId,
                    isRead = false,
                    deleted = false,
                    createdAt = System.currentTimeMillis() / 1000,
                    media = null,
                    sendState = SendState.SENDING
                )
                runCatching { Naber.api.sendLocation(conversationId, point.first, point.second, clientId) }
                    .onSuccess { sent ->
                        messages = messages.map { if (it.clientId == clientId) sent else it }
                    }
                    .onFailure { failure ->
                        error = failure.message
                        messages = messages.map {
                            if (it.clientId == clientId) it.copy(sendState = SendState.FAILED) else it
                        }
                    }
            } finally {
                locationBusy = false
            }
        }
    }

    // Izin penceresi kapandiktan sonra gonderme burada tetiklenir;
    // launcher'in geri cagrisi sendLocation tanimlanmadan once kurulur.
    LaunchedEffect(sendLocationRequest.value) {
        if (sendLocationRequest.value) {
            sendLocationRequest.value = false
            sendLocation()
        }
    }

    fun sendPoll(question: String, options: List<String>, multiple: Boolean) {
        val clientId = UUID.randomUUID().toString()
        scope.launch {
            runCatching { Naber.api.sendPoll(conversationId, question, options, multiple, clientId) }
                .onSuccess { sent -> messages = messages + sent }
                .onFailure { error = it.message }
        }
    }

    fun vote(pollId: Int, optionIndex: Int) {
        scope.launch {
            runCatching { Naber.api.votePoll(pollId, optionIndex) }
                .onSuccess { updated ->
                    messages = messages.map {
                        if (it.poll?.id == updated.id) it.copy(poll = updated) else it
                    }
                }
                .onFailure { error = it.message }
        }
    }

    fun sendImage(uri: Uri) {
        val clientId = UUID.randomUUID().toString()
        val reply = replyTarget?.let { buildReplySummary(it) }
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
            sendState = SendState.SENDING,
            replyTo = reply
        )
        replyTarget = null
        scope.launch { deliverImage(clientId, uri) }
    }

    /**
     * Gonderilemeyen mesajlari yeniden dener.
     *
     * Kuyruk cihaza yazildigi icin uygulama kapatilip acilsa bile mesajlar
     * kaybolmaz; baglanti geri geldiginde kendiliginden gonderilirler.
     */
    fun retryPending() {
        val pending = messages.filter { it.id <= 0 && it.sendState == SendState.FAILED }
        if (pending.isEmpty()) return
        scope.launch {
            pending.forEach { message ->
                if (message.type == "image") {
                    val source = message.localImageUri ?: LocalMedia.uriFor(0, message.clientId)
                    if (source != null && LocalFiles.exists(source)) {
                        deliverImage(message.clientId, Uri.parse(source))
                    }
                } else if (message.body.isNotBlank()) {
                    deliverText(message.clientId, message.body)
                }
            }
        }
    }

    // Baglanti geri geldiginde kuyruktakiler kendiliginden gonderilir.
    LaunchedEffect(connected) {
        if (connected) retryPending()
    }

    /**
     * Listenin basina gelindiginde daha eski mesajlar cekilir.
     * Tum gecmis bir kerede indirilmez; kullanici yukari kaydirdikca gelir.
     */
    LaunchedEffect(listState, messages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index > 2 || loadingOlder || !hasOlder || messages.isEmpty()) return@collect
                val oldest = messages.firstOrNull { it.id > 0 }?.id ?: return@collect
                loadingOlder = true
                runCatching { Naber.api.messages(conversationId, before = oldest) }
                    .onSuccess { (older, _, _) ->
                        if (older.isEmpty()) {
                            hasOlder = false
                        } else {
                            messages = (older + messages).distinctBy { it.key }.sortedBy { it.createdAt }
                        }
                    }
                loadingOlder = false
            }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { sendImage(it) }
    }

    // Kamerayla cekilen fotograf: cekim basarili olursa hazirlanan hedef
    // dosyanin adresi dogrudan gonderilir (galeriye dusmez).
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val taken = cameraUri
        cameraUri = null
        if (success && taken != null) sendImage(taken)
    }

    val current = chat

    // Kaybolan mesajlar aciksa suresi dolanlar cihazda da gizlenir: sunucu
    // onlari siliyor ama yerel kopya bir sonraki yenilemeye kadar duruyor.
    val visibleMessages = remember(messages, current?.disappearSeconds) {
        val seconds = current?.disappearSeconds ?: 0
        if (seconds <= 0) {
            messages
        } else {
            val cutoff = System.currentTimeMillis() / 1000 - seconds
            messages.filter { it.createdAt >= cutoff }
        }
    }

    // Grupta "@" yazilirken uye onerisi: son "@" isaretinden sonrasi
    // aranan metin sayilir. Bosluk iceren isimler de yazilabilsin diye
    // arama ilk bosluktan sonra kesilmez, yalnizca uzunlugu sinirlanir.
    val mentionQuery = remember(draft, current?.isGroup) {
        if (current?.isGroup != true) {
            null
        } else {
            val at = draft.lastIndexOf('@')
            when {
                at < 0 -> null
                at > 0 && !draft[at - 1].isWhitespace() -> null
                else -> draft.substring(at + 1).takeIf { !it.contains('\n') && it.length <= 24 }
            }
        }
    }

    val mentionSuggestions: List<User> = remember(mentionQuery, current?.members) {
        val query = mentionQuery
        val members = current?.members.orEmpty().filter { it.id != myId }
        when {
            query == null -> emptyList()
            query.isBlank() -> members.take(6)
            else -> members.filter { it.displayName.contains(query, ignoreCase = true) }.take(6)
        }
    }

    val mentionNames: List<String> = remember(current?.members) {
        val group = current?.takeIf { it.isGroup }
        if (group == null) {
            emptyList()
        } else {
            group.members.map { it.displayName }.filter { it.isNotBlank() } + MENTION_ALL_TOKENS
        }
    }

    val wallpaper = remember(wallpaperId, NaberColors.dark) {
        ChatWallpaper.PRESETS.firstOrNull { it.id == wallpaperId } ?: ChatWallpaper.PRESETS.first()
    }

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
                    .clickable(enabled = current != null) {
                        val c = current ?: return@clickable
                        if (c.isGroup) onGroupInfo(c.id) else c.peer?.let { onOpenProfile(it.id) }
                    }
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
                        text = { Text(if (current?.pinned == true) "Sabitlemeyi kaldir" else "Sohbeti sabitle") },
                        onClick = {
                            menuOpen = false
                            val pin = current?.pinned != true
                            scope.launch {
                                runCatching { Naber.api.setChatPinned(conversationId, pin) }
                                chat = chat?.copy(pinned = pin)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (current?.notifyMuted == true) "Bildirimleri ac" else "Bildirimleri sessize al") },
                        onClick = {
                            menuOpen = false
                            if (current?.notifyMuted == true) {
                                scope.launch {
                                    runCatching { Naber.api.setChatNotifications(conversationId, false) }
                                    chat = chat?.copy(notifyMuted = false, notifyMutedUntil = 0)
                                }
                            } else {
                                muteDialogOpen = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sohbet arka plani") },
                        onClick = {
                            menuOpen = false
                            wallpaperDialogOpen = true
                        }
                    )
                    // Kaybolan mesajlar ayarini grupta yalnizca yonetici
                    // degistirebilir; sunucu da ayni kurali uygular,
                    // buradaki kontrol sadece gorsel.
                    val canSetDisappear = current != null &&
                        (!current.isGroup || current.role == "owner" || current.role == "admin")
                    if (canSetDisappear) {
                        DropdownMenuItem(
                            text = { Text("Kaybolan mesajlar") },
                            trailingIcon = {
                                Text(
                                    disappearLabel(current?.disappearSeconds ?: 0),
                                    fontSize = 12.sp,
                                    color = NaberColors.TextSecondary
                                )
                            },
                            onClick = {
                                menuOpen = false
                                disappearDialogOpen = true
                            }
                        )
                    }
                }
            }
        }

        val disappearSeconds = current?.disappearSeconds ?: 0
        if (disappearSeconds > 0) {
            Text(
                "Kaybolan mesajlar acik: mesajlar ${disappearLabel(disappearSeconds)} sonra siliniyor.",
                fontSize = 12.sp,
                color = NaberColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.SurfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
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

        Box(
            modifier = Modifier
                .weight(1f)
                .background(wallpaper.color)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaberColors.Accent)
                }

                visibleMessages.isEmpty() -> EmptyState(
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
                    if (loadingOlder) {
                        item("eski-yukleniyor") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NaberColors.Accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    items(visibleMessages, key = { it.key }) { message ->
                        MessageRow(
                            message = message,
                            mine = message.senderId == myId,
                            isGroup = current?.isGroup == true,
                            mentionNames = mentionNames,
                            onVote = { pollId, index -> vote(pollId, index) },
                            tick = tickFor(message, chat?.deliveredWatermark ?: 0, chat?.readWatermark ?: 0),
                            onImageClick = { fullScreen = it },
                            onLongPress = { actionTarget = message },
                            onImageError = {
                                // Imzali adres eskimis olabilir; tazeleyip bir kez daha dene.
                                val mediaId = message.media?.id ?: 0
                                if (mediaId > 0 && !retriedImages.contains(mediaId)) {
                                    retriedImages.add(mediaId)
                                    scope.launch {
                                        runCatching { Naber.api.mediaUrl(mediaId) }.onSuccess { fresh ->
                                            messages = messages.map {
                                                if (it.media?.id == mediaId) it.copy(media = fresh) else it
                                            }
                                        }
                                    }
                                }
                            },
                            onReact = { target, emoji -> react(target, emoji) }
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

        // Grupta "@" yazilinca uye onerileri cikar; dokununca isim eklenir.
        if (mentionSuggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.TopBar)
            ) {
                mentionSuggestions.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val at = draft.lastIndexOf('@')
                                if (at >= 0) draft = draft.substring(0, at) + "@${member.displayName} "
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SenderAvatar(
                            name = member.displayName,
                            url = member.avatar,
                            id = member.id,
                            size = 26.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(member.displayName, color = NaberColors.TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        // Yanitlanacak mesaj secildiyse yazma alaninin ustunde on izlemesi gorunur.
        replyTarget?.let { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NaberColors.TopBar)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (target.senderId == myId) "Kendinize yanit" else "${target.senderName.ifBlank { current?.peer?.displayName.orEmpty() }} kisisine yanit",
                        color = NaberColors.Accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (target.type == "image") "Fotograf" else target.body,
                        color = NaberColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Yanitlamayi iptal et",
                    tint = NaberColors.TextSecondary,
                    modifier = Modifier.size(20.dp).clickable { replyTarget = null }
                )
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
                Box {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NaberColors.SurfaceHigh)
                            .clickable { attachMenuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Gorsel ekle", tint = NaberColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Galeriden sec") },
                            leadingIcon = { Icon(Icons.Filled.Image, null) },
                            onClick = {
                                attachMenuOpen = false
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Anket olustur") },
                            leadingIcon = { Icon(Icons.Filled.Poll, null) },
                            onClick = {
                                attachMenuOpen = false
                                pollDialogOpen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Konum gonder") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                            onClick = {
                                attachMenuOpen = false
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) sendLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotograf cek") },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                            onClick = {
                                attachMenuOpen = false
                                val target = LocalFiles.cameraTarget(context)
                                if (target == null) {
                                    error = "Kamera acilamadi."
                                } else {
                                    cameraUri = Uri.fromFile(target.first)
                                    cameraLauncher.launch(target.second)
                                }
                            }
                        )
                    }
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
                // MessageImage ic Box'i matchParentSize() kullaniyor; sadece
                // genislik verilirse yukseklik sifira duser ve resim hic
                // gorunmez (siyah ekran). Tam ekranda hem genislik hem
                // yukseklik verilmeli.
                MessageImage(
                    media = null,
                    localUri = model,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    actionTarget?.let { message ->
        val canDeleteForAll = (message.senderId == myId || chat?.amAdmin == true) && !message.deleted
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { actionTarget = null },
            title = { Text("Mesaj") },
            text = {
                Column {
                    if (!message.deleted && message.id > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QUICK_REACTIONS.forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            react(message, emoji)
                                            actionTarget = null
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                    if (!message.deleted && message.id > 0) {
                        MessageAction("Yanitla", Icons.AutoMirrored.Filled.Reply) {
                            replyTarget = message
                            actionTarget = null
                        }
                    }
                    if (message.senderId == myId && message.type == "text" && !message.deleted && message.id > 0) {
                        MessageAction("Duzenle", Icons.Filled.Edit) {
                            editTarget = message
                            actionTarget = null
                        }
                    }
                    if (!message.deleted && message.id > 0) {
                        MessageAction("Ilet", Icons.AutoMirrored.Filled.Send) {
                            forwardTarget = message
                            actionTarget = null
                        }
                    }
                    if (message.body.isNotBlank() && !message.deleted) {
                        MessageAction("Kopyala", Icons.Filled.ContentCopy) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Naber", message.body))
                            actionTarget = null
                            error = "Mesaj kopyalandi."
                        }
                    }
                    MessageAction("Bilgi", Icons.Filled.Info) {
                        val target = message
                        actionTarget = null
                        scope.launch {
                            runCatching { Naber.api.messageInfo(target.id) }
                                .onSuccess { infoTarget = it }
                                .onFailure { error = it.message }
                        }
                    }
                    MessageAction("Kendimden sil", Icons.Filled.DeleteOutline) {
                        val target = message
                        actionTarget = null
                        scope.launch {
                            runCatching { Naber.api.deleteMessage(target.id, "me") }
                                .onSuccess { messages = messages.filterNot { it.id == target.id } }
                                .onFailure { error = it.message }
                        }
                    }
                    if (canDeleteForAll) {
                        MessageAction("Herkesten sil", Icons.Filled.Delete, NaberColors.Danger) {
                            val target = message
                            actionTarget = null
                            scope.launch {
                                runCatching { Naber.api.deleteMessage(target.id, "all") }
                                    .onSuccess {
                                        messages = messages.map {
                                            if (it.id == target.id) it.copy(deleted = true, body = "", media = null) else it
                                        }
                                    }
                                    .onFailure { error = it.message }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Kapat", color = NaberColors.TextSecondary) }
            }
        )
    }

    infoTarget?.let { info ->
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { infoTarget = null },
            title = { Text("Mesaj bilgisi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Gonderen", info.senderName)
                    InfoRow("Gonderildi", "${formatChatTime(info.createdAt)} ${formatClock(info.createdAt)}")
                    InfoRow(
                        "Iletildi",
                        if (info.deliveredAt > 0) "${formatChatTime(info.deliveredAt)} ${formatClock(info.deliveredAt)}" else "Henuz ulasmadi"
                    )
                    InfoRow(
                        "Okundu",
                        if (info.readAt > 0) "${formatChatTime(info.readAt)} ${formatClock(info.readAt)}" else "Henuz okunmadi"
                    )
                    if (info.mediaSize > 0) {
                        InfoRow("Gorsel", "${info.mediaWidth}x${info.mediaHeight} - ${formatBytes(info.mediaSize)}")
                    }
                    if (info.recipients.size > 1) {
                        Text(
                            "Alicilar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NaberColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        info.recipients.forEach { recipient ->
                            InfoRow(
                                recipient.name,
                                when {
                                    recipient.read -> "okudu"
                                    recipient.delivered -> "ulasti"
                                    else -> "bekliyor"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text("Kapat", color = NaberColors.Accent) }
            }
        )
    }

    editTarget?.let { target ->
        TextFieldDialog(
            title = "Mesaji duzenle",
            initial = target.body,
            onDismiss = { editTarget = null }
        ) { newBody ->
            editTarget = null
            val body = newBody.trim()
            if (body.isEmpty() || body == target.body) return@TextFieldDialog
            scope.launch {
                runCatching { Naber.api.editMessage(target.id, body) }
                    .onSuccess { updated -> messages = messages.map { if (it.id == target.id) updated else it } }
                    .onFailure { error = it.message }
            }
        }
    }

    if (muteDialogOpen) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { muteDialogOpen = false },
            title = { Text("Bildirimleri sessize al") },
            text = {
                Column {
                    listOf(
                        "8 saat" to 8 * 3600,
                        "1 hafta" to 7 * 24 * 3600,
                        "Surekli" to 0
                    ).forEach { (label, duration) ->
                        MessageAction(label, Icons.Filled.NotificationsOff) {
                            muteDialogOpen = false
                            scope.launch {
                                runCatching { Naber.api.setChatNotifications(conversationId, true, duration) }
                                val until = if (duration > 0) System.currentTimeMillis() / 1000 + duration else 0
                                chat = chat?.copy(notifyMuted = true, notifyMutedUntil = until)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { muteDialogOpen = false }) { Text("Vazgec", color = NaberColors.TextSecondary) }
            }
        )
    }

    if (pollDialogOpen) {
        PollDialog(
            onDismiss = { pollDialogOpen = false },
            onCreate = { question, options, multiple ->
                pollDialogOpen = false
                sendPoll(question, options, multiple)
            }
        )
    }

    if (wallpaperDialogOpen) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { wallpaperDialogOpen = false },
            title = { Text("Sohbet arka plani") },
            text = {
                Column {
                    Text(
                        "Secim yalnizca bu sohbet ve bu cihaz icindir; karsi taraf gormez.",
                        fontSize = 13.sp,
                        color = NaberColors.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    ChatWallpaper.PRESETS.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ChatWallpaper.set(context, conversationId, preset.id)
                                    wallpaperId = preset.id
                                    wallpaperDialogOpen = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(preset.label, color = NaberColors.TextPrimary, fontSize = 14.5.sp)
                            if (preset.id == wallpaperId) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = NaberColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { wallpaperDialogOpen = false }) { Text("Kapat", color = NaberColors.TextSecondary) }
            }
        )
    }

    if (disappearDialogOpen) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { disappearDialogOpen = false },
            title = { Text("Kaybolan mesajlar") },
            text = {
                Column {
                    Text(
                        "Secilen sureden eski mesajlar hem sunucudan hem cihazlardan " +
                            "silinir. Ayar sohbetin iki tarafi icin de gecerlidir.",
                        fontSize = 13.sp,
                        color = NaberColors.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    DISAPPEAR_OPTIONS.forEach { seconds ->
                        val selected = (chat?.disappearSeconds ?: 0) == seconds
                        MessageAction(
                            label = disappearLabel(seconds),
                            icon = if (selected) Icons.Filled.Check else Icons.Filled.Timer
                        ) {
                            disappearDialogOpen = false
                            scope.launch {
                                runCatching { Naber.api.setDisappearing(conversationId, seconds) }
                                    .onSuccess { updated -> chat = updated ?: chat?.copy(disappearSeconds = seconds) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { disappearDialogOpen = false }) { Text("Vazgec", color = NaberColors.TextSecondary) }
            }
        )
    }

    forwardTarget?.let { target ->
        ForwardDialog(
            onDismiss = { forwardTarget = null },
            onPicked = { targetConversationId ->
                scope.launch {
                    runCatching { Naber.api.forwardMessage(target.id, targetConversationId) }
                        .onSuccess { forwardTarget = null; error = "Mesaj iletildi." }
                        .onFailure { error = it.message }
                }
            }
        )
    }
}

/** Iletilecek sohbeti secmek icin kucuk bir liste. */
@Composable
private fun ForwardDialog(onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { Naber.api.chats().first }.onSuccess { chats = it }
        loading = false
    }

    AlertDialog(
        containerColor = NaberColors.Surface,
        onDismissRequest = onDismiss,
        title = { Text("Ilet") },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NaberColors.Accent)
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    LazyColumn {
                        items(chats, key = { it.id }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPicked(c.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatAvatar(c, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(c.title, color = NaberColors.TextPrimary, fontSize = 14.5.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Vazgec", color = NaberColors.TextSecondary) }
        }
    )
}

/** Mesaj durumundan tik gorunumunu belirler. */
private fun tickFor(message: Message, deliveredWatermark: Int, readWatermark: Int): TickState = when {
    message.sendState == SendState.SENDING -> TickState.SENDING
    message.sendState == SendState.FAILED -> TickState.FAILED
    message.id <= 0 -> TickState.SENT
    readWatermark >= message.id -> TickState.READ
    deliveredWatermark >= message.id -> TickState.DELIVERED
    else -> TickState.SENT
}

@Composable
private fun MessageAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = NaberColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontSize = 15.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = NaberColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = NaberColors.TextPrimary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: Message,
    mine: Boolean,
    isGroup: Boolean,
    /** Vurgulanacak "@isim"ler; grup disinda bos gelir. */
    mentionNames: List<String>,
    onVote: (Int, Int) -> Unit,
    tick: TickState,
    onImageClick: (Any) -> Unit,
    onLongPress: () -> Unit,
    onImageError: () -> Unit,
    onReact: (Message, String) -> Unit
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

            message.replyTo?.let { reply ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (mine) Color.White.copy(alpha = 0.12f) else NaberColors.SurfaceHigh
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        reply.senderName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (mine) Color.White.copy(alpha = 0.9f) else NaberColors.Accent
                    )
                    Text(
                        when {
                            reply.deleted -> "Bu mesaj silindi"
                            reply.type == "image" -> "Fotograf"
                            reply.type == "location" -> "Konum"
                            else -> reply.body
                        },
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (mine) Color.White.copy(alpha = 0.75f) else NaberColors.TextSecondary
                    )
                }
            }

            if (message.deleted) {
                Text(
                    "Bu mesaj silindi",
                    fontSize = 14.sp,
                    color = if (mine) Color.White.copy(alpha = 0.7f) else NaberColors.TextSecondary
                )
            } else {
                if (message.type == "image") {
                    // Olculer bilindiginde balon en bastan dogru boyutta cizilir,
                    // gorsel inerken bos dev bir kutu olusmaz.
                    val ratio = message.media?.let {
                        if (it.width > 0 && it.height > 0) {
                            (it.width.toFloat() / it.height.toFloat()).coerceIn(0.5f, 1.8f)
                        } else {
                            null
                        }
                    } ?: 0.8f

                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NaberColors.SurfaceHigh)
                            .clickable { message.displayImage?.let(onImageClick) }
                    ) {
                        MessageImage(
                            media = message.media,
                            localUri = message.localImageUri ?: com.naber.app.data.LocalMedia.uriFor(message.media?.id ?: 0, message.clientId),
                            modifier = Modifier.fillMaxSize(),
                            preview = message.preview,
                            onError = onImageError
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

                message.poll?.let { poll ->
                    PollBubble(poll = poll, mine = mine, onVote = { index -> onVote(poll.id, index) })
                }

                val point = if (message.type == "location") parseLocation(message.body) else null
                if (point != null) {
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mine) Color.White.copy(alpha = 0.12f) else NaberColors.SurfaceHigh)
                            .clickable {
                                // Harita uygulamasi yoksa acilmaz; uygulama
                                // cokmemesi icin hata yutulur.
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, mapsUri(point.first, point.second))
                                    )
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = if (mine) Color.White else NaberColors.Accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Konum",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (mine) Color.White else NaberColors.TextPrimary
                            )
                            Text(
                                "Haritada ac",
                                fontSize = 12.sp,
                                color = if (mine) Color.White.copy(alpha = 0.8f) else NaberColors.TextSecondary
                            )
                        }
                    }
                }

                if (message.body.isNotBlank() && message.type != "location" && message.type != "poll") {
                    Text(
                        mentionText(
                            message.body,
                            mentionNames,
                            if (mine) Color.White else NaberColors.Accent
                        ),
                        fontSize = 15.sp,
                        color = if (mine) Color.White else NaberColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = if (message.type == "image") 4.dp else 0.dp)
                    )
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.forEach { reaction ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (reaction.reacted) NaberColors.Accent.copy(alpha = 0.25f)
                                    else (if (mine) Color.White.copy(alpha = 0.15f) else NaberColors.SurfaceHigh)
                                )
                                .clickable { onReact(message, reaction.emoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reaction.emoji, fontSize = 12.sp)
                            if (reaction.count > 1) {
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    reaction.count.toString(),
                                    fontSize = 10.5.sp,
                                    color = if (mine) Color.White.copy(alpha = 0.85f) else NaberColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 3.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.edited && !message.deleted) {
                    Text(
                        "duzenlendi",
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = if (mine) Color.White.copy(alpha = 0.6f) else NaberColors.TextSecondary
                    )
                }
                Text(
                    formatClock(message.createdAt),
                    fontSize = 10.5.sp,
                    color = if (mine) Color.White.copy(alpha = 0.75f) else NaberColors.TextSecondary
                )
                if (mine) {
                    when (tick) {
                        TickState.SENDING -> CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.dp,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        TickState.FAILED -> Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Gonderilemedi",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )

                        // Tek tik: sunucuya ulasti, karsi tarafin cihazina inmedi.
                        TickState.SENT -> Icon(
                            Icons.Filled.Done,
                            contentDescription = "Gonderildi",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )

                        // Gri cift tik: cihazina ulasti, henuz okunmadi.
                        TickState.DELIVERED -> Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = "Iletildi",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )

                        // Mavi cift tik: okundu.
                        TickState.READ -> Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = "Okundu",
                            tint = Color(0xFF6FD3FF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Mesaj balonu icindeki anket: secenekler, oy cubuklari ve sayilar. */
@Composable
private fun PollBubble(poll: Poll, mine: Boolean, onVote: (Int) -> Unit) {
    val textColor = if (mine) Color.White else NaberColors.TextPrimary
    val secondary = if (mine) Color.White.copy(alpha = 0.75f) else NaberColors.TextSecondary
    val barColor = if (mine) Color.White.copy(alpha = 0.35f) else NaberColors.Accent.copy(alpha = 0.35f)

    Column(modifier = Modifier.width(250.dp)) {
        Text(poll.question, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        Spacer(Modifier.height(2.dp))
        Text(
            if (poll.multiple) "Birden fazla secilebilir" else "Tek secim",
            fontSize = 11.sp,
            color = secondary
        )
        Spacer(Modifier.height(8.dp))

        poll.options.forEach { option ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (mine) Color.White.copy(alpha = 0.12f) else NaberColors.SurfaceHigh)
                    .clickable { onVote(option.index) }
            ) {
                // Oy orani balonun icinde bir cubuk olarak cizilir; ayri bir
                // ilerleme bileseni yerine arka plan genisligi kullanilir.
                // Dis kutu matchParentSize ile satirin olcusunu alir, ic kutu
                // o olcunun yuzdesi kadar genisler; ikisi tek modifier'da
                // birlestirilemez cunku matchParentSize genisligi de sabitler.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(option.percent / 100f)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (option.mine) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Oyunuz",
                            tint = textColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        option.text,
                        fontSize = 13.5.sp,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${option.votes}", fontSize = 12.sp, color = secondary)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            if (poll.total == 0) "Henuz oy yok" else "Toplam ${poll.total} oy",
            fontSize = 11.sp,
            color = secondary
        )
    }
}

/** Yeni anket penceresi: soru, secenekler ve tek/coklu secim. */
@Composable
private fun PollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        containerColor = NaberColors.Surface,
        onDismissRequest = onDismiss,
        title = { Text("Anket olustur") },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it; error = null },
                    label = { Text("Soru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[index] = it; error = null },
                        label = { Text("${index + 1}. secenek") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    )
                }

                if (options.size < POLL_MAX_OPTIONS) {
                    Text(
                        "+ Secenek ekle",
                        color = NaberColors.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { options.add("") }
                            .padding(vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { multiple = !multiple }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (multiple) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (multiple) NaberColors.Accent else NaberColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Birden fazla secenek isaretlenebilsin", fontSize = 13.sp, color = NaberColors.TextPrimary)
                }

                val shown = error
                if (shown != null) {
                    Text(shown, fontSize = 12.sp, color = NaberColors.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Ayni secenek iki kez yazilirsa oylar bolunur; sunucu da
                // ayni kurali uyguluyor, burada kullaniciya sebebi soyleniyor.
                val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                when {
                    question.isBlank() -> error = "Soru bos olamaz."
                    clean.size < POLL_MIN_OPTIONS -> error = "En az $POLL_MIN_OPTIONS farkli secenek gerekiyor."
                    else -> onCreate(question.trim(), clean, multiple)
                }
            }) { Text("Olustur", color = NaberColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgec", color = NaberColors.TextSecondary) }
        }
    )
}
