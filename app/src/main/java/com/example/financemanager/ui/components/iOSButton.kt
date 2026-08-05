package com.example.financemanager.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.iOSBlue
import com.example.financemanager.theme.iOSRadiusMedium
import com.example.financemanager.theme.iOSRadiusSmall
import com.example.financemanager.theme.iOSRed

/**
 * iOS-style Button component following Apple's Human Interface Guidelines.
 *
 * Supports multiple variants matching iOS button styles:
 * - Filled: Solid blue background with white text (primary action)
 * - Tinted: Semi-transparent blue background with blue text
 * - Plain: Transparent background with blue text only
 * - Destructive: Red background with white text (destructive actions)
 * - Accent: Custom color background (for special actions)
 */
enum class iOSButtonVariant {
    Filled,       // Blue background, white text
    Tinted,       // Semi-transparent blue background, blue text
    Plain,        // Text only, blue color
    Destructive,  // Red background, white text
    Accent        // Alternative color (green, orange, etc.)
}

enum class iOSButtonSize {
    Large,   // 50dp height
    Medium,  // 44dp height (standard iOS touch target)
    Small    // 34dp height
}

@Composable
fun iOSButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: iOSButtonVariant = iOSButtonVariant.Filled,
    size: iOSButtonSize = iOSButtonSize.Medium,
    enabled: Boolean = true,
    accentColor: Color = iOSBlue,  // For Accent variant
    content: @Composable RowScope.() -> Unit
) {
    val sizeSpec = when (size) {
        iOSButtonSize.Large -> Triple(
            50.dp,
            PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
        )
        iOSButtonSize.Medium -> Triple(
            44.dp,
            PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
        )
        iOSButtonSize.Small -> Triple(
            34.dp,
            PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
        )
    }

    val (height, padding, textStyle) = sizeSpec

    val (backgroundColor, contentColor) = when (variant) {
        iOSButtonVariant.Filled -> iOSBlue to Color.White
        iOSButtonVariant.Tinted -> iOSBlue.copy(alpha = 0.15f) to iOSBlue
        iOSButtonVariant.Plain -> Color.Transparent to iOSBlue
        iOSButtonVariant.Destructive -> iOSRed to Color.White
        iOSButtonVariant.Accent -> accentColor to if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = RoundedCornerShape(iOSRadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = padding
    ) {
        ProvideTextStyle(textStyle) {
            content()
        }
    }
}
