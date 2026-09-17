package com.naber.app.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Uygulama paleti.
 *
 * Renkler sabit degil, [dark] durumuna gore hesaplanir. Boylece tema
 * degisince `NaberColors.X` okuyan her ekran kendiliginden yeniden
 * cizilir; yuzlerce cagri yerini degistirmek gerekmez.
 */
object NaberColors {

    /** true: koyu tema (varsayilan), false: acik tema. */
    var dark by mutableStateOf(true)

    val Background: Color get() = if (dark) Color(0xFF0B1016) else Color(0xFFF7F9FB)
    val Surface: Color get() = if (dark) Color(0xFF131B24) else Color(0xFFFFFFFF)
    val SurfaceHigh: Color get() = if (dark) Color(0xFF1A242F) else Color(0xFFEDF1F5)
    val TopBar: Color get() = if (dark) Color(0xFF0F161D) else Color(0xFFFFFFFF)
    val Accent: Color get() = if (dark) Color(0xFF25B08B) else Color(0xFF148A6B)
    val AccentDim: Color get() = if (dark) Color(0xFF1B7F65) else Color(0xFF0E6B52)
    val Bubble: Color get() = if (dark) Color(0xFF4C5FE8) else Color(0xFF3F52DE)
    val BubbleIn: Color get() = if (dark) Color(0xFF1C2631) else Color(0xFFE6EBF1)
    val TextPrimary: Color get() = if (dark) Color(0xFFE8EDF3) else Color(0xFF111A22)
    val TextSecondary: Color get() = if (dark) Color(0xFF8B99A8) else Color(0xFF5B6977)
    val Divider: Color get() = if (dark) Color(0xFF1E2934) else Color(0xFFDDE3EA)
    val Danger: Color get() = if (dark) Color(0xFFE5484D) else Color(0xFFCE2C31)
    val Online: Color get() = if (dark) Color(0xFF3BD07F) else Color(0xFF1FA55C)
    val Warning: Color get() = if (dark) Color(0xFFE8A33D) else Color(0xFFB9760F)
}

/**
 * Material renk semasi da temaya gore uretilir; hazir bilesenlerin
 * (AlertDialog, Switch gibi) acik temada koyu kalmamasi icin.
 */
@Composable
private fun naberColorScheme(): ColorScheme {
    val accent = NaberColors.Accent
    return if (NaberColors.dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04120D),
            primaryContainer = NaberColors.AccentDim,
            onPrimaryContainer = Color.White,
            secondary = NaberColors.Bubble,
            onSecondary = Color.White,
            background = NaberColors.Background,
            onBackground = NaberColors.TextPrimary,
            surface = NaberColors.Surface,
            onSurface = NaberColors.TextPrimary,
            surfaceVariant = NaberColors.SurfaceHigh,
            onSurfaceVariant = NaberColors.TextSecondary,
            outline = NaberColors.Divider,
            error = NaberColors.Danger,
            onError = Color.White,
            errorContainer = Color(0xFF3A1417),
            onErrorContainer = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = NaberColors.AccentDim,
            onPrimaryContainer = Color.White,
            secondary = NaberColors.Bubble,
            onSecondary = Color.White,
            background = NaberColors.Background,
            onBackground = NaberColors.TextPrimary,
            surface = NaberColors.Surface,
            onSurface = NaberColors.TextPrimary,
            surfaceVariant = NaberColors.SurfaceHigh,
            onSurfaceVariant = NaberColors.TextSecondary,
            outline = NaberColors.Divider,
            error = NaberColors.Danger,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002)
        )
    }
}

private val NaberTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

/** Dokunma dalgasini (ripple) tamamen kapatan bos efekt. */
private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaberTheme(content: @Composable () -> Unit) {
    val indication: Indication = NoIndication
    CompositionLocalProvider(
        LocalIndication provides indication,
        LocalRippleConfiguration provides null
    ) {
        MaterialTheme(
            colorScheme = naberColorScheme(),
            typography = NaberTypography,
            content = content
        )
    }
}
