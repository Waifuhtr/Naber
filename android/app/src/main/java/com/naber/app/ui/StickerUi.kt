package com.naber.app.ui

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import coil.compose.AsyncImage
import com.naber.app.data.MediaStore
import com.naber.app.data.Sticker
import com.naber.app.data.StickerStore
import com.naber.app.ui.theme.NaberColors
import kotlinx.coroutines.launch

/** Metindeki ":ad:" bicimindeki ozel emoji adlari. */
private val EMOJI_TOKEN = Regex(":([a-zA-Z0-9_]{1,30}):")

/**
 * Cikartma / ozel emoji gorseli.
 *
 * Adres imzali oldugu ve her istekte degistigi icin dosya medya
 * kimligine gore cihazda saklanir; ayni gorsel bir daha inmez.
 */
@Composable
fun StickerImage(sticker: Sticker, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stored by remember(sticker.mediaId) {
        mutableStateOf(MediaStore.cachedUri(context, sticker.mediaId))
    }
    LaunchedEffect(sticker.mediaId, sticker.url) {
        if (stored == null && sticker.url.isNotBlank()) {
            stored = MediaStore.ensure(context, sticker.mediaId, sticker.url)
        }
    }
    AsyncImage(
        model = stored ?: sticker.url,
        contentDescription = sticker.name,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

/**
 * "@isim" gecen yerleri vurgular.
 *
 * Isimler sohbetin uye listesinden gelir; boylece metindeki bir e-posta
 * adresi yanlislikla bahsetme gibi gorunmez.
 */
internal fun mentionText(body: String, names: List<String>, highlight: Color): AnnotatedString {
    if (names.isEmpty() || !body.contains('@')) return AnnotatedString(body)
    return buildAnnotatedString {
        append(body)
        val style = SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)
        // Uzun isimler once: "@Ali Veli" yazilmisken yalnizca "Ali" kismi
        // vurgulanip geri kalani duz kalmasin.
        names.sortedByDescending { it.length }.forEach { name ->
            val token = "@" + name
            var index = body.indexOf(token, ignoreCase = true)
            while (index >= 0) {
                addStyle(style, index, index + token.length)
                index = body.indexOf(token, index + token.length, ignoreCase = true)
            }
        }
    }
}

/**
 * Mesaj metni: "@isim" vurgusu ve ":ad:" bicimindeki ozel emojiler
 * satir icinde kucuk gorsele donusur.
 */
@Composable
fun MessageBodyText(
    body: String,
    mentionNames: List<String>,
    highlight: Color,
    color: Color,
    fontSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    // Katalog degisince (yeni emoji eklenince) yeniden cozulur.
    val catalogSize = StickerStore.cached().size
    val matches = remember(body, catalogSize) {
        EMOJI_TOKEN.findAll(body)
            .mapNotNull { match ->
                StickerStore.emoji(match.groupValues[1].lowercase())?.let { match to it }
            }
            .toList()
    }

    if (matches.isEmpty()) {
        Text(
            mentionText(body, mentionNames, highlight),
            fontSize = fontSize,
            color = color,
            modifier = modifier
        )
        return
    }

    val annotated = remember(body, catalogSize, mentionNames) {
        buildAnnotatedString {
            var last = 0
            matches.forEach { (match, sticker) ->
                if (match.range.first > last) {
                    append(mentionText(body.substring(last, match.range.first), mentionNames, highlight))
                }
                appendInlineContent(sticker.token, sticker.token)
                last = match.range.last + 1
            }
            if (last < body.length) {
                append(mentionText(body.substring(last), mentionNames, highlight))
            }
        }
    }

    val inline = matches.associate { (_, sticker) ->
        sticker.token to InlineTextContent(
            Placeholder(
                width = 1.6.em,
                height = 1.6.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            StickerImage(sticker = sticker, size = 22.dp, modifier = Modifier.fillMaxSize())
        }
    }

    Text(
        annotated,
        inlineContent = inline,
        fontSize = fontSize,
        color = color,
        modifier = modifier
    )
}

/**
 * Cikartma secici: paketler sekme olarak ustte, altinda izgara.
 * "Ozel emoji" sekmesi ayni katalogtaki emoji turundekileri gosterir.
 */
@Composable
fun StickerPickerDialog(
    onDismiss: () -> Unit,
    onPickSticker: (Sticker) -> Unit,
    onPickEmoji: (Sticker) -> Unit,
    onAdd: () -> Unit
) {
    val packs = StickerStore.packs()
    val emojis = StickerStore.emojis()
    val tabs = packs.map { it.first } + if (emojis.isNotEmpty()) listOf(EMOJI_TAB) else emptyList()
    var tabIndex by remember(tabs.size) { mutableStateOf(0) }
    val active = tabs.getOrNull(tabIndex)
    val shown = if (active == EMOJI_TAB) emojis else packs.firstOrNull { it.first == active }?.second.orEmpty()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NaberColors.Surface)
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cikartmalar",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NaberColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "+ Ekle",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NaberColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (tabs.isEmpty()) {
                Text(
                    "Henuz cikartma yok. \"+ Ekle\" ile kendi paketini olusturabilirsin.",
                    fontSize = 13.sp,
                    color = NaberColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = index == tabIndex
                        Text(
                            title,
                            fontSize = 12.5.sp,
                            color = if (selected) NaberColors.TextPrimary else NaberColors.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) NaberColors.Accent.copy(alpha = 0.25f) else Color.Transparent
                                )
                                .clickable { tabIndex = index }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (active == EMOJI_TAB) 6 else 4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 10.dp)
                ) {
                    items(shown.size) { index ->
                        val sticker = shown[index]
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (sticker.isEmoji) onPickEmoji(sticker) else onPickSticker(sticker)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            StickerImage(sticker, size = if (sticker.isEmoji) 34.dp else 68.dp)
                        }
                    }
                }
            }

            Text(
                "Kapat",
                color = NaberColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

private const val EMOJI_TAB = "Ozel emoji"

/**
 * Yeni cikartma ya da ozel emoji ekleme.
 *
 * Gorsel once normal medya yolundan yuklenir, sonra katalog kaydi
 * olusturulur. Hareketli dosyalar (GIF / animasyonlu WebP) oldugu gibi
 * saklanir; kucultme yapilmaz ki animasyon bozulmasin.
 */
@Composable
fun AddStickerDialog(onDismiss: () -> Unit, onSaved: (Sticker) -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var kind by remember { mutableStateOf("sticker") }
    var name by remember { mutableStateOf("") }
    var pack by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pickedUri = it } }

    androidx.compose.material3.AlertDialog(
        containerColor = NaberColors.Surface,
        onDismissRequest = onDismiss,
        title = { Text(if (kind == "emoji") "Ozel emoji ekle" else "Cikartma ekle") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sticker" to "Cikartma", "emoji" to "Ozel emoji").forEach { (key, label) ->
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (kind == key) NaberColors.TextPrimary else NaberColors.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (kind == key) NaberColors.Accent.copy(alpha = 0.25f) else Color.Transparent
                                )
                                .clickable { kind = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NaberColors.SurfaceHigh)
                        .clickable(enabled = !busy) {
                            picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (pickedUri != null) {
                        AsyncImage(
                            model = pickedUri,
                            contentDescription = "Secilen gorsel",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    } else {
                        Text("Gorsel sec (PNG, WebP, GIF)", fontSize = 13.sp, color = NaberColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(if (kind == "emoji") "Ad (:ad: olarak yazilir)" else "Ad") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (kind == "sticker") {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pack,
                        onValueChange = { pack = it },
                        singleLine = true,
                        label = { Text("Paket adi (istege bagli)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = NaberColors.Danger, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !busy && pickedUri != null && name.isNotBlank(),
                onClick = {
                    val uri = pickedUri ?: return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            // Hareketli dosyalar yeniden kodlanmaz; kucultme
                            // yalnizca hareketsiz gorsellere uygulanir.
                            val raw = readBytes(context, uri)
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            val animated = mime.contains("gif") || mime.contains("webp")
                            val media = if (animated) {
                                com.naber.app.Naber.api.uploadMedia(raw, mime.ifBlank { "image/webp" }, 0, 0) {}
                            } else {
                                val prepared = prepareImage(context, uri, maxSize = 512)
                                    ?: throw IllegalStateException("Gorsel okunamadi.")
                                com.naber.app.Naber.api.uploadMedia(
                                    prepared.bytes, prepared.mime, prepared.width, prepared.height
                                ) {}
                            }
                            val sticker = com.naber.app.Naber.api.addSticker(kind, pack, name, media.id, animated)
                            StickerStore.add(sticker)
                            onSaved(sticker)
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Yukleniyor..." else "Ekle", color = NaberColors.Accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Vazgec", color = NaberColors.TextSecondary)
            }
        }
    )
}

/** Secilen dosyayi oldugu gibi okur (hareketli gorseller icin). */
private suspend fun readBytes(context: android.content.Context, uri: android.net.Uri): ByteArray =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Dosya okunamadi.")
    }
