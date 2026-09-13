package com.example.financemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financemanager.theme.AccentGreen
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography

/**
 * The "there's nothing here yet" state, in one place.
 *
 * An empty screen is the first thing a new user sees, and a bare grey sentence tells them the
 * query failed rather than what the screen is for. The shape here — mark, headline, one line of
 * plain explanation, and at most one action — is lifted from the Goals screen, which already did
 * this well; everywhere else was left to improvise.
 *
 * [actionLabel] and [onAction] are optional together: some empty states are the result of a
 * filter, where the fix is to change the filter, not to create something.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = AccentGreen,
    actionLabel: String? = null,
    actionIcon: ImageVector = Icons.Default.Add,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            title,
            style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = Typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            iOSButton(
                onClick = onAction,
                variant = iOSButtonVariant.Accent,
                accentColor = accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Icon(actionIcon, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
