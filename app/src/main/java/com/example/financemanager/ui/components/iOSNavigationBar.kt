package com.example.financemanager.ui.components

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.financemanager.theme.TextPrimary

/**
 * Top and bottom navigation chrome.
 *
 * Both bars sit on the page background rather than on their own surface colour,
 * so the app reads as one continuous sheet with a single hairline marking the
 * edge of the scroll area. Nothing here is tinted with the accent — the accent
 * is reserved for actions and money.
 */

data class iOSTabItem(
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun iOSTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    isLargeTitle: Boolean = true
) {
    TopAppBar(
        title = {
            Text(
                title,
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DeepBackground,
            scrolledContainerColor = DeepBackground,
            titleContentColor = TextPrimary,
            // Neutral icons keep the bar quiet; the accent stays on the FAB.
            navigationIconContentColor = TextPrimary,
            actionIconContentColor = TextMuted
        )
    )
}

@Composable
fun iOSBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary
        )
    }
}

/**
 * Bottom tab bar. Selection is shown by weight and full-contrast colour rather
 * than a coloured pill, which keeps five tabs from turning into five badges.
 */
@Composable
fun iOSTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<iOSTabItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DeepBackground,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
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
                    iOSTabBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = tab.icon,
                        label = tab.label
                    )
                }
            }
        }
    }
}

@Composable
fun iOSTabBarItem(
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
