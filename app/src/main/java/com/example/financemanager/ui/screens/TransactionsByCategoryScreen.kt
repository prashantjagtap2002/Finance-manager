package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.Category
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import kotlinx.coroutines.launch
import com.example.financemanager.theme.AccentGreen
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.DeepBackground
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.SecondaryTeal
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import com.example.financemanager.theme.WarningAmber
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsByCategoryScreen(
    categoryId: Long,
    year: String,
    month: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allTransactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var transactionToSplit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToRecategorize by remember { mutableStateOf<Transaction?>(null) }

    // Bulk selection state
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun deleteWithUndo(transaction: Transaction) {
        viewModel.deleteTransaction(transaction)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Transaction deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreTransaction(transaction)
            }
        }
    }

    fun bulkDeleteWithUndo(txns: List<Transaction>) {
        txns.forEach { viewModel.deleteTransaction(it) }
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${txns.size} transactions deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                txns.forEach { viewModel.restoreTransaction(it) }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()

    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }

    val transactions = remember(allTransactions, searchQuery, filterStartDate, filterEndDate, categoryId, year, month) {
        allTransactions.filter { t ->
            var match = t.categoryId == categoryId

            if (searchQuery.isNotBlank()) {
                val searchStr = searchQuery.lowercase()
                match = match && ((t.merchantName?.lowercase()?.contains(searchStr) == true) ||
                                 t.note.lowercase().contains(searchStr))
            }

            if (filterStartDate != null && filterEndDate != null) {
                // inclusive date range
                match = match && t.date >= filterStartDate!! && t.date <= (filterEndDate!! + 86400000L)
            } else {
                // Default year/month filter if no custom range is set
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = t.date }
                val tYear = cal.get(java.util.Calendar.YEAR).toString()
                val tMonth = cal.get(java.util.Calendar.MONTH).toString()
                match = match && tYear == year && tMonth == month
            }
            match
        }.sortedByDescending { it.date }
    }
    val categories by viewModel.categories.collectAsState()

    val category = categories.firstOrNull { it.id == categoryId }
    val categoryName = category?.name ?: "Category"
    val categoryColor = category?.colorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.Gray

    val spentThisPeriod = remember(transactions) {
        transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text("${selectedIds.size} selected", style = Typography.titleLarge.copy(color = TextPrimary))
                    } else {
                        Column {
                            Text(categoryName, style = Typography.titleLarge.copy(color = TextPrimary))
                            val monthInt = month.toIntOrNull() ?: 0
                            val monthName = java.text.DateFormatSymbols().months[monthInt]
                            Text("$monthName $year", style = Typography.bodyMedium.copy(color = TextSecondary))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) exitSelectionMode() else onNavigateBack() }) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showBulkDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = AlertRed)
                        }
                    } else {
                        if (transactions.isNotEmpty()) {
                            IconButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.ChecklistRtl, contentDescription = "Select", tint = TextPrimary)
                            }
                        }
                        if (filterStartDate != null) {
                            IconButton(onClick = { filterStartDate = null; filterEndDate = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Filter", tint = AlertRed)
                            }
                        }
                        IconButton(onClick = { showStartPicker = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter by Date", tint = PrimaryViolet)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DeepBackground
    ) { paddingValues ->
        // Start Date Picker
        if (showStartPicker) {
            DatePickerDialog(
                onDismissRequest = { showStartPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        filterStartDate = startPickerState.selectedDateMillis
                        showStartPicker = false
                        showEndPicker = true // chain end picker
                    }) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = startPickerState, title = { Text("Select Start Date", modifier = Modifier.padding(16.dp)) })
            }
        }

        // End Date Picker
        if (showEndPicker) {
            DatePickerDialog(
                onDismissRequest = { showEndPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        filterEndDate = endPickerState.selectedDateMillis
                        showEndPicker = false
                    }) { Text("Apply Filter") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = endPickerState, title = { Text("Select End Date", modifier = Modifier.padding(16.dp)) })
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryViolet,
                    unfocusedBorderColor = DarkSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Spend vs budget progress
            if ((category?.budgetLimit ?: 0.0) > 0.0) {
                val budgetLimit = category!!.budgetLimit
                val progress = (spentThisPeriod / budgetLimit).toFloat()
                val progressColor = when {
                    spentThisPeriod > budgetLimit -> AlertRed
                    spentThisPeriod > budgetLimit * 0.8 -> WarningAmber
                    else -> AccentGreen
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${moneyString(spentThisPeriod)} of ${moneyString(budgetLimit)} spent",
                            style = Typography.labelMedium.copy(color = TextSecondary)
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = Typography.labelMedium.copy(color = progressColor, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = DarkSurface
                    )
                }
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions found.",
                        style = Typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(transactions, key = { it.id }) { transaction ->
                        if (selectionMode) {
                            SelectableTransactionRow(
                                transaction = transaction,
                                categoryColor = categoryColor,
                                selected = transaction.id in selectedIds,
                                onToggle = {
                                    selectedIds = if (transaction.id in selectedIds) {
                                        selectedIds - transaction.id
                                    } else {
                                        selectedIds + transaction.id
                                    }
                                }
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        transactionToDelete = transaction
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AlertRed.copy(alpha = 0.85f))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                    }
                                }
                            ) {
                                TransactionCategoryItem(
                                    transaction = transaction,
                                    categoryColor = categoryColor,
                                    onEditClick = { editingTransaction = transaction },
                                    onDeleteClick = { transactionToDelete = transaction },
                                    onRecategorizeClick = { transactionToRecategorize = transaction }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Edit Transaction Dialog
    editingTransaction?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            accounts = accounts,
            categories = categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { updatedAmount, selectedAccount, selectedCategory, updatedNote, updatedDate ->
                val newTrans = transaction.copy(
                    amount = updatedAmount,
                    sourceAccountId = selectedAccount.id,
                    categoryId = selectedCategory?.id ?: 0L,
                    note = updatedNote,
                    date = updatedDate
                )
                viewModel.updateTransaction(transaction, newTrans)
                editingTransaction = null
            },
            onSplitClick = {
                transactionToSplit = transaction
                editingTransaction = null
            }
        )
    }

    // Split Transaction Dialog
    transactionToSplit?.let { transaction ->
        SplitTransactionDialog(
            transaction = transaction,
            categories = categories,
            onDismiss = { transactionToSplit = null },
            onConfirm = { splitAmount, splitCategory ->
                viewModel.splitTransaction(transaction, splitAmount, splitCategory.id)
                transactionToSplit = null
            }
        )
    }

    // Recategorize Dialog
    transactionToRecategorize?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToRecategorize = null },
            title = { Text("Move to Envelope", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 320.dp)) {
                    items(categories.filter { it.id != transaction.categoryId }, key = { it.id }) { targetCategory ->
                        Surface(
                            onClick = {
                                viewModel.updateTransaction(transaction, transaction.copy(categoryId = targetCategory.id))
                                transactionToRecategorize = null
                            },
                            color = DarkSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(targetCategory.colorHex)))
                                )
                                Text(targetCategory.name, style = Typography.bodyMedium.copy(color = TextPrimary))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { transactionToRecategorize = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Confirmation Dialog
    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Text(
                    "Are you sure you want to delete \"${transaction.merchantName?.takeIf { it.isNotBlank() } ?: transaction.note}\"?",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteWithUndo(transaction)
                    transactionToDelete = null
                }) {
                    Text("Delete", color = AlertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} Transactions", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Text(
                    "Are you sure you want to delete these ${selectedIds.size} transactions?",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = transactions.filter { it.id in selectedIds }
                    bulkDeleteWithUndo(toDelete)
                    showBulkDeleteConfirm = false
                    exitSelectionMode()
                }) {
                    Text("Delete", color = AlertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun SelectableTransactionRow(
    transaction: Transaction,
    categoryColor: Color,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    iOSCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) PrimaryViolet else TextSecondary
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Column {
                    Text(
                        text = transaction.merchantName?.takeIf { it.isNotBlank() } ?: transaction.note,
                        style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 1
                    )
                    Text(
                        text = dateFormatter.format(Date(transaction.date)),
                        style = Typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            Text(
                text = moneyString(transaction.amount),
                style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
fun TransactionCategoryItem(
    transaction: Transaction,
    categoryColor: Color,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onRecategorizeClick: () -> Unit = {}
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Color indicator dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )

                Column {
                    Text(
                        text = transaction.merchantName?.takeIf { it.isNotBlank() } ?: transaction.note,
                        style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 1
                    )
                    Text(
                        text = dateFormatter.format(Date(transaction.date)),
                        style = Typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = moneyString(transaction.amount),
                    style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                )
                IconButton(onClick = onRecategorizeClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Move to another envelope", tint = SecondaryTeal, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = PrimaryViolet, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
