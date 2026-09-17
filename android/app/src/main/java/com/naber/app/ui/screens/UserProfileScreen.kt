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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.UserProfile
import com.naber.app.ui.Avatar
import com.naber.app.ui.NaberCard
import com.naber.app.ui.OnlineDot
import com.naber.app.ui.ThinDivider
import com.naber.app.ui.formatPresence
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Baska bir kullanicinin profili.
 *
 * Kisiler sekmesinde bir kullanicinin fotografina dokununca acilir.
 * Sohbet burada baslamaz; yalnizca profil bilgisi ve durtme butonu var.
 */
@Composable
fun UserProfileScreen(userId: Int, onBack: () -> Unit, onOpenChat: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pokeBusy by remember { mutableStateOf(false) }
    // Sunucudan gelen bekleme suresi; her saniye geri sayilir.
    var cooldown by remember { mutableIntStateOf(0) }
    var pokeMessage by remember { mutableStateOf<String?>(null) }
    var chatBusy by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            val fresh = Naber.api.userProfile(userId)
            profile = fresh
            cooldown = fresh.pokeCooldown
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(userId) { load() }

    // Geri sayim ekranda gorunsun; sunucuya tekrar sormaya gerek yok.
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun poke() {
        if (pokeBusy || cooldown > 0) return
        pokeBusy = true
        pokeMessage = null
        scope.launch {
            try {
                cooldown = Naber.api.pokeUser(userId)
                pokeMessage = "${profile?.user?.displayName ?: "Kullanici"} durtuldu!"
            } catch (e: Exception) {
                pokeMessage = e.message
            } finally {
                pokeBusy = false
            }
        }
    }

    fun openChat() {
        if (chatBusy) return
        chatBusy = true
        scope.launch {
            try {
                val (_, conversationId) = Naber.api.addContact(userId = userId)
                if (conversationId > 0) onOpenChat(conversationId)
            } catch (e: Exception) {
                pokeMessage = e.message
            } finally {
                chatBusy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(NaberColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = NaberColors.TextPrimary,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp)
            )
            Text("Profil", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NaberColors.Accent)
            }

            profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Kullanici bulunamadi.", color = NaberColors.TextSecondary)
            }

            else -> {
                val current = profile!!
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(NaberColors.AccentDim, NaberColors.Background))
                            )
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-34).dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Avatar(current.user, size = 96.dp)
                    }

                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(current.user.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (current.user.online) {
                                OnlineDot(size = 9.dp, borderColor = NaberColors.Background)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(formatPresence(current.user), color = NaberColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    NaberCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                        ProfileInfoRow("Naber adresi", current.user.naberEmail)
                        if (current.user.about.isNotBlank()) {
                            ThinDivider(startIndent = 16.dp)
                            ProfileInfoRow("Hakkinda", current.user.about)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Durtme butonu: bekleme suresi varsa geri sayim gosterilir.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (cooldown > 0 || pokeBusy) NaberColors.SurfaceHigh else NaberColors.Accent)
                                .clickable(enabled = cooldown <= 0 && !pokeBusy) { poke() }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.BackHand,
                                contentDescription = null,
                                tint = if (cooldown > 0 || pokeBusy) NaberColors.TextSecondary else Color(0xFF04120D),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    pokeBusy -> "Gonderiliyor..."
                                    cooldown > 0 -> "${cooldown} sn"
                                    else -> "Durt"
                                },
                                color = if (cooldown > 0 || pokeBusy) NaberColors.TextSecondary else Color(0xFF04120D),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NaberColors.SurfaceHigh)
                                .clickable(enabled = !chatBusy) { openChat() }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (chatBusy) "Aciliyor..." else "Sohbet ac",
                                color = NaberColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    pokeMessage?.let {
                        Text(
                            it,
                            color = NaberColors.TextSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = NaberColors.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = NaberColors.TextPrimary, fontSize = 14.5.sp)
    }
}
