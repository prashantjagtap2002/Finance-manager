package com.example.financemanager.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.financemanager.core.ThemePreference

val LocalThemeIsDark = compositionLocalOf { true }

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary, onPrimary = Color.White,
    primaryContainer = AppPrimarySoftLight, onPrimaryContainer = Color(0xFF312C8C),
    inversePrimary = AppPrimaryDark,
    secondary = AppTeal, onSecondary = Color.White,
    secondaryContainer = AppSurfaceVariantLight, onSecondaryContainer = AppTextPrimaryLight,
    tertiary = AppSuccess, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7), onTertiaryContainer = Color(0xFF064E3B),
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
    errorContainer = Color(0xFFFFE4E9), onErrorContainer = Color(0xFF881337)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark, onPrimary = Color(0xFF13122B),
    primaryContainer = AppPrimarySoftDark, onPrimaryContainer = AppPrimaryDark,
    inversePrimary = AppPrimary,
    secondary = AppTealDark, onSecondary = Color(0xFF04211E),
    secondaryContainer = AppSurfaceVariantDark, onSecondaryContainer = AppTextPrimaryDark,
    tertiary = AppSuccessDark, onTertiary = Color(0xFF04231A),
    tertiaryContainer = Color(0xFF10281F), onTertiaryContainer = AppSuccessDark,
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
    error = AppErrorDark, onError = Color(0xFF2B0710),
    errorContainer = Color(0xFF2E1219), onErrorContainer = AppErrorDark
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
    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, typography = Typography, shapes = AppShapes, content = content)
    }
}
