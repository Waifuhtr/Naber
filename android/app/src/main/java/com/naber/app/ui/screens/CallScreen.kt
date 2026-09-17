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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naber.app.Naber
import com.naber.app.call.CallBubbleService
import com.naber.app.call.CallStage
import com.naber.app.call.CallUiState
import com.naber.app.data.User
import com.naber.app.ui.Avatar
import com.naber.app.ui.formatDuration
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.delay

/**
 * Gelen arama, aktif arama ve grup aramasi ekrani.
 * Grup aramalarinda yonetici katilimcilari susturabilir veya sesten atabilir.
 */
@Composable
fun CallScreen(state: CallUiState) {
    var elapsed by remember { mutableIntStateOf(0) }
    val myId = Naber.session.user?.id ?: 0
    val context = LocalContext.current
    // Izin durumu her arama acilisinda bir kez okunur; ayarlardan verilip
    // donuldugunde ekran yeniden kuruldugu icin guncellenir.
    var bubbleAllowed by remember { mutableStateOf(CallBubbleService.canShow(context)) }

    LaunchedEffect(state.stage) {
        bubbleAllowed = CallBubbleService.canShow(context)
    }

    LaunchedEffect(state.stage, state.startedAt) {
        if (state.stage == CallStage.ACTIVE && state.startedAt > 0) {
            while (true) {
                elapsed = ((System.currentTimeMillis() - state.startedAt) / 1000).toInt()
                delay(1000)
            }
        } else {
            elapsed = 0
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14313A), Color(0xFF0B1016))
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    if (state.isGroup) {
                        Box(
                            modifier = Modifier.size(112.dp).clip(CircleShape).background(NaberColors.AccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    } else {
                        Avatar(state.peer, size = 124.dp)
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        state.title.ifBlank { state.peer?.displayName ?: "Bilinmeyen" },
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when (state.stage) {
                            CallStage.INCOMING -> if (state.isGroup) "Grup aramasi" else "Gelen arama"
                            CallStage.DIALING -> "Araniyor..."
                            CallStage.CONNECTING -> "Baglaniyor..."
                            CallStage.ACTIVE -> formatDuration(elapsed)
                            else -> state.statusText.ifBlank { "Arama sonlandi" }
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp
                    )

                    if (state.forceMuted) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Yonetici mikrofonunuzu kapatti",
                            color = NaberColors.Warning,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }

                if (state.isGroup && state.participants.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.participants, key = { it.id }) { participant ->
                            ParticipantRow(
                                participant = participant,
                                canModerate = state.canModerate && participant.id != myId,
                                onMute = { Naber.calls.moderateMute(participant.id, !participant.muted) },
                                onKick = { Naber.calls.moderateKick(participant.id) }
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.stage == CallStage.ACTIVE || state.stage == CallStage.CONNECTING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            CallButton(
                                icon = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                description = if (state.muted) "Mikrofonu ac" else "Mikrofonu kapat",
                                background = if (state.muted) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f),
                                onClick = { Naber.calls.toggleMute() }
                            )
                            CallButton(
                                icon = Icons.Filled.VolumeUp,
                                description = "Hoparlor",
                                background = if (state.speakerOn) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f),
                                onClick = { Naber.calls.toggleSpeaker() }
                            )
                            // Sagirlastirma: acikken kimseyi duymayiz ve
                            // mikrofon da kapanir (Discord'daki gibi).
                            CallButton(
                                icon = if (state.deafened) Icons.Filled.VolumeOff else Icons.Filled.HeadsetMic,
                                description = if (state.deafened) "Sesi ac" else "Sagirlastir",
                                background = if (state.deafened) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f),
                                onClick = { Naber.calls.toggleDeafen() }
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        if (!bubbleAllowed) {
                            // Baloncuk yalnizca "diger uygulamalarin uzerinde
                            // goster" izniyle calisir; izin istegi burada.
                            Text(
                                "Arka planda baloncuk icin izin ver",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            context.startActivity(CallBubbleService.permissionIntent(context))
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
                        horizontalArrangement = if (state.stage == CallStage.INCOMING) Arrangement.SpaceEvenly else Arrangement.Center
                    ) {
                        when (state.stage) {
                            CallStage.INCOMING -> {
                                CallButton(
                                    icon = Icons.Filled.CallEnd,
                                    description = "Reddet",
                                    background = NaberColors.Danger,
                                    size = 66,
                                    onClick = { Naber.calls.reject() }
                                )
                                CallButton(
                                    icon = Icons.Filled.Call,
                                    description = "Kabul et",
                                    background = NaberColors.Accent,
                                    size = 66,
                                    onClick = { Naber.calls.accept() }
                                )
                            }

                            CallStage.ENDED -> CallButton(
                                icon = Icons.Filled.CallEnd,
                                description = "Kapat",
                                background = Color.White.copy(alpha = 0.22f),
                                size = 66,
                                onClick = { Naber.calls.dismiss() }
                            )

                            else -> CallButton(
                                icon = Icons.Filled.CallEnd,
                                description = if (state.isGroup) "Aramadan ayril" else "Aramayi bitir",
                                background = NaberColors.Danger,
                                size = 66,
                                onClick = { Naber.calls.hangUp() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: User,
    canModerate: Boolean,
    onMute: () -> Unit,
    onKick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val statusText = when (participant.callStatus) {
        "joined" -> "aramada"
        "ringing" -> "caliyor..."
        "kicked" -> "cikarildi"
        "rejected" -> "reddetti"
        else -> "ayrildi"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(participant, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(participant.displayName, color = Color.White, fontSize = 14.sp)
            Text(statusText, color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp)
        }
        if (participant.muted) {
            Icon(
                Icons.Filled.MicOff,
                contentDescription = "Susturuldu",
                tint = NaberColors.Warning,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (canModerate) {
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Islemler",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp).clickable { menu = true }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (participant.muted) "Susturmayi kaldir" else "Suresiz sustur") },
                        onClick = { menu = false; onMute() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sesten at", color = NaberColors.Danger) },
                        onClick = { menu = false; onKick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    description: String,
    background: Color,
    onClick: () -> Unit,
    size: Int = 56
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size((size / 2.4).dp))
    }
}
