package com.naber.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.Chat
import com.naber.app.data.GROUP_PERMISSIONS
import com.naber.app.data.GroupPrank
import com.naber.app.data.GroupStats
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.ChatAvatar
import com.naber.app.ui.formatChatTime
import com.naber.app.ui.prepareImage
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.RoleTag
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Grup bilgisi: uyeler, yoneticiler ve yonetici islemleri.
 */
@Composable
fun GroupInfoScreen(conversationId: Int, onBack: () -> Unit, onLeft: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var chat by remember { mutableStateOf<Chat?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf(false) }
    var editAbout by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var addMembers by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<User>>(emptyList()) }
    var codeMessage by remember { mutableStateOf<String?>(null) }
    var regenerating by remember { mutableStateOf(false) }
    var permTarget by remember { mutableStateOf<User?>(null) }
    var transferTarget by remember { mutableStateOf<User?>(null) }
    // Saka savunmasi: once sahte zafer (sahibin etiketi kaybolur),
    // sonra komik mesaj ve gruptan atilma.
    var prank by remember { mutableStateOf<GroupPrank?>(null) }
    var prankStage by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<User>>(emptyList()) }
    var stats by remember { mutableStateOf<GroupStats?>(null) }

    LaunchedEffect(prankStage) {
        if (prankStage == 1) {
            delay(1800)
            prankStage = 2
        }
    }

    val myId = Naber.session.user?.id ?: 0

    suspend fun reload() {
        runCatching { chat = Naber.api.chatInfo(conversationId) }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(conversationId) { reload() }

    // Bekleyen katilma istekleri ve kucuk grup ozeti; ikisi de sayfa
    // acilinca bir kez cekilir, istek yanitlaninca tazelenir.
    LaunchedEffect(conversationId, chat?.pendingRequests) {
        if (chat?.amAdmin == true && (chat?.pendingRequests ?: 0) > 0) {
            runCatching { requests = Naber.api.joinRequests(conversationId) }
        } else {
            requests = emptyList()
        }
    }

    LaunchedEffect(conversationId) {
        runCatching { stats = Naber.api.groupStats(conversationId) }
    }

    // Baska bir yonetici uye eklerse/cikarirsa ekran kendiliginden guncellenir.
    val revisions by Naber.events.revisions.collectAsState()
    LaunchedEffect(revisions[conversationId]) {
        if (!loading) reload()
    }

    fun act(block: suspend () -> Chat) {
        scope.launch {
            runCatching { chat = block() }.onFailure { error = it.message }
        }
    }

    // Grup fotografi: secilen gorsel kucultulup yuklenir, sonra gruba baglanir.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        photoBusy = true
        scope.launch {
            try {
                val prepared = prepareImage(context, uri, maxSize = 640)
                if (prepared != null) {
                    val media = Naber.api.uploadMedia(prepared.bytes, prepared.mime, prepared.width, prepared.height) {}
                    chat = Naber.api.updateGroup(conversationId, null, null, media.id)
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                photoBusy = false
            }
        }
    }

    fun removeMember(member: User) {
        scope.launch {
            runCatching { Naber.api.removeGroupMember(conversationId, member.id) }
                .onSuccess { result ->
                    result.chat?.let { chat = it }
                    result.prank?.let {
                        prank = it
                        prankStage = 1
                    }
                }
                .onFailure { error = it.message }
        }
    }

    val current = chat
    // Grup duzenleme: yonetici ya da bu yetkisi acilmis uye.
    val canEditGroup = current?.perms?.contains("edit_group") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = NaberColors.TextPrimary,
                modifier = Modifier.size(24.dp).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(14.dp))
            Text("Grup bilgisi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary, modifier = Modifier.weight(1f))
            if (current?.perms?.contains("add_member") == true) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Uye ekle",
                    tint = NaberColors.Accent,
                    modifier = Modifier.size(22.dp).clickable {
                        addMembers = true
                        scope.launch {
                            runCatching {
                                val existing = current.members.map { it.id }.toSet()
                                candidates = Naber.api.users().filter { it.id !in existing }
                            }
                        }
                    }
                )
            }
        }

        if (loading || current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NaberColors.Accent)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        ChatAvatar(current, size = 92.dp)
                        if (canEditGroup) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NaberColors.Accent)
                                    .clickable(enabled = !photoBusy) {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoBusy) {
                                    CircularProgressIndicator(
                                        color = androidx.compose.ui.graphics.Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.PhotoCamera,
                                        contentDescription = "Grup fotografini degistir",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NaberColors.TextPrimary
                        )
                        if (canEditGroup) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Adi degistir",
                                tint = NaberColors.TextSecondary,
                                modifier = Modifier.size(17.dp).clickable { editTitle = true }
                            )
                        }
                    }
                    Text("${current.memberCount} uye", fontSize = 13.sp, color = NaberColors.TextSecondary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        current.about.ifBlank { if (canEditGroup) "Aciklama ekleyin" else "" },
                        fontSize = 13.5.sp,
                        color = NaberColors.TextSecondary,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clickable(enabled = canEditGroup) { editAbout = true }
                    )
                }
            }

            error?.let {
                item {
                    Text(it, color = NaberColors.Danger, fontSize = 12.5.sp, modifier = Modifier.padding(16.dp))
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Davet kodu",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NaberColors.TextSecondary
                    )
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current.inviteCode.ifBlank { "......" },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = NaberColors.Accent,
                            modifier = Modifier
                                .clickable(enabled = current.inviteCode.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Naber", current.inviteCode))
                                    codeMessage = "Kod kopyalandi."
                                }
                        )
                        if (current.inviteCode.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Kodu paylas",
                                tint = NaberColors.TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        // Android paylasim sayfasi: WhatsApp,
                                        // Telegram, SMS... hepsine tek dokunusla.
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Naber'de \"${current.title}\" grubuna katil. " +
                                                    "Kod ile grup bul ekranina su kodu gir: ${current.inviteCode}"
                                            )
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(share, "Davet kodunu paylas"))
                                        }
                                    }
                            )
                        }
                        if (current.amAdmin) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Kodu yenile",
                                tint = NaberColors.TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(enabled = !regenerating) {
                                        regenerating = true
                                        scope.launch {
                                            runCatching { Naber.api.regenerateInviteCode(conversationId) }
                                                .onSuccess { newCode ->
                                                    chat = chat?.copy(inviteCode = newCode)
                                                    codeMessage = "Yeni kod uretildi."
                                                }
                                                .onFailure { error = it.message }
                                            regenerating = false
                                        }
                                    }
                            )
                        }
                    }
                    Text(
                        "Bu kodu bilen, uygulamaya giris yapmis herkes \"Kod ile grup bul\" ile katilabilir.",
                        fontSize = 11.5.sp,
                        color = NaberColors.TextSecondary
                    )
                    codeMessage?.let {
                        Text(it, fontSize = 11.5.sp, color = NaberColors.Accent, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (current.amAdmin && requests.isNotEmpty()) {
                item {
                    Text(
                        "Bekleyen istekler (${requests.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NaberColors.Warning,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp)
                    )
                }
                items(requests, key = { "istek-${it.id}" }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(person, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(person.displayName, fontSize = 14.5.sp, color = NaberColors.TextPrimary)
                            Text(
                                person.naberEmail.ifBlank { "@${person.username}" },
                                fontSize = 11.5.sp,
                                color = NaberColors.TextSecondary
                            )
                        }
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Onayla",
                            tint = NaberColors.Accent,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    scope.launch {
                                        runCatching { Naber.api.resolveJoinRequest(conversationId, person.id, true) }
                                            .onSuccess { (updated, list) ->
                                                updated?.let { chat = it }
                                                requests = list
                                            }
                                            .onFailure { error = it.message }
                                    }
                                }
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Reddet",
                            tint = NaberColors.Danger,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    scope.launch {
                                        runCatching { Naber.api.resolveJoinRequest(conversationId, person.id, false) }
                                            .onSuccess { (updated, list) ->
                                                updated?.let { chat = it }
                                                requests = list
                                            }
                                            .onFailure { error = it.message }
                                    }
                                }
                        )
                    }
                }
            }

            stats?.let { summary ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            "Grup ozeti",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NaberColors.TextSecondary
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Toplam ${summary.totalMessages} mesaj, bugun ${summary.todayMessages}.",
                            fontSize = 13.sp,
                            color = NaberColors.TextPrimary
                        )
                        if (summary.topMember.isNotBlank()) {
                            Text(
                                "En cok yazan: ${summary.topMember} (${summary.topMessages})",
                                fontSize = 13.sp,
                                color = NaberColors.TextPrimary
                            )
                        }
                    }
                }
            }

            if (current.amAdmin) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Uyelik onayi", fontSize = 14.sp, color = NaberColors.TextPrimary)
                            Text(
                                "Acikken davet koduyla gelenler once onayini bekler.",
                                fontSize = 11.5.sp,
                                color = NaberColors.TextSecondary
                            )
                        }
                        Switch(
                            checked = current.requireApproval,
                            onCheckedChange = { value ->
                                act { Naber.api.updateGroup(conversationId, null, null, null, null, value) }
                            }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "\"@herkes\" yalnizca yoneticilerde",
                                fontSize = 14.sp,
                                color = NaberColors.TextPrimary
                            )
                            Text(
                                "Acikken duz uyeler @herkes yazarak herkese bildirim gonderemez.",
                                fontSize = 11.5.sp,
                                color = NaberColors.TextSecondary
                            )
                        }
                        Switch(
                            checked = current.mentionAllAdmins,
                            onCheckedChange = { value ->
                                act { Naber.api.updateGroup(conversationId, null, null, null, value) }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    "Uyeler",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NaberColors.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
            }

            items(current.members, key = { it.id }) { member ->
                MemberRow(
                    member = member,
                    chat = current,
                    myId = myId,
                    // Sahte zafer aninda sahibin etiketi gizlenir.
                    hideRoleTag = prankStage == 1 && prank?.victimId == member.id,
                    onMute = { muted -> act { Naber.api.setMemberMuted(conversationId, member.id, muted) } },
                    onRole = { role -> act { Naber.api.setGroupRole(conversationId, member.id, role) } },
                    onPerms = { permTarget = member },
                    onTransfer = { transferTarget = member },
                    onRemove = { removeMember(member) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmLeave = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = NaberColors.Danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Gruptan ayril", color = NaberColors.Danger, fontSize = 15.sp)
                }
            }

            if (current.amOwner) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmDelete = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = NaberColors.Danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Grubu sil", color = NaberColors.Danger, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (editTitle && current != null) {
        TextFieldDialog(
            title = "Grup adi",
            initial = current.title,
            onDismiss = { editTitle = false },
            onConfirm = { value ->
                editTitle = false
                act { Naber.api.updateGroup(conversationId, value, null, null) }
            }
        )
    }

    if (editAbout && current != null) {
        TextFieldDialog(
            title = "Grup aciklamasi",
            initial = current.about,
            onDismiss = { editAbout = false },
            onConfirm = { value ->
                editAbout = false
                act { Naber.api.updateGroup(conversationId, null, value, null) }
            }
        )
    }

    // Ayrintili yetki anahtarlari: yonetici yapmadan tek tek yetki verme.
    permTarget?.let { member ->
        var selected by remember(member.id) { mutableStateOf(member.perms.toSet()) }
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { permTarget = null },
            title = { Text("${member.displayName} yetkileri") },
            text = {
                Column {
                    Text(
                        "Yonetici yapmadan tek tek yetki verebilirsin.",
                        fontSize = 12.sp,
                        color = NaberColors.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    GROUP_PERMISSIONS.forEach { permission ->
                        val on = selected.contains(permission.key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (on) selected - permission.key else selected + permission.key
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(permission.title, fontSize = 14.sp, color = NaberColors.TextPrimary)
                                Text(permission.description, fontSize = 11.5.sp, color = NaberColors.TextSecondary)
                            }
                            Switch(
                                checked = on,
                                onCheckedChange = {
                                    selected = if (on) selected - permission.key else selected + permission.key
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = member
                    val perms = selected.toList()
                    permTarget = null
                    act { Naber.api.setGroupRole(conversationId, target.id, "member", perms) }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { permTarget = null }) { Text("Vazgec") } }
        )
    }

    transferTarget?.let { member ->
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { transferTarget = null },
            title = { Text("Sahiplik devredilsin mi?") },
            text = { Text("${member.displayName} grubun yeni sahibi olacak, sen yonetici olarak kalacaksin. Bu islem geri alinamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = member
                    transferTarget = null
                    act { Naber.api.setGroupRole(conversationId, target.id, "owner") }
                }) { Text("Devret", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { transferTarget = null }) { Text("Vazgec") } }
        )
    }

    // Saka savunmasinin son perdesi: komik mesaj ve gruptan atilma.
    if (prankStage == 2) {
        val message = prank?.message.orEmpty()
        val seconds = prank?.restoreSeconds ?: 10
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = {},
            title = { Text("Darbe girisimi basarisiz") },
            text = {
                Column {
                    Text(message, fontSize = 14.sp, color = NaberColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$seconds saniye sonra gruba otomatik olarak geri alinacaksin.",
                        fontSize = 12.sp,
                        color = NaberColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prankStage = 0
                    prank = null
                    onLeft()
                }) { Text("Hak ettim") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { confirmDelete = false },
            title = { Text("Grup silinsin mi?") },
            text = { Text("Grup, butun mesajlari ve uyelikleriyle birlikte kalici olarak silinir. Bu islem geri alinamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { Naber.api.deleteGroup(conversationId) }
                            .onSuccess { onLeft() }
                            .onFailure { error = it.message }
                    }
                }) { Text("Sil", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgec") } }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { confirmLeave = false },
            title = { Text("Gruptan ayrilinsin mi?") },
            text = { Text("Bu gruptaki mesajlari artik goremezsiniz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch {
                        runCatching { Naber.api.leaveGroup(conversationId) }
                            .onSuccess { onLeft() }
                            .onFailure { error = it.message }
                    }
                }) { Text("Ayril", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Vazgec") } }
        )
    }

    if (addMembers) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { addMembers = false },
            title = { Text("Uye ekle") },
            text = {
                if (candidates.isEmpty()) {
                    Text("Eklenebilecek baska kisi yok.")
                } else {
                    LazyColumn {
                        items(candidates, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        addMembers = false
                                        act { Naber.api.addGroupMembers(conversationId, listOf(user.id)) }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(user, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(user.displayName, color = NaberColors.TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addMembers = false }) { Text("Kapat") } }
        )
    }
}

@Composable
private fun MemberRow(
    member: User,
    chat: Chat,
    myId: Int,
    hideRoleTag: Boolean,
    onMute: (Boolean) -> Unit,
    onRole: (String) -> Unit,
    onPerms: () -> Unit,
    onTransfer: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    // Yetkili uye (yonetici olmadan) yalnizca duz uyelere mudahale edebilir.
    val canRemoveAsDelegate = member.role == "member" && chat.perms.contains("remove_member")
    val canModerate = when {
        member.id == myId -> false
        chat.amOwner -> member.role != "owner"
        chat.amAdmin -> member.role == "member"
        else -> canRemoveAsDelegate
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(member, size = 44.dp)
            if (member.online) OnlineDot(modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (member.id == myId) "${member.displayName} (siz)" else member.displayName,
                    fontSize = 15.sp,
                    color = NaberColors.TextPrimary
                )
                if (member.roleLabel.isNotBlank() && !hideRoleTag) RoleTag(member.roleLabel)
                if (member.roleLabel.isBlank() && member.perms.isNotEmpty()) RoleTag("Yetkili uye")
                if (member.chatMuted) {
                    Icon(
                        Icons.Filled.MicOff,
                        contentDescription = "Susturuldu",
                        tint = NaberColors.Warning,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                if (member.joinedAt > 0) {
                    "${member.naberEmail.ifBlank { "@${member.username}" }} - katildi: ${formatChatTime(member.joinedAt)}"
                } else {
                    member.naberEmail.ifBlank { "@${member.username}" }
                },
                fontSize = 12.sp,
                color = NaberColors.TextSecondary
            )
        }

        if (canModerate) {
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Islemler",
                    tint = NaberColors.TextSecondary,
                    modifier = Modifier.size(20.dp).clickable { menu = true }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (chat.amAdmin) {
                        DropdownMenuItem(
                            text = { Text(if (member.chatMuted) "Susturmayi kaldir" else "Sohbette sustur") },
                            onClick = { menu = false; onMute(!member.chatMuted) }
                        )
                    }
                    if (chat.amOwner) {
                        DropdownMenuItem(
                            text = { Text(if (member.role == "admin") "Yoneticiligi al" else "Yonetici yap") },
                            onClick = { menu = false; onRole(if (member.role == "admin") "member" else "admin") }
                        )
                        if (member.role == "member") {
                            DropdownMenuItem(
                                text = { Text("Yetkileri duzenle") },
                                onClick = { menu = false; onPerms() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Sahipligi devret") },
                            onClick = { menu = false; onTransfer() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Gruptan cikar", color = NaberColors.Danger) },
                        onClick = { menu = false; onRemove() }
                    )
                }
            }
        }
    }
}
