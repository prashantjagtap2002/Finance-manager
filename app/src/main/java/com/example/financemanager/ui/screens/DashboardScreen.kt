package com.example.financemanager.ui.screens

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSTopAppBar
import com.example.financemanager.ui.components.iOSBadge
import com.example.financemanager.ui.components.iOSListSeparator
import com.example.financemanager.theme.LocalThemeIsDark
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToQuickEntry: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToDebt: () -> Unit = {},
    onNavigateToNetWorth: () -> Unit = {},
    onNavigateToSmsTransactions: () -> Unit = {},
    onNavigateToCategory: (Long, Int, Int) -> Unit = { _, _, _ -> }
) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val monthlySpent by viewModel.monthlyExpenseTotal.collectAsState()
    val monthlyCategorySpend by viewModel.monthlyCategorySpend.collectAsState(initial = emptyMap())
    val cashflowWarning by viewModel.cashflowWarning.collectAsState(initial = null)
    val streakDays by viewModel.streakDays.collectAsState()
    val goals by viewModel.savingsGoals.collectAsState()
    val recurring by viewModel.recurringTransactions.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val safeToSpend by viewModel.safeToSpend.collectAsState(initial = 0.0)
    val badges by viewModel.unlockedBadges.collectAsState()
    val aiRecap by viewModel.aiRecapText.collectAsState()

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val totalBalance = accounts.sumOf { it.balance }

    // Bills due in the next 7 days
    val upcomingBills = remember(recurring) {
        val now = System.currentTimeMillis()
        val weekEnd = now + 7L * 24 * 60 * 60 * 1000
        recurring
            .filter { it.type == TransactionType.EXPENSE && it.nextExecutionDate in now..weekEnd }
            .sortedBy { it.nextExecutionDate }
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

    Scaffold(
        topBar = {
            val isDark = LocalThemeIsDark.current
            iOSTopAppBar(
                title = "Finance",
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (isPrivacy) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPrivacy) "Show balances" else "Hide balances"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val isDark = LocalThemeIsDark.current
            FloatingActionButton(
                onClick = onNavigateToQuickEntry,
                containerColor = iOSBlue,
                contentColor = DeepBackground,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick Entry", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = if (LocalThemeIsDark.current) iOSBackgroundDark else iOSBackgroundLight
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            cashflowWarning?.let { warningMsg ->
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Row(
                            modifier = Modifier.padding(iOSSpacingMedium),
                            horizontalArrangement = Arrangement.spacedBy(iOSSpacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = iOSOrange)
                            Text(warningMsg, style = iOSCallout)
                        }
                    }
                }
            }

            val unverifiedTxs = transactions.filter { it.isAutoLogged && !it.isVerified }
            if (unverifiedTxs.isNotEmpty()) {
                item {
                    iOSCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = "Review", tint = WarningAmber, modifier = Modifier.size(18.dp))
                                    Text("Review Needed", style = Typography.titleMedium.copy(color = TextPrimary))
                                }
                                Text("${unverifiedTxs.size} pending", style = Typography.labelMedium.copy(color = TextSecondary))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            unverifiedTxs.take(3).forEach { tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.merchantName ?: tx.note, style = Typography.bodyMedium.copy(color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(moneyString(tx.amount, isPrivacy), style = Typography.labelMedium.copy(color = AlertRed))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { viewModel.deleteTransaction(tx) },
                                            modifier = Modifier.size(32.dp).background(DarkSurface, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = AlertRed, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.updateTransaction(tx, tx.copy(isVerified = true)) },
                                            modifier = Modifier.size(32.dp).background(DarkSurface, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Approve", tint = AccentGreen, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Total Balance Header - Minimalist Redesign
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = moneyString(totalBalance, isPrivacy, decimals = 2),
                        style = Typography.displayMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Balance",
                        style = Typography.labelLarge.copy(color = TextSecondary)
                    )
                    
                    if (streakDays >= 2) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarningAmber.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = WarningAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "$streakDays-day streak",
                                style = Typography.labelSmall.copy(color = WarningAmber, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // 1.5 Quick Actions Row — with icon glow + press scale
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Net Worth quick action
                    val interactionSource1 = remember { MutableInteractionSource() }
                    val isPressed1 by interactionSource1.collectIsPressedAsState()
                    val scale1 by animateFloatAsState(
                        targetValue = if (isPressed1) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "quickScale1"
                    )
                    iOSCard(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale1)
                            .clickable(interactionSource = interactionSource1, indication = null) { onNavigateToNetWorth() }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Net Worth", style = Typography.titleMedium.copy(color = TextPrimary))
                            Text("Track growth", style = Typography.labelSmall.copy(color = TextMuted))
                        }
                    }
                    
                    // IOU Tracker quick action
                    val interactionSource2 = remember { MutableInteractionSource() }
                    val isPressed2 by interactionSource2.collectIsPressedAsState()
                    val scale2 by animateFloatAsState(
                        targetValue = if (isPressed2) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "quickScale2"
                    )
                    iOSCard(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale2)
                            .clickable(interactionSource = interactionSource2, indication = null) { onNavigateToDebt() }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("IOU Tracker", style = Typography.titleMedium.copy(color = TextPrimary))
                            Text("Debts & loans", style = Typography.labelSmall.copy(color = TextMuted))
                        }
                    }
                }
            }

            // AI Recap Card
            aiRecap?.let { text ->
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = PrimaryViolet)
                            Text(
                                text,
                                style = Typography.bodyMedium.copy(color = TextPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Badges Row
            if (badges.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Achievements", style = Typography.titleMedium.copy(color = TextPrimary), modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(badges) { badge ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                    Text(badge, style = Typography.labelMedium.copy(color = TextSecondary))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Upcoming bills strip (only when something is due this week)
            if (upcomingBills.isNotEmpty()) {
                item {
                    iOSCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.EventRepeat, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                    Text("Upcoming this week", style = Typography.titleMedium.copy(color = TextPrimary))
                                }
                                Text(
                                    "See all",
                                    style = Typography.labelMedium.copy(color = SecondaryTeal),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNavigateToSubscriptions() }
                                        .padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            upcomingBills.take(3).forEach { bill ->
                                val days = daysUntil(bill.nextExecutionDate)
                                val dueLabel = when {
                                    days <= 0 -> "today"
                                    days == 1 -> "tomorrow"
                                    else -> "in $days days"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        bill.note.ifEmpty { "Recurring bill" },
                                        style = Typography.bodyMedium.copy(color = TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${moneyString(bill.amount, isPrivacy)} • $dueLabel",
                                        style = Typography.labelMedium.copy(
                                            color = if (days <= 1) AlertRed else TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Savings goals snapshot
            if (goals.isNotEmpty()) {
                item {
                    iOSCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                    Text("Savings Goals", style = Typography.titleMedium.copy(color = TextPrimary))
                                }
                                Text(
                                    "View all",
                                    style = Typography.labelMedium.copy(color = SecondaryTeal),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNavigateToGoals() }
                                        .padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            goals.sortedByDescending { if (it.targetAmount > 0) it.savedAmount / it.targetAmount else 0.0 }
                                .take(3)
                                .forEach { goal ->
                                    val color = try {
                                        Color(android.graphics.Color.parseColor(goal.colorHex))
                                    } catch (e: Exception) {
                                        AccentGreen
                                    }
                                    val ratio = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
                                    val animatedGoalRatio by animateFloatAsState(
                                        targetValue = ratio.coerceIn(0f, 1f),
                                        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
                                        label = "goalProgress"
                                    )
                                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(goal.name, style = Typography.labelLarge.copy(color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${(ratio * 100).toInt()}%",
                                                style = Typography.labelMedium.copy(color = color, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        LinearProgressIndicator(
                                            progress = { animatedGoalRatio },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape),
                                            color = color,
                                            trackColor = BorderColor
                                        )
                                    }
                                }
                        }
                    }
                }
            }

            // 4. Accounts List (Horizontal Carousel)
            item {
                Column {
                    Text("Accounts", style = Typography.titleMedium.copy(color = TextPrimary))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(accounts, key = { it.id }) { account ->
                            AccountCard(account, isPrivacy, modifier = Modifier.animateItem())
                        }
                    }
                }
            }

            // 5. Envelope Budgets / Categories
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Envelope Budgets", style = Typography.titleMedium.copy(color = TextPrimary))
                    Text(
                        "Manage",
                        style = Typography.labelMedium.copy(color = SecondaryTeal),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onNavigateToBudget() }
                            .padding(6.dp)
                    )
                }
            }

            items(categories, key = { "cat-${it.id}" }) { category ->
                // Spending this month only, from the shared monthly map
                val categorySpent = monthlyCategorySpend[category.id] ?: 0.0
                val now = java.util.Calendar.getInstance()
                EnvelopeProgressItem(
                    category, 
                    categorySpent, 
                    isPrivacy, 
                    modifier = Modifier
                        .animateItem()
                        .clickable { onNavigateToCategory(category.id, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)) }
                )
            }

            // 6. Recent Transactions Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Transactions", style = Typography.titleMedium.copy(color = TextPrimary))
                    Row {
                        TextButton(onClick = onNavigateToSmsTransactions) {
                            Text("SMS", style = Typography.labelLarge.copy(color = WarningAmber))
                        }
                        TextButton(onClick = onNavigateToLogs) {
                            Text("View All Logs", style = Typography.labelLarge.copy(color = SecondaryTeal))
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No transactions logged yet.", style = Typography.bodyMedium.copy(color = TextMuted))
                    }
                }
            }

            items(transactions.take(10), key = { "tx-${it.id}" }) { transaction ->
                val accountName = accounts.firstOrNull { it.id == transaction.sourceAccountId }?.name ?: "Account"
                val categoryName = categories.firstOrNull { it.id == transaction.categoryId }?.name ?: "Income/Transfer"
                TransactionItem(
                    transaction = transaction,
                    accountName = accountName,
                    categoryName = categoryName,
                    privacy = isPrivacy,
                    onEdit = { editingTransaction = transaction },
                    onDelete = { deleteWithUndo(transaction) },
                    modifier = Modifier.animateItem()
                )
            }

            // Padding at bottom
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    // Edit Transaction Dialog on Dashboard
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

@Composable
fun AccountCard(account: Account, privacy: Boolean = false, modifier: Modifier = Modifier) {
    val accent = when (account.type) {
        com.example.financemanager.data.AccountType.BANK -> SecondaryTeal
        com.example.financemanager.data.AccountType.CASH -> AccentGreen
        com.example.financemanager.data.AccountType.CREDIT_CARD -> AlertRed
        com.example.financemanager.data.AccountType.WALLET -> WarningAmber
    }

    iOSCard(
        modifier = modifier
            .width(160.dp)
            .height(105.dp),
        style = iOSCardStyle.Grouped
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        account.name,
                        style = Typography.labelMedium.copy(color = TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column {
                    Text(
                        moneyString(account.balance, privacy),
                        style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        account.type.name,
                        style = TextStyle(color = TextSecondary, fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun EnvelopeProgressItem(category: Category, spent: Double, privacy: Boolean = false, modifier: Modifier = Modifier) {
    val limit = category.budgetLimit
    val remaining = maxOf(0.0, limit - spent)
    val ratio = if (limit > 0) (spent / limit).toFloat() else 0f

    // Choose progress color
    val progressColor = when {
        ratio > 1.0f -> AlertRed
        ratio > 0.8f -> WarningAmber
        else -> AccentGreen
    }

    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "envelopeRatio"
    )

    iOSCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, BorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(category.iconName),
                            contentDescription = category.name,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(category.name, style = Typography.titleMedium.copy(color = TextPrimary))
                        Text("Limit: ${moneyString(limit, privacy)}", style = Typography.labelMedium.copy(color = TextSecondary))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${moneyString(remaining, privacy)} left",
                        style = Typography.titleMedium.copy(
                            color = if (remaining > 0) TextPrimary else AlertRed,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text("Spent: ${moneyString(spent, privacy)}", style = Typography.labelMedium.copy(color = TextSecondary))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = BorderColor
            )
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    accountName: String,
    categoryName: String,
    privacy: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> AlertRed
        TransactionType.INCOME -> AccentGreen
        TransactionType.TRANSFER -> SecondaryTeal
    }

    val prefix = when (transaction.type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> ""
    }

    val dateFormatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormatter.format(Date(transaction.date))

    iOSCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (transaction.type) {
                            TransactionType.EXPENSE -> Icons.Outlined.ArrowDownward
                            TransactionType.INCOME -> Icons.Outlined.ArrowUpward
                            TransactionType.TRANSFER -> Icons.Outlined.SwapHoriz
                        }
                        Icon(icon, contentDescription = transaction.type.name, tint = amountColor, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text(
                            transaction.note.ifEmpty { categoryName },
                            style = Typography.titleMedium.copy(color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$accountName • $formattedDate",
                            style = Typography.labelMedium.copy(color = TextSecondary)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "$prefix${moneyString(transaction.amount, privacy)}",
                        style = Typography.titleMedium.copy(color = amountColor, fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SecondaryTeal, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(iconName: String): ImageVector = when (iconName) {
    "restaurant" -> Icons.Default.Restaurant
    "shopping_bag" -> Icons.Default.ShoppingBag
    "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
    "home" -> Icons.Default.Home
    "movie" -> Icons.Default.Movie
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    else -> Icons.Default.Category
}
