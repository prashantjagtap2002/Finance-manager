package com.example.financemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.CompactRadius
import com.example.financemanager.theme.SecondaryTeal
import com.example.financemanager.theme.Typography

/**
 * A list row with its edit and delete actions behind it instead of beside it:
 * swipe right to edit, swipe left to delete. Inline icon buttons cost every row
 * ~100dp of width that the title and amount need far more, and the actions are
 * used on a small fraction of rows.
 *
 * The row never actually dismisses — it springs back once the action fires — so
 * the caller stays in charge of confirmation and undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionsBox(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // The swipe state is remembered for the row's whole life, and so is the lambda handed to it.
    // Called directly, onEdit/onDelete would be the ones from the row's FIRST composition, holding
    // the transaction as it was then: edit 500 to 50, swipe to delete, and the ledger reverses the
    // stale 500. Reading through rememberUpdatedState always reaches the latest callbacks.
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> currentOnEdit()
            SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false
    })

    // A tick the moment the swipe crosses the commit point, in either direction, so the user feels
    // the action arm (and disarm, if they drag back) without having to watch for it. This fires
    // on the crossing itself, not on release, which is what ties the haptic to its cause.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    SwipeToDismissBox(
        state = state,
        // Swiping is invisible to TalkBack, so the same two actions are offered there.
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Edit") { onEdit(); true },
                CustomAccessibilityAction("Delete") { onDelete(); true }
            )
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SecondaryTeal to Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> AlertRed to Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> Color.Transparent to Alignment.Center
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CompactRadius))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    val isEdit = direction == SwipeToDismissBoxValue.StartToEnd
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isEdit) Icons.Default.Edit else Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (isEdit) "Edit" else "Delete",
                            style = Typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}
