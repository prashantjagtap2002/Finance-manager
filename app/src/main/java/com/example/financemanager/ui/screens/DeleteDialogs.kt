package com.example.financemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel

/**
 * Confirms deleting an envelope and decides where its transactions go. Deleting without choosing
 * used to strand them on an id nothing resolved: gone from every breakdown, still in the totals.
 */
@Composable
fun DeleteCategoryDialog(
    category: Category,
    categories: List<Category>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var affectedCount by remember(category.id) { mutableStateOf<Int?>(null) }
    var reassignTo by remember(category.id) { mutableStateOf(0L) }

    LaunchedEffect(category.id) {
        viewModel.countTransactionsInCategory(category.id) { affectedCount = it }
    }

    val others = categories.filter { it.id != category.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Delete ${category.name}", style = Typography.titleLarge.copy(color = AlertRed)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    when (affectedCount) {
                        null -> "Checking what's filed under this envelope…"
                        0 -> "Nothing is filed under this envelope."
                        1 -> "1 transaction is filed under this envelope."
                        else -> "$affectedCount transactions are filed under this envelope."
                    },
                    style = Typography.bodyMedium.copy(color = TextPrimary)
                )
                if ((affectedCount ?: 0) > 0) {
                    Text(
                        "They'll stay in your totals — pick where they should sit.",
                        style = Typography.labelMedium.copy(color = TextSecondary),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ChoiceRow(
                        label = "Leave uncategorised",
                        selected = reassignTo == 0L,
                        onSelect = { reassignTo = 0L }
                    )
                    others.forEach { option ->
                        ChoiceRow(
                            label = "Move to ${option.name}",
                            selected = reassignTo == option.id,
                            onSelect = { reassignTo = option.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.deleteCategory(category, reassignTo)
                onDismiss()
            }) {
                Text("Delete", color = AlertRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/**
 * Confirms deleting an account. Merging into another account carries the history and the balance
 * across; deleting the history instead unwinds each transaction through the ledger, so the far
 * side of any transfer gets its money back.
 */
@Composable
fun DeleteAccountDialog(
    account: Account,
    accounts: List<Account>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var affectedCount by remember(account.id) { mutableStateOf<Int?>(null) }
    val others = accounts.filter { it.id != account.id }
    var moveTo by remember(account.id) { mutableStateOf(others.firstOrNull()?.id) }

    LaunchedEffect(account.id) {
        viewModel.countTransactionsForAccount(account.id) { affectedCount = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Delete ${account.name}", style = Typography.titleLarge.copy(color = AlertRed)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    when (affectedCount) {
                        null -> "Checking this account's history…"
                        0 -> "This account has no transactions."
                        1 -> "This account has 1 transaction."
                        else -> "This account has $affectedCount transactions."
                    },
                    style = Typography.bodyMedium.copy(color = TextPrimary)
                )
                Text(
                    "Its balance of ${moneyString(account.balance)} has to go somewhere.",
                    style = Typography.labelMedium.copy(color = TextSecondary),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                others.forEach { option ->
                    ChoiceRow(
                        label = "Merge into ${option.name}",
                        selected = moveTo == option.id,
                        onSelect = { moveTo = option.id }
                    )
                }
                ChoiceRow(
                    label = if ((affectedCount ?: 0) > 0) {
                        "Delete the account and its transactions"
                    } else {
                        "Delete the account"
                    },
                    selected = moveTo == null,
                    onSelect = { moveTo = null }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.deleteAccount(account, moveTo)
                onDismiss()
            }) {
                Text("Delete", color = AlertRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = Typography.bodyMedium.copy(color = TextPrimary))
    }
}
