package com.example.financemanager.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the translucent tab bar floating over the bottom of a top-level screen, including
 * the system navigation bar beneath it. Zero when no tab bar is showing.
 */
val LocalTabBarHeight = compositionLocalOf { 0.dp }

/**
 * Content insets for a screen that sits under the tab bar. Pass to `Scaffold(contentWindowInsets)`
 * so the FAB and snackbars float above the bar, then apply the bottom of the Scaffold's padding as
 * the list's `contentPadding` rather than as a `Modifier.padding` — the list has to run *under* the
 * bar for the material to have anything to show, while its last row still scrolls clear of it.
 */
@Composable
fun tabBarAwareInsets(): WindowInsets {
    val bar: Dp = LocalTabBarHeight.current
    return ScaffoldDefaults.contentWindowInsets.union(WindowInsets(bottom = bar))
}
