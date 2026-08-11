package com.example.financemanager.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// iOS TYPOGRAPHY SYSTEM
// Based on Apple's SF Pro font and text hierarchy
// Large Title → Title 1/2/3 → Headline → Body → Callout → Subhead → Footnote
// ============================================================================

// iOS Large Title - 34sp Bold (used for hero sections, large navigation titles)
val iOSLargeTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-1.0).sp
)

// iOS Title 1 - 28sp Bold (used for screen titles)
val iOSTitle1 = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.7).sp
)

// iOS Title 2 - 22sp Bold (used for section headers)
val iOSTitle2 = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.45).sp
)

// iOS Title 3 - 20sp Semibold (used for subsection headers)
val iOSTitle3 = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.35).sp
)

// iOS Headline - 17sp Semibold (used for emphasis)
val iOSHeadline = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.43).sp
)

// iOS Body - 17sp Regular (primary text - same size as Headline but regular weight)
val iOSBody = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.43).sp
)

// iOS Callout - 16sp Regular (secondary body text, used in lists)
val iOSCallout = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    letterSpacing = (-0.32).sp
)

// iOS Subhead - 15sp Regular (tertiary text)
val iOSSubhead = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.24).sp
)

// iOS Footnote - 13sp Regular (captions, metadata)
val iOSFootnote = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.08).sp
)

// iOS Caption 1 - 12sp Regular (tiny labels)
val iOSCaption1 = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.1.sp
)

// iOS Caption 2 - 11sp Regular (smallest labels)
// Section labels: small, medium weight, slightly tracked out. The one place
// where letter-spacing goes positive.
val iOSCaption2 = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.4.sp
)

// ============================================================================
// MATERIAL 3 TYPOGRAPHY MAPPING
// Maps iOS typography to Material 3 Typography slots
// ============================================================================

val Typography = Typography(
    // Large Title (for hero sections)
    displayLarge = iOSLargeTitle,

    // Title 1 (screen titles)
    displayMedium = iOSTitle1,

    // Title 2 (section headers)
    displaySmall = iOSTitle2,

    // Title 3 (subsection headers)
    headlineLarge = iOSTitle3,

    // Headline (emphasis)
    headlineMedium = iOSHeadline,
    headlineSmall = iOSHeadline.copy(fontWeight = FontWeight.SemiBold),

    // Title (for navigation, tab bar labels)
    titleLarge = iOSHeadline.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = iOSCallout.copy(fontWeight = FontWeight.Medium),
    titleSmall = iOSSubhead,

    // Body (primary text)
    bodyLarge = iOSBody,
    bodyMedium = iOSCallout,
    bodySmall = iOSSubhead,

    // Label (buttons, badges, small UI elements)
    labelLarge = iOSCallout.copy(fontWeight = FontWeight.Medium),
    labelMedium = iOSFootnote,
    labelSmall = iOSCaption1
)

// ============================================================================
// IOS TEXT STYLES COMPOSABLE
// Convenience functions for accessing iOS text styles in composables
// ============================================================================

@Composable
fun iOSTextStyle(
    variant: String = "body"
): TextStyle {
    return when (variant) {
        "largeTitle" -> iOSLargeTitle
        "title1" -> iOSTitle1
        "title2" -> iOSTitle2
        "title3" -> iOSTitle3
        "headline" -> iOSHeadline
        "body" -> iOSBody
        "callout" -> iOSCallout
        "subhead" -> iOSSubhead
        "footnote" -> iOSFootnote
        "caption1" -> iOSCaption1
        "caption2" -> iOSCaption2
        else -> iOSBody
    }
}
