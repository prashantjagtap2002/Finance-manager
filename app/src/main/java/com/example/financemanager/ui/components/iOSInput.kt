package com.example.financemanager.ui.components

import com.example.financemanager.theme.LocalThemeIsDark
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.iOSBlue
import com.example.financemanager.theme.iOSFillDark
import com.example.financemanager.theme.iOSFillLight
import com.example.financemanager.theme.iOSRadiusSmall
import com.example.financemanager.theme.iOSTextPrimaryDark
import com.example.financemanager.theme.iOSTextPrimaryLight

/**
 * iOS-style Input/TextField component following Apple's design patterns.
 *
 * Used throughout iOS for search bars, form inputs, and text entry.
 * Features:
 * - Rounded corners (10dp) matching iOS input fields
 * - Gray background (iOSFill) for search/text fields
 * - Blue focus border
 * - Optional leading/trailing icons
 * - Search field variant
 */
@Composable
fun iOSInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isSearchField: Boolean = false,
    leadingIcon: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val isDark = LocalThemeIsDark.current
    val backgroundColor = if (isDark) iOSFillDark else iOSFillLight

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = Color(0xFF8E8E93)) },
        leadingIcon = leadingIcon?.let {
            {
                Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                    leadingIcon()
                }
            }
        },
        trailingIcon = trailingIcon?.let {
            {
                Row(modifier = Modifier.padding(start = 8.dp, end = 16.dp)) {
                    trailingIcon()
                }
            }
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(iOSRadiusSmall),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = iOSBlue,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = backgroundColor,
            unfocusedContainerColor = backgroundColor,
            cursorColor = iOSBlue,
            focusedTextColor = if (isDark) iOSTextPrimaryDark else iOSTextPrimaryLight,
            unfocusedTextColor = if (isDark) iOSTextPrimaryDark else iOSTextPrimaryLight,
            focusedPlaceholderColor = Color(0xFF8E8E93),
            unfocusedPlaceholderColor = Color(0xFF8E8E93)
        ),
        textStyle = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal
        ),
        visualTransformation = visualTransformation
    )
}
