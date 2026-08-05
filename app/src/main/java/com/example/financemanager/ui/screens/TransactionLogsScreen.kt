package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.financemanager.data.*
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import com.example.financemanager.domain.NlpParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionLogsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    // Dialog state for editing transaction
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    val nlpResult = remember(searchQuery) {
        if (searchQuery.isNotBlank()) NlpParser.parse(searchQuery)
        else null
    }

    val filteredTransactions = remember(transactions, searchQuery, selectedCategoryId, nlpResult, categories) {
        transactions.filter { transaction ->
            val textMatch = transaction.note.contains(searchQuery, ignoreCase = true) ||
                    (transaction.merchantName?.contains(searchQuery, ignoreCase = true) == true)
            
            val nlpMatch = if (nlpResult != null && (nlpResult.amount != null || nlpResult.categoryName != null)) {
                val amountMatches = if (nlpResult.amount != null) transaction.amount >= nlpResult.amount * 0.9 && transaction.amount <= nlpResult.amount * 1.1 else true
                val catMatches = if (nlpResult.categoryName != null) {
                    val catId = categories.firstOrNull { it.name == nlpResult.categoryName }?.id
                    transaction.categoryId == catId
                } else true
                amountMatches && catMatches
            } else false

            val categoryMatches = selectedCategoryId == null || transaction.categoryId == selectedCategoryId
            (textMatch || nlpMatch) && categoryMatches
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Logs", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val path = viewModel.exportTransactionsToCsv(context)
                            if (path != null) {
                                Toast.makeText(context, "Exported: $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No transactions to export", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DeepBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search and Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search logs...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Box {
                    IconButton(
                        onClick = { showCategoryFilterMenu = true },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedCategoryId != null) SecondaryTeal else TextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showCategoryFilterMenu,
                        onDismissRequest = { showCategoryFilterMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories", color = TextPrimary) },
                            onClick = {
                                selectedCategoryId = null
                                showCategoryFilterMenu = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name, color = TextPrimary) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    showCategoryFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logs List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions match search criteria.", style = Typography.bodyMedium.copy(color = TextMuted))
                        }
                    }
                }

                items(filteredTransactions, key = { it.id }) { transaction ->
                    val accountName = accounts.firstOrNull { it.id == transaction.sourceAccountId }?.name ?: "Account"
                    val category = categories.firstOrNull { it.id == transaction.categoryId }
                    val categoryName = category?.name ?: "Income/Transfer"

                    LogItem(
                        transaction = transaction,
                        accountName = accountName,
                        categoryName = categoryName,
                        tags = viewModel.extractTags(transaction.note),
                        onTagClick = { tag -> searchQuery = tag },
                        onEditClick = { editingTransaction = transaction },
                        onDeleteClick = { deleteWithUndo(transaction) },
                        modifier = Modifier.animateItem()
                    )
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
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogItem(
    transaction: Transaction,
    accountName: String,
    categoryName: String,
    tags: List<String>,
    onTagClick: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> AlertRed
        TransactionType.INCOME -> AccentGreen
        TransactionType.TRANSFER -> SecondaryTeal
    }
    val sign = when (transaction.type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> ""
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormatter.format(Date(transaction.date))

    iOSCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note.ifEmpty { "Transaction" },
                    style = Typography.titleMedium.copy(color = TextPrimary)
                )
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = Typography.labelSmall.copy(color = SecondaryTeal, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SecondaryTeal.copy(alpha = 0.15f))
                                    .clickable { onTagClick(tag) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$categoryName • $accountName",
                    style = Typography.labelMedium.copy(color = TextSecondary)
                )
                Text(
                    text = formattedDate,
                    style = Typography.labelSmall.copy(color = TextMuted)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$sign ${moneyString(transaction.amount, false)}",
                    style = Typography.titleMedium.copy(color = amountColor, fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SecondaryTeal, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, account: Account, category: Category?, note: String, date: Long) -> Unit
) {
    var amountStr by remember { mutableStateOf(transaction.amount.toString()) }
    var note by remember { mutableStateOf(transaction.note) }

    var selectedAccount by remember {
        mutableStateOf(accounts.firstOrNull { it.id == transaction.sourceAccountId } ?: accounts.first())
    }
    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull { it.id == transaction.categoryId })
    }

    var showAccountMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // Date picker state
    val selectedDate = remember { java.util.Calendar.getInstance().apply { timeInMillis = transaction.date } }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = transaction.date)
    val dateFormatter = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction", style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepBackground)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Date", style = Typography.bodyMedium.copy(color = TextSecondary))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateFormatter.format(selectedDate.time), style = Typography.bodyMedium.copy(color = TextPrimary))
                        Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = SecondaryTeal, modifier = Modifier.size(18.dp))
                    }
                }

                // Account Selection dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showAccountMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Account: ${selectedAccount.name}", color = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccount = account
                                    showAccountMenu = false
                                }
                            )
                        }
                    }
                }

                // Category Selection dropdown
                if (transaction.type != TransactionType.TRANSFER) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showCategoryMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Category: ${selectedCategory?.name ?: "None / Uncategorized"}", color = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None / Uncategorized") },
                                onClick = {
                                    selectedCategory = null
                                    showCategoryMenu = false
                                }
                            )
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            iOSButton(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: transaction.amount
                    onConfirm(amt, selectedAccount, selectedCategory, note, selectedDate.timeInMillis)
                },
                variant = iOSButtonVariant.Accent,
                accentColor = SecondaryTeal
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate.timeInMillis = it
                    }
                    showDatePicker = false
                }) {
                    Text("OK", style = Typography.labelLarge.copy(color = AccentGreen))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", style = Typography.labelLarge.copy(color = TextSecondary))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DarkSurface,
                titleContentColor = TextPrimary,
                headlineContentColor = TextPrimary,
                weekdayContentColor = TextSecondary,
                subheadContentColor = TextSecondary,
                navigationContentColor = TextPrimary,
                yearContentColor = TextPrimary,
                currentYearContentColor = AccentGreen,
                selectedYearContentColor = DarkSurface,
                selectedYearContainerColor = AccentGreen,
                dayContentColor = TextPrimary,
                selectedDayContentColor = DarkSurface,
                selectedDayContainerColor = AccentGreen,
                todayContentColor = AccentGreen,
                todayDateBorderColor = AccentGreen
            )
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

