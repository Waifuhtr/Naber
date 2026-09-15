package com.naber.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

/**
 * Giris ekrani.
 * Sunucu adresi uygulamaya gomulu oldugu icin kullaniciya sorulmaz.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var registerMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (username.isBlank() || password.isBlank()) {
            error = "Kullanici adi ve sifre gerekli."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                if (registerMode) {
                    val user = Naber.api.register(username.trim(), password, displayName.trim().ifBlank { username.trim() })
                    info = "Naber adresiniz: ${user.naberEmail}"
                } else {
                    Naber.api.login(username.trim(), password)
                }
                busy = false
                onLoggedIn()
            } catch (e: Exception) {
                busy = false
                error = e.message ?: "Giris yapilamadi."
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = NaberColors.Surface,
        unfocusedContainerColor = NaberColors.Surface,
        focusedBorderColor = NaberColors.Accent,
        unfocusedBorderColor = NaberColors.Divider,
        focusedLabelColor = NaberColors.Accent,
        unfocusedLabelColor = NaberColors.TextSecondary
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NaberColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = NaberColors.Background, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))
            Text("Naber", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
            Text(
                if (registerMode) "Yeni hesap olustur" else "Hesabiniza giris yapin",
                style = MaterialTheme.typography.bodyMedium,
                color = NaberColors.TextSecondary
            )

            Spacer(Modifier.height(30.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(if (registerMode) "Kullanici adi" else "Kullanici adi veya Naber adresi") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            if (registerMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Gorunen ad") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Sifre") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            if (registerMode) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Kayit olunca size ozel bir Naber adresi olusturulur (ornek: ${username.trim().ifBlank { "kullaniciadi" }}@naber.com). Arkadaslariniz sizi bu adresle bulabilir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NaberColors.TextSecondary
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = NaberColors.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            info?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = NaberColors.Accent, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { submit() },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NaberColors.Accent,
                    contentColor = NaberColors.Background
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NaberColors.Background,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (registerMode) "Kayit ol" else "Giris yap", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(onClick = { registerMode = !registerMode; error = null; info = null }) {
                Text(
                    if (registerMode) "Zaten hesabim var" else "Hesabim yok, kayit olmak istiyorum",
                    color = NaberColors.Accent
                )
            }
        }
    }
}
