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
 * Mono's palette. Ink on paper in daylight, and a set of near-blacks at night
 * that are layered rather than flat — the page sits below the cards, the cards
 * below the controls — so depth comes from value rather than from borders.
 * One orange accent, taken from the record label in the app icon.
 */
object MonoColors {
    val Accent = Color(0xFFFF6A00)

    val Paper = Color(0xFFF4F4F5)
    val Card = Color(0xFFFFFFFF)
    val Raised = Color(0xFFE9E9EC)
    val Ink = Color(0xFF111113)
    val Muted = Color(0xFF6E6E76)
    val Line = Color(0xFFE2E2E6)

    val NightPage = Color(0xFF0A0A0B)
    val NightCard = Color(0xFF151517)
    val NightRaised = Color(0xFF1F1F23)
    val NightInk = Color(0xFFF2F2F3)
    val NightMuted = Color(0xFF9A9AA0)
    val NightLine = Color(0xFF2A2A2F)
}

private val LightScheme = lightColorScheme(
    primary = MonoColors.Ink,
    onPrimary = MonoColors.Paper,
    secondary = MonoColors.Accent,
    onSecondary = Color.White,
    background = MonoColors.Paper,
    onBackground = MonoColors.Ink,
    surface = MonoColors.Card,
    onSurface = MonoColors.Ink,
    surfaceVariant = MonoColors.Raised,
    onSurfaceVariant = MonoColors.Muted,
    outline = MonoColors.Line,
)

private val DarkScheme = darkColorScheme(
    primary = MonoColors.NightInk,
    onPrimary = MonoColors.NightPage,
    secondary = MonoColors.Accent,
    onSecondary = Color.White,
    background = MonoColors.NightPage,
    onBackground = MonoColors.NightInk,
    surface = MonoColors.NightCard,
    onSurface = MonoColors.NightInk,
    surfaceVariant = MonoColors.NightRaised,
    onSurfaceVariant = MonoColors.NightMuted,
    outline = MonoColors.NightLine,
)

/**
 * Tightened from the first pass, where everything was extra-bold and a few
 * points too large: headings crowded the controls beside them and song titles
 * were truncating at six or seven characters. Weight now carries the hierarchy
 * and size stays close to the text it labels.
 */
private val MonoTypography = Typography(
    headlineLarge = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
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
