package com.naber.app.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.naber.app.Naber
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.prepareImage
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

@Composable
fun GroupCreateScreen(onBack: () -> Unit, onCreated: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var avatarMediaId by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        avatarUri = uri.toString()
        scope.launch {
            runCatching {
                val prepared = prepareImage(context, uri, maxSize = 640) ?: return@runCatching
                val media = Naber.api.uploadImage(prepared.bytes, prepared.mime, prepared.width, prepared.height) {}
                avatarMediaId = media.id
            }.onFailure { error = it.message }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val contacts = Naber.api.contacts()
            users = if (contacts.isNotEmpty()) contacts else Naber.api.users()
        }.onFailure { error = it.message }
    }

    fun create() {
        if (title.trim().length < 2) {
            error = "Grup adi en az 2 karakter olmali."
            return
        }
        if (selected.isEmpty()) {
            error = "En az bir kisi secin."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val chat = Naber.api.createGroup(title.trim(), selected.toList(), avatarMediaId)
                onCreated(chat.id)
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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
            Text("Yeni grup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary, modifier = Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NaberColors.Accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Olustur",
                    tint = NaberColors.Accent,
                    modifier = Modifier.size(26.dp).clickable { create() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NaberColors.SurfaceHigh)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = NaberColors.TextSecondary)
                }
            }
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                placeholder = { Text("Grup adi") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NaberColors.Surface,
                    unfocusedContainerColor = NaberColors.Surface,
                    focusedBorderColor = NaberColors.Accent,
                    unfocusedBorderColor = NaberColors.Divider
                ),
                modifier = Modifier.weight(1f)
            )
        }

        error?.let {
            Text(it, color = NaberColors.Danger, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Text(
            "Uyeler (${selected.size} secili)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NaberColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
        )

        if (users.isEmpty()) {
            EmptyState("Kisi yok", "Once Kisiler sekmesinden birini ekleyin.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(users, key = { it.id }) { user ->
                    val isSelected = selected.contains(user.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (isSelected) selected - user.id else selected + user.id
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(user, size = 44.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(user.displayName, fontSize = 15.sp, color = NaberColors.TextPrimary)
                            Text(user.naberEmail, fontSize = 12.sp, color = NaberColors.TextSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) NaberColors.Accent else NaberColors.SurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = NaberColors.Background,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
