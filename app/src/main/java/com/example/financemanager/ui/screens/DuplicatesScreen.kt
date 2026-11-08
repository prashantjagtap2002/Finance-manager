package com.example.financemanager.ui.screens

import com.example.financemanager.ui.components.formatDate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.financemanager.ui.components.IosTopAppBar
import com.example.financemanager.ui.components.rememberLargeTitleBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financemanager.data.Transaction
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.EmptyState
import com.example.financemanager.ui.components.IosCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pairs that look like one payment recorded twice. Each pair offers deleting either copy, or
 * saying they really are two payments — which is remembered, so the pair never comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val pairs by viewModel.duplicatePairs.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val isPrivacy by viewModel.isPrivacyMode.collectAsStateWithLifecycle()
    val accountsById = remember(accounts) { accounts.associateBy { it.id } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun delete(tx: Transaction) {
        viewModel.deleteTransaction(tx)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar("Duplicate deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreTransaction(tx)
        }
    }

    val titleBehavior = rememberLargeTitleBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(titleBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IosTopAppBar(
                title = "Possible duplicates",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                scrollBehavior = titleBehavior
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (pairs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Verified,
                        title = "No duplicates",
                        message = "Nothing in the last 90 days looks like the same payment logged twice.",
                        accent = AccentGreen
                    )
                }
            } else {
                item {
                    Text(
                        "Each pair has the same amount, logged minutes apart — usually once by hand and once from the bank's SMS. Delete the extra copy, or keep both if they really were two payments.",
                        style = Typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            items(pairs, key = { it.key }) { pair ->
                IosCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        val gapMinutes = abs(pair.second.date - pair.first.date) / 60_000
                        Text(
                            "${moneyString(pair.first.amount, isPrivacy)} · ${if (gapMinutes == 0L) "under a minute" else "$gapMinutes min"} apart",
                            style = Typography.labelMedium.copy(color = WarningAmber, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        DuplicateRow(pair.first, accountsById[pair.first.sourceAccountId]?.name ?: "Account") { delete(pair.first) }
                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                        DuplicateRow(pair.second, accountsById[pair.second.sourceAccountId]?.name ?: "Account") { delete(pair.second) }
                        TextButton(
                            onClick = { viewModel.dismissDuplicate(pair) },
                            modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
                        ) {
                            Text("Not a duplicate — keep both", color = SecondaryTeal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateRow(tx: Transaction, accountName: String, onDelete: () -> Unit) {
    val time = formatDate(tx.date, "d MMM, h:mm a")
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.note.ifBlank { tx.merchantName ?: "Transaction" },
                style = Typography.titleSmall.copy(color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (tx.isAutoLogged) "From SMS" else "Entered by hand"} • $accountName • $time",
                style = Typography.labelMedium.copy(color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete this copy", tint = AlertRed)
        }
    }
}
