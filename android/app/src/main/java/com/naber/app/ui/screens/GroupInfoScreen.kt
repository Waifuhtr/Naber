package com.naber.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.Chat
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.ChatAvatar
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.RoleTag
import com.naber.app.ui.theme.NaberColors
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

    val myId = Naber.session.user?.id ?: 0

    suspend fun reload() {
        runCatching { chat = Naber.api.chatInfo(conversationId) }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(conversationId) { reload() }

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

    val current = chat

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
            if (current?.amAdmin == true) {
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
                    ChatAvatar(current, size = 92.dp)
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NaberColors.TextPrimary
                        )
                        if (current.amAdmin) {
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
                        current.about.ifBlank { if (current.amAdmin) "Aciklama ekleyin" else "" },
                        fontSize = 13.5.sp,
                        color = NaberColors.TextSecondary,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clickable(enabled = current.amAdmin) { editAbout = true }
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
                    onMute = { muted -> act { Naber.api.setMemberMuted(conversationId, member.id, muted) } },
                    onRole = { role -> act { Naber.api.setGroupRole(conversationId, member.id, role) } },
                    onRemove = { act { Naber.api.removeGroupMember(conversationId, member.id) } }
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
    onMute: (Boolean) -> Unit,
    onRole: (String) -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val canModerate = when {
        member.id == myId -> false
        chat.amOwner -> member.role != "owner"
        chat.amAdmin -> member.role == "member"
        else -> false
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
                if (member.roleLabel.isNotBlank()) RoleTag(member.roleLabel)
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
                member.naberEmail.ifBlank { "@${member.username}" },
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
                    DropdownMenuItem(
                        text = { Text(if (member.chatMuted) "Susturmayi kaldir" else "Sohbette sustur") },
                        onClick = { menu = false; onMute(!member.chatMuted) }
                    )
                    if (chat.amOwner) {
                        DropdownMenuItem(
                            text = { Text(if (member.role == "admin") "Yoneticiligi al" else "Yonetici yap") },
                            onClick = { menu = false; onRole(if (member.role == "admin") "member" else "admin") }
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
