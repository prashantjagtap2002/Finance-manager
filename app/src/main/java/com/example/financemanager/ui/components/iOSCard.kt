package com.example.financemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.financemanager.theme.iOSSecondaryBackgroundDark
import com.example.financemanager.theme.iOSSecondaryBackgroundLight
import com.example.financemanager.theme.iOSSeparatorDark
import com.example.financemanager.theme.iOSSeparatorLight
import com.example.financemanager.theme.iOSTertiaryBackgroundDark
import com.example.financemanager.theme.iOSTextPrimaryDark
import com.example.financemanager.theme.iOSTextPrimaryLight
import com.example.financemanager.theme.iOSRadiusMedium
import com.example.financemanager.theme.LocalThemeIsDark

/**
 * iOS-style Card component following Apple's InsetGrouped design pattern.
 *
 * Commonly used in iOS apps like Settings, Mail, and Music.
 * Provides rounded containers with subtle separators between items.
 *
 * Styles:
 * - Plain: No background, minimal borders (rarely used)
 * - Grouped: InsetGrouped style with rounded corners and subtle background (PRIMARY)
 * - Elevated: Subtle shadow (rare in iOS, use sparingly)
 */
enum class iOSCardStyle {
    Plain,      // No background, minimal borders
    Grouped,    // InsetGrouped style with rounded corners
    Elevated    // Subtle shadow (rare use)
}

@Composable
fun iOSCard(
    modifier: Modifier = Modifier,
    style: iOSCardStyle = iOSCardStyle.Grouped,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current

    val cardSpec = when (style) {
        iOSCardStyle.Plain -> Triple(Color.Transparent, Color.Transparent, 0.dp)
        iOSCardStyle.Grouped -> Triple(
            if (isDark) iOSSecondaryBackgroundDark else iOSSecondaryBackgroundLight,
            if (isDark) iOSSeparatorDark else iOSSeparatorLight,
            0.dp
        )
        iOSCardStyle.Elevated -> Triple(
            if (isDark) iOSTertiaryBackgroundDark else Color.White,
            Color.Transparent,
            2.dp
        )
    }

    val (backgroundColor, borderColor, elevation) = cardSpec

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        contentColor = if (isDark) iOSTextPrimaryDark else iOSTextPrimaryLight,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
        border = null
    ) {
        Column(content = content)
    }
}

/**
 * iOS-style list separator following Apple's InsetGrouped pattern.
 *
 * Creates a subtle horizontal divider that is inset from the left edge
 * (16dp inset) matching iOS list separator appearance.
 */
@Composable
fun iOSListSeparator(
    modifier: Modifier = Modifier
) {
    val isDark = LocalThemeIsDark.current
    val separatorColor = if (isDark) iOSSeparatorDark else iOSSeparatorLight

    HorizontalDivider(
        color = separatorColor,
        thickness = 0.5.dp,
        modifier = modifier.padding(start = 16.dp)
    )
}
