package com.dadsvictory.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dadsvictory.data.prefs.ThemeMode

/**
 * Warm, masculine, calm. Deep navy at night, warm neutral by day — nothing
 * clinical, nothing childish, and an amber accent taken from the sunrise in the
 * app's logo.
 *
 * Contrast was chosen to clear WCAG AA for body text in both schemes, and a high
 * contrast switch in Settings pushes it further for anyone who needs it.
 */

private val Amber = Color(0xFFE9A55C)
private val AmberDeep = Color(0xFF9C5A12)
private val Steel = Color(0xFF7FA3C9)
private val SteelDeep = Color(0xFF2A4E75)
private val Pine = Color(0xFF7FB894)
private val PineDeep = Color(0xFF215B39)
private val Clay = Color(0xFFE58A7B)
private val ClayDeep = Color(0xFF8E2F22)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1704),
    primaryContainer = Color(0xFF4A2E0C),
    onPrimaryContainer = Color(0xFFFFDCB4),
    secondary = Steel,
    onSecondary = Color(0xFF0C1B2A),
    secondaryContainer = Color(0xFF23384F),
    onSecondaryContainer = Color(0xFFD3E4F7),
    tertiary = Pine,
    onTertiary = Color(0xFF06281A),
    tertiaryContainer = Color(0xFF1E4632),
    onTertiaryContainer = Color(0xFFC5EBD4),
    error = Clay,
    onError = Color(0xFF3A0A05),
    errorContainer = Color(0xFF5F1B12),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0B1420),
    onBackground = Color(0xFFE6ECF3),
    surface = Color(0xFF0B1420),
    onSurface = Color(0xFFE6ECF3),
    surfaceVariant = Color(0xFF1A2839),
    onSurfaceVariant = Color(0xFFC0CCDA),
    surfaceContainer = Color(0xFF121D2C),
    surfaceContainerHigh = Color(0xFF182534),
    surfaceContainerHighest = Color(0xFF1F2D3E),
    outline = Color(0xFF7C8B9C),
    outlineVariant = Color(0xFF33455A),
    inverseSurface = Color(0xFFE6ECF3),
    inverseOnSurface = Color(0xFF0B1420),
)

private val LightScheme = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0BC),
    onPrimaryContainer = Color(0xFF331C00),
    secondary = SteelDeep,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E4F7),
    onSecondaryContainer = Color(0xFF0C1B2A),
    tertiary = PineDeep,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5EBD4),
    onTertiaryContainer = Color(0xFF04271A),
    error = ClayDeep,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410100),
    background = Color(0xFFFAF6F1),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFAF6F1),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFF0E8DE),
    onSurfaceVariant = Color(0xFF4C463D),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF5EFE7),
    surfaceContainerHighest = Color(0xFFEFE7DC),
    outline = Color(0xFF7D7668),
    outlineVariant = Color(0xFFD8CFC2),
    inverseSurface = Color(0xFF302E2A),
    inverseOnSurface = Color(0xFFF5EFE7),
)

/** Pushed further apart for anyone who needs more separation than AA. */
private val DarkHighContrast = DarkScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFF0D1621),
    surfaceContainerHigh = Color(0xFF14202E),
    surfaceVariant = Color(0xFF16232F),
    onSurfaceVariant = Color(0xFFE4EBF2),
    outline = Color(0xFFB9C6D4),
    primary = Color(0xFFFFC17E),
)

private val LightHighContrast = LightScheme.copy(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2ECE4),
    onSurfaceVariant = Color(0xFF2B2721),
    outline = Color(0xFF4A443B),
    primary = Color(0xFF6E3F08),
    secondary = Color(0xFF16334F),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        // Body text a notch larger than the Material default: this is an app for
        // reading a sentence of encouragement, often without reading glasses to hand.
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

/** The giant streak number on the dashboard. */
val StreakNumberStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 96.sp,
    lineHeight = 100.sp,
    letterSpacing = (-4).sp,
)

/** Honours the "reduce motion" setting for every animation in the app. */
val LocalReducedMotion = staticCompositionLocalOf { false }

val CardCorner = 20.dp
val ScreenPadding = 16.dp

@Composable
fun DadsVictoryTheme(
    themeMode: ThemeMode,
    dynamicColour: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val scheme = when {
        // Dynamic colour is offered but off by default: the sunrise palette is
        // part of what this app is, and high contrast always wins over it.
        highContrast -> if (dark) DarkHighContrast else LightHighContrast
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
