package com.example.financemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.financemanager.theme.AppPrimaryDark
import com.example.financemanager.theme.AppTextPrimaryDark
import com.example.financemanager.theme.HeroGradientEnd
import com.example.financemanager.theme.HeroGradientStart
import com.example.financemanager.theme.LocalThemeIsDark

/**
 * The one card per screen that is allowed to be loud: brushed titanium with a gold hairline.
 *
 * It is dark in both themes, like a metal card on any table, so its contents are composed with
 * [LocalThemeIsDark] forced on. Every theme accessor inside (TextPrimary, AlertRed, BorderColor…)
 * then resolves to its dark-theme tone, which is the tone that reads on titanium — callers write
 * the same code they would for any other card.
 *
 * Use it once per screen. Its weight comes from being the exception; [IosCard] is the default.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val shape = RoundedCornerShape(22.dp)
    val start = HeroGradientStart
    val end = HeroGradientEnd
    // Only a tappable card gives under the finger; a static one shouldn't pretend to be pressable.
    val interactionSource = remember { MutableInteractionSource() }

    CompositionLocalProvider(
        LocalThemeIsDark provides true,
        LocalContentColor provides AppTextPrimaryDark
    ) {
        Column(
            modifier = modifier
                .then(if (onClick != null) Modifier.pressScale(interactionSource, pressedScale = 0.98f) else Modifier)
                // A dark card on a dark canvas casts nothing visible; on a light canvas the
                // shadow is what seats it.
                .shadow(
                    elevation = if (isDark) 0.dp else 12.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.30f)
                )
                .clip(shape)
                .background(Brush.linearGradient(listOf(start, end)))
                // A soft sheen from the top-right corner, so the metal catches light.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.maxDimension * 0.85f
                        )
                    )
                }
                .border(1.dp, AppPrimaryDark.copy(alpha = 0.35f), shape)
                .then(if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                ) else Modifier),
            content = content
        )
    }
}
