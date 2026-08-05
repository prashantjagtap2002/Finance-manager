package com.example.financemanager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DynamicDateFilter(val label: String) {
    THIS_CYCLE("This Cycle"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    YTD("Year to Date"),
    CUSTOM("Custom")
}

private fun utcToLocalStartOfDay(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)
    val localCal = Calendar.getInstance().apply {
        set(year, month, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
}

private fun utcToLocalEndOfDay(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)
    val localCal = Calendar.getInstance().apply {
        set(year, month, day, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return localCal.timeInMillis
}

private fun computeDynamicDateRange(
    filterType: DynamicDateFilter,
    periodOffset: Int,
    customStart: Long?,
    customEnd: Long?
): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    fun startOfDay(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    fun endOfDay(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    val now = System.currentTimeMillis()
    
    return when (filterType) {
        DynamicDateFilter.THIS_CYCLE -> {
            cal.timeInMillis = now
            cal.add(Calendar.MONTH, periodOffset)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val start = startOfDay(cal.timeInMillis)
            if (periodOffset == 0) {
                start to endOfDay(now)
            } else {
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to endOfDay(cal.timeInMillis)
            }
        }
        DynamicDateFilter.LAST_MONTH -> {
            cal.timeInMillis = now
            cal.add(Calendar.MONTH, -1 + periodOffset)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val start = startOfDay(cal.timeInMillis)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            start to endOfDay(cal.timeInMillis)
        }
        DynamicDateFilter.LAST_3_MONTHS -> {
            cal.timeInMillis = now
            cal.add(Calendar.MONTH, -3 + (periodOffset * 3))
            val start = startOfDay(cal.timeInMillis)
            cal.add(Calendar.MONTH, 3)
            cal.add(Calendar.DAY_OF_MONTH, -1)
            val end = if (periodOffset == 0 && cal.timeInMillis > now) now else cal.timeInMillis
            start to endOfDay(end)
        }
        DynamicDateFilter.YTD -> {
            cal.timeInMillis = now
            cal.add(Calendar.YEAR, periodOffset)
            cal.set(Calendar.DAY_OF_YEAR, 1)
            val start = startOfDay(cal.timeInMillis)
            if (periodOffset == 0) {
                start to endOfDay(now)
            } else {
                cal.set(Calendar.MONTH, 11)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                start to endOfDay(cal.timeInMillis)
            }
        }
        DynamicDateFilter.CUSTOM -> {
            (customStart ?: startOfDay(now)) to (customEnd ?: endOfDay(now))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (Long, String, String) -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()
    val goals by viewModel.savingsGoals.collectAsState()

    var currentFilterType by remember { mutableStateOf(DynamicDateFilter.THIS_CYCLE) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var customRangeStart by remember { mutableStateOf<Long?>(null) }
    var customRangeEnd by remember { mutableStateOf<Long?>(null) }
    var selectedDayTimestamp by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentFilterType) {
        periodOffset = 0
        selectedDayTimestamp = null
    }

    val (rangeStart, rangeEnd) = remember(currentFilterType, periodOffset, customRangeStart, customRangeEnd) {
        computeDynamicDateRange(currentFilterType, periodOffset, customRangeStart, customRangeEnd)
    }

    val (effectiveStart, effectiveEnd) = remember(rangeStart, rangeEnd, selectedDayTimestamp) {
        if (selectedDayTimestamp != null) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDayTimestamp!!
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val s = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val e = cal.timeInMillis
            s to e
        } else {
            rangeStart to rangeEnd
        }
    }

    // Expenses for the full period trend (bar graph)
    val periodExpenses = remember(transactions, rangeStart, rangeEnd) {
        transactions.filter { it.type == TransactionType.EXPENSE && it.date in rangeStart..rangeEnd }
    }

    // Everything on this page is scoped to [effectiveStart, effectiveEnd] (either single selected day or full period)
    val filteredTransactions = remember(transactions, effectiveStart, effectiveEnd) {
        transactions.filter { it.date in effectiveStart..effectiveEnd }
    }
    val filteredExpenses = filteredTransactions.filter { it.type == TransactionType.EXPENSE }
    val filteredIncome = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = filteredExpenses.sumOf { it.amount }

    // 1. Category breakdown (drives the donut chart)
    val defaultColor = TextPrimary
    val categoryBreakdown = remember(filteredExpenses, categories, defaultColor) {
        val map = mutableMapOf<Long, Double>()
        filteredExpenses.forEach {
            map[it.categoryId] = (map[it.categoryId] ?: 0.0) + it.amount
        }

        // Vibrant palette for the pie chart
        val palette = listOf(
            Color(0xFF3B82F6), // Blue
            Color(0xFF10B981), // Green
            Color(0xFFF59E0B), // Yellow/Orange
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6), // Purple
            Color(0xFFEF4444), // Red
            Color(0xFF14B8A6), // Teal
            Color(0xFFF97316)  // Orange
        )

        val shares = map.mapNotNull { entry ->
            val cat = categories.firstOrNull { it.id == entry.key } ?: return@mapNotNull null
            CategoryShare(
                categoryId = cat.id,
                name = cat.name,
                amount = entry.value,
                color = defaultColor // Temporary, will be assigned below
            )
        }.sortedByDescending { it.amount }

        // Assign palette colors by rank
        shares.mapIndexed { index, share ->
            share.copy(color = palette[index % palette.size])
        }
    }

    // 2. Peak spending day & category, within the selected range
    val peakDayAndCategory = remember(filteredExpenses, categories) {
        if (filteredExpenses.isEmpty()) Pair("None", "None")
        else {
            val dayFormatter = SimpleDateFormat("EEEE", Locale.getDefault())
            val dayGroups = filteredExpenses.groupBy { dayFormatter.format(Date(it.date)) }
            val peakDay = dayGroups.maxByOrNull { it.value.sumOf { t -> t.amount } }?.key ?: "None"

            val catGroups = filteredExpenses.groupBy { it.categoryId }
            val peakCatId = catGroups.maxByOrNull { it.value.sumOf { t -> t.amount } }?.key
            val peakCategory = categories.firstOrNull { it.id == peakCatId }?.name ?: "None"
            Pair(peakDay, peakCategory)
        }
    }

    // 3. Narrative comparison: selected period vs. the immediately preceding period of equal length
    val narrativeInsights = remember(transactions, categories, rangeStart, rangeEnd) {
        val periodLength = rangeEnd - rangeStart + 1
        val prevStart = rangeStart - periodLength
        val prevEnd = rangeStart - 1

        val curExpensesLocal = transactions.filter { it.type == TransactionType.EXPENSE && it.date in rangeStart..rangeEnd }
        val prevExpenses = transactions.filter { it.type == TransactionType.EXPENSE && it.date in prevStart..prevEnd }
        val curIncomeLocal = transactions.filter { it.type == TransactionType.INCOME && it.date in rangeStart..rangeEnd }.sumOf { it.amount }

        val curTotal = curExpensesLocal.sumOf { it.amount }
        val prevTotal = prevExpenses.sumOf { it.amount }

        val lines = mutableListOf<Pair<String, Boolean>>() // text to isPositive
        if (prevTotal > 0 && curTotal > 0) {
            val changePct = ((curTotal - prevTotal) / prevTotal * 100).toInt()
            when {
                changePct > 5 -> lines.add("You've spent $changePct% more than the previous period." to false)
                changePct < -5 -> lines.add("Nice! Spending is ${-changePct}% lower than the previous period." to true)
                else -> lines.add("Your spending is on par with the previous period." to true)
            }

            // Biggest category swing
            val curByCat = curExpensesLocal.groupBy { it.categoryId }.mapValues { it.value.sumOf { t -> t.amount } }
            val prevByCat = prevExpenses.groupBy { it.categoryId }.mapValues { it.value.sumOf { t -> t.amount } }
            val biggestJump = curByCat
                .mapNotNull { (catId, cur) ->
                    val prev = prevByCat[catId] ?: 0.0
                    if (prev > 0 && cur > prev * 1.2) {
                        val cat = categories.firstOrNull { it.id == catId } ?: return@mapNotNull null
                        Triple(cat.name, ((cur - prev) / prev * 100).toInt(), cur - prev)
                    } else null
                }
                .maxByOrNull { it.third }
            biggestJump?.let { (name, pct, _) ->
                lines.add("$name is up $pct% vs the previous period — worth a look." to false)
            }
        }
        if (curIncomeLocal > 0) {
            val net = curIncomeLocal - curTotal
            if (net > 0) {
                lines.add("On track to save ${moneyString(net, false)} this period." to true)
            } else {
                lines.add("You're ${moneyString(-net, false)} over your income this period." to false)
            }
        }
        lines
    }

    // 4. Achievements are lifetime milestones, intentionally not scoped to the date filter
    val achievements = remember(transactions, streakDays, goals) {
        listOf(
            Achievement("First Steps", "Log your first transaction", transactions.isNotEmpty()),
            Achievement("On Fire", "Keep a 7-day logging streak", streakDays >= 7),
            Achievement("Century Club", "Log 100 transactions", transactions.size >= 100),
            Achievement("Goal Setter", "Create a savings goal", goals.isNotEmpty()),
            Achievement("Goal Getter", "Fully fund a savings goal", goals.any { it.targetAmount > 0 && it.savedAmount >= it.targetAmount })
        )
    }

    // 5. Cash flow forecast to the end of the selected period
    val currentBalance = accounts.sumOf { it.balance }
    val forecastEndBalance = remember(currentBalance, filteredExpenses, rangeStart, rangeEnd) {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val elapsedDays = ((minOf(now, rangeEnd) - rangeStart + 1).coerceAtLeast(dayMs)) / dayMs.toDouble()
        val avgDailyBurn = filteredExpenses.sumOf { it.amount } / elapsedDays
        val remainingDays = (rangeEnd - maxOf(now, rangeStart)).coerceAtLeast(0) / dayMs.toDouble()
        maxOf(0.0, currentBalance - avgDailyBurn * remainingDays)
    }
    val periodIsInFuture = rangeEnd > System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Insights", style = com.example.financemanager.theme.Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    // Export CSV
                    IconButton(onClick = {
                        val path = viewModel.exportTransactionsToCsv(context)
                        if (path != null) {
                            Toast.makeText(context, "Exported: $path", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "No transactions to export", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Report", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Dynamic Date Range Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DynamicDateFilter.values().forEach { filter ->
                    val isSelected = currentFilterType == filter
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) PrimaryViolet else DeepBackground,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "pill_bg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) DeepBackground else TextPrimary,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "pill_text"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (filter == DynamicDateFilter.CUSTOM) {
                                    showDatePicker = true
                                } else {
                                    currentFilterType = filter
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter.label,
                            color = textColor,
                            style = com.example.financemanager.theme.Typography.labelMedium
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header showing selected date range with period navigation chevrons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dayFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        val dateText = if (selectedDayTimestamp != null) {
                            "${dayFmt.format(Date(selectedDayTimestamp!!))} (Single Day)"
                        } else {
                            "${dayFmt.format(Date(rangeStart))} – ${dayFmt.format(Date(rangeEnd))}"
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    periodOffset--
                                    selectedDayTimestamp = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Period",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = dateText,
                                style = com.example.financemanager.theme.Typography.titleSmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    if (periodOffset < 0) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        periodOffset++
                                        selectedDayTimestamp = null
                                    }
                                },
                                enabled = periodOffset < 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Period",
                                    tint = if (periodOffset < 0) TextSecondary else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (selectedDayTimestamp != null) {
                            Text(
                                text = "Show full period",
                                style = com.example.financemanager.theme.Typography.labelSmall.copy(color = SecondaryTeal, fontWeight = FontWeight.Bold),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDayTimestamp = null
                                }
                            )
                        }
                    }
                }
                
                // 0. Bar Chart Card (Supports horizontal swipe left/right to navigate periods)
                item {
                    var totalDragAmount by remember { mutableFloatStateOf(0f) }
                    iOSCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (totalDragAmount > 60f) {
                                            // Swiped Left to Right -> Move to Previous Period
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            periodOffset--
                                            selectedDayTimestamp = null
                                        } else if (totalDragAmount < -60f) {
                                            // Swiped Right to Left -> Move to Next Period
                                            if (periodOffset < 0) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                periodOffset++
                                                selectedDayTimestamp = null
                                            }
                                        }
                                        totalDragAmount = 0f
                                    },
                                    onDragCancel = { totalDragAmount = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        totalDragAmount += dragAmount
                                    }
                                )
                            },
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                "Daily Spending Trend",
                                style = com.example.financemanager.theme.Typography.titleMedium.copy(color = TextPrimary)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            DailyExpenseBarChart(
                                expenses = periodExpenses,
                                rangeStart = rangeStart,
                                rangeEnd = rangeEnd,
                                selectedDayTimestamp = selectedDayTimestamp,
                                onDaySelected = { selectedDayTimestamp = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // 1. Donut Chart Card
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Expense Category Breakdown",
                                style = com.example.financemanager.theme.Typography.titleMedium.copy(color = TextPrimary),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (filteredExpenses.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .height(160.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No expense logs found for the selected period.", style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextMuted))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    DonutChart(
                                        categoryBreakdown = categoryBreakdown,
                                        totalExpense = totalExpense,
                                        modifier = Modifier.size(130.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        categoryBreakdown.forEach { item ->
                                            val percent = if (totalExpense > 0) (item.amount / totalExpense) * 100 else 0.0
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.clickable {
                                                    val cal = Calendar.getInstance()
                                                    cal.timeInMillis = rangeStart
                                                    val monthStr = cal.get(Calendar.MONTH).toString()
                                                    val yearStr = cal.get(Calendar.YEAR).toString()
                                                    onNavigateToCategory(item.categoryId, yearStr, monthStr)
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(item.color)
                                                )
                                                Column {
                                                    Text(
                                                        item.name,
                                                        style = com.example.financemanager.theme.Typography.labelMedium.copy(color = TextPrimary),
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        "${moneyString(item.amount, false)} (${percent.toInt()}%)",
                                                        style = TextStyle(color = TextSecondary, fontSize = 10.sp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Key Insights Card
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Key Insights", style = com.example.financemanager.theme.Typography.titleMedium.copy(color = TextPrimary))
                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredExpenses.isEmpty()) {
                                Text(
                                    "No expenses in the selected period yet.",
                                    style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextMuted)
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryViolet)
                                    )
                                    Text(
                                        "Your peak spending day is ${peakDayAndCategory.first}.",
                                        style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextPrimary)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SecondaryTeal)
                                    )
                                    Text(
                                        "You spend the most on ${peakDayAndCategory.second}.",
                                        style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextPrimary)
                                    )
                                }
                            }

                            // Narrative comparison vs previous period
                            narrativeInsights.forEach { (text, isPositive) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isPositive) AccentGreen else WarningAmber)
                                    )
                                    Text(
                                        text,
                                        style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextPrimary)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Achievements Card (lifetime milestones)
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Achievements", style = com.example.financemanager.theme.Typography.titleMedium.copy(color = TextPrimary))
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${achievements.count { it.unlocked }}/${achievements.size}",
                                    style = com.example.financemanager.theme.Typography.labelLarge.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            achievements.forEach { achievement ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (achievement.unlocked) WarningAmber.copy(alpha = 0.18f)
                                                else BorderColor.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (achievement.unlocked) Icons.Default.EmojiEvents else Icons.Outlined.Lock,
                                            contentDescription = null,
                                            tint = if (achievement.unlocked) WarningAmber else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            achievement.title,
                                            style = com.example.financemanager.theme.Typography.titleSmall.copy(
                                                color = if (achievement.unlocked) TextPrimary else TextMuted,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            achievement.description,
                                            style = com.example.financemanager.theme.Typography.labelMedium.copy(color = TextMuted)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Cash Flow Forecast (to the end of the selected period)
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Cash Flow Forecast", style = com.example.financemanager.theme.Typography.titleMedium.copy(color = TextPrimary))
                            Spacer(modifier = Modifier.height(10.dp))
                            if (periodIsInFuture) {
                                Text(
                                    "Based on your current balance of ${moneyString(currentBalance, false)} and your spending pace this period, you're projected to have:",
                                    style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    moneyString(forecastEndBalance, false),
                                    style = com.example.financemanager.theme.Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "by ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(rangeEnd))}",
                                    style = com.example.financemanager.theme.Typography.labelMedium.copy(color = TextMuted)
                                )
                            } else {
                                Text(
                                    "Current balance across all accounts:",
                                    style = com.example.financemanager.theme.Typography.bodyMedium.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    moneyString(currentBalance, false),
                                    style = com.example.financemanager.theme.Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = datePickerState.selectedStartDateMillis
                    val end = datePickerState.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        customRangeStart = utcToLocalStartOfDay(start)
                        customRangeEnd = utcToLocalEndOfDay(end)
                        currentFilterType = DynamicDateFilter.CUSTOM
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DonutChart(
    categoryBreakdown: List<CategoryShare>,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(categoryBreakdown) {
        isVisible = false
        kotlinx.coroutines.delay(50)
        isVisible = true
    }

    val sweepProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "sweep_progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            categoryBreakdown.forEach { share ->
                val sweepAngle = ((share.amount / totalExpense) * 360f).toFloat()
                drawArc(
                    color = share.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * sweepProgress,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    style = Stroke(width = 24.dp.toPx())
                )
                startAngle += sweepAngle
            }
        }
        
        Text(
            text = moneyString(totalExpense, false),
            style = com.example.financemanager.theme.Typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            maxLines = 1
        )
    }
}

data class CategoryShare(val categoryId: Long, val name: String, val amount: Double, val color: Color)
data class Achievement(val title: String, val description: String, val unlocked: Boolean)

data class DayBucket(
    val timestamp: Long,
    val label: String,
    val fullDateStr: String,
    val amount: Double
)

@Composable
fun DailyExpenseBarChart(
    expenses: List<com.example.financemanager.data.Transaction>,
    rangeStart: Long,
    rangeEnd: Long,
    selectedDayTimestamp: Long?,
    onDaySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val cal = Calendar.getInstance()
    fun startOfDay(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val dayBuckets = remember(expenses, rangeStart, rangeEnd) {
        val dayFmtShort = SimpleDateFormat("d MMM", Locale.getDefault())
        val dayFmtFull = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dayNumFmt = SimpleDateFormat("d", Locale.getDefault())

        val dayAmountMap = mutableMapOf<Long, Double>()
        expenses.forEach { tx ->
            val day = startOfDay(tx.date)
            dayAmountMap[day] = (dayAmountMap[day] ?: 0.0) + tx.amount
        }

        val buckets = mutableListOf<DayBucket>()
        var current = startOfDay(rangeStart)
        val end = startOfDay(rangeEnd)
        val totalDays = ((end - current) / (24 * 60 * 60 * 1000L) + 1).coerceAtLeast(1)

        while (current <= end) {
            val amt = dayAmountMap[current] ?: 0.0
            val label = if (totalDays <= 14) dayFmtShort.format(Date(current)) else dayNumFmt.format(Date(current))
            val fullStr = dayFmtFull.format(Date(current))
            buckets.add(DayBucket(current, label, fullStr, amt))

            cal.timeInMillis = current
            cal.add(Calendar.DAY_OF_YEAR, 1)
            current = cal.timeInMillis
            if (buckets.size > 730) break
        }
        buckets
    }

    if (dayBuckets.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No data to chart", color = TextMuted)
        }
        return
    }

    val maxAmount = dayBuckets.maxOfOrNull { it.amount } ?: 1.0
    val maxScaled = if (maxAmount == 0.0) 1.0 else maxAmount

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(dayBuckets) {
        isVisible = false
        kotlinx.coroutines.delay(50)
        isVisible = true
    }

    val heightProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "bar_height"
    )

    val selectedBucket = dayBuckets.firstOrNull { it.timestamp == selectedDayTimestamp }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected day indicator (clean typography, no green box)
        if (selectedBucket != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedBucket.fullDateStr} • ${moneyString(selectedBucket.amount, false)}",
                    style = Typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Clear filter",
                    style = Typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Normal),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDaySelected(null)
                    }
                )
            }
        }

        val totalDays = dayBuckets.size
        val isScrollable = totalDays > 31

        val rowModifier = if (isScrollable) {
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .horizontalScroll(rememberScrollState())
        } else {
            Modifier
                .fillMaxWidth()
                .height(150.dp)
        }

        Row(
            modifier = rowModifier,
            horizontalArrangement = if (isScrollable) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            dayBuckets.forEachIndexed { index, bucket ->
                val isSelected = bucket.timestamp == selectedDayTimestamp
                val fraction = ((bucket.amount / maxScaled) * heightProgress).toFloat().coerceIn(0.04f, 1.0f)

                val barColor = when {
                    isSelected -> AccentGreen
                    selectedDayTimestamp != null -> PrimaryViolet.copy(alpha = 0.25f)
                    bucket.amount > 0 -> PrimaryViolet
                    else -> BorderColor.copy(alpha = 0.3f)
                }

                val showLabel = when {
                    isSelected -> true
                    totalDays <= 14 -> true
                    totalDays <= 31 -> (index % 5 == 0 || index == 0 || index == totalDays - 1)
                    else -> (index % 10 == 0 || index == 0 || index == totalDays - 1)
                }

                val itemModifier = if (isScrollable) {
                    Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                } else {
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                }

                Column(
                    modifier = itemModifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDaySelected(if (isSelected) null else bucket.timestamp)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Chart Area Box (Takes all available height above label)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isScrollable) 0.7f else 0.5f)
                                .fillMaxHeight(fraction = fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date label under bar (Fixed height at bottom)
                    Box(
                        modifier = Modifier.height(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showLabel) {
                            Text(
                                text = bucket.label,
                                style = Typography.labelSmall.copy(
                                    color = if (isSelected) AccentGreen else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
