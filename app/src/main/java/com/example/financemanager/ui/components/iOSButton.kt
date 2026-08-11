package com.example.financemanager.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.FieldRadius
import com.example.financemanager.theme.OnAccent
import com.example.financemanager.theme.PrimarySoft
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.TextPrimary

/**
 * Button variants, ordered by how loud they are. A screen should carry at most
 * one Filled button; everything else steps down to Tinted, Outlined or Plain.
 *
 * - Filled: solid accent, the single primary action
 * - Tinted: accent on a soft accent wash
 * - Outlined: hairline border only, neutral text
 * - Plain: text only
 * - Destructive: solid red
 * - Accent: caller-supplied colour, for category-tinted actions
 */
enum class iOSButtonVariant {
    Filled,
    Tinted,
    Outlined,
    Plain,
    Destructive,
    Accent
}

enum class iOSButtonSize {
    Large,   // 52dp
    Medium,  // 44dp — standard touch target
    Small    // 34dp
}

@Composable
fun iOSButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: iOSButtonVariant = iOSButtonVariant.Filled,
    size: iOSButtonSize = iOSButtonSize.Medium,
    enabled: Boolean = true,
    accentColor: Color = PrimaryViolet,
    content: @Composable RowScope.() -> Unit
) {
    val sizeSpec = when (size) {
        iOSButtonSize.Large -> Triple(
            52.dp,
            PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
        )
        iOSButtonSize.Medium -> Triple(
            44.dp,
            PaddingValues(horizontal = 18.dp, vertical = 11.dp),
            TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp)
        )
        iOSButtonSize.Small -> Triple(
            34.dp,
            PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
        )
    }

    val (height, padding, textStyle) = sizeSpec
    val accent = PrimaryViolet
    val onFilled = OnAccent

    val (backgroundColor, contentColor) = when (variant) {
        iOSButtonVariant.Filled -> accent to onFilled
        iOSButtonVariant.Tinted -> PrimarySoft to accent
        iOSButtonVariant.Outlined -> Color.Transparent to TextPrimary
        iOSButtonVariant.Plain -> Color.Transparent to accent
        iOSButtonVariant.Destructive -> AlertRed to onFilled
        iOSButtonVariant.Accent -> accentColor to
            if (accentColor.luminance() > 0.45f) Color(0xFF0C0E12) else Color.White
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = RoundedCornerShape(FieldRadius),
        border = if (variant == iOSButtonVariant.Outlined) BorderStroke(1.dp, BorderColor) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = backgroundColor.alpha * 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.38f)
        ),
        contentPadding = padding
    ) {
        ProvideTextStyle(textStyle) {
            content()
        }
    }
}
