package com.naber.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Emerald = Color(0xFF0F6B5C)
private val EmeraldLight = Color(0xFF39A08B)
private val Sand = Color(0xFFF6F3EE)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFE7),
    onPrimaryContainer = Color(0xFF04281F),
    secondary = Color(0xFF4F6F66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F0EA),
    background = Sand,
    onBackground = Color(0xFF191C1B),
    surface = Color.White,
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFEDEFEC),
    onSurfaceVariant = Color(0xFF4A4F4C),
    outline = Color(0xFFBFC6C2),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF115043),
    onPrimaryContainer = Color(0xFFD5EFE7),
    secondary = Color(0xFFB2CCC3),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF171C1B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF242A28),
    onSurfaceVariant = Color(0xFFC3C9C5),
    outline = Color(0xFF3C4542),
    error = Color(0xFFF2B8B5)
)

private val NaberTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NaberTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = findActivity(view.context)
            if (activity != null) {
                @Suppress("DEPRECATION")
                activity.window.statusBarColor = colors.primary.toArgb()
                WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = false
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = NaberTypography, content = content)
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
