package com.example.financemanager.ui.components

import com.example.financemanager.theme.LocalThemeIsDark
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.financemanager.theme.iOSGreen

/**
 * iOS-style Toggle Switch component matching Apple's switch appearance.
 *
 * Features:
 * - White thumb when checked/unchecked
 * - Green track when checked, gray when unchecked
 * - No visible border (transparent)
 * - Matches iOS Settings app toggle appearance exactly
 */
@Composable
fun iOSToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = LocalThemeIsDark.current

    // Thumb color: white when checked, gray when unchecked
    val thumbColor = Color.White

    // Track color: green when checked, gray when unchecked
    val trackColor = if (checked) iOSGreen else
        if (isDark) Color(0xFF636366) else Color(0xFF787880).copy(alpha = 0.36f)

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = thumbColor,
            uncheckedThumbColor = thumbColor,
            checkedTrackColor = trackColor,
            uncheckedTrackColor = trackColor,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
            checkedIconColor = Color.Transparent,
            uncheckedIconColor = Color.Transparent
        )
    )
}
