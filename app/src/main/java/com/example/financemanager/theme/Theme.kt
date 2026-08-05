package com.example.financemanager.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.financemanager.core.ThemePreference

// ============================================================================
// GLOBAL THEME STATE
// Custom components read this instead of isSystemInDarkTheme() so they
// respect the user's in-app Light/Dark/System preference.
// ============================================================================
val LocalThemeIsDark = compositionLocalOf { true }

// ============================================================================
// iOS DARK COLOR SCHEME
// Maps iOS system colors to Material 3 color scheme
// ============================================================================
private val DarkColorScheme = darkColorScheme(
    // Primary (iOS Blue)
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = iOSSecondaryBackgroundDark,
    onPrimaryContainer = iOSTextPrimaryDark,

    // Secondary
    secondary = iOSTextSecondaryDark,
    onSecondary = iOSTextPrimaryDark,
    secondaryContainer = iOSTertiaryBackgroundDark,
    onSecondaryContainer = iOSTextPrimaryDark,

    // Tertiary (iOS Green for success)
    tertiary = iOSGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0A2E1F),
    onTertiaryContainer = iOSGreen,

    // Background
    background = Color(0xFF000000),
    onBackground = iOSTextPrimaryDark,

    // Surface (cards, elevated surfaces)
    surface = Color(0xFF111111),
    onSurface = iOSTextPrimaryDark,
    surfaceVariant = iOSBorderDark,
    onSurfaceVariant = iOSTextSecondaryDark,

    // Surface tint
    surfaceTint = iOSBlue,

    // Outline (borders, separators)
    outline = iOSSeparatorDark,
    outlineVariant = Color(0xFF3F3F46),

    // Error (iOS Red)
    error = iOSRed,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = iOSTextPrimaryDark,

    // Scrim (for overlays)
    scrim = Color.Black.copy(alpha = 0.8f),

    // Inverse surface (for tooltips, etc.)
    inverseSurface = iOSTertiaryBackgroundDark,
    inverseOnSurface = iOSTextPrimaryDark,

    // Inverse primary
    inversePrimary = iOSBlue
)

// ============================================================================
// iOS LIGHT COLOR SCHEME
// Maps iOS system colors to Material 3 color scheme
// ============================================================================
private val LightColorScheme = lightColorScheme(
    // Primary (iOS Blue)
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5EA),
    onPrimaryContainer = iOSBlue,

    // Secondary
    secondary = iOSTextSecondaryLight,
    onSecondary = iOSTextPrimaryLight,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = iOSTextPrimaryLight,

    // Tertiary (iOS Green for success)
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),

    // Background
    background = Color(0xFFF9F9F9),
    onBackground = iOSTextPrimaryLight,

    // Surface (cards, elevated surfaces)
    surface = Color(0xFFFFFFFF),
    onSurface = iOSTextPrimaryLight,
    surfaceVariant = iOSSecondaryBackgroundLight,
    onSurfaceVariant = iOSTextSecondaryLight,

    // Surface tint
    surfaceTint = iOSBlue,

    // Outline (borders, separators)
    outline = iOSSeparatorLight,
    outlineVariant = iOSBorderLight,

    // Error (iOS Red)
    error = iOSRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    // Scrim (for overlays)
    scrim = Color.Black.copy(alpha = 0.8f),

    // Inverse surface (for tooltips, etc.)
    inverseSurface = iOSTextPrimaryLight,
    inverseOnSurface = iOSBackgroundLight,

    // Inverse primary
    inversePrimary = iOSBlue
)

@Composable
fun FinanceManagerTheme(
    themeMode: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
