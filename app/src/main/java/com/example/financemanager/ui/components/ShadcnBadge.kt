package com.example.financemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ShadcnBadgeVariant { Primary, Secondary, Destructive, Outline }

/**
 * shadcn/ui-style Badge: a small pill (rounded-full) with a label, available
 * in default/secondary/destructive/outline variants matching shadcn.
 */
@Composable
fun ShadcnBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: ShadcnBadgeVariant = ShadcnBadgeVariant.Secondary
) {
    val container: Color
    val contentColor: Color
    val border: BorderStroke?
    when (variant) {
        ShadcnBadgeVariant.Primary -> {
            container = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
            border = null
        }
        ShadcnBadgeVariant.Secondary -> {
            container = MaterialTheme.colorScheme.secondary
            contentColor = MaterialTheme.colorScheme.onSecondary
            border = null
        }
        ShadcnBadgeVariant.Destructive -> {
            container = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            border = null
        }
        ShadcnBadgeVariant.Outline -> {
            container = MaterialTheme.colorScheme.surface
            contentColor = MaterialTheme.colorScheme.onSurface
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
