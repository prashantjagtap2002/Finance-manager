package com.example.financemanager.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

// ============================================================================
// iOS SYSTEM COLORS
// Based on Apple Human Interface Guidelines color system
// Enhanced with richer, higher-contrast palette for premium fintech aesthetic
// ============================================================================

// --- Primary/Brand Colors (iOS System Colors) ---
val iOSBlue = Color(0xFF007AFF)           // Primary actions, links
val iOSBlueDark = Color(0xFF0A84FF)       // Dark mode variant
val iOSGreen = Color(0xFF34C759)          // Success, income, positive
val iOSRed = Color(0xFFFF3B30)            // Destructive, expenses, alerts
val iOSOrange = Color(0xFFFF9500)         // Warnings, pending
val iOSYellow = Color(0xFFFFCC00)         // Highlights
val iOSPurple = Color(0xFFAF52DE)         // Premium features
val iOSPink = Color(0xFFFF2D55)           // Accent
val iOSTeal = Color(0xFF5AC8FA)           // Secondary accent
val iOSIndigo = Color(0xFF5856D6)         // Alternative accent

// --- Background Colors (Light Mode) ---
val iOSBackgroundLight = Color(0xFFF2F2F7)           // Primary background (soft gray, not pure white)
val iOSSecondaryBackgroundLight = Color(0xFFFFFFFF)  // Card surfaces (white cards on gray bg)
val iOSTertiaryBackgroundLight = Color(0xFFFFFFFF)   // Card/surface

// --- Background Colors (Dark Mode) ---
// Premium slate-charcoal palette — NOT pure black for better readability
val iOSBackgroundDark = Color(0xFF0A0A0F)            // Primary background (rich near-black)
val iOSSecondaryBackgroundDark = Color(0xFF161620)   // Card surfaces (slightly lifted)
val iOSTertiaryBackgroundDark = Color(0xFF1E1E2A)    // Elevated cards

// --- Text Colors (Light Mode) ---
val iOSTextPrimaryLight = Color(0xFF1C1C1E)          // Primary labels (slightly softer than black)
val iOSTextSecondaryLight = Color(0xFF636366)        // Secondary labels (improved contrast)
val iOSTextTertiaryLight = Color(0xFF8E8E93)          // Tertiary labels
val iOSTextQuaternaryLight = Color(0xFF999999)       // Quaternary labels

// --- Text Colors (Dark Mode) ---
val iOSTextPrimaryDark = Color(0xFFF5F5F7)            // Primary labels (crisp off-white)
val iOSTextSecondaryDark = Color(0xFF98989D)          // Secondary labels (brighter for readability)
val iOSTextTertiaryDark = Color(0xFF636366)           // Tertiary labels
val iOSTextQuaternaryDark = Color(0xFF48484A)         // Quaternary labels

// --- Separator/Border Colors ---
val iOSSeparatorLight = Color(0xFFD1D1D6)             // Subtle separation
val iOSSeparatorDark = Color(0xFF2C2C34)              // Dark mode separator
val iOSBorderLight = Color(0xFFE5E5EA)               // Card borders
val iOSBorderDark = Color(0xFF2C2C34)                // Dark mode borders

// --- Fill Colors (for inputs, search bars) ---
val iOSFillLight = Color(0xFFE5E5EA)                 // Search bars, inputs
val iOSFillDark = Color(0xFF1C1C24)                  // Dark mode fills

// --- Translucent Colors (for glass/blur effects) ---
val iOSTranslucentBackgroundLight = Color(0xE6FFFFFF)
val iOSTranslucentBackgroundDark = Color(0xE60A0A0F)
val iOSTranslucentBarLight = Color(0xF2F2F2F7)       // Tab bar, navigation (light)
val iOSTranslucentBarDark = Color(0xF0161620)         // Tab bar, navigation (dark)
val iOSTranslucentCardLight = Color(0xCCFFFFFF)       // Card blur (light)
val iOSTranslucentCardDark = Color(0xCC161620)        // Card blur (dark)

// ============================================================================
// iOS SHAPE & SPACING CONSTANTS
// ============================================================================

// iOS Corner Radius (matches iOS design guidelines)
val iOSRadiusSmall = 10.dp      // Buttons, small inputs
val iOSRadiusMedium = 14.dp     // Cards, grouped lists (InsetGrouped)
val iOSRadiusLarge = 20.dp      // Large cards, modals

// iOS Spacing (8pt grid system)
val iOSSpacingUnit = 8.dp       // Base spacing unit
val iOSSpacingSmall = 12.dp     // Tight spacing
val iOSSpacingMedium = 16.dp    // Standard spacing
val iOSSpacingLarge = 20.dp     // Section spacing
val iOSSpacingXLarge = 24.dp    // Extra large spacing

// ============================================================================
// DYNAMIC THEME-AWARE COLORS
// These composable properties respect the user's in-app theme selection
// by reading LocalThemeIsDark from the composition.
// ============================================================================

val PrimaryViolet: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFFFFFFF) else Color(0xFF111111)
val SecondaryTeal: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF444444) else Color(0xFFE5E5EA)
val AccentGreen: Color @Composable get() = Color(0xFF2ECC71)
val AlertRed: Color @Composable get() = Color(0xFFFF6B6B)
val WarningAmber: Color @Composable get() = Color(0xFFF1C40F)
val GoldAccent: Color @Composable get() = iOSYellow
val DeepBackground: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF000000) else Color(0xFFF9F9F9)
val DarkSurface: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF111111) else Color(0xFFFFFFFF)
val BorderColor: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF222222) else Color(0xFFEEEEEE)
val TextPrimary: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFFFFFFF) else Color(0xFF111111)
val TextSecondary: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFAAAAAA) else Color(0xFF888888)
val TextMuted: Color @Composable get() = if (LocalThemeIsDark.current) iOSTextTertiaryDark else iOSTextTertiaryLight

// Legacy gradient colors (premium fintech card palette)
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

// Legacy gradient lists
val VioletGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val TealGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val GreenGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
val CardGradient: List<Color> @Composable get() = listOf(DarkSurface, DarkSurface)
