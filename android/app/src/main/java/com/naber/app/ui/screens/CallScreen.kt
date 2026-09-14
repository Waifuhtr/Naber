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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naber.app.Naber
import com.naber.app.call.CallStage
import com.naber.app.call.CallUiState
import com.naber.app.ui.Avatar
import com.naber.app.ui.formatDuration
import kotlinx.coroutines.delay

/**
 * Gelen arama ve aktif arama ekrani.
 * Uygulamanin herhangi bir ekraninin uzerinde tam ekran acilir.
 */
@Composable
fun CallScreen(state: CallUiState) {
    var elapsed by remember { mutableStateOf(0) }

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
                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF07231E))
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 60.dp)
                ) {
                    Avatar(state.peer, size = 132.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        state.peer?.displayName ?: "Bilinmeyen",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (state.stage) {
                            CallStage.INCOMING -> "Gelen arama"
                            CallStage.DIALING -> "Araniyor..."
                            CallStage.CONNECTING -> "Baglaniyor..."
                            CallStage.ACTIVE -> formatDuration(elapsed)
                            else -> state.statusText.ifBlank { "Arama sonlandi" }
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color(0xFFFFCDD2),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.stage == CallStage.ACTIVE || state.stage == CallStage.CONNECTING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            CallButton(
                                icon = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                description = if (state.muted) "Mikrofonu ac" else "Mikrofonu kapat",
                                background = Color.White.copy(alpha = if (state.muted) 0.35f else 0.18f),
                                onClick = { Naber.calls.toggleMute() }
                            )
                            CallButton(
                                icon = Icons.Filled.VolumeUp,
                                description = "Hoparlor",
                                background = Color.White.copy(alpha = if (state.speakerOn) 0.35f else 0.18f),
                                onClick = { Naber.calls.toggleSpeaker() }
                            )
                        }
                        Spacer(Modifier.height(30.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                        horizontalArrangement = if (state.stage == CallStage.INCOMING) Arrangement.SpaceEvenly else Arrangement.Center
                    ) {
                        if (state.stage == CallStage.INCOMING) {
                            CallButton(
                                icon = Icons.Filled.CallEnd,
                                description = "Reddet",
                                background = Color(0xFFD32F2F),
                                size = 68,
                                onClick = { Naber.calls.reject() }
                            )
                            CallButton(
                                icon = Icons.Filled.Call,
                                description = "Kabul et",
                                background = Color(0xFF2E7D32),
                                size = 68,
                                onClick = { Naber.calls.accept() }
                            )
                        } else if (state.stage != CallStage.ENDED) {
                            CallButton(
                                icon = Icons.Filled.CallEnd,
                                description = "Aramayi bitir",
                                background = Color(0xFFD32F2F),
                                size = 68,
                                onClick = { Naber.calls.hangUp() }
                            )
                        } else {
                            CallButton(
                                icon = Icons.Filled.CallEnd,
                                description = "Kapat",
                                background = Color.White.copy(alpha = 0.25f),
                                size = 68,
                                onClick = { Naber.calls.dismissError() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
