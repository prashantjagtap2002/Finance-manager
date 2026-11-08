package com.example.financemanager.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.example.financemanager.theme.LocalThemeIsDark
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import com.example.financemanager.theme.iOSHeadline
import com.example.financemanager.theme.iOSLargeTitle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.DeepBackground
import com.example.financemanager.theme.TextMuted
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.TextPrimary

/**
 * Top and bottom navigation chrome.
 *
 * Both bars sit on the page background rather than on their own surface colour,
 * so the app reads as one continuous sheet with a single hairline marking the
 * edge of the scroll area. Nothing here is tinted with the accent — the accent
 * is reserved for actions and money.
 */

/**
 * [selectedIcon] is the filled glyph shown for the active tab. Changing the icon's *form*, not just
 * its colour, is how iOS marks selection: it still reads in grayscale and for colour-blind users.
 */
data class IosTabItem(
    val icon: ImageVector,
    val label: String,
    val selectedIcon: ImageVector = icon
)

/**
 * The scroll behaviour that lets a large title shrink into the bar as the list beneath it scrolls,
 * the way iOS navigation titles do. Pass it to [IosTopAppBar] and attach its
 * `nestedScrollConnection` to the screen's Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLargeTitleBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/**
 * The navigation bar for every screen.
 *
 * With a [scrollBehavior] and [isLargeTitle], the title starts large and left-aligned above the
 * content and travels up into the bar as the content scrolls, tracking the finger 1:1 and
 * settling to whichever state the fling is heading for. Without one (forms, sheets, screens that
 * don't scroll) it is the compact inline title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    isLargeTitle: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = DeepBackground,
        scrolledContainerColor = DeepBackground,
        titleContentColor = TextPrimary,
        // Neutral icons keep the bar quiet; the accent stays on the FAB.
        navigationIconContentColor = TextPrimary,
        // Tertiary grey reads as "disabled" on a light canvas; these are
        // live actions, so they sit one step up.
        actionIconContentColor = TextSecondary
    )

    if (isLargeTitle && scrollBehavior != null) {
        // LargeTopAppBar draws the title twice, large in the lower row and inline in the upper,
        // and styles each from the theme. Overriding the two slots here rather than styling the
        // Text is what lets each row keep its own size, tracking and weight.
        val base = MaterialTheme.typography
        MaterialTheme(
            typography = base.copy(
                headlineMedium = iOSLargeTitle.copy(fontWeight = FontWeight.Bold),
                titleLarge = iOSHeadline.copy(fontWeight = FontWeight.SemiBold)
            )
        ) {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = modifier,
                navigationIcon = navigationIcon,
                actions = actions,
                expandedHeight = 108.dp,
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        }
        return
    }

    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = if (isLargeTitle) 26.sp else 18.sp,
                    lineHeight = if (isLargeTitle) 32.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (isLargeTitle) (-0.6).sp else (-0.3).sp,
                    color = TextPrimary
                )
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun IosBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary
        )
    }
}

/**
 * Bottom tab bar, built as a translucent material rather than an opaque strip: the screen scrolls
 * on underneath it and shows through, blurred and slightly saturated, the way an iOS tab bar
 * does. That keeps the full height of the screen for content and tells the user there is more
 * below without a hard edge cutting the list off.
 *
 * [backdrop] is the screen content, recorded once by the caller; the bar draws the slice of it
 * that lies beneath itself. Selection is shown by a filled glyph and full-contrast colour rather
 * than a coloured pill, which keeps five tabs from turning into five badges.
 */
@Composable
fun IosTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<IosTabItem>,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null
) {
    val isDark = LocalThemeIsDark.current
    val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Without a blur behind it (API < 31) a see-through bar would put sharp text under the tab
    // labels, so the material goes nearly solid instead of illegible.
    val tint = DeepBackground.copy(alpha = if (!canBlur) 0.97f else if (isDark) 0.72f else 0.76f)
    var barTop by remember { mutableFloatStateOf(0f) }
    val blurRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val material = remember(blurRadius) { if (canBlur) frostedMaterial(blurRadius) else null }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { barTop = it.positionInRoot().y }
    ) {
        if (material != null && backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        renderEffect = material
                    }
                    // barTop is read here, in the draw phase, so the slide in and out of the bar
                    // re-aims the backdrop without recomposing anything.
                    .drawBehind { translate(top = -barTop) { drawLayer(backdrop) } }
            )
        }
        Box(Modifier.matchParentSize().background(tint))
        Column {
            HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp, top = 6.dp)
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    IosTabBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = if (selectedTab == index) tab.selectedIcon else tab.icon,
                        label = tab.label
                    )
                }
            }
        }
    }
}

/**
 * Blur plus a saturation lift, as Apple's materials do: blurring alone greys colour out, and a
 * bar that muddies the gold and green passing beneath it reads as dirty glass rather than frost.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun frostedMaterial(radius: Float): RenderEffect {
    val saturate = android.graphics.ColorMatrix().apply { setSaturation(1.6f) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(saturate)),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
    ).asComposeRenderEffect()
}

@Composable
fun IosTabBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val interactionSource = remember { MutableInteractionSource() }

    val selectedColor = TextPrimary
    val unselectedColor = TextMuted
    val tint by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(160),
        label = "tabTint"
    )

    Column(
        modifier = Modifier
            // No highlight box behind a tab, as on iOS; the glyph itself dips under the finger.
            .pressScale(interactionSource, pressedScale = 0.9f)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(23.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.sp,
                color = tint
            )
        )
    }
}
