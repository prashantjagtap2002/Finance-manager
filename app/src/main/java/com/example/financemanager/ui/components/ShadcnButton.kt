package com.example.financemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ShadcnButtonVariant { Primary, Secondary, Destructive, Outline, Ghost }
enum class ShadcnButtonSize { Small, Default, Large }

/**
 * shadcn/ui-style button for Jetpack Compose.
 *
 * Mirrors shadcn variants (default/secondary/destructive/outline/ghost) and
 * sizes (sm/default/lg) with a rounded-md shape and tight padding. Colors are
 * sourced from the MaterialTheme color scheme which is mapped to shadcn's
 * Zinc tokens in [com.example.financemanager.theme.FinanceManagerTheme].
 */
@Composable
fun ShadcnButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.Primary,
    size: ShadcnButtonSize = ShadcnButtonSize.Default,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp) // shadcn rounded-md
    val (height, padding) = when (size) {
        ShadcnButtonSize.Small -> 36.dp to PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ShadcnButtonSize.Default -> 40.dp to PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ShadcnButtonSize.Large -> 44.dp to PaddingValues(horizontal = 32.dp, vertical = 8.dp)
    }
    val heightModifier = Modifier.height(height)

    when (variant) {
        ShadcnButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = modifier.then(heightModifier),
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            content = content
        )

        ShadcnButtonVariant.Secondary -> Button(
            onClick = onClick,
            modifier = modifier.then(heightModifier),
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            content = content
        )

        ShadcnButtonVariant.Destructive -> Button(
            onClick = onClick,
            modifier = modifier.then(heightModifier),
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            content = content
        )

        ShadcnButtonVariant.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(heightModifier),
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            content = content
        )

        ShadcnButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            modifier = modifier.then(heightModifier),
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            content = content
        )
    }
}
