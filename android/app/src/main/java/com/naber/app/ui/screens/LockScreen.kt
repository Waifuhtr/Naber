package com.naber.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.data.AppLock
import com.naber.app.ui.theme.NaberColors

/**
 * PIN ile uygulama kilidi ekrani.
 *
 * Uygulama arka plana alinip geri gelindiginde gosterilir; dogru PIN
 * girilene kadar sohbetler gorunmez.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    // PIN uzunlugu degiskendir (4-8): "onayla" tusu yerine her yeni hanede
    // sessizce denenir. Yanlis uyarisi ancak en uzun olasilik da tutmayinca
    // gosterilir, yoksa 6 haneli PIN girerken 4. hanede uyari cikardi.
    fun press(digit: String) {
        if (pin.length >= AppLock.MAX_LENGTH) return
        wrong = false
        val next = pin + digit
        pin = next
        if (next.length < AppLock.MIN_LENGTH) return

        if (AppLock.verify(context, next)) {
            pin = ""
            onUnlocked()
        } else if (next.length == AppLock.MAX_LENGTH) {
            pin = ""
            wrong = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            // Alttaki ekran hala yerinde duruyor; dokunuslar ona gecmesin diye
            // bos bir tiklama ile yutulur.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = NaberColors.Accent,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("Naber kilitli", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = NaberColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (wrong) "PIN yanlis, tekrar deneyin" else "Devam etmek icin PIN girin",
            fontSize = 13.sp,
            color = if (wrong) NaberColors.Danger else NaberColors.TextSecondary
        )

        Spacer(Modifier.height(26.dp))

        // Girilen hane sayisi kadar dolu nokta.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(AppLock.MAX_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) NaberColors.Accent else NaberColors.SurfaceHigh
                        )
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "<")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "" -> Unit
                            "<" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Sil",
                                tint = NaberColors.TextSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                            )

                            else -> Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(NaberColors.SurfaceHigh)
                                    .clickable { press(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, fontSize = 22.sp, color = NaberColors.TextPrimary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
