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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.formatPresence
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

/**
 * Kisiler sekmesi.
 * Kullanicilar birbirini Naber adresiyle (kullaniciadi@naber.com) bulur ve ekler.
 */
@Composable
fun ContactsTab(onOpenChat: (Int) -> Unit, onNewGroup: () -> Unit) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<User>>(emptyList()) }
    var directory by remember { mutableStateOf<List<User>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching {
            contacts = Naber.api.contacts()
            directory = Naber.api.users()
        }.onFailure { message = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun addByAddress() {
        val value = query.trim()
        if (value.isEmpty()) return
        busy = true
        message = null
        scope.launch {
            try {
                val (user, conversationId) = Naber.api.addContact(email = value)
                message = "${user.displayName} kisilere eklendi."
                query = ""
                reload()
                if (conversationId > 0) onOpenChat(conversationId)
            } catch (e: Exception) {
                message = e.message ?: "Kullanici bulunamadi."
            } finally {
                busy = false
            }
        }
    }

    fun openWith(user: User) {
        busy = true
        scope.launch {
            try {
                val (_, conversationId) = Naber.api.addContact(userId = user.id)
                reload()
                if (conversationId > 0) onOpenChat(conversationId)
            } catch (e: Exception) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    val filteredDirectory = remember(directory, query, contacts) {
        val contactIds = contacts.map { it.id }.toSet()
        directory.filter { user ->
            user.id !in contactIds &&
                (query.isBlank() ||
                    user.displayName.contains(query, true) ||
                    user.username.contains(query, true) ||
                    user.naberEmail.contains(query, true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kisiler", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Group,
                contentDescription = "Yeni grup",
                tint = NaberColors.Accent,
                modifier = Modifier.size(22.dp).clickable { onNewGroup() }
            )
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; message = null },
                placeholder = { Text("Naber adresi veya kullanici adi") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = NaberColors.TextSecondary) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Ekle",
                        tint = if (query.isBlank()) NaberColors.Divider else NaberColors.Accent,
                        modifier = Modifier.clickable(enabled = query.isNotBlank() && !busy) { addByAddress() }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NaberColors.Surface,
                    unfocusedContainerColor = NaberColors.Surface,
                    focusedBorderColor = NaberColors.Accent,
                    unfocusedBorderColor = NaberColors.Divider
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(top = 6.dp))
            Text(
                "Sizin adresiniz: ${Naber.session.user?.naberEmail.orEmpty()}",
                fontSize = 12.sp,
                color = NaberColors.TextSecondary
            )
            message?.let {
                Spacer(Modifier.padding(top = 6.dp))
                Text(it, fontSize = 12.5.sp, color = NaberColors.Accent)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NaberColors.Accent)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (contacts.isNotEmpty()) {
                item { SectionTitle("Kisilerim (${contacts.size})") }
                items(contacts, key = { "c-${it.id}" }) { user ->
                    UserRow(user = user, actionIcon = Icons.Filled.Chat, onClick = { openWith(user) })
                }
            }

            if (filteredDirectory.isNotEmpty()) {
                item { SectionTitle("Naber'deki diger kisiler") }
                items(filteredDirectory, key = { "d-${it.id}" }) { user ->
                    UserRow(user = user, actionIcon = Icons.Filled.PersonAdd, onClick = { openWith(user) })
                }
            }

            if (contacts.isEmpty() && filteredDirectory.isEmpty()) {
                item {
                    EmptyState(
                        "Kisi bulunamadi",
                        "Arkadasinizin Naber adresini yazip sagdaki ekle simgesine dokunun."
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = NaberColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun UserRow(
    user: User,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(user, size = 46.dp)
            if (user.online) OnlineDot(modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                user.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = NaberColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                user.naberEmail.ifBlank { formatPresence(user) },
                fontSize = 12.5.sp,
                color = NaberColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(actionIcon, contentDescription = null, tint = NaberColors.Accent, modifier = Modifier.size(20.dp))
    }
}
