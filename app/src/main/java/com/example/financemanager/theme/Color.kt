package com.example.financemanager.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// PALETTE — "Titanium & Gold"
//
// Charcoal and brushed metal with one warm gold accent: the look of a metal
// card rather than a banking portal. Chosen by the user on 2026-10-10 from four
// mocked-up directions, after the earlier indigo/teal set read as "made for a
// kid" and a toned-down sapphire still read as flat and generic.
//
// Colour is spent on three things only: the gold accent, money state (sage in,
// coral out), and the titanium hero card. Everything else is warm neutral —
// greys with a slight yellow bias so they sit with the gold instead of against
// it. Light tones clear 4.5:1 on a white card; dark tones clear it on #161618.
//
// Prefer the @Composable accessors at the bottom of this file in UI code — they
// resolve to the right tone for the active theme.
// ============================================================================

// --- Accent --------------------------------------------------------------
val AppPrimary = Color(0xFF94681F)          // antique gold, deep enough for white
val AppPrimaryDark = Color(0xFFE3B566)      // champagne gold on charcoal
val AppPrimarySoftLight = Color(0xFFF4ECDD) // tinted chip/fill on white
val AppPrimarySoftDark = Color(0xFF2A2418)  // tinted chip/fill on charcoal
// Label colour for anything filled with the accent. The accent runs light on a
// dark canvas and deep on a light one, so this has to flip with the theme.
val AppOnAccentLight = Color(0xFFFFFFFF)
val AppOnAccentDark = Color(0xFF1A1305)

// --- Money & state -------------------------------------------------------
// Money in and money out are the two colours read most often and at the smallest sizes, so they
// carry full saturation: the earlier pastel pair (#7FD3A0 / #F08A7E on dark) washed out on a phone
// screen in daylight. Light values hold >= 4.7:1 against white for text.
val AppSuccess = Color(0xFF0F8043)          // emerald
val AppSuccessDark = Color(0xFF36D07F)
val AppError = Color(0xFFD93025)            // vermilion
val AppErrorDark = Color(0xFFFF5B4F)
val AppWarning = Color(0xFFA0601A)          // amber
val AppWarningDark = Color(0xFFEBA75C)
val AppHighlight = Color(0xFF8F6A1E)        // gold leaf
val AppHighlightDark = Color(0xFFF0CF8A)
val AppAccent = Color(0xFF7E5A9E)           // mauve
val AppAccentDark = Color(0xFFC9A0DC)
// The secondary hue, and the one with the most call sites after the accent.
// A cool steel blue: the one cold note against all the warm metal, so it
// separates without competing with the gold.
val AppTeal = Color(0xFF4F6E8A)             // steel
val AppTealDark = Color(0xFF9BB4C9)

// Washes, for panels that need to signal state without shouting it.
val AppSuccessSoftLight = Color(0xFFDCF2E4)
val AppSuccessSoftDark = Color(0xFF0E2A1A)
val AppErrorSoftLight = Color(0xFFFCE4E1)
val AppErrorSoftDark = Color(0xFF341512)

// --- Hero card -----------------------------------------------------------
// Brushed titanium, dark in BOTH themes — a metal card on a light table is
// still a dark card. HeroCard draws its contents with the dark-theme tokens for
// that reason. The light-theme ends are a touch deeper so the card holds its
// weight against a pale canvas.
val AppHeroStartLight = Color(0xFF1B1C1F)
val AppHeroEndLight = Color(0xFF3A3C41)
val AppHeroStartDark = Color(0xFF26272B)
val AppHeroEndDark = Color(0xFF3A3C41)

// --- Categorical chart palette -------------------------------------------
// Eight hues at matched weight, led by the gold so charts feel like part of the
// app: gold, steel, mauve, sage, coral, sand, slate, teal. Deep tones for
// white, light tones for charcoal.
val ChartPaletteLight = listOf(
    Color(0xFF94681F), // gold
    Color(0xFF4F6E8A), // steel
    Color(0xFF7E5A9E), // mauve
    Color(0xFF27794C), // sage
    Color(0xFFB9483A), // coral
    Color(0xFF9C8457), // sand
    Color(0xFF5F6672), // slate
    Color(0xFF2F7F79)  // teal
)
val ChartPaletteDark = listOf(
    Color(0xFFE3B566),
    Color(0xFF9BB4C9),
    Color(0xFFC9A0DC),
    Color(0xFF7FD3A0),
    Color(0xFFF08A7E),
    Color(0xFFD9C7A3),
    Color(0xFF9AA1AC),
    Color(0xFF7EC4BE)
)

// --- Neutrals ------------------------------------------------------------
// Warm greys throughout. Light: the canvas is pulled well clear of white so a
// white card reads as a card (a near-white canvas once left a 3% step and
// flattened every screen); fills and chips sit a step *below* the canvas, the
// way they sit a step above it in dark — recessed either way.
val AppBackgroundLight = Color(0xFFF2F1EE)
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceVariantLight = Color(0xFFE8E6E1)
val AppTextPrimaryLight = Color(0xFF121212)
val AppTextSecondaryLight = Color(0xFF55534E)
val AppTextTertiaryLight = Color(0xFF6F6C65)
val AppOutlineLight = Color(0xFFE0DDD6)

// Dark: charcoal, not navy. Cards step up in lightness only.
val AppBackgroundDark = Color(0xFF0C0C0E)
val AppSurfaceDark = Color(0xFF161618)
val AppSurfaceVariantDark = Color(0xFF202023)
val AppTextPrimaryDark = Color(0xFFF4F2EE)
val AppTextSecondaryDark = Color(0xFFABA8A1)
val AppTextTertiaryDark = Color(0xFF8E8B84)
val AppOutlineDark = Color(0xFF26262A)

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
val iOSTextQuaternaryLight = Color(0xFFB3B0A8)
val iOSTextPrimaryDark = AppTextPrimaryDark
val iOSTextSecondaryDark = AppTextSecondaryDark
val iOSTextTertiaryDark = AppTextTertiaryDark
val iOSTextQuaternaryDark = Color(0xFF5E5C57)
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

// --- User-chosen colours (categories, goals) -------------------------------
// Category and goal colours are stored as hex strings — seeded from Material's
// bright 500 swatches, picked by the user, or arriving via sync — so they can't
// be fixed in the palette above. Instead they're re-toned at draw time: the hue
// survives, so categories stay tellable apart, but lightness is pinned and
// chroma capped to sit alongside the rest of the palette. That covers rows that
// already exist without a migration. Unparseable input falls back to a neutral
// rather than throwing, which parseColor alone would do mid-composition.

/** The mid tone of a stored colour: icons, dots, bars, filled selections. */
@Composable
fun paletteTone(hex: String?): Color {
    val dark = LocalThemeIsDark.current
    return remember(hex, dark) { retone(hex, dark, wash = false) }
}

/** A faint tint of a stored colour for containers that carry [TextPrimary]. */
@Composable
fun paletteWash(hex: String?): Color {
    val dark = LocalThemeIsDark.current
    return remember(hex, dark) { retone(hex, dark, wash = true) }
}

private fun retone(hex: String?, dark: Boolean, wash: Boolean): Color {
    val argb = try {
        hex?.let { android.graphics.Color.parseColor(it) }
    } catch (e: IllegalArgumentException) {
        null
    }
    val (_, c, h) = if (argb == null) Triple(0.0, 0.0, 0.0) else srgbToOklch(argb)
    val (targetL, maxC) = when {
        wash && dark -> 0.26 to 0.04
        wash -> 0.94 to 0.035
        dark -> 0.76 to 0.10
        else -> 0.53 to 0.11
    }
    // Greys keep their low chroma; only loud colours get pulled in.
    return oklchToColor(targetL, minOf(c, maxC), h)
}

private fun srgbToOklch(argb: Int): Triple<Double, Double, Double> {
    fun lin(v: Int): Double {
        val x = v / 255.0
        return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    }
    val r = lin((argb shr 16) and 0xFF)
    val g = lin((argb shr 8) and 0xFF)
    val b = lin(argb and 0xFF)
    val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return Triple(okL, Math.hypot(okA, okB), Math.atan2(okB, okA))
}

private fun oklchToColor(okL: Double, chroma: Double, hueRad: Double): Color {
    val a = chroma * Math.cos(hueRad)
    val b = chroma * Math.sin(hueRad)
    val l = (okL + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
    val m = (okL - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
    val s = (okL - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
    fun enc(x: Double): Float {
        val v = x.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) 12.92 * v else 1.055 * Math.pow(v, 1 / 2.4) - 0.055).toFloat()
    }
    return Color(
        red = enc(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        green = enc(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        blue = enc(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
    )
}

// The hero gradient is real (see HeroCard). The rest stay flat: one metal card
// per screen is a feature, five of them is a toy. Kept as aliases so older call
// sites still compile.
val HeroGradientStart: Color @Composable get() = if (LocalThemeIsDark.current) AppHeroStartDark else AppHeroStartLight
val HeroGradientEnd: Color @Composable get() = if (LocalThemeIsDark.current) AppHeroEndDark else AppHeroEndLight
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
