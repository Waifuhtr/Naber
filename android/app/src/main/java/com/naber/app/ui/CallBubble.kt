package com.naber.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.call.CallUiState
import com.naber.app.data.User
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Baloncuk bu sure dokunulmazsa seffaflasip uykuya gecer. */
private const val IDLE_DELAY_MS = 5_000L

/**
 * Discord tarzi arama baloncugu.
 *
 * Uygulama arka plandayken ekranin uzerinde durur: aramadaki kisilerin
 * minik profil fotograflarini gosterir, konusanin cevresindeki halka
 * yanip soner. Dokunulunca ses paneli acilir (mikrofon, sagirlastirma,
 * aramaya donus, kapatma). Bir sure dokunulmazsa kendiliginden
 * seffaflasir ve tekrar dokunulana kadar oyle kalir.
 */
@Composable
fun CallBubble(
    state: CallUiState,
    onDrag: (Float, Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onHangUp: () -> Unit,
    onOpen: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var awakeAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }

    LaunchedEffect(awakeAt) {
        idle = false
        delay(IDLE_DELAY_MS)
        expanded = false
        idle = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (idle) 0.2f else 1f,
        animationSpec = tween(durationMillis = 700),
        label = "baloncukSaydamlik"
    )

    // Grup aramasinda katilimcilar, ikili aramada karsi taraf gosterilir.
    val people = remember(state.participants, state.peer) {
        if (state.participants.isNotEmpty()) state.participants else listOfNotNull(state.peer)
    }
    val speakers = people.filter { state.speaking.contains(it.id) }
    val shown = (if (speakers.isNotEmpty()) speakers else people).take(3)

    Column(horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier
                .alpha(alpha)
                .clip(RoundedCornerShape(28.dp))
                .background(NaberColors.Surface)
                .border(1.dp, NaberColors.Divider, RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    // Suruklemeyle dokunma ayni alanda calissin diye ikisi
                    // tek bir pointerInput icinde paralel dinlenir.
                    coroutineScope {
                        launch {
                            detectTapGestures {
                                if (!idle) expanded = !expanded
                                awakeAt = System.currentTimeMillis()
                            }
                        }
                        launch {
                            detectDragGestures(
                                onDragStart = { awakeAt = System.currentTimeMillis() }
                            ) { change, amount ->
                                change.consume()
                                onDrag(amount.x, amount.y)
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-8).dp)
        ) {
            if (shown.isEmpty()) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Arama suruyor",
                    tint = NaberColors.Accent,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                shown.forEach { user ->
                    BubbleAvatar(user = user, speaking = state.speaking.contains(user.id), size = 30.dp)
                }
                val extra = people.size - shown.size
                if (extra > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "+$extra",
                        color = NaberColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (expanded && !idle) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .alpha(alpha)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NaberColors.Surface)
                    .border(1.dp, NaberColors.Divider, RoundedCornerShape(24.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BubbleButton(
                    icon = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    description = if (state.muted) "Mikrofonu ac" else "Mikrofonu kapat",
                    active = state.muted
                ) {
                    awakeAt = System.currentTimeMillis()
                    onToggleMute()
                }
                BubbleButton(
                    icon = if (state.deafened) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    description = if (state.deafened) "Sesi ac" else "Sagirlastir",
                    active = state.deafened
                ) {
                    awakeAt = System.currentTimeMillis()
                    onToggleDeafen()
                }
                BubbleButton(
                    icon = Icons.Filled.OpenInFull,
                    description = "Aramaya don",
                    active = false,
                    onClick = onOpen
                )
                BubbleButton(
                    icon = Icons.Filled.CallEnd,
                    description = "Aramayi kapat",
                    active = false,
                    danger = true,
                    onClick = onHangUp
                )
            }
        }
    }
}

/**
 * Konusan kisinin fotografinin cevresindeki halka yanip soner.
 * Animasyon yalnizca konusurken kurulur; sessizken bos halka cizilir.
 */
@Composable
private fun BubbleAvatar(user: User, speaking: Boolean, size: Dp) {
    if (speaking) {
        val transition = rememberInfiniteTransition(label = "konusma")
        val ring by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 520), RepeatMode.Reverse),
            label = "halka"
        )
        AvatarRing(user = user, size = size, ringColor = NaberColors.Accent.copy(alpha = ring))
    } else {
        AvatarRing(user = user, size = size, ringColor = Color.Transparent)
    }
}

@Composable
private fun AvatarRing(user: User, size: Dp, ringColor: Color) {
    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center
    ) {
        Avatar(user = user, size = size)
    }
}

@Composable
private fun BubbleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        danger -> NaberColors.Danger
        active -> NaberColors.Accent
        else -> NaberColors.SurfaceHigh
    }
    val tint = if (danger || active) Color.White else NaberColors.TextPrimary
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(description) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
