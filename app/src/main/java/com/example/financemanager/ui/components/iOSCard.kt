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
 * One surface colour, one hairline border, one radius. How depth is signalled
 * differs by theme, because the same move does not work in both: on a dark
 * canvas the card steps up in lightness and a drop shadow would read as mud, so
 * it stays flat; on a light canvas a white card needs a soft shadow to separate
 * from the page at all.
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
            else surface to hairline
    }

    // Light mode only: a shadow soft enough to lift the card without announcing
    // itself. Plain draws no box, so it casts nothing either.
    val shadow = when {
        isDark -> 0.dp
        style == iOSCardStyle.Plain -> 0.dp
        style == iOSCardStyle.Elevated -> 4.dp
        else -> 2.dp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CompactRadius),
        color = backgroundColor,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        shadowElevation = shadow,
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
