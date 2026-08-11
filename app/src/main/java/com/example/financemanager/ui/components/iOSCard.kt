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
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.CompactRadius
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.LocalThemeIsDark
import com.example.financemanager.theme.SubtleSurface
import com.example.financemanager.theme.TextPrimary

/**
 * The single container primitive for the app.
 *
 * Cards are flat: one surface colour, one hairline border, one radius. Depth
 * comes from the border and the step in lightness against the canvas, never
 * from shadows — a drop shadow on a near-black background just reads as mud.
 *
 * Styles:
 * - Plain: no chrome at all, for grouping without a visible box
 * - Grouped: the default surface card
 * - Elevated: same surface, slightly stronger border for the one card on a
 *   screen that should pull focus
 */
enum class iOSCardStyle {
    Plain,
    Grouped,
    Elevated
}

@Composable
fun iOSCard(
    modifier: Modifier = Modifier,
    style: iOSCardStyle = iOSCardStyle.Grouped,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val surface = DarkSurface
    val hairline = BorderColor

    val (backgroundColor, borderColor) = when (style) {
        iOSCardStyle.Plain -> Color.Transparent to Color.Transparent
        iOSCardStyle.Grouped -> surface to hairline
        // Emphasis reads as "lighter" on a dark canvas and as "more defined"
        // on a light one, so it is not the same move in both themes.
        iOSCardStyle.Elevated ->
            if (isDark) SubtleSurface to hairline
            else surface to Color(0xFFD6DAE1)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CompactRadius),
        color = backgroundColor,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (borderColor == Color.Transparent) null else BorderStroke(1.dp, borderColor)
    ) {
        Column(content = content)
    }
}

/**
 * Hairline divider between rows inside a card, inset to line up with the text
 * column rather than cutting the card edge to edge.
 */
@Composable
fun iOSListSeparator(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        color = BorderColor,
        thickness = 1.dp,
        modifier = modifier.padding(start = 16.dp)
    )
}
