package com.example.financemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.iOSBlue
import com.example.financemanager.theme.iOSRadiusSmall

/**
 * iOS-style Badge component following Apple's design patterns.
 *
 * Used for status indicators, tags, and small labels throughout iOS.
 * Commonly seen in App Store, Messages, and system UI.
 *
 * Styles:
 * - Filled: Solid color background with white text (most common)
 * - Outlined: Border only with colored text
 * - Tinted: Semi-transparent background with colored text
 */
enum class iOSBadgeStyle {
    Filled,    // Solid color background
    Outlined,  // Border only
    Tinted     // Semi-transparent background
}

@Composable
fun iOSBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: iOSBadgeStyle = iOSBadgeStyle.Filled,
    color: Color = iOSBlue
) {
    val badgeSpec = when (style) {
        iOSBadgeStyle.Filled -> Pair(color, Color.White)
        iOSBadgeStyle.Outlined -> Pair(Color.Transparent, color)
        iOSBadgeStyle.Tinted -> Pair(color.copy(alpha = 0.15f), color)
    }

    val (backgroundColor, textColor) = badgeSpec

    val border = if (style == iOSBadgeStyle.Outlined)
        BorderStroke(1.dp, color)
    else
        null

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = border
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )
    }
}
