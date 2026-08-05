package com.example.financemanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.Account
import com.example.financemanager.data.Debt
import com.example.financemanager.data.DebtType
import com.example.financemanager.theme.*
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val debts by viewModel.debts.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Dialog & Sheet States
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var paymentDebt by remember { mutableStateOf<Debt?>(null) }
    var showPayoffPlanner by remember { mutableStateOf(false) }
    
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Split based on tab
    val currentType = if (selectedTab == 0) DebtType.LENT else DebtType.BORROWED
    val displayedDebts = debts.filter { it.type == currentType }
    
    val activeDebts = displayedDebts.filter { !it.isSettled }
    val settledDebts = displayedDebts.filter { it.isSettled }
    
    // Group active debts by person
    val groupedActiveDebts = activeDebts.groupBy { it.personName.trim().lowercase() }
    
    // Expanded state for groups
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    var showSettled by remember { mutableStateOf(false) }

    // Net balances
    val totalToReceive = debts.filter { it.type == DebtType.LENT && !it.isSettled }.sumOf { it.amount - it.paidAmount }
    val totalToPay = debts.filter { it.type == DebtType.BORROWED && !it.isSettled }.sumOf { it.amount - it.paidAmount }

    Scaffold(
        containerColor = DeepBackground,
        topBar = {
            TopAppBar(
                title = { Text("IOU Tracker", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showPayoffPlanner = true }) {
                        Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = "Payoff Planner", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TextPrimary,
                contentColor = DeepBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Debt")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("People owe you", style = Typography.labelMedium.copy(color = TextSecondary))
                    Text(moneyString(totalToReceive, false), style = Typography.titleMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You owe", style = Typography.labelMedium.copy(color = TextSecondary))
                    Text(moneyString(totalToPay, false), style = Typography.titleMedium.copy(color = AlertRed, fontWeight = FontWeight.Bold))
                }
            }

            // Tabs
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepBackground,
                contentColor = TextPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Owe Me", style = Typography.labelLarge) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("I Owe", style = Typography.labelLarge) }
                )
            }

            // List
            if (activeDebts.isEmpty() && settledDebts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No IOUs yet. Tap + to add one.", style = Typography.bodyLarge.copy(color = TextSecondary))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Grouped Active Debts
                    groupedActiveDebts.forEach { (personKey, personDebts) ->
                        val netBalance = personDebts.sumOf { it.amount - it.paidAmount }
                        val displayName = personDebts.first().personName
                        val isExpanded = expandedGroups.contains(personKey)
                        val isSingle = personDebts.size == 1
                        
                        item(key = "header_$personKey") {
                            iOSCard(
                                style = iOSCardStyle.Grouped,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isSingle) {
                                            expandedGroups = if (isExpanded) expandedGroups - personKey else expandedGroups + personKey
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.animateContentSize()) {
                                    // Header row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryViolet)
                                                .border(1.dp, BorderColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                displayName.take(1).uppercase(),
                                                color = DeepBackground,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(displayName, style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                                                if (!isSingle) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = DarkSurface,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text("${personDebts.size} IOUs", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = Typography.labelSmall.copy(color = TextSecondary))
                                                    }
                                                }
                                            }
                                            Text("Net: ${moneyString(netBalance, false)}", style = Typography.labelMedium.copy(color = if (selectedTab == 0) AccentGreen else AlertRed))
                                        }
                                        
                                        if (!isSingle) {
                                            Icon(
                                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                    
                                    // Expanded items
                                    if (isExpanded || isSingle) {
                                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                            personDebts.forEach { debt ->
                                                HorizontalDivider(color = BorderColor)
                                                DebtItemRow(
                                                    debt = debt,
                                                    formatter = formatter,
                                                    onPaymentClick = { paymentDebt = it },
                                                    onEditClick = { editingDebt = it },
                                                    onDeleteClick = { viewModel.deleteDebt(it) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Settled Section
                    if (settledDebts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSettled = !showSettled }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Settled (${settledDebts.size})", style = Typography.titleMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                Icon(
                                    if (showSettled) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Settled",
                                    tint = TextSecondary
                                )
                            }
                        }
                        
                        if (showSettled) {
                            items(settledDebts, key = { it.id }) { debt ->
                                iOSCard(
                                    style = iOSCardStyle.Grouped,
                                    modifier = Modifier.fillMaxWidth().alpha(0.6f)
                                ) {
                                    DebtItemRow(
                                        debt = debt,
                                        formatter = formatter,
                                        onPaymentClick = {},
                                        onEditClick = { editingDebt = it },
                                        onDeleteClick = { viewModel.deleteDebt(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Record Payment Bottom Sheet
    if (paymentDebt != null) {
        var paymentAmount by remember { mutableStateOf((paymentDebt!!.amount - paymentDebt!!.paidAmount).toString()) }
        var selectedAccount by remember { mutableStateOf<Account?>(accounts.firstOrNull()) }
        var accountDropdownExpanded by remember { mutableStateOf(false) }
        
        ModalBottomSheet(
            onDismissRequest = { paymentDebt = null },
            containerColor = DeepBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Record Payment",
                    style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                )
                Text(
                    "Remaining Balance: ${moneyString(paymentDebt!!.amount - paymentDebt!!.paidAmount, false)}",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
                
                OutlinedTextField(
                    value = paymentAmount,
                    onValueChange = { paymentAmount = it },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = accountDropdownExpanded,
                    onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccount = acc
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                iOSButton(
                    onClick = {
                        val amt = paymentAmount.toDoubleOrNull()
                        if (amt != null && amt > 0 && selectedAccount != null) {
                            viewModel.recordDebtPayment(paymentDebt!!, amt, selectedAccount!!)
                            paymentDebt = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    variant = iOSButtonVariant.Accent,
                    accentColor = SecondaryTeal
                ) {
                    Text("Confirm Payment", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingDebt != null) {
        val isEdit = editingDebt != null
        val initialDebt = editingDebt
        
        var name by remember { mutableStateOf(initialDebt?.personName ?: "") }
        var amountStr by remember { mutableStateOf(initialDebt?.amount?.toString() ?: "") }
        var notes by remember { mutableStateOf(initialDebt?.notes ?: "") }
        
        var showDatePicker by remember { mutableStateOf(false) }
        var dueDateMillis by remember { mutableStateOf(initialDebt?.dueDate) }
        
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dueDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingDebt = null
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    if (isEdit) "Edit IOU" else if (selectedTab == 0) "Add Lent Money" else "Add Borrowed Money",
                    style = Typography.titleLarge.copy(color = TextPrimary)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Person Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Total Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Due Date Picker
                    OutlinedTextField(
                        value = if (dueDateMillis != null) formatter.format(Date(dueDateMillis!!)) else "No Due Date",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Due Date") },
                        trailingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", modifier = Modifier.clickable { showDatePicker = true })
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                    )
                    
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull()
                        if (name.isNotBlank() && amount != null) {
                            if (isEdit) {
                                viewModel.updateDebt(
                                    initialDebt!!.copy(
                                        personName = name.trim(),
                                        amount = amount,
                                        dueDate = dueDateMillis,
                                        notes = notes.trim()
                                    )
                                )
                            } else {
                                viewModel.insertDebt(
                                    Debt(
                                        personName = name.trim(),
                                        amount = amount,
                                        type = if (selectedTab == 0) DebtType.LENT else DebtType.BORROWED,
                                        dueDate = dueDateMillis,
                                        notes = notes.trim()
                                    )
                                )
                            }
                            showAddDialog = false
                            editingDebt = null
                        }
                    }
                ) {
                    Text("Save", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingDebt = null
                }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtItemRow(
    debt: Debt,
    formatter: SimpleDateFormat,
    onPaymentClick: (Debt) -> Unit,
    onEditClick: (Debt) -> Unit,
    onDeleteClick: (Debt) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepBackground)
            .clickable { onEditClick(debt) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatter.format(Date(debt.date)),
                style = Typography.labelMedium.copy(color = TextSecondary)
            )
            if (debt.notes.isNotEmpty()) {
                Text(
                    debt.notes,
                    style = Typography.bodySmall.copy(
                        color = TextMuted,
                        textDecoration = if (debt.isSettled) TextDecoration.LineThrough else null
                    )
                )
            }
            
            // Badges
            if (!debt.isSettled && debt.dueDate != null) {
                val now = System.currentTimeMillis()
                val diffDays = (debt.dueDate - now) / (1000 * 60 * 60 * 24)
                if (diffDays < 0) {
                    Text("Overdue", style = Typography.labelSmall.copy(color = AlertRed, fontWeight = FontWeight.Bold))
                } else if (diffDays <= 7) {
                    Text("Due in ${diffDays + 1} days", style = Typography.labelSmall.copy(color = SecondaryTeal, fontWeight = FontWeight.Bold))
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                moneyString(debt.amount, false),
                style = Typography.titleMedium.copy(
                    color = if (debt.isSettled) TextMuted else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (debt.isSettled) TextDecoration.LineThrough else null
                )
            )
            
            if (!debt.isSettled) {
                Spacer(modifier = Modifier.height(8.dp))
                // Progress bar
                LinearProgressIndicator(
                    progress = { if (debt.amount > 0) (debt.paidAmount / debt.amount).toFloat() else 0f },
                    modifier = Modifier.width(80.dp).height(4.dp),
                    color = SecondaryTeal,
                    trackColor = BorderColor
                )
                Text(
                    "${moneyString(debt.paidAmount, false)} paid",
                    style = Typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (!debt.isSettled) {
            Icon(
                Icons.Default.Payment,
                contentDescription = "Record Payment",
                tint = SecondaryTeal,
                modifier = Modifier
                    .size(32.dp)
                    .background(SecondaryTeal.copy(alpha = 0.15f), CircleShape)
                    .padding(6.dp)
                    .clickable { onPaymentClick(debt) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        IconButton(onClick = { onEditClick(debt) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SecondaryTeal, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onDeleteClick(debt) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoffPlannerSheet(
    debts: List<Debt>,
    onDismiss: () -> Unit
) {
    // We only care about active debts you owe (BORROWED) for Payoff Planning usually, 
    // but we can just use all BORROWED active debts.
    val borrowedDebts = debts.filter { it.type == DebtType.BORROWED && !it.isSettled }
    
    var strategy by remember { mutableStateOf("Avalanche") } // Avalanche vs Snowball
    
    // Sort logic
    val sortedDebts = remember(borrowedDebts, strategy) {
        if (strategy == "Avalanche") {
            // Highest interest rate first
            borrowedDebts.sortedByDescending { it.interestRate ?: 0.0 }
        } else {
            // Snowball: Lowest remaining balance first
            borrowedDebts.sortedBy { it.amount - it.paidAmount }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Debt Payoff Planner", style = Typography.headlineSmall.copy(color = TextPrimary))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Strategy Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(4.dp)
            ) {
                listOf("Avalanche", "Snowball").forEach { option ->
                    val isSelected = strategy == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryViolet else Color.Transparent)
                            .clickable { strategy = option }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option,
                            style = Typography.labelMedium.copy(color = if (isSelected) DeepBackground else TextSecondary)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (strategy == "Avalanche") "Pays off highest interest debts first. Saves the most money." 
                else "Pays off smallest balances first. Builds momentum quickly.",
                style = Typography.labelSmall.copy(color = TextMuted),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (sortedDebts.isEmpty()) {
                Text("You don't have any active debts to plan for!", style = Typography.bodyMedium.copy(color = TextSecondary))
            } else {
                LazyColumn {
                    items(sortedDebts, key = { "payoff-${it.id}" }) { debt ->
                        val interest = debt.interestRate ?: 0.0
                        val minPayment = debt.minimumPayment ?: (debt.amount * 0.05)
                        val remaining = debt.amount - debt.paidAmount
                        val months = if (minPayment > 0) (remaining / minPayment).toInt() else 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(debt.personName, style = Typography.titleMedium.copy(color = TextPrimary))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Int: ${interest}%", style = Typography.labelSmall.copy(color = AlertRed))
                                    Text("Min: ${moneyString(minPayment, false)}", style = Typography.labelSmall.copy(color = TextSecondary))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(moneyString(remaining, false), style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                                Text("~ $months months", style = Typography.labelSmall.copy(color = SecondaryTeal))
                            }
                        }
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
