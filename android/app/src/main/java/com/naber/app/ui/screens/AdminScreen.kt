package com.naber.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naber.app.Naber
import com.naber.app.data.AdminStats
import com.naber.app.data.StorageTestResult
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.formatBytes
import com.naber.app.ui.formatDuration
import kotlinx.coroutines.launch

/**
 * Yonetim paneli.
 * Bu ekrana yalnizca WordPress'ten is_admin=true donen kullanicilar ulasir;
 * ayrica sunucu tarafinda da her admin ucunda rol kontrolu yapilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var storageTest by remember { mutableStateOf<StorageTestResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<User?>(null) }

    suspend fun load() {
        try {
            stats = Naber.api.adminStats()
            users = Naber.api.adminUsers()
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                title = { Text("Yonetim", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> EmptyState(
                title = "Yonetim verileri alinamadi",
                description = error ?: "",
                modifier = Modifier.padding(padding)
            )

            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val current = stats
                    if (current != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Kullanici", "${current.totalUsers}", "${current.onlineUsers} cevrimici", Modifier.weight(1f))
                            StatCard("Mesaj", "${current.totalMessages}", "bugun ${current.todayMessages}", Modifier.weight(1f))
                        }
                    }
                }
                item {
                    val current = stats
                    if (current != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Sohbet", "${current.conversations}", "toplam", Modifier.weight(1f))
                            StatCard(
                                "Arama",
                                "${current.totalCalls}",
                                "${formatDuration(current.callSeconds)} - ${current.missedCalls} cevapsiz",
                                Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    val current = stats ?: return@item
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Depolama (Backblaze B2)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${current.storageFiles} dosya - ${formatBytes(current.storageBytes)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            StatusLine("Bucket bilgileri girilmis", current.storageReady)
                            StatusLine("Bildirimler (FCM) yapilandirilmis", current.pushReady)
                            StatusLine("TURN sunucusu tanimli", current.turnConfigured)

                            OutlinedButton(
                                onClick = {
                                    testing = true
                                    storageTest = null
                                    scope.launch {
                                        try {
                                            storageTest = Naber.api.adminStorageTest(true)
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            testing = false
                                        }
                                    }
                                },
                                enabled = !testing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Test ediliyor...")
                                } else {
                                    Text("Bucket bilgilerini test et")
                                }
                            }

                            storageTest?.let { result ->
                                Spacer(Modifier.size(4.dp))
                                result.steps.forEach { step ->
                                    StatusLine("${step.label}: ${step.message}", step.ok)
                                }
                                Text(
                                    result.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Kullanicilar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }

                items(users, key = { it.id }) { user ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(user, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName + if (user.isAdmin) "  (yonetici)" else "",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "@${user.username}" + if (user.online) " - cevrimici" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = !user.disabled,
                                onCheckedChange = { active ->
                                    scope.launch {
                                        runCatching { Naber.api.adminSetDisabled(user.id, !active) }
                                            .onSuccess { updated ->
                                                if (updated != null) {
                                                    users = users.map { if (it.id == updated.id) updated else it }
                                                }
                                            }
                                            .onFailure { error = it.message }
                                    }
                                }
                            )
                            IconButton(onClick = { confirmDelete = user }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Kullanici silinsin mi?") },
            text = { Text("${user.displayName} ve tum mesajlari kalici olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = user
                    confirmDelete = null
                    scope.launch {
                        runCatching { Naber.api.adminDeleteUser(target.id) }
                            .onSuccess { users = users.filterNot { it.id == target.id } }
                            .onFailure { error = it.message }
                    }
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgec") }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
