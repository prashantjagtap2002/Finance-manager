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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSTopAppBar
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
    onNavigateToFinancialTools: () -> Unit = {},
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
    val monthlyIncome by viewModel.monthlyIncomeTotal.collectAsState()
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
    var showInsightsSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var updatingRolloverCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var showDashboardCustomization by remember { mutableStateOf(false) }
    var dashboardSections by remember { mutableStateOf(FinancePreferences.dashboardSections()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val totalBalance = remember(accounts) { accounts.sumOf { it.balance } }
    val accountNames = remember(accounts) { accounts.associateBy { it.id } }
    val categoryNames = remember(categories) { categories.associateBy { it.id } }
    val unverifiedTxs = remember(transactions) {
        transactions.filter { it.isAutoLogged && !it.isVerified }
    }

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
                    IconButton(onClick = { showDashboardCustomization = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Customize dashboard")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val isDark = LocalThemeIsDark.current
            FloatingActionButton(
                onClick = onNavigateToQuickEntry,
                // The accent runs light on a dark canvas and deep on a light one,
                // so the glyph has to flip with it to stay legible.
                containerColor = PrimaryViolet,
                contentColor = if (isDark) Color(0xFF13122B) else Color.White,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick Entry", modifier = Modifier.size(28.dp))
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
                                            modifier = Modifier.size(32.dp).background(BorderColor.copy(alpha = 0.2f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = AlertRed, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.updateTransaction(tx, tx.copy(isVerified = true)) },
                                            modifier = Modifier.size(32.dp).background(BorderColor.copy(alpha = 0.2f), CircleShape)
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

            // 1. Hero Card: Displays Current Month Spent & Total Balance (Clickable for Financial Insights & Balances)
            item {
                iOSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInsightsSheet = true },
                    style = iOSCardStyle.Grouped
                ) {
                    // Left-aligned and unpilled: one small label, one large number,
                    // a hairline, then the secondary figure. The number is the
                    // only thing on this card that should be loud.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp, horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SPENT THIS MONTH",
                                style = iOSCaption2.copy(color = TextMuted, letterSpacing = 1.sp)
                            )
                            if (streakDays >= 2) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = "Streak",
                                        tint = WarningAmber,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        "$streakDays days",
                                        style = iOSCaption2.copy(color = TextMuted)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = moneyString(monthlySpent, isPrivacy, decimals = 2),
                            style = Typography.displayMedium.copy(color = TextPrimary)
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total balance",
                                    style = Typography.labelMedium.copy(color = TextMuted)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = moneyString(totalBalance, isPrivacy, decimals = 0),
                                    style = Typography.titleLarge.copy(color = TextPrimary)
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Insights",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
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
            if ("intelligence" in dashboardSections) item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToFinancialTools() },
                    style = iOSCardStyle.Grouped
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = SecondaryTeal)
                        Column(Modifier.weight(1f)) {
                            Text("Financial Intelligence", style = Typography.titleMedium.copy(color = TextPrimary))
                            Text("Health score, what-if plans, merchants & calendar", style = Typography.bodySmall.copy(color = TextSecondary))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }

            aiRecap?.let { text ->
                if ("intelligence" in dashboardSections) item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToFinancialTools() },
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
                        SectionHeader("Achievements", modifier = Modifier.padding(bottom = 8.dp))
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
                                    Icon(Icons.Default.EventRepeat, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
                                    Text("Upcoming this week", style = Typography.titleMedium.copy(color = TextPrimary))
                                }
                                SectionAction("See all", onNavigateToSubscriptions)
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
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
                                    Text("Savings Goals", style = Typography.titleMedium.copy(color = TextPrimary))
                                }
                                SectionAction("View all", onNavigateToGoals)
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
            if ("accounts" in dashboardSections) item {
                Column {
                    SectionHeader("Accounts")
                    Spacer(modifier = Modifier.height(10.dp))
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
            if ("budgets" in dashboardSections) item {
                SectionHeader("Envelope Budgets") {
                    SectionAction("Manage", onNavigateToBudget)
                }
            }

            if ("budgets" in dashboardSections) items(categories, key = { "cat-${it.id}" }) { category ->
                // Spending this month only, from the shared monthly map
                val categorySpent = monthlyCategorySpend[category.id] ?: 0.0
                val now = java.util.Calendar.getInstance()
                EnvelopeProgressItem(
                    category = category, 
                    spent = categorySpent, 
                    privacy = isPrivacy, 
                    onEdit = { editingCategory = it },
                    onUpdateRollover = { updatingRolloverCategory = it },
                    onDelete = { deletingCategory = it },
                    modifier = Modifier
                        .animateItem()
                        .clickable { onNavigateToCategory(category.id, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)) }
                )
            }

            // 6. Recent Transactions Section Header
            if ("transactions" in dashboardSections) item {
                SectionHeader("Recent Transactions") {
                    SectionAction("SMS", onNavigateToSmsTransactions)
                    Spacer(modifier = Modifier.width(4.dp))
                    SectionAction("View all", onNavigateToLogs)
                }
            }

            if ("transactions" in dashboardSections && transactions.isEmpty()) {
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

            if ("transactions" in dashboardSections) items(transactions.take(10), key = { "tx-${it.id}" }) { transaction ->
                val accountName = accountNames[transaction.sourceAccountId]?.name ?: "Account"
                val categoryName = categoryNames[transaction.categoryId]?.name ?: "Income/Transfer"
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

    if (showDashboardCustomization) {
        AlertDialog(
            onDismissRequest = { showDashboardCustomization = false },
            containerColor = DarkSurface,
            title = { Text("Customize dashboard", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose the sections you want to see.", color = TextSecondary)
                            listOf("accounts" to "Accounts", "budgets" to "Envelope budgets", "transactions" to "Recent transactions", "intelligence" to "Financial intelligence").forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            dashboardSections = if (key in dashboardSections) dashboardSections - key else dashboardSections + key
                        }) {
                            Checkbox(checked = key in dashboardSections, onCheckedChange = { checked -> dashboardSections = if (checked) dashboardSections + key else dashboardSections - key })
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { FinancePreferences.setDashboardSections(dashboardSections); showDashboardCustomization = false }) { Text("Done", color = AccentGreen) }
            }
        )
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

    // Edit Category Envelope Dialog
    editingCategory?.let { category ->
        var newLimitInput by remember { mutableStateOf(category.budgetLimit.toInt().toString()) }
        var newNameInput by remember { mutableStateOf(category.name) }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            containerColor = DarkSurface,
            title = { Text("Edit Envelope Budget", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLimitInput,
                        onValueChange = { newLimitInput = it },
                        label = { Text("Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val limitVal = newLimitInput.toDoubleOrNull() ?: category.budgetLimit
                    viewModel.updateCategory(category.copy(name = newNameInput.trim(), budgetLimit = limitVal))
                    editingCategory = null
                }) {
                    Text("Save", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Update Rollover / Extra Funds Dialog
    updatingRolloverCategory?.let { category ->
        var rolloverInput by remember { mutableStateOf(category.rolloverAmount.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { updatingRolloverCategory = null },
            containerColor = DarkSurface,
            title = { Text("Update Rollover Funds", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add or update extra rollover funds carried into '${category.name}'.", style = Typography.bodySmall.copy(color = TextSecondary))
                    OutlinedTextField(
                        value = rolloverInput,
                        onValueChange = { rolloverInput = it },
                        label = { Text("Rollover Funds (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val valInput = rolloverInput.toDoubleOrNull() ?: 0.0
                    viewModel.updateCategory(category.copy(rolloverAmount = valInput))
                    updatingRolloverCategory = null
                }) {
                    Text("Update", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { updatingRolloverCategory = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Delete Category Envelope Dialog
    deletingCategory?.let { category ->
        DeleteCategoryDialog(
            category = category,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { deletingCategory = null }
        )
    }

    // Financial Insights & Balances Bottom Sheet
    if (showInsightsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInsightsSheet = false },
            containerColor = DeepBackground,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Financial Overview",
                        style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showInsightsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Total Balance Card
                iOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Account Balance", style = Typography.labelMedium.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            moneyString(totalBalance, isPrivacy, decimals = 2),
                            style = Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Income vs Spend vs Net Cashflow Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    iOSCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Income", style = Typography.labelSmall.copy(color = TextMuted))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                moneyString(monthlyIncome, isPrivacy, decimals = 0),
                                style = Typography.titleMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    iOSCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Expenses", style = Typography.labelSmall.copy(color = TextMuted))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                moneyString(monthlySpent, isPrivacy, decimals = 0),
                                style = Typography.titleMedium.copy(color = AlertRed, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    iOSCard(modifier = Modifier.weight(1f)) {
                        val net = monthlyIncome - monthlySpent
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Net Flow", style = Typography.labelSmall.copy(color = TextMuted))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                moneyString(net, isPrivacy, decimals = 0),
                                style = Typography.titleMedium.copy(
                                    color = if (net >= 0) AccentGreen else AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Balances List
                Text("Accounts Breakdown", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))

                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryViolet.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(acc.name, style = Typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                                Text(acc.type.name, style = Typography.labelSmall.copy(color = TextMuted))
                            }
                        }
                        Text(
                            moneyString(acc.balance, isPrivacy),
                            style = Typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Button to open full Reports & Insights
                iOSButton(
                    onClick = {
                        showInsightsSheet = false
                        onNavigateToInsights()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Detailed Reports & Insights ➔")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Section label for the dashboard feed. Small, tracked-out and muted so the
 * headings recede and the figures under them carry the page.
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = iOSCaption2.copy(color = TextMuted, letterSpacing = 1.sp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** The one link treatment in the app: accent, small, no button chrome. */
@Composable
private fun SectionAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = Typography.labelMedium.copy(color = PrimaryViolet, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun AccountCard(account: Account, privacy: Boolean = FinancePreferences.privacyMode, modifier: Modifier = Modifier) {
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
fun EnvelopeProgressItem(
    category: Category, 
    spent: Double, 
    privacy: Boolean = FinancePreferences.privacyMode, 
    onEdit: (Category) -> Unit = {},
    onUpdateRollover: (Category) -> Unit = {},
    onDelete: (Category) -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
                        Text(
                            category.name,
                            style = Typography.titleMedium.copy(color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Limit: ${moneyString(limit, privacy)}", style = Typography.labelMedium.copy(color = TextSecondary))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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

                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { onEdit(category) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = SecondaryTeal, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onUpdateRollover(category) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Update Rollover", tint = AccentGreen, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onDelete(category) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Envelope", tint = AlertRed, modifier = Modifier.size(16.dp))
                    }
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
    privacy: Boolean = FinancePreferences.privacyMode,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Expenses are the default case in this list, so they stay neutral — a
    // column of red numbers reads as an alert rather than a ledger. Only money
    // arriving gets colour.
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> TextPrimary
        TransactionType.INCOME -> AccentGreen
        TransactionType.TRANSFER -> TextSecondary
    }
    val iconColor = when (transaction.type) {
        TransactionType.INCOME -> AccentGreen
        else -> TextMuted
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
                        Icon(icon, contentDescription = transaction.type.name, tint = iconColor, modifier = Modifier.size(22.dp))
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
                        style = Typography.titleMedium.copy(color = amountColor, fontWeight = FontWeight.SemiBold)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(16.dp))
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
