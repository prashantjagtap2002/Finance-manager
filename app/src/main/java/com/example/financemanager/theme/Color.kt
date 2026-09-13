package com.example.financemanager.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// PALETTE
//
// A near-monochrome canvas does the structural work; colour is spent only on
// money, state, and a single indigo accent. Every hue ships a light tone and a
// dark tone rather than one washed-out middle value, so it stays vivid on white
// and on near-black instead of going grey in one of them.
//
// Prefer the @Composable accessors at the bottom of this file in UI code — they
// resolve to the right tone for the active theme.
// ============================================================================

// --- Accent --------------------------------------------------------------
val AppPrimary = Color(0xFF4F46E5)          // indigo 600
val AppPrimaryDark = Color(0xFF818CF8)      // indigo 400
val AppPrimarySoftLight = Color(0xFFEEEFFE) // tinted chip/fill on white
val AppPrimarySoftDark = Color(0xFF1C1B33)  // tinted chip/fill on near-black
// Label colour for anything filled with the accent. The accent runs light on a
// dark canvas and deep on a light one, so this has to flip with the theme.
val AppOnAccentLight = Color(0xFFFFFFFF)
val AppOnAccentDark = Color(0xFF13122B)

// --- Money & state -------------------------------------------------------
val AppSuccess = Color(0xFF059669)          // emerald 600
val AppSuccessDark = Color(0xFF34D399)      // emerald 400
val AppError = Color(0xFFE11D48)            // rose 600
val AppErrorDark = Color(0xFFFB7185)        // rose 400
val AppWarning = Color(0xFFD97706)          // amber 600
val AppWarningDark = Color(0xFFFBBF24)      // amber 400
val AppHighlight = Color(0xFFCA8A04)        // yellow 600
val AppHighlightDark = Color(0xFFFACC15)    // yellow 400
val AppAccent = Color(0xFF7C3AED)           // violet 600
val AppAccentDark = Color(0xFFA78BFA)       // violet 400
val AppTeal = Color(0xFF0D9488)             // teal 600
val AppTealDark = Color(0xFF2DD4BF)         // teal 400

// Washes, for panels that need to signal state without shouting it.
val AppSuccessSoftLight = Color(0xFFE7F6EF)
val AppSuccessSoftDark = Color(0xFF0E2620)
val AppErrorSoftLight = Color(0xFFFDECF0)
val AppErrorSoftDark = Color(0xFF2A1218)

// --- Categorical chart palette -------------------------------------------
// Eight hues spaced far enough apart to stay tellable apart in a donut slice,
// starting from the accent so charts feel like part of the app rather than a
// separate widget. Deep tones for white, bright tones for near-black.
val ChartPaletteLight = listOf(
    Color(0xFF4F46E5), // indigo
    Color(0xFF0D9488), // teal
    Color(0xFFE11D48), // rose
    Color(0xFFD97706), // amber
    Color(0xFFC026D3), // fuchsia
    Color(0xFF0284C7), // sky
    Color(0xFF059669), // emerald
    Color(0xFF65A30D)  // lime
)
val ChartPaletteDark = listOf(
    Color(0xFF818CF8),
    Color(0xFF2DD4BF),
    Color(0xFFFB7185),
    Color(0xFFFBBF24),
    Color(0xFFE879F9),
    Color(0xFF38BDF8),
    Color(0xFF34D399),
    Color(0xFFA3E635)
)

// --- Neutrals ------------------------------------------------------------
// Light: the canvas is pulled well clear of white so a white card is read as a
// card. The earlier near-white canvas left only a 3% step against its cards,
// which flattened every screen into one grey sheet; dark mode gets a ~7% step
// between canvas and surface, and light mode needs the same room.
val AppBackgroundLight = Color(0xFFEDF0F5)
val AppSurfaceLight = Color(0xFFFFFFFF)
// Fills and chips sit a step *below* the canvas here, the way they sit a step
// above it in dark — recessed either way.
val AppSurfaceVariantLight = Color(0xFFE2E7EF)
val AppTextPrimaryLight = Color(0xFF0B0D12)
val AppTextSecondaryLight = Color(0xFF4E5666)
val AppTextTertiaryLight = Color(0xFF757D8C)
val AppOutlineLight = Color(0xFFDCE1E9)

// Dark: true near-black canvas, not navy. Cards step up in lightness only.
val AppBackgroundDark = Color(0xFF0A0A0C)
val AppSurfaceDark = Color(0xFF141517)
val AppSurfaceVariantDark = Color(0xFF1D1F22)
val AppTextPrimaryDark = Color(0xFFF5F6F7)
val AppTextSecondaryDark = Color(0xFFA2A8B2)
val AppTextTertiaryDark = Color(0xFF767C86)
val AppOutlineDark = Color(0xFF26282C)

// --- Shape & rhythm ------------------------------------------------------
// Fewer, larger radii and a wider spacing scale read as calmer than many
// small ones.
val CompactRadius = 16.dp
val FieldRadius = 12.dp
val AppSpacingSmall = 8.dp
val AppSpacingMedium = 16.dp
val AppSpacingLarge = 24.dp

// ============================================================================
// COMPATIBILITY ALIASES
// Kept so existing screens keep compiling while the visual system settles.
// ============================================================================
val iOSBlue = AppPrimary
val iOSBlueDark = AppPrimaryDark
val iOSGreen = AppSuccess
val iOSRed = AppError
val iOSOrange = AppWarning
val iOSYellow = AppHighlight
val iOSPurple = AppAccent
val iOSPink = AppError
val iOSTeal = AppTeal
val iOSIndigo = AppPrimary
val iOSBackgroundLight = AppBackgroundLight
val iOSSecondaryBackgroundLight = AppSurfaceLight
val iOSTertiaryBackgroundLight = AppSurfaceVariantLight
val iOSBackgroundDark = AppBackgroundDark
val iOSSecondaryBackgroundDark = AppSurfaceDark
val iOSTertiaryBackgroundDark = AppSurfaceVariantDark
val iOSTextPrimaryLight = AppTextPrimaryLight
val iOSTextSecondaryLight = AppTextSecondaryLight
val iOSTextTertiaryLight = AppTextTertiaryLight
val iOSTextQuaternaryLight = Color(0xFFAEB4BF)
val iOSTextPrimaryDark = AppTextPrimaryDark
val iOSTextSecondaryDark = AppTextSecondaryDark
val iOSTextTertiaryDark = AppTextTertiaryDark
val iOSTextQuaternaryDark = Color(0xFF5A5F68)
val iOSSeparatorLight = AppOutlineLight
val iOSSeparatorDark = AppOutlineDark
val iOSBorderLight = AppOutlineLight
val iOSBorderDark = AppOutlineDark
val iOSFillLight = AppSurfaceVariantLight
val iOSFillDark = AppSurfaceVariantDark
val iOSTranslucentBackgroundLight = AppBackgroundLight
val iOSTranslucentBackgroundDark = AppBackgroundDark
val iOSTranslucentBarLight = AppBackgroundLight
val iOSTranslucentBarDark = AppBackgroundDark
val iOSTranslucentCardLight = AppSurfaceLight
val iOSTranslucentCardDark = AppSurfaceDark
val iOSRadiusSmall = 10.dp
val iOSRadiusMedium = CompactRadius
val iOSRadiusLarge = 20.dp
val iOSSpacingUnit = AppSpacingSmall
val iOSSpacingSmall = 12.dp
val iOSSpacingMedium = AppSpacingMedium
val iOSSpacingLarge = 20.dp
val iOSSpacingXLarge = AppSpacingLarge

// ============================================================================
// THEME-AWARE ACCESSORS
// ============================================================================
val PrimaryViolet: Color @Composable get() = if (LocalThemeIsDark.current) AppPrimaryDark else AppPrimary
val PrimarySoft: Color @Composable get() = if (LocalThemeIsDark.current) AppPrimarySoftDark else AppPrimarySoftLight
val OnAccent: Color @Composable get() = if (LocalThemeIsDark.current) AppOnAccentDark else AppOnAccentLight
val SuccessSoft: Color @Composable get() = if (LocalThemeIsDark.current) AppSuccessSoftDark else AppSuccessSoftLight
val ErrorSoft: Color @Composable get() = if (LocalThemeIsDark.current) AppErrorSoftDark else AppErrorSoftLight
val ChartPalette: List<Color> @Composable get() = if (LocalThemeIsDark.current) ChartPaletteDark else ChartPaletteLight
val SecondaryTeal: Color @Composable get() = if (LocalThemeIsDark.current) AppTealDark else AppTeal
val AccentGreen: Color @Composable get() = if (LocalThemeIsDark.current) AppSuccessDark else AppSuccess
val AlertRed: Color @Composable get() = if (LocalThemeIsDark.current) AppErrorDark else AppError
val WarningAmber: Color @Composable get() = if (LocalThemeIsDark.current) AppWarningDark else AppWarning
val GoldAccent: Color @Composable get() = if (LocalThemeIsDark.current) AppHighlightDark else AppHighlight
val VioletAccent: Color @Composable get() = if (LocalThemeIsDark.current) AppAccentDark else AppAccent
val DeepBackground: Color @Composable get() = if (LocalThemeIsDark.current) AppBackgroundDark else AppBackgroundLight
val DarkSurface: Color @Composable get() = if (LocalThemeIsDark.current) AppSurfaceDark else AppSurfaceLight
val SubtleSurface: Color @Composable get() = if (LocalThemeIsDark.current) AppSurfaceVariantDark else AppSurfaceVariantLight
val BorderColor: Color @Composable get() = if (LocalThemeIsDark.current) AppOutlineDark else AppOutlineLight
val TextPrimary: Color @Composable get() = if (LocalThemeIsDark.current) AppTextPrimaryDark else AppTextPrimaryLight
val TextSecondary: Color @Composable get() = if (LocalThemeIsDark.current) AppTextSecondaryDark else AppTextSecondaryLight
val TextMuted: Color @Composable get() = if (LocalThemeIsDark.current) AppTextTertiaryDark else AppTextTertiaryLight

// Gradients are deliberately flat: the accent belongs on the numbers, not on
// large filled areas. Kept as aliases so older call sites still compile.
val HeroGradientStart: Color @Composable get() = DarkSurface
val HeroGradientEnd: Color @Composable get() = DarkSurface
val BankCardStart: Color @Composable get() = DarkSurface
val BankCardEnd: Color @Composable get() = DarkSurface
val CashCardStart: Color @Composable get() = DarkSurface
val CashCardEnd: Color @Composable get() = DarkSurface
val CreditCardStart: Color @Composable get() = DarkSurface
val CreditCardEnd: Color @Composable get() = DarkSurface
val WalletCardStart: Color @Composable get() = DarkSurface
val WalletCardEnd: Color @Composable get() = DarkSurface
val VioletGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val TealGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val GreenGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val CardGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
