package com.mono.music

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Mono's palette: paper-white behind, white cards on top, near-black ink, and
 * a single orange accent borrowed from the record label in the app icon.
 */
object MonoColors {
    val Ink = Color(0xFF111111)
    val Paper = Color(0xFFF1F1F1)
    val Card = Color(0xFFFFFFFF)
    val Muted = Color(0xFF6B6B6B)
    val Line = Color(0xFFE2E2E2)
    val Chip = Color(0xFFEDEDED)
    val Accent = Color(0xFFFF6A00)

    val DarkPaper = Color(0xFF0E0E0E)
    val DarkCard = Color(0xFF1A1A1A)
    val DarkLine = Color(0xFF2A2A2A)
    val DarkMuted = Color(0xFFA0A0A0)
}

private val LightScheme = lightColorScheme(
    primary = MonoColors.Ink,
    onPrimary = Color.White,
    secondary = MonoColors.Accent,
    onSecondary = Color.White,
    background = MonoColors.Paper,
    onBackground = MonoColors.Ink,
    surface = MonoColors.Card,
    onSurface = MonoColors.Ink,
    surfaceVariant = MonoColors.Chip,
    onSurfaceVariant = MonoColors.Muted,
    outline = MonoColors.Line,
)

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = MonoColors.Ink,
    secondary = MonoColors.Accent,
    onSecondary = Color.White,
    background = MonoColors.DarkPaper,
    onBackground = Color.White,
    surface = MonoColors.DarkCard,
    onSurface = Color.White,
    surfaceVariant = MonoColors.DarkCard,
    onSurfaceVariant = MonoColors.DarkMuted,
    outline = MonoColors.DarkLine,
)

private val MonoTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MonoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = MonoTypography, content = content)
}
