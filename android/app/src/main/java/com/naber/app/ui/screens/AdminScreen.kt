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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.AdminChat
import com.naber.app.data.AdminStats
import com.naber.app.data.ServerSettings
import com.naber.app.data.StorageTestResult
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.NaberCard
import com.naber.app.ui.RoleTag
import com.naber.app.ui.ThinDivider
import com.naber.app.ui.formatBytes
import com.naber.app.ui.formatDuration
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

/**
 * Yonetim paneli.
 * Bu ekrana yalnizca WordPress'ten is_admin=true donen kullanicilar ulasir;
 * ayrica her admin ucunda sunucu tarafinda da rol kontrolu yapilir.
 */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var chats by remember { mutableStateOf<List<AdminChat>>(emptyList()) }
    var settings by remember { mutableStateOf<ServerSettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var storageTest by remember { mutableStateOf<StorageTestResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    var banTarget by remember { mutableStateOf<User?>(null) }
    var deleteTarget by remember { mutableStateOf<User?>(null) }
    var passwordTarget by remember { mutableStateOf<User?>(null) }
    var deleteChatTarget by remember { mutableStateOf<AdminChat?>(null) }

    suspend fun reload() {
        runCatching {
            stats = Naber.api.adminStats()
            users = Naber.api.adminUsers(search)
            chats = Naber.api.adminChats()
            settings = Naber.api.adminSettings()
            error = null
        }.onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun updateUser(user: User, block: suspend () -> User?) {
        scope.launch {
            runCatching { block() }
                .onSuccess { updated ->
                    if (updated != null) users = users.map { if (it.id == updated.id) updated else it }
                    info = "${user.displayName} guncellendi."
                }
                .onFailure { error = it.message }
        }
    }

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
            Text("Yonetim", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.naber.app.ui.NaberChip("Ozet", tab == 0) { tab = 0 }
            com.naber.app.ui.NaberChip("Kullanicilar", tab == 1) { tab = 1 }
            com.naber.app.ui.NaberChip("Sohbetler", tab == 2) { tab = 2 }
            com.naber.app.ui.NaberChip("Sunucu", tab == 3) { tab = 3 }
        }

        info?.let {
            Text(it, color = NaberColors.Accent, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        error?.let {
            Text(it, color = NaberColors.Danger, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NaberColors.Accent)
            }
            return@Column
        }

        when (tab) {
            0 -> SummaryTab(
                stats = stats,
                storageTest = storageTest,
                testing = testing,
                onTest = {
                    testing = true
                    storageTest = null
                    scope.launch {
                        runCatching { storageTest = Naber.api.adminStorageTest(true) }
                            .onFailure { error = it.message }
                        testing = false
                    }
                }
            )

            1 -> Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                        scope.launch { runCatching { users = Naber.api.adminUsers(search) } }
                    },
                    placeholder = { Text("Kullanici ara") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NaberColors.Surface,
                        unfocusedContainerColor = NaberColors.Surface,
                        focusedBorderColor = NaberColors.Accent,
                        unfocusedBorderColor = NaberColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(users, key = { it.id }) { user ->
                        AdminUserRow(
                            user = user,
                            onBan = { banTarget = user },
                            onUnban = { updateUser(user) { Naber.api.adminUpdateUser(user.id, banned = false) } },
                            onToggleDisabled = { updateUser(user) { Naber.api.adminUpdateUser(user.id, disabled = !user.disabled) } },
                            onToggleAdmin = { updateUser(user) { Naber.api.adminUpdateUser(user.id, admin = !user.isAdmin) } },
                            onLogout = { updateUser(user) { Naber.api.adminUpdateUser(user.id, logout = true) } },
                            onPassword = { passwordTarget = user },
                            onDelete = { deleteTarget = user }
                        )
                        ThinDivider(startIndent = 70.dp)
                    }
                }
            }

            2 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (chats.isEmpty()) {
                    item { EmptyState("Sohbet yok", "Henuz bir sohbet baslatilmamis.") }
                }
                items(chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.title, fontSize = 15.sp, color = NaberColors.TextPrimary)
                                if (chat.type == "group") RoleTag("Grup")
                            }
                            Text(
                                "${chat.memberCount} uye - ${chat.messageCount} mesaj",
                                fontSize = 12.sp,
                                color = NaberColors.TextSecondary
                            )
                        }
                        Text(
                            "Sil",
                            color = NaberColors.Danger,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { deleteChatTarget = chat }
                        )
                    }
                    ThinDivider()
                }
            }

            else -> ServerTab(settings)
        }
    }

    banTarget?.let { user ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { banTarget = null },
            title = { Text("${user.displayName} yasaklansin mi?") },
            text = {
                Column {
                    Text("Yasakli kullanici giris yapamaz, listelerde gorunmez ve tum oturumlari kapatilir.")
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        placeholder = { Text("Sebep (istege bagli)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = user
                    val text = reason
                    banTarget = null
                    updateUser(target) { Naber.api.adminUpdateUser(target.id, banned = true, reason = text) }
                }) { Text("Yasakla", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { banTarget = null }) { Text("Vazgec") } }
        )
    }

    passwordTarget?.let { user ->
        TextFieldDialog(
            title = "${user.displayName} icin yeni sifre",
            initial = "",
            onDismiss = { passwordTarget = null },
            onConfirm = { value ->
                passwordTarget = null
                if (value.length >= 6) {
                    updateUser(user) { Naber.api.adminUpdateUser(user.id, password = value) }
                } else {
                    error = "Sifre en az 6 karakter olmali."
                }
            }
        )
    }

    deleteTarget?.let { user ->
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { deleteTarget = null },
            title = { Text("Kullanici silinsin mi?") },
            text = { Text("${user.displayName} ve tum mesajlari kalici olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = user
                    deleteTarget = null
                    scope.launch {
                        runCatching { Naber.api.adminDeleteUser(target.id) }
                            .onSuccess {
                                users = users.filterNot { it.id == target.id }
                                info = "${target.displayName} silindi."
                            }
                            .onFailure { error = it.message }
                    }
                }) { Text("Sil", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Vazgec") } }
        )
    }

    deleteChatTarget?.let { chat ->
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { deleteChatTarget = null },
            title = { Text("Sohbet silinsin mi?") },
            text = { Text("\"${chat.title}\" ve icindeki ${chat.messageCount} mesaj silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = chat
                    deleteChatTarget = null
                    scope.launch {
                        runCatching { Naber.api.adminDeleteChat(target.id) }
                            .onSuccess { chats = chats.filterNot { it.id == target.id } }
                            .onFailure { error = it.message }
                    }
                }) { Text("Sil", color = NaberColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteChatTarget = null }) { Text("Vazgec") } }
        )
    }
}

@Composable
private fun SummaryTab(
    stats: AdminStats?,
    storageTest: StorageTestResult?,
    testing: Boolean,
    onTest: () -> Unit
) {
    if (stats == null) {
        EmptyState("Veri yok", "Istatistikler alinamadi.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Kullanici", "${stats.totalUsers}", "${stats.onlineUsers} cevrimici, ${stats.bannedUsers} yasakli", Modifier.weight(1f))
                StatCard("Mesaj", "${stats.totalMessages}", "bugun ${stats.todayMessages}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Sohbet", "${stats.conversations}", "${stats.groups} grup", Modifier.weight(1f))
                StatCard(
                    "Arama",
                    "${stats.totalCalls}",
                    "${formatDuration(stats.callSeconds)} - ${stats.missedCalls} cevapsiz",
                    Modifier.weight(1f)
                )
            }
        }

        item {
            NaberCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Depolama (Backblaze B2)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
                    Text(
                        "${stats.storageFiles} dosya - ${formatBytes(stats.storageBytes)}",
                        fontSize = 13.sp,
                        color = NaberColors.TextSecondary
                    )
                    StatusLine("Bucket bilgileri girilmis", stats.storageReady)
                    StatusLine("Bildirimler (FCM) yapilandirilmis", stats.pushReady)
                    StatusLine("TURN sunucusu tanimli", stats.turnConfigured)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NaberColors.SurfaceHigh)
                            .clickable(enabled = !testing) { onTest() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NaberColors.Accent)
                        } else {
                            Text("Bucket bilgilerini test et", color = NaberColors.Accent, fontSize = 14.sp)
                        }
                    }

                    storageTest?.let { result ->
                        result.steps.forEach { step ->
                            StatusLine("${step.label}: ${step.message}", step.ok)
                        }
                        Text(
                            result.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (result.ok) NaberColors.Accent else NaberColors.Danger
                        )
                    }
                }
            }
        }

        item {
            NaberCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Son katilanlar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
                    stats.recentUsers.take(5).forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Avatar(user, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(user.displayName, fontSize = 14.sp, color = NaberColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text(user.naberEmail, fontSize = 11.5.sp, color = NaberColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerTab(settings: ServerSettings?) {
    if (settings == null) {
        EmptyState("Sunucu bilgisi yok", "Ayarlar alinamadi.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NaberCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WordPress sunucusu", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
                    InfoLine("Adres", settings.server)
                    InfoLine("Eklenti surumu", settings.pluginVersion)
                    InfoLine("Naber adresi alan adi", "@${settings.emailDomain}")
                    InfoLine("Yeni kayitlar", if (settings.allowRegistration) "Acik" else "Kapali")
                }
            }
        }
        item {
            NaberCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backblaze B2", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
                    InfoLine("Bucket", settings.bucketName.ifBlank { "girilmemis" })
                    InfoLine("Bucket ID", settings.bucketId.ifBlank { "-" })
                    InfoLine("Key ID", settings.keyId.ifBlank { "-" })
                    InfoLine("Klasor oneki", settings.pathPrefix)
                    InfoLine("Azami dosya", "${settings.maxUploadMb} MB")
                    InfoLine("Imzali adres suresi", "${settings.linkTtl} sn")
                    Text(
                        "Bu bilgiler WordPress > Naber Chat ekranindan degistirilir.",
                        fontSize = 12.sp,
                        color = NaberColors.TextSecondary
                    )
                }
            }
        }
        item {
            NaberCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Arama ve bildirim", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
                    InfoLine("STUN", settings.stunUrls.ifBlank { "-" })
                    InfoLine("TURN", settings.turnUrls.ifBlank { "tanimli degil" })
                    InfoLine("Firebase projesi", settings.fcmProjectId.ifBlank { "tanimli degil" })
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.5.sp, color = NaberColors.TextSecondary)
        Text(value, fontSize = 14.sp, color = NaberColors.TextPrimary)
    }
}

@Composable
private fun AdminUserRow(
    user: User,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onToggleDisabled: () -> Unit,
    onToggleAdmin: () -> Unit,
    onLogout: () -> Unit,
    onPassword: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(user, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, fontSize = 15.sp, color = NaberColors.TextPrimary)
                if (user.isAdmin) RoleTag("Yonetici")
                if (user.banned) RoleTag("Yasakli")
                if (user.disabled) RoleTag("Pasif")
            }
            Text(user.naberEmail, fontSize = 12.sp, color = NaberColors.TextSecondary)
            if (user.banned && user.banReason.isNotBlank()) {
                Text("Sebep: ${user.banReason}", fontSize = 11.5.sp, color = NaberColors.Danger)
            }
        }

        Box {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Islemler",
                tint = NaberColors.TextSecondary,
                modifier = Modifier.size(20.dp).clickable { menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (user.banned) "Yasagi kaldir" else "Yasakla") },
                    onClick = { menu = false; if (user.banned) onUnban() else onBan() }
                )
                DropdownMenuItem(
                    text = { Text(if (user.disabled) "Aktif et" else "Devre disi birak") },
                    onClick = { menu = false; onToggleDisabled() }
                )
                DropdownMenuItem(
                    text = { Text(if (user.isAdmin) "Yoneticiligi al" else "Yonetici yap") },
                    onClick = { menu = false; onToggleAdmin() }
                )
                DropdownMenuItem(
                    text = { Text("Sifre belirle") },
                    onClick = { menu = false; onPassword() }
                )
                DropdownMenuItem(
                    text = { Text("Oturumlari kapat") },
                    onClick = { menu = false; onLogout() }
                )
                DropdownMenuItem(
                    text = { Text("Kullaniciyi sil", color = NaberColors.Danger) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    NaberCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 11.sp, color = NaberColors.TextSecondary)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = NaberColors.TextSecondary)
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (ok) NaberColors.Accent else NaberColors.Danger,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = NaberColors.TextPrimary)
    }
}
