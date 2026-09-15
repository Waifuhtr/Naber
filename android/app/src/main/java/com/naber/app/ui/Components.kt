package com.naber.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.naber.app.Naber
import com.naber.app.data.Chat
import com.naber.app.data.LocalFiles
import com.naber.app.data.Media
import com.naber.app.data.MediaStore
import com.naber.app.data.User
import com.naber.app.ui.theme.NaberColors

private val avatarPalette = listOf(
    Color(0xFF2E7D6B), Color(0xFF3F6CB2), Color(0xFF8E5BB5),
    Color(0xFFB2603F), Color(0xFF3F8EB2), Color(0xFF6B8E23),
    Color(0xFFB2537A), Color(0xFF4F6BAF)
)

fun avatarColor(id: Int): Color = avatarPalette[id.mod(avatarPalette.size)]

/**
 * Profil fotografi.
 * Kendi fotografimiz icin once cihazdaki kopya kullanilir; yeni fotograf
 * secildigi anda ekranda gorunur, sunucudan inmesini beklemez.
 */
@Composable
fun Avatar(user: User?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val myId = Naber.session.user?.id ?: 0
    val stored = if (user != null && user.id == myId) Naber.session.localAvatar else ""
    val local = if (stored.startsWith("file://") && !LocalFiles.exists(stored)) "" else stored
    AvatarBase(
        model = local.ifBlank { user?.avatar.orEmpty() },
        cacheKey = "avatar-${user?.id ?: 0}",
        name = user?.displayName.orEmpty(),
        colorSeed = user?.id ?: 0,
        size = size,
        modifier = modifier
    )
}

/** Grup mesajlarinda gonderenin kucuk fotografi. */
@Composable
fun SenderAvatar(name: String, url: String, id: Int, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    AvatarBase(
        model = url,
        cacheKey = "avatar-$id",
        name = name,
        colorSeed = id,
        size = size,
        modifier = modifier
    )
}

@Composable
fun ChatAvatar(chat: Chat, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    if (chat.isGroup) {
        if (chat.avatar.isNotBlank()) {
            AvatarBase(
                model = chat.avatar,
                cacheKey = "group-${chat.id}",
                name = chat.title,
                colorSeed = chat.id,
                size = size,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.size(size).clip(CircleShape).background(avatarColor(chat.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size / 2)
                )
            }
        }
    } else {
        Avatar(chat.peer, size, modifier)
    }
}

@Composable
private fun AvatarBase(
    model: String,
    cacheKey: String,
    name: String,
    colorSeed: Int,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val color = avatarColor(colorSeed)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value / 2.5f).sp
        )
        if (model.isNotBlank()) {
            AsyncImage(
                model = stableImageRequest(model, cacheKey),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Imzali Backblaze adresleri her istekte degistigi icin onbellek anahtari
 * medya kimligine sabitlenir; boylece ayni gorsel tekrar indirilmez.
 */
@Composable
fun stableImageRequest(model: Any, cacheKey: String): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(model)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .crossfade(false)
        .build()

/**
 * Gorseli once cihazdaki kopyadan gosterir; kopya yoksa bir kez indirip saklar.
 * Sunucudaki imzali adres degisse bile gorsel yeniden yuklenmez.
 */
@Composable
fun rememberStoredImage(media: Media?, localUri: Any?): Any? {
    val context = LocalContext.current
    val mediaId = media?.id ?: 0

    var stored by remember(mediaId) {
        mutableStateOf<String?>(if (mediaId > 0) MediaStore.cachedUri(context, mediaId) else null)
    }

    LaunchedEffect(mediaId, media?.url) {
        if (stored == null && mediaId > 0 && !media?.url.isNullOrBlank()) {
            stored = MediaStore.ensure(context, mediaId, media!!.url)
        }
    }

    return localUri ?: stored ?: media?.url?.takeIf { it.isNotBlank() }
}

@Composable
fun MessageImage(
    media: Media?,
    localUri: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null
) {
    val model = rememberStoredImage(media, localUri) ?: return
    AsyncImage(
        model = stableImageRequest(model, "media-${media?.id ?: model.hashCode()}"),
        contentDescription = "Gorsel",
        modifier = modifier,
        contentScale = contentScale,
        onError = { onError?.invoke() }
    )
}

@Composable
fun OnlineDot(size: Dp = 12.dp, borderColor: Color = NaberColors.Background, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(borderColor)
            .padding(2.dp)
            .clip(CircleShape)
            .background(NaberColors.Online)
    )
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(NaberColors.Accent)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color(0xFF04120D),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NaberChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) NaberColors.Accent else NaberColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF04120D) else NaberColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = NaberColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Profil ve yonetim ekranlarindaki kart bloklari. */
@Composable
fun NaberCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NaberColors.Surface),
        content = content
    )
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    tint: Color = NaberColors.TextSecondary,
    titleColor: Color = NaberColors.TextPrimary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (subtitle.isNotBlank()) {
                Text(title, fontSize = 12.sp, color = NaberColors.TextSecondary)
                Text(subtitle, fontSize = 14.5.sp, color = titleColor)
            } else {
                Text(title, fontSize = 15.sp, color = titleColor)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ThinDivider(startIndent: Dp = 0.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(NaberColors.Divider)
    )
}

@Composable
fun RoleTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, NaberColors.Accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text, color = NaberColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
