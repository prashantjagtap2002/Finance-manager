package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.Account
import com.example.financemanager.data.Investment
import com.example.financemanager.data.InvestmentTransaction
import com.example.financemanager.data.InvestmentTxnType
import com.example.financemanager.data.InvestmentType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import com.example.financemanager.ui.viewmodel.InvestmentHolding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun qtyString(q: Double): String {
    val s = String.format(Locale.getDefault(), "%.4f", q)
    return s.trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val holdings by viewModel.portfolioHoldings.collectAsState()
    val isRefreshing by viewModel.isRefreshingPrices.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val totalInvested = holdings.sumOf { it.investedAmount }
    val totalCurrent = holdings.sumOf { it.currentValue }
    val totalGainLoss = totalCurrent - totalInvested
    val totalGainLossPercent = if (totalInvested > 0.0) (totalGainLoss / totalInvested) * 100 else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investments", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAllPrices() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryViolet)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Prices", tint = PrimaryViolet)
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Investment", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        // Prices are the one thing on this screen that goes stale on its own, so the gesture
        // people already reach for should fetch them — the toolbar button stays for discoverability.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAllPrices() },
            modifier = Modifier.padding(paddingValues)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "portfolio_summary") {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalGainLoss >= 0.0) SuccessSoft else ErrorSoft
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Portfolio Value", style = Typography.labelMedium.copy(color = TextSecondary))
                        Text(moneyString(totalCurrent), style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Invested", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(moneyString(totalInvested), style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Gain / Loss", style = Typography.labelMedium.copy(color = TextSecondary))
                                val glColor = if (totalGainLoss >= 0.0) AccentGreen else AlertRed
                                val sign = if (totalGainLoss >= 0.0) "+" else ""
                                Text(
                                    "$sign${moneyString(totalGainLoss)} ($sign${String.format(Locale.getDefault(), "%.1f", totalGainLossPercent)}%)",
                                    style = Typography.titleMedium.copy(color = glColor, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "holdings_header") {
                Text("Holdings", style = Typography.titleMedium.copy(color = TextPrimary))
            }

            if (holdings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No holdings yet. Tap + to add a stock or SIP.", style = Typography.bodyMedium.copy(color = TextSecondary))
                    }
                }
            } else {
                items(holdings, key = { it.investment.id }) { holding ->
                    HoldingCard(holding = holding, onClick = { onNavigateToDetail(holding.investment.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
        }
    }

    if (showAddDialog) {
        AddInvestmentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, symbol, exchange, type ->
                viewModel.addInvestment(name, symbol, exchange, type)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun HoldingCard(holding: InvestmentHolding, onClick: () -> Unit) {
    val glColor = if (holding.gainLoss >= 0.0) AccentGreen else AlertRed
    val sign = if (holding.gainLoss >= 0.0) "+" else ""

    iOSCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(holding.investment.name, style = Typography.titleMedium.copy(color = TextPrimary))
                        if (holding.hasSip) {
                            Text(
                                "SIP",
                                style = Typography.labelSmall.copy(color = SecondaryTeal, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SecondaryTeal.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${holding.investment.symbol ?: holding.investment.type.name} • ${qtyString(holding.quantity)} units",
                        style = Typography.labelMedium.copy(color = TextSecondary)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(moneyString(holding.currentValue), style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    Text(
                        "$sign${moneyString(holding.gainLoss)} ($sign${String.format(Locale.getDefault(), "%.1f", holding.gainLossPercent)}%)",
                        style = Typography.labelSmall.copy(color = glColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddInvestmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, symbol: String?, exchange: String?, type: InvestmentType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(InvestmentType.STOCK) }
    var exchange by remember { mutableStateOf("NSE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Investment", style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == InvestmentType.STOCK,
                        onClick = { type = InvestmentType.STOCK },
                        label = { Text("Stock") }
                    )
                    FilterChip(
                        selected = type == InvestmentType.MUTUAL_FUND,
                        onClick = { type = InvestmentType.MUTUAL_FUND },
                        label = { Text("Mutual Fund / SIP") }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (type == InvestmentType.STOCK) "Company Name" else "Fund Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == InvestmentType.STOCK) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text("Ticker Symbol (e.g. TCS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = exchange == "NSE", onClick = { exchange = "NSE" }, label = { Text("NSE") })
                        FilterChip(selected = exchange == "BSE", onClick = { exchange = "BSE" }, label = { Text("BSE") })
                    }
                    Text(
                        "Ticker is used to fetch live prices. Leave blank to track manually.",
                        style = Typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }
        },
        confirmButton = {
            iOSButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            symbol.takeIf { it.isNotBlank() && type == InvestmentType.STOCK },
                            if (type == InvestmentType.STOCK) exchange else null,
                            type
                        )
                    }
                },
                variant = iOSButtonVariant.Accent,
                accentColor = PrimaryViolet,
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentDetailScreen(
    investmentId: Long,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val holdings by viewModel.portfolioHoldings.collectAsState()
    val allTxns by viewModel.investmentTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val holding = holdings.firstOrNull { it.investment.id == investmentId }
    val txns = remember(allTxns, investmentId) {
        allTxns.filter { it.investmentId == investmentId }.sortedByDescending { it.date }
    }

    var showLogDialog by remember { mutableStateOf(false) }
    var logType by remember { mutableStateOf(InvestmentTxnType.BUY) }
    var editingTxn by remember { mutableStateOf<InvestmentTransaction?>(null) }
    var txnToDelete by remember { mutableStateOf<InvestmentTransaction?>(null) }
    var showManualPriceDialog by remember { mutableStateOf(false) }
    var showDeleteInvestmentConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(holding?.investment?.name ?: "Investment", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteInvestmentConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Investment", tint = AlertRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (holding != null) {
                iOSCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Quantity", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(qtyString(holding.quantity), style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Avg Buy Price", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(moneyString(holding.avgBuyPrice), style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualPriceDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Current Price", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(moneyString(holding.investment.currentPrice), style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                            Icon(Icons.Default.Edit, contentDescription = "Update price manually", tint = SecondaryTeal, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        val glColor = if (holding.gainLoss >= 0.0) AccentGreen else AlertRed
                        val sign = if (holding.gainLoss >= 0.0) "+" else ""
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Value: ${moneyString(holding.currentValue)}", style = Typography.bodyMedium.copy(color = TextPrimary))
                            Text(
                                "$sign${moneyString(holding.gainLoss)} ($sign${String.format(Locale.getDefault(), "%.1f", holding.gainLossPercent)}%)",
                                style = Typography.bodyMedium.copy(color = glColor, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    iOSButton(
                        onClick = { logType = InvestmentTxnType.BUY; showLogDialog = true },
                        modifier = Modifier.weight(1f),
                        variant = iOSButtonVariant.Accent,
                        accentColor = AccentGreen
                    ) { Text("Log Buy") }
                    iOSButton(
                        onClick = { logType = InvestmentTxnType.SIP_INSTALLMENT; showLogDialog = true },
                        modifier = Modifier.weight(1f),
                        variant = iOSButtonVariant.Accent,
                        accentColor = SecondaryTeal
                    ) { Text("Log SIP") }
                    iOSButton(
                        onClick = { logType = InvestmentTxnType.SELL; showLogDialog = true },
                        modifier = Modifier.weight(1f),
                        variant = iOSButtonVariant.Accent,
                        accentColor = AlertRed
                    ) { Text("Log Sell") }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Transaction Log", style = Typography.titleMedium.copy(color = TextPrimary), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            if (txns.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No buys, sells or SIP installments logged yet.", style = Typography.bodyMedium.copy(color = TextSecondary))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(txns, key = { it.id }) { txn ->
                        InvestmentTxnItem(
                            txn = txn,
                            onEditClick = { editingTxn = txn },
                            onDeleteClick = { txnToDelete = txn }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showLogDialog && holding != null) {
        LogInvestmentTxnDialog(
            txnType = logType,
            accounts = accounts,
            onDismiss = { showLogDialog = false },
            onConfirm = { quantity, price, accountId, date ->
                when (logType) {
                    InvestmentTxnType.SELL -> viewModel.recordInvestmentSell(holding.investment, quantity, price, accountId, date)
                    InvestmentTxnType.SIP_INSTALLMENT -> viewModel.recordInvestmentBuy(holding.investment, quantity, price, accountId, date, isSip = true)
                    InvestmentTxnType.BUY -> viewModel.recordInvestmentBuy(holding.investment, quantity, price, accountId, date, isSip = false)
                }
                showLogDialog = false
            }
        )
    }

    editingTxn?.let { txn ->
        LogInvestmentTxnDialog(
            txnType = txn.type,
            accounts = accounts,
            initialQuantity = txn.quantity,
            initialPrice = txn.pricePerUnit,
            initialAccountId = txn.sourceAccountId,
            initialDate = txn.date,
            onDismiss = { editingTxn = null },
            onConfirm = { quantity, price, accountId, date ->
                viewModel.updateInvestmentTransaction(
                    txn.copy(quantity = quantity, pricePerUnit = price, amount = quantity * price, sourceAccountId = accountId, date = date)
                )
                editingTxn = null
            }
        )
    }

    txnToDelete?.let { txn ->
        AlertDialog(
            onDismissRequest = { txnToDelete = null },
            title = { Text("Delete Log", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = { Text("Remove this ${qtyString(txn.quantity)}-unit entry? Its account effect will be reversed.", style = Typography.bodyMedium.copy(color = TextSecondary)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInvestmentTransaction(txn)
                    txnToDelete = null
                }) { Text("Delete", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { txnToDelete = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    if (showManualPriceDialog && holding != null) {
        var priceStr by remember { mutableStateOf(holding.investment.currentPrice.toString()) }
        AlertDialog(
            onDismissRequest = { showManualPriceDialog = false },
            title = { Text("Update Price", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Current Price per Unit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        priceStr.toDoubleOrNull()?.let { viewModel.updateInvestmentPriceManually(holding.investment, it) }
                        showManualPriceDialog = false
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = SecondaryTeal
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showManualPriceDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    if (showDeleteInvestmentConfirm && holding != null) {
        AlertDialog(
            onDismissRequest = { showDeleteInvestmentConfirm = false },
            title = { Text("Delete Investment", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = { Text("This removes \"${holding.investment.name}\" and all its logged buys/sells/SIP installments. This cannot be undone.", style = Typography.bodyMedium.copy(color = TextSecondary)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInvestment(holding.investment)
                    showDeleteInvestmentConfirm = false
                    onNavigateBack()
                }) { Text("Delete", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteInvestmentConfirm = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun InvestmentTxnItem(
    txn: InvestmentTransaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val (label, color) = when (txn.type) {
        InvestmentTxnType.BUY -> "Buy" to AccentGreen
        InvestmentTxnType.SIP_INSTALLMENT -> "SIP" to SecondaryTeal
        InvestmentTxnType.SELL -> "Sell" to AlertRed
    }

    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        label,
                        style = Typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(dateFormatter.format(Date(txn.date)), style = Typography.labelSmall.copy(color = TextMuted))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${qtyString(txn.quantity)} units @ ${moneyString(txn.pricePerUnit)}", style = Typography.bodyMedium.copy(color = TextPrimary))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(moneyString(txn.amount), style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogInvestmentTxnDialog(
    txnType: InvestmentTxnType,
    accounts: List<Account>,
    initialQuantity: Double? = null,
    initialPrice: Double? = null,
    initialAccountId: Long? = null,
    initialDate: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, price: Double, accountId: Long, date: Long) -> Unit
) {
    var quantityStr by remember { mutableStateOf(initialQuantity?.toString() ?: "") }
    var priceStr by remember { mutableStateOf(initialPrice?.toString() ?: "") }
    var selectedAccount by remember {
        mutableStateOf(accounts.firstOrNull { it.id == initialAccountId } ?: accounts.firstOrNull())
    }
    var showAccountMenu by remember { mutableStateOf(false) }

    val selectedDate = remember { java.util.Calendar.getInstance().apply { timeInMillis = initialDate ?: System.currentTimeMillis() } }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    val title = when (txnType) {
        InvestmentTxnType.BUY -> "Log Buy"
        InvestmentTxnType.SIP_INSTALLMENT -> "Log SIP Installment"
        InvestmentTxnType.SELL -> "Log Sell"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Quantity / Units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price per Unit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                    Text(dateFormatter.format(selectedDate.time), style = Typography.bodyMedium.copy(color = TextPrimary))
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showAccountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedAccount?.let { "Account: ${it.name}" } ?: "Select account", color = TextPrimary)
                    }
                    DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedAccount = account; showAccountMenu = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val qty = quantityStr.toDoubleOrNull()
            val price = priceStr.toDoubleOrNull()
            val isValid = qty != null && qty > 0.0 && price != null && price > 0.0 && selectedAccount != null
            iOSButton(
                onClick = { onConfirm(qty!!, price!!, selectedAccount!!.id, selectedDate.timeInMillis) },
                variant = iOSButtonVariant.Accent,
                accentColor = PrimaryViolet,
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate.timeInMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
