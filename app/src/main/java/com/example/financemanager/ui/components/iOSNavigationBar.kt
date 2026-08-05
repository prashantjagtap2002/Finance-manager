package com.example.financemanager.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.iOSBlue
import com.example.financemanager.theme.iOSTranslucentBarDark
import com.example.financemanager.theme.iOSTranslucentBarLight
import com.example.financemanager.theme.iOSTextPrimaryDark
import com.example.financemanager.theme.iOSTextPrimaryLight
import com.example.financemanager.theme.iOSTitle1
import com.example.financemanager.theme.LocalThemeIsDark

/**
 * iOS-style Navigation Bar components following Apple's design patterns.
 *
 * Includes:
 * - iOSTopAppBar: Large title navigation bar (collapsible)
 * - iOSTabBar: Bottom tab bar with translucent background and blur effect
 * - iOSTabBarItem: Individual tab item with icon and label
 */

// Data class for tab bar items
data class iOSTabItem(
    val icon: ImageVector,
    val label: String
)

/**
 * iOS-style Large Title Top App Bar.
 *
 * Matches iOS navigation bars with:
 * - Large bold title (28sp Title 1 style)
 * - Transparent/translucent background
 * - Blue navigation icons
 * - Optional back button and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun iOSTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    isLargeTitle: Boolean = true
) {
    val isDark = LocalThemeIsDark.current

    TopAppBar(
        title = {
            Text(
                title,
                style = if (isLargeTitle)
                    iOSTitle1.copy(
                        color = if (isDark) iOSTextPrimaryDark else iOSTextPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )
                else
                    TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) iOSTextPrimaryDark else iOSTextPrimaryLight
                    )
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            navigationIconContentColor = iOSBlue,
            actionIconContentColor = iOSBlue
        )
    )
}

/**
 * iOS-style navigation icon (back button).
 * Pre-configured with chevron-left icon and iOS styling.
 */
@Composable
fun iOSBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = iOSBlue
        )
    }
}

/**
 * iOS-style Tab Bar component.
 *
 * Features:
 * - Translucent background with glass effect
 * - SF Symbols-style icons
 * - Blue tint when selected
 * - Gray color when unselected
 * - 5 tabs maximum (iOS standard)
 */
@Composable
fun iOSTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<iOSTabItem>,
    modifier: Modifier = Modifier
) {
    val isDark = LocalThemeIsDark.current
    val backgroundColor = if (isDark)
        Color(0xFF000000)
    else
        Color(0xFFFFFFFF)

    Surface(
        modifier = modifier,
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Column {
            androidx.compose.material3.HorizontalDivider(
                color = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE),
                thickness = 1.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
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

/**
 * iOS-style Tab Bar Item.
 *
 * Individual tab item with:
 * - Icon (24dp) that changes color based on selection
 * - Label below icon
 * - Blue when selected, gray when unselected
 */
@Composable
fun iOSTabBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val isDark = LocalThemeIsDark.current
    val interactionSource = remember { MutableInteractionSource() }

    val selectedColor = if (isDark) Color.White else Color(0xFF111111)
    val unselectedColor = Color(0xFF888888)

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
            tint = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) selectedColor else unselectedColor
            )
        )
    }
}
