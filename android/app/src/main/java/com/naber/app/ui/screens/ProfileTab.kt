package com.naber.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.AppLock
import com.naber.app.data.LocalFiles
import com.naber.app.data.LocalMedia
import com.naber.app.data.LocalStore
import com.naber.app.data.MediaStore
import com.naber.app.ui.Avatar
import com.naber.app.ui.NaberCard
import com.naber.app.ui.SettingsRow
import com.naber.app.ui.formatBytes
import com.naber.app.ui.ThinDivider
import com.naber.app.ui.prepareImage
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

@Composable
fun ProfileTab(onAdmin: () -> Unit, onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(Naber.session.user) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf(false) }
    var editAbout by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearMedia by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled(context)) }
    var setPinDialog by remember { mutableStateOf(false) }
    var lockOptions by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableStateOf(0) }
    // Temizlik sonrasi sayilar yeniden hesaplansin diye artirilir.
    var storageVersion by remember { mutableStateOf(0) }

    val storageBytes = remember(storageVersion) { MediaStore.totalBytes(context) }
    val storageFiles = remember(storageVersion) { MediaStore.fileCount(context) }
    val historyBytes = remember(storageVersion) { LocalStore.totalBytes(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Secilen fotograf aninda gosterilir, yukleme arka planda surer.
        Naber.session.localAvatar = uri.toString()
        avatarVersion++
        busy = true
        scope.launch {
            try {
                val prepared = prepareImage(context, uri, maxSize = 640)
                if (prepared != null) {
                    // Galeri adresi uygulama kapaninca gecersiz olur; kalici kopya alinir.
                    val myId = Naber.session.user?.id ?: 0
                    LocalFiles.persist(context, prepared.bytes, "avatar-$myId.jpg")?.let { path ->
                        Naber.session.localAvatar = path
                        avatarVersion++
                    }
                    val media = Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) {}
                    user = Naber.api.updateProfile(null, null, media.id)
                    message = "Profil fotografi guncellendi."
                }
            } catch (e: Exception) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Kapak + profil fotografi
        Box(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(NaberColors.AccentDim, NaberColors.Background)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .offset(y = 34.dp)
            ) {
                key(avatarVersion) {
                    Avatar(user, size = 96.dp, modifier = Modifier.clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NaberColors.Accent)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NaberColors.Background,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = "Fotograf sec",
                            tint = NaberColors.Background,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(44.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                user?.displayName.orEmpty(),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = NaberColors.TextPrimary
            )
            Text(
                if (user?.isAdmin == true) "Yonetici" else "Naber kullanicisi",
                fontSize = 13.sp,
                color = NaberColors.TextSecondary
            )
        }

        message?.let {
            Text(
                it,
                fontSize = 12.5.sp,
                color = NaberColors.Accent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        NaberCard(modifier = Modifier.padding(horizontal = 14.dp)) {
            SettingsRow(
                icon = Icons.Filled.Badge,
                title = "Gorunen ad",
                subtitle = user?.displayName.orEmpty(),
                onClick = { editName = true }
            )
            ThinDivider(startIndent = 54.dp)
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Hakkimda",
                subtitle = user?.about?.ifBlank { "Bir seyler yazin" } ?: "Bir seyler yazin",
                onClick = { editAbout = true }
            )
            ThinDivider(startIndent = 54.dp)
            SettingsRow(
                icon = Icons.Filled.AlternateEmail,
                title = "Naber adresiniz",
                subtitle = user?.naberEmail.orEmpty(),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Naber", user?.naberEmail.orEmpty()))
                    message = "Adres kopyalandi. Arkadaslariniz bu adresle sizi bulabilir."
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        NaberCard(modifier = Modifier.padding(horizontal = 14.dp)) {
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Bildirimler",
                subtitle = "Sohbet basina sohbet ekranindan ayarlanir"
            )
            ThinDivider(startIndent = 54.dp)
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "Uygulama kilidi",
                subtitle = if (lockEnabled) "PIN acik" else "Kapali",
                onClick = { if (lockEnabled) lockOptions = true else setPinDialog = true }
            )
            if (user?.isAdmin == true) {
                ThinDivider(startIndent = 54.dp)
                SettingsRow(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = "Yonetim paneli",
                    subtitle = "Kullanicilar, yasaklama, depolama",
                    tint = NaberColors.Accent,
                    onClick = onAdmin
                )
                ThinDivider(startIndent = 54.dp)
                SettingsRow(
                    icon = Icons.Filled.Cloud,
                    title = "Sunucu",
                    subtitle = Naber.session.baseUrl.removePrefix("https://"),
                    tint = NaberColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Depolama: gorseller ve sohbet gecmisi cihazda saklanir; buradan
        // ne kadar yer kapladigi gorulur ve temizlenebilir.
        NaberCard(modifier = Modifier.padding(horizontal = 14.dp)) {
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = "Depolama",
                subtitle = if (storageFiles > 0) {
                    "$storageFiles gorsel - ${formatBytes(storageBytes)}"
                } else {
                    "Saklanan gorsel yok"
                },
                onClick = { confirmClearMedia = true }
            )
            ThinDivider(startIndent = 54.dp)
            SettingsRow(
                icon = Icons.Filled.History,
                title = "Sohbet gecmisi",
                subtitle = "Cihazda ${formatBytes(historyBytes)} - cevrimdisiyken de okunur",
                tint = NaberColors.TextSecondary
            )
        }

        Spacer(Modifier.height(14.dp))

        NaberCard(modifier = Modifier.padding(horizontal = 14.dp)) {
            SettingsRow(
                icon = Icons.Filled.Logout,
                title = "Cikis yap",
                tint = NaberColors.Danger,
                titleColor = NaberColors.Danger,
                onClick = { confirmLogout = true }
            )
        }

        Spacer(Modifier.height(30.dp))
    }

    if (editName) {
        TextFieldDialog(
            title = "Gorunen ad",
            initial = user?.displayName.orEmpty(),
            onDismiss = { editName = false },
            onConfirm = { value ->
                editName = false
                scope.launch {
                    runCatching { Naber.api.updateProfile(value, null, null) }
                        .onSuccess { user = it; message = "Kaydedildi." }
                        .onFailure { message = it.message }
                }
            }
        )
    }

    if (editAbout) {
        TextFieldDialog(
            title = "Hakkimda",
            initial = user?.about.orEmpty(),
            onDismiss = { editAbout = false },
            onConfirm = { value ->
                editAbout = false
                scope.launch {
                    runCatching { Naber.api.updateProfile(null, value, null) }
                        .onSuccess { user = it; message = "Kaydedildi." }
                        .onFailure { message = it.message }
                }
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { confirmLogout = false },
            title = { Text("Cikis yapilsin mi?") },
            text = { Text("Tekrar giris yapmaniz gerekecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch {
                        Naber.events.reset()
                        // Cihazdaki kopyalar baska bir hesaba gorunmemeli.
                        LocalStore.clear(context)
                        MediaStore.clear(context)
                        LocalMedia.clear()
                        Naber.api.logout()
                        onLoggedOut()
                    }
                }) { Text("Cikis yap", color = NaberColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Vazgec") }
            }
        )
    }

    if (confirmClearMedia) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { confirmClearMedia = false },
            title = { Text("Gorseller silinsin mi?") },
            text = {
                Text(
                    "Cihazda saklanan $storageFiles gorsel (${formatBytes(storageBytes)}) silinecek. " +
                        "Mesajlar silinmez; gorseller gerektiginde yeniden indirilir."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearMedia = false
                    MediaStore.clear(context)
                    LocalMedia.clear()
                    storageVersion++
                    message = "Saklanan gorseller silindi."
                }) { Text("Sil", color = NaberColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMedia = false }) { Text("Vazgec") }
            }
        )
    }

    if (lockOptions) {
        AlertDialog(
            containerColor = NaberColors.Surface,
            onDismissRequest = { lockOptions = false },
            title = { Text("Uygulama kilidi") },
            text = { Text("PIN'i degistirebilir veya kilidi tamamen kaldirabilirsiniz.") },
            confirmButton = {
                TextButton(onClick = {
                    lockOptions = false
                    setPinDialog = true
                }) { Text("PIN degistir", color = NaberColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    lockOptions = false
                    AppLock.clear(context)
                    lockEnabled = false
                    message = "Uygulama kilidi kaldirildi."
                }) { Text("Kilidi kaldir", color = NaberColors.Danger) }
            }
        )
    }

    if (setPinDialog) {
        PinDialog(
            onDismiss = { setPinDialog = false },
            onConfirm = { pin ->
                setPinDialog = false
                if (AppLock.setPin(context, pin)) {
                    lockEnabled = true
                    message = "Uygulama kilidi acildi. Uygulamadan her cikista PIN sorulacak."
                }
            }
        )
    }
}

/** Yeni PIN belirleme penceresi; yanlis yazmaya karsi iki kez sorar. */
@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        containerColor = NaberColors.Surface,
        onDismissRequest = onDismiss,
        title = { Text("Uygulama kilidi") },
        text = {
            Column {
                Text(
                    "4-8 haneli bir PIN belirleyin. Uygulama arka plana alindiginda kilitlenir.",
                    fontSize = 13.sp,
                    color = NaberColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        pin = input.filter { it.isDigit() }.take(AppLock.MAX_LENGTH)
                        error = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = again,
                    onValueChange = { input ->
                        again = input.filter { it.isDigit() }.take(AppLock.MAX_LENGTH)
                        error = null
                    },
                    label = { Text("PIN tekrar") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                val shown = error
                if (shown != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(shown, fontSize = 12.sp, color = NaberColors.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !AppLock.isValidPin(pin) -> error = "PIN ${AppLock.MIN_LENGTH}-${AppLock.MAX_LENGTH} rakam olmali."
                    pin != again -> error = "Iki PIN ayni degil."
                    else -> onConfirm(pin)
                }
            }) { Text("Kaydet", color = NaberColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgec", color = NaberColors.TextSecondary) }
        }
    )
}

@Composable
fun TextFieldDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        containerColor = NaberColors.Surface,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) { Text("Kaydet", color = NaberColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgec", color = NaberColors.TextSecondary) }
        }
    )
}
