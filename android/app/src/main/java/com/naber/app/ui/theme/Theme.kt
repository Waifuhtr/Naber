package com.naber.app.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Koyu tema paleti. */
object NaberColors {
    val Background = Color(0xFF0B1016)
    val Surface = Color(0xFF131B24)
    val SurfaceHigh = Color(0xFF1A242F)
    val TopBar = Color(0xFF0F161D)
    val Accent = Color(0xFF25B08B)
    val AccentDim = Color(0xFF1B7F65)
    val Bubble = Color(0xFF4C5FE8)
    val BubbleIn = Color(0xFF1C2631)
    val TextPrimary = Color(0xFFE8EDF3)
    val TextSecondary = Color(0xFF8B99A8)
    val Divider = Color(0xFF1E2934)
    val Danger = Color(0xFFE5484D)
    val Online = Color(0xFF3BD07F)
    val Warning = Color(0xFFE8A33D)
}

private val DarkColors = darkColorScheme(
    primary = NaberColors.Accent,
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
            colorScheme = DarkColors,
            typography = NaberTypography,
            content = content
        )
    }
}
