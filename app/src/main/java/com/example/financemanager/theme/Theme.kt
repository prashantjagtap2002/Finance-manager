package com.example.financemanager.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.LocalIndication
import com.example.financemanager.ui.components.IosHighlightIndication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.example.financemanager.core.ThemePreference

val LocalThemeIsDark = compositionLocalOf { true }

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary, onPrimary = Color.White,
    primaryContainer = AppPrimarySoftLight, onPrimaryContainer = Color(0xFF4A3410),
    inversePrimary = AppPrimaryDark,
    secondary = AppTeal, onSecondary = Color.White,
    secondaryContainer = AppSurfaceVariantLight, onSecondaryContainer = AppTextPrimaryLight,
    tertiary = AppSuccess, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9EEDF), onTertiaryContainer = Color(0xFF123D25),
    background = AppBackgroundLight, onBackground = AppTextPrimaryLight,
    surface = AppSurfaceLight, onSurface = AppTextPrimaryLight,
    surfaceVariant = AppSurfaceVariantLight, onSurfaceVariant = AppTextSecondaryLight,
    surfaceContainerLowest = AppSurfaceLight,
    surfaceContainerLow = AppSurfaceLight,
    surfaceContainer = AppSurfaceLight,
    surfaceContainerHigh = AppSurfaceVariantLight,
    surfaceContainerHighest = AppSurfaceVariantLight,
    outline = AppOutlineLight, outlineVariant = AppOutlineLight,
    scrim = Color(0x66000000),
    inverseSurface = AppTextPrimaryLight, inverseOnSurface = AppBackgroundLight,
    error = AppError, onError = Color.White,
    errorContainer = Color(0xFFF9DCD7), onErrorContainer = Color(0xFF5E1F17)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark, onPrimary = AppOnAccentDark,
    primaryContainer = AppPrimarySoftDark, onPrimaryContainer = AppPrimaryDark,
    inversePrimary = AppPrimary,
    secondary = AppTealDark, onSecondary = Color(0xFF0E1720),
    secondaryContainer = AppSurfaceVariantDark, onSecondaryContainer = AppTextPrimaryDark,
    tertiary = AppSuccessDark, onTertiary = Color(0xFF0B2216),
    tertiaryContainer = Color(0xFF15291D), onTertiaryContainer = AppSuccessDark,
    background = AppBackgroundDark, onBackground = AppTextPrimaryDark,
    surface = AppSurfaceDark, onSurface = AppTextPrimaryDark,
    surfaceVariant = AppSurfaceVariantDark, onSurfaceVariant = AppTextSecondaryDark,
    surfaceContainerLowest = AppBackgroundDark,
    surfaceContainerLow = AppSurfaceDark,
    surfaceContainer = AppSurfaceDark,
    surfaceContainerHigh = AppSurfaceVariantDark,
    surfaceContainerHighest = AppSurfaceVariantDark,
    outline = AppOutlineDark, outlineVariant = AppOutlineDark,
    scrim = Color(0xAA000000),
    inverseSurface = AppTextPrimaryDark, inverseOnSurface = AppBackgroundDark,
    error = AppErrorDark, onError = Color(0xFF2A0D09),
    errorContainer = Color(0xFF3A1C17), onErrorContainer = AppErrorDark
)

// One radius family, generously rounded. Minimal UIs get their calm from
// consistency, so nothing here should be hand-rolled at a call site.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(FieldRadius),
    large = RoundedCornerShape(CompactRadius),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun FinanceManagerTheme(themeMode: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    // enableEdgeToEdge() picks status/navigation bar icon colours from the *system* setting, so
    // a user on Light while the phone is in dark mode would get white icons on a white bar.
    // The app's own theme is the authority here.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, typography = Typography, shapes = AppShapes) {
            // Every Modifier.clickable gets the iOS press highlight instead of Material's ripple.
            // A white wash is what reads on charcoal; on a light canvas it would vanish, so the
            // light theme darkens instead.
            val highlight = if (darkTheme) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
            CompositionLocalProvider(
                LocalIndication provides remember(highlight) { IosHighlightIndication(highlight) },
                content = content
            )
        }
    }
}
