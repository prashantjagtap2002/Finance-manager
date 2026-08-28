package com.example.financemanager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
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
    onNavigateToCustomInsight: () -> Unit = {},
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
    val palette = ChartPalette
    val categoryBreakdown = remember(filteredExpenses, categories, defaultColor, palette) {
        val map = mutableMapOf<Long, Double>()
        filteredExpenses.forEach {
            map[it.categoryId] = (map[it.categoryId] ?: 0.0) + it.amount
        }

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
                lines.add("On track to save ${moneyString(net)} this period." to true)
            } else {
                lines.add("You're ${moneyString(-net)} over your income this period." to false)
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
                title = { Text("Reports & Insights", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCustomInsight) {
                        Icon(Icons.Default.Add, contentDescription = "Create custom insight", tint = PrimaryViolet)
                    }
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
                DynamicDateFilter.entries.forEach { filter ->
                    val isSelected = currentFilterType == filter
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) PrimaryViolet else SubtleSurface,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "pill_bg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) OnAccent else TextSecondary,
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
                            style = Typography.labelMedium
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
                            if (currentFilterType != DynamicDateFilter.CUSTOM) {
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
                                    style = Typography.titleSmall.copy(color = TextSecondary)
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
                            } else {
                                Text(
                                    text = dateText,
                                    style = Typography.titleSmall.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = "Edit custom range",
                                        tint = SecondaryTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (selectedDayTimestamp != null) {
                            Text(
                                text = "Show full period",
                                style = Typography.labelSmall.copy(color = SecondaryTeal, fontWeight = FontWeight.Bold),
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
                                style = Typography.titleMedium.copy(color = TextPrimary)
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
                
                // 0.5. Daily Amount Line Graph (straight lines, tap a point to read the amount)
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            DailyAmountLineChart(
                                expenses = periodExpenses,
                                allTransactions = transactions,
                                currentBalance = currentBalance,
                                rangeStart = rangeStart,
                                rangeEnd = rangeEnd
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
                                style = Typography.titleMedium.copy(color = TextPrimary),
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
                                    Text("No expense logs found for the selected period.", style = Typography.bodyMedium.copy(color = TextMuted))
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
                                                        style = Typography.labelMedium.copy(color = TextPrimary),
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        "${moneyString(item.amount)} (${percent.toInt()}%)",
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

                // 1.5. Income vs. Expense Comparison Chart Card
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            IncomeVsExpenseComparisonChart(
                                income = filteredIncome,
                                expense = totalExpense
                            )
                        }
                    }
                }

                // 1.8. Cumulative Cash Flow Trajectory Curve Card
                item {
                    iOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = iOSCardStyle.Grouped
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            CumulativeCashFlowTrendChart(
                                expenses = periodExpenses,
                                rangeStart = rangeStart,
                                rangeEnd = rangeEnd
                            )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = PrimaryViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Key Insights", style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            if (filteredExpenses.isEmpty()) {
                                Text(
                                    "No expenses in the selected period yet.",
                                    style = Typography.bodyMedium.copy(color = TextMuted)
                                )
                            } else {
                                InsightRow(
                                    icon = Icons.Default.CalendarMonth,
                                    tint = PrimaryViolet,
                                    text = "Your peak spending day is ${peakDayAndCategory.first}."
                                )
                                InsightRow(
                                    icon = Icons.Default.Category,
                                    tint = SecondaryTeal,
                                    text = "You spend the most on ${peakDayAndCategory.second}."
                                )
                            }

                            // Narrative comparison vs previous period
                            narrativeInsights.forEach { (text, isPositive) ->
                                InsightRow(
                                    icon = if (isPositive) Icons.Default.CheckCircle else Icons.Default.Info,
                                    tint = if (isPositive) AccentGreen else WarningAmber,
                                    text = text
                                )
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
                                Text("Achievements", style = Typography.titleMedium.copy(color = TextPrimary))
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${achievements.count { it.unlocked }}/${achievements.size}",
                                    style = Typography.labelLarge.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { achievements.count { it.unlocked }.toFloat() / achievements.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = WarningAmber,
                                trackColor = BorderColor.copy(alpha = 0.4f),
                                strokeCap = StrokeCap.Round
                            )
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
                                            style = Typography.titleSmall.copy(
                                                color = if (achievement.unlocked) TextPrimary else TextMuted,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            achievement.description,
                                            style = Typography.labelMedium.copy(color = TextMuted)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Cash Flow Forecast", style = Typography.titleMedium.copy(color = TextPrimary))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            if (periodIsInFuture) {
                                Text(
                                    "Based on your current balance of ${moneyString(currentBalance)} and your spending pace this period, you're projected to have:",
                                    style = Typography.bodyMedium.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    moneyString(forecastEndBalance),
                                    style = Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "by ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(rangeEnd))}",
                                    style = Typography.labelMedium.copy(color = TextMuted)
                                )
                            } else {
                                Text(
                                    "Current balance across all accounts:",
                                    style = Typography.bodyMedium.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    moneyString(currentBalance),
                                    style = Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
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
        delay(50.milliseconds)
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
            text = moneyString(totalExpense),
            style = Typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun InsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(
            text,
            style = Typography.bodyMedium.copy(color = TextPrimary),
            modifier = Modifier.weight(1f)
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

/** One bucket per calendar day in [rangeStart]..[rangeEnd], holding that day's total expense. */
private fun buildDayBuckets(
    expenses: List<com.example.financemanager.data.Transaction>,
    rangeStart: Long,
    rangeEnd: Long
): List<DayBucket> {
    val cal = Calendar.getInstance()
    fun startOfDay(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

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
        buckets.add(DayBucket(current, label, dayFmtFull.format(Date(current)), amt))

        cal.timeInMillis = current
        cal.add(Calendar.DAY_OF_YEAR, 1)
        current = cal.timeInMillis
        if (buckets.size > 730) break
    }
    return buckets
}

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

    val dayBuckets = remember(expenses, rangeStart, rangeEnd) {
        buildDayBuckets(expenses, rangeStart, rangeEnd)
    }

    if (dayBuckets.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No data to chart", color = TextMuted)
        }
        return
    }

    val maxAmount = dayBuckets.maxOfOrNull { it.amount } ?: 1.0
    val maxScaled = if (maxAmount == 0.0) 1.0 else maxAmount
    val totalAmount = dayBuckets.sumOf { it.amount }
    val averageAmount = totalAmount / dayBuckets.size.coerceAtLeast(1)
    val peakDay = dayBuckets.maxByOrNull { it.amount }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(dayBuckets) {
        isVisible = false
        delay(50.milliseconds)
        isVisible = true
    }

    val heightProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "bar_height"
    )

    val selectedBucket = dayBuckets.firstOrNull { it.timestamp == selectedDayTimestamp }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendMetric("Total", moneyString(totalAmount), Modifier.weight(1f))
            TrendMetric("Avg/day", moneyString(averageAmount), Modifier.weight(1f))
            TrendMetric("Peak", peakDay?.label ?: "-", Modifier.weight(1f))
        }

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
                    text = "${selectedBucket.fullDateStr} • ${moneyString(selectedBucket.amount)}",
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
                val targetFraction = ((bucket.amount / maxScaled) * heightProgress).toFloat().coerceIn(0.04f, 1.0f)
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "bar_frac"
                )

                val barColor = when {
                    isSelected -> AccentGreen
                    selectedDayTimestamp != null -> PrimaryViolet.copy(alpha = 0.25f)
                    bucket.amount > 0 -> PrimaryViolet
                    else -> BorderColor.copy(alpha = 0.3f)
                }

                val showLabel = when {
                    isSelected -> true
                    totalDays <= 14 -> true
                    totalDays <= 31 -> (index % 5 == 0 || index == totalDays - 1)
                    else -> (index % 10 == 0 || index == totalDays - 1)
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
                                .fillMaxHeight(fraction = animatedFraction)
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

@Composable
private fun TrendMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SubtleSurface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = Typography.labelSmall.copy(color = TextSecondary))
            Text(
                value,
                style = Typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class LineSeriesMode(val label: String) {
    TOTAL_AMOUNT("Total Amount"),
    DAILY_SPEND("Daily Spend")
}

/**
 * End-of-day total balance across all accounts for every day in the range, reconstructed
 * backwards from the live balance. Returns the day series plus the opening balance
 * (the total just before the first charted day).
 */
private fun buildBalanceSeries(
    allTransactions: List<com.example.financemanager.data.Transaction>,
    currentBalance: Double,
    rangeStart: Long,
    rangeEnd: Long
): Pair<List<DayBucket>, Double> {
    val days = buildDayBuckets(emptyList(), rangeStart, rangeEnd)
    if (days.isEmpty()) return emptyList<DayBucket>() to currentBalance

    val cal = Calendar.getInstance()
    fun startOfDay(millis: Long): Long {
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    fun net(tx: com.example.financemanager.data.Transaction) = when (tx.type) {
        TransactionType.INCOME -> tx.amount
        TransactionType.EXPENSE -> -tx.amount
        TransactionType.TRANSFER -> 0.0 // moves money between own accounts, total is unchanged
    }

    val firstDayStart = days.first().timestamp
    val lastDayEnd = days.last().timestamp + (24L * 60 * 60 * 1000) - 1

    val netByDay = mutableMapOf<Long, Double>()
    var netAfterWindow = 0.0
    allTransactions.forEach { tx ->
        when {
            tx.date > lastDayEnd -> netAfterWindow += net(tx)
            tx.date >= firstDayStart -> {
                val day = startOfDay(tx.date)
                netByDay[day] = (netByDay[day] ?: 0.0) + net(tx)
            }
            // anything before the window is already baked into the current balance
        }
    }

    // Walk backwards from today's balance to get each day's closing total
    val balances = DoubleArray(days.size)
    var running = currentBalance - netAfterWindow
    for (i in days.indices.reversed()) {
        balances[i] = running
        running -= netByDay[days[i].timestamp] ?: 0.0
    }

    return days.mapIndexed { i, day -> day.copy(amount = balances[i]) } to running
}

/**
 * Straight-line (polyline) graph with two series: the running total across all accounts
 * (rising and falling segments color-coded, so it reads as "when did my money go up/down")
 * and the per-day spend. Tap any point to pin it and read the exact amount and change;
 * the line draws itself left-to-right whenever the data, mode, or period changes.
 */
@Composable
fun DailyAmountLineChart(
    expenses: List<com.example.financemanager.data.Transaction>,
    allTransactions: List<com.example.financemanager.data.Transaction>,
    currentBalance: Double,
    rangeStart: Long,
    rangeEnd: Long,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var mode by remember { mutableStateOf(LineSeriesMode.TOTAL_AMOUNT) }

    val spendBuckets = remember(expenses, rangeStart, rangeEnd) {
        buildDayBuckets(expenses, rangeStart, rangeEnd)
    }
    val balanceSeries = remember(allTransactions, currentBalance, rangeStart, rangeEnd) {
        buildBalanceSeries(allTransactions, currentBalance, rangeStart, rangeEnd)
    }

    val isBalanceMode = mode == LineSeriesMode.TOTAL_AMOUNT
    val dayBuckets = if (isBalanceMode) balanceSeries.first else spendBuckets
    val openingValue = if (isBalanceMode) balanceSeries.second else 0.0

    // Day-over-day movement of the plotted series (first point compares to the opening value)
    val deltas = remember(dayBuckets, openingValue) {
        dayBuckets.mapIndexed { index, bucket ->
            bucket.amount - (if (index == 0) openingValue else dayBuckets[index - 1].amount)
        }
    }
    val biggestRiseIndex = deltas.indices.maxByOrNull { deltas[it] }?.takeIf { deltas[it] > 0.0 }
    val biggestDropIndex = deltas.indices.minByOrNull { deltas[it] }?.takeIf { deltas[it] < 0.0 }
    val netChange = if (dayBuckets.isEmpty()) 0.0 else dayBuckets.last().amount - openingValue

    // When the account was at its highest / lowest inside the period
    val peakIndex = dayBuckets.indices.maxByOrNull { dayBuckets[it].amount }
    val lowIndex = dayBuckets.indices.minByOrNull { dayBuckets[it].amount }

    var selectedIndex by remember(dayBuckets) { mutableIntStateOf(-1) }

    // Left-to-right reveal, replayed on every data / period change
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(dayBuckets) {
        isVisible = false
        selectedIndex = -1
        delay(50.milliseconds)
        isVisible = true
    }
    val sweep by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
        label = "line_sweep"
    )

    val upColor = AccentGreen
    val downColor = AlertRed
    val spendColor = SecondaryTeal
    val trendColor = if (!isBalanceMode) spendColor else if (netChange >= 0) upColor else downColor
    val markerColor = PrimaryViolet
    val gridColor = BorderColor.copy(alpha = 0.35f)
    val fillBrush = Brush.verticalGradient(
        colors = listOf(trendColor.copy(alpha = 0.24f), trendColor.copy(alpha = 0f))
    )
    val tooltipBg = SubtleSurface

    val totalSpend = spendBuckets.sumOf { it.amount }
    val hasData = if (isBalanceMode) dayBuckets.isNotEmpty() else totalSpend > 0.0
    val selected = dayBuckets.getOrNull(selectedIndex)
    val selectedDelta = deltas.getOrNull(selectedIndex) ?: 0.0

    fun signedMoney(value: Double): String =
        (if (value >= 0) "+" else "−") + moneyString(kotlin.math.abs(value))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    if (isBalanceMode) "Total Amount Trend" else "Daily Amount Line",
                    style = Typography.titleMedium.copy(color = TextPrimary)
                )
            }
            if (hasData) {
                Text(
                    text = when {
                        selected != null -> moneyString(selected.amount)
                        isBalanceMode -> signedMoney(netChange)
                        else -> moneyString(totalSpend)
                    },
                    style = Typography.labelMedium.copy(
                        color = if (selected != null) markerColor else trendColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Series switch
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineSeriesMode.entries.forEach { seriesMode ->
                val isActive = mode == seriesMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) PrimaryViolet else SubtleSurface)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            mode = seriesMode
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        seriesMode.label,
                        style = Typography.labelSmall.copy(
                            color = if (isActive) OnAccent else TextSecondary,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                selected != null && isBalanceMode ->
                    "${selected.fullDateStr} • ${moneyString(selected.amount)} (${signedMoney(selectedDelta)} that day)"
                selected != null -> "${selected.fullDateStr} • ${moneyString(selected.amount)}"
                isBalanceMode -> "Tap any point to see your total on that day"
                else -> "Tap any point to see that day's amount"
            },
            style = Typography.labelSmall.copy(color = if (selected != null) TextSecondary else TextMuted)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasData) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (isBalanceMode) "No account balance to chart for this period."
                    else "No spending recorded for the selected period.",
                    style = Typography.bodyMedium.copy(color = TextMuted)
                )
            }
            return
        }

        // Balance can rise and fall, so it is scaled between its own low and high;
        // daily spend always sits on a zero baseline.
        val highValue = dayBuckets.maxOf { it.amount }
        val lowValue = if (isBalanceMode) dayBuckets.minOf { it.amount } else 0.0
        val valueSpan = (highValue - lowValue).coerceAtLeast(1.0)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val sidePad = with(density) { 8.dp.toPx() }
            val topPad = with(density) { 14.dp.toPx() }
            val bottomPad = with(density) { 10.dp.toPx() }
            val plotW = (widthPx - sidePad * 2).coerceAtLeast(1f)
            val plotH = (heightPx - topPad - bottomPad).coerceAtLeast(1f)
            // Balance lines keep a little headroom top and bottom so peaks stay readable
            val vInset = if (isBalanceMode) plotH * 0.08f else 0f

            val points = remember(dayBuckets, widthPx, heightPx, highValue, lowValue) {
                dayBuckets.mapIndexed { index, bucket ->
                    val x = if (dayBuckets.size == 1) sidePad + plotW / 2f
                    else sidePad + plotW * index / (dayBuckets.size - 1).toFloat()
                    val usableH = plotH - vInset * 2
                    val y = topPad + vInset + usableH -
                        ((bucket.amount - lowValue) / valueSpan).toFloat() * usableH
                    Offset(x, y)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { tap ->
                            val nearest = points.indices.minByOrNull { kotlin.math.abs(points[it].x - tap.x) }
                            if (nearest != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedIndex = if (nearest == selectedIndex) -1 else nearest
                            }
                        }
                    }
            ) {
                val baseline = topPad + plotH

                // Horizontal guidelines
                for (i in 0..2) {
                    val y = topPad + plotH * i / 2f
                    drawLine(
                        color = gridColor,
                        start = Offset(sidePad, y),
                        end = Offset(sidePad + plotW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (points.size == 1) {
                    drawCircle(color = trendColor, radius = 5.dp.toPx(), center = points[0])
                    return@Canvas
                }

                // Straight segments, revealed left-to-right
                val progressed = (sweep * (points.size - 1)).coerceIn(0f, (points.size - 1).toFloat())
                val fullSegments = kotlin.math.floor(progressed).toInt()
                val fraction = progressed - fullSegments

                var tipX = points[0].x
                var tipY = points[0].y
                val linePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1..fullSegments) {
                        lineTo(points[i].x, points[i].y)
                        tipX = points[i].x
                        tipY = points[i].y
                    }
                    if (fullSegments < points.size - 1 && fraction > 0f) {
                        val from = points[fullSegments]
                        val to = points[fullSegments + 1]
                        tipX = from.x + (to.x - from.x) * fraction
                        tipY = from.y + (to.y - from.y) * fraction
                        lineTo(tipX, tipY)
                    }
                }

                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(tipX, baseline)
                    lineTo(points[0].x, baseline)
                    close()
                }

                drawPath(fillPath, brush = fillBrush)

                val strokePx = 2.5f.dp.toPx()
                if (isBalanceMode) {
                    // Color each straight segment by direction: green where the total rose, red where it fell
                    for (i in 1..fullSegments) {
                        drawLine(
                            color = if (points[i].y <= points[i - 1].y) upColor else downColor,
                            start = points[i - 1],
                            end = points[i],
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                    if (fullSegments < points.size - 1 && fraction > 0f) {
                        val from = points[fullSegments]
                        drawLine(
                            color = if (tipY <= from.y) upColor else downColor,
                            start = from,
                            end = Offset(tipX, tipY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawPath(
                        linePath,
                        color = spendColor,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Dots on revealed points (hidden when the range is too dense to read)
                if (points.size <= 40) {
                    points.forEachIndexed { index, point ->
                        if (point.x <= tipX + 0.5f && index != selectedIndex) {
                            val dotColor = if (isBalanceMode) {
                                if (deltas[index] >= 0) upColor else downColor
                            } else spendColor
                            drawCircle(color = dotColor, radius = 3.dp.toPx(), center = point)
                        }
                    }
                }

                // Mark the high point, the low point and the sharpest single-day fall
                if (isBalanceMode) {
                    val dropPt = biggestDropIndex?.let { points.getOrNull(it) }
                    if (dropPt != null && dropPt.x <= tipX + 0.5f && biggestDropIndex != selectedIndex) {
                        drawCircle(
                            color = downColor.copy(alpha = 0.75f),
                            radius = 6.dp.toPx(),
                            center = dropPt,
                            style = Stroke(width = 1.5f.dp.toPx())
                        )
                    }
                    listOf(peakIndex to upColor, lowIndex to downColor).forEach { (idx, markColor) ->
                        val pt = idx?.let { points.getOrNull(it) }
                        if (pt != null && pt.x <= tipX + 0.5f && idx != selectedIndex) {
                            drawCircle(color = markColor.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = pt)
                            drawCircle(color = markColor, radius = 4.5f.dp.toPx(), center = pt)
                        }
                    }
                }

                // Selected point marker
                val sel = points.getOrNull(selectedIndex)
                if (sel != null) {
                    drawLine(
                        color = markerColor.copy(alpha = 0.55f),
                        start = Offset(sel.x, topPad),
                        end = Offset(sel.x, baseline),
                        strokeWidth = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                        )
                    )
                    drawCircle(color = markerColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = sel)
                    drawCircle(color = markerColor, radius = 5.dp.toPx(), center = sel)
                }
            }

            // "High"/"Low" callouts so the peak and the bottom are readable without tapping
            if (isBalanceMode && points.size > 1 && highValue - lowValue > 0.0) {
                listOf(
                    Triple(peakIndex, upColor, "High"),
                    Triple(lowIndex, downColor, "Low")
                ).forEach { (idx, tagColor, tag) ->
                    val pt = idx?.let { points.getOrNull(it) }
                    val bucket = idx?.let { dayBuckets.getOrNull(it) }
                    if (pt != null && bucket != null && idx != selectedIndex) {
                        var tagSize by remember(tag) { mutableStateOf(IntSize.Zero) }
                        val gapPx = with(density) { 13.dp.toPx() }
                        val above = tag == "High"
                        Text(
                            text = "$tag ${moneyString(bucket.amount)}",
                            style = Typography.labelSmall.copy(
                                color = tagColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            modifier = Modifier
                                .offset {
                                    val x = (pt.x - tagSize.width / 2f)
                                        .coerceIn(0f, (widthPx - tagSize.width).coerceAtLeast(0f))
                                    val y = (if (above) pt.y - tagSize.height - gapPx else pt.y + gapPx)
                                        .coerceIn(0f, (heightPx - tagSize.height).coerceAtLeast(0f))
                                    IntOffset(x.toInt(), y.toInt())
                                }
                                .onGloballyPositioned { tagSize = it.size }
                        )
                    }
                }
            }

            // Tooltip pinned above the selected point
            val selPoint = points.getOrNull(selectedIndex)
            if (selPoint != null && selected != null) {
                var tipSize by remember { mutableStateOf(IntSize.Zero) }
                val gapPx = with(density) { 12.dp.toPx() }
                Column(
                    modifier = Modifier
                        .offset {
                            val x = (selPoint.x - tipSize.width / 2f)
                                .coerceIn(0f, (widthPx - tipSize.width).coerceAtLeast(0f))
                            val y = (selPoint.y - tipSize.height - gapPx)
                                .coerceIn(0f, (heightPx - tipSize.height).coerceAtLeast(0f))
                            IntOffset(x.toInt(), y.toInt())
                        }
                        .onGloballyPositioned { tipSize = it.size }
                        .clip(RoundedCornerShape(8.dp))
                        .background(tooltipBg)
                        .border(1.dp, BorderColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        selected.fullDateStr,
                        style = Typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp),
                        maxLines = 1
                    )
                    Text(
                        moneyString(selected.amount),
                        style = Typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    if (isBalanceMode) {
                        Text(
                            signedMoney(selectedDelta),
                            style = Typography.labelSmall.copy(
                                color = if (selectedDelta >= 0) upColor else downColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // X-axis: first / middle / last day labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = when (dayBuckets.size) {
                1 -> listOf(dayBuckets.first().label)
                2 -> listOf(dayBuckets.first().label, dayBuckets.last().label)
                else -> listOf(
                    dayBuckets.first().label,
                    dayBuckets[dayBuckets.size / 2].label,
                    dayBuckets.last().label
                )
            }
            labels.forEach { label ->
                Text(
                    label,
                    style = Typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }

        // When the total moved the most, and where it ended up
        if (isBalanceMode) {
            Spacer(modifier = Modifier.height(6.dp))

            if (peakIndex != null) {
                MovementRow(
                    icon = Icons.Default.ArrowUpward,
                    tint = upColor,
                    text = "Highest total ${moneyString(dayBuckets[peakIndex].amount)} on ${dayBuckets[peakIndex].fullDateStr}"
                )
            }
            if (lowIndex != null && lowIndex != peakIndex) {
                MovementRow(
                    icon = Icons.Default.ArrowDownward,
                    tint = downColor,
                    text = "Lowest total ${moneyString(dayBuckets[lowIndex].amount)} on ${dayBuckets[lowIndex].fullDateStr}"
                )
            }
            if (biggestRiseIndex != null) {
                MovementRow(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = upColor,
                    text = "Biggest rise ${signedMoney(deltas[biggestRiseIndex])} on ${dayBuckets[biggestRiseIndex].fullDateStr}"
                )
            }
            if (biggestDropIndex != null) {
                MovementRow(
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    tint = downColor,
                    text = "Biggest drop ${signedMoney(deltas[biggestDropIndex])} on ${dayBuckets[biggestDropIndex].fullDateStr}"
                )
            }
            MovementRow(
                icon = if (netChange >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                tint = if (netChange >= 0) upColor else downColor,
                text = "Over this period your total went ${if (netChange >= 0) "up" else "down"} " +
                    "${moneyString(kotlin.math.abs(netChange))} — from ${moneyString(openingValue)} " +
                    "to ${moneyString(dayBuckets.last().amount)}"
            )
        }
    }
}

@Composable
private fun MovementRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = Typography.labelSmall.copy(color = TextSecondary),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun IncomeVsExpenseComparisonChart(
    income: Double,
    expense: Double,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(1.0, maxOf(income, expense))
    val incomeRatio = (income / maxVal).toFloat().coerceIn(0.05f, 1f)
    val expenseRatio = (expense / maxVal).toFloat().coerceIn(0.05f, 1f)

    val animatedIncomeRatio by animateFloatAsState(
        targetValue = incomeRatio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "inc_ratio"
    )
    val animatedExpenseRatio by animateFloatAsState(
        targetValue = expenseRatio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "exp_ratio"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cash Flow Comparison", style = Typography.titleMedium.copy(color = TextPrimary))
            val net = income - expense
            val chipColor = if (net >= 0) AccentGreen else AlertRed
            Text(
                text = if (net >= 0) "+${moneyString(net)}" else moneyString(net),
                style = Typography.labelMedium.copy(color = chipColor, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Income Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight(animatedIncomeRatio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(AccentGreen)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Income", style = Typography.labelSmall.copy(color = TextSecondary))
                Text(moneyString(income), style = Typography.labelSmall.copy(color = AccentGreen, fontWeight = FontWeight.Bold))
            }

            // Expense Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight(animatedExpenseRatio)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(AlertRed)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Expenses", style = Typography.labelSmall.copy(color = TextSecondary))
                Text(moneyString(expense), style = Typography.labelSmall.copy(color = AlertRed, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun CumulativeCashFlowTrendChart(
    expenses: List<com.example.financemanager.data.Transaction>,
    rangeStart: Long,
    rangeEnd: Long,
    modifier: Modifier = Modifier
) {
    val dayBuckets = remember(expenses, rangeStart, rangeEnd) {
        val cal = Calendar.getInstance()
        fun startOfDay(m: Long): Long {
            cal.timeInMillis = m
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        val dayMap = mutableMapOf<Long, Double>()
        expenses.forEach { tx ->
            val day = startOfDay(tx.date)
            dayMap[day] = (dayMap[day] ?: 0.0) + tx.amount
        }
        val list = mutableListOf<Double>()
        var curr = startOfDay(rangeStart)
        val end = startOfDay(rangeEnd)
        var cumulative = 0.0
        while (curr <= end) {
            cumulative += dayMap[curr] ?: 0.0
            list.add(cumulative)
            cal.timeInMillis = curr
            cal.add(Calendar.DAY_OF_YEAR, 1)
            curr = cal.timeInMillis
            if (list.size > 365) break
        }
        list
    }

    val hasData = dayBuckets.isNotEmpty() && dayBuckets.any { it > 0.0 }
    val maxVal = dayBuckets.lastOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val linePrimaryColor = PrimaryViolet
    val fillGradient = Brush.verticalGradient(
        colors = listOf(PrimaryViolet.copy(alpha = 0.35f), PrimaryViolet.copy(alpha = 0.0f))
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(20.dp))
                Text("Cumulative Spend Trajectory", style = Typography.titleMedium.copy(color = TextPrimary))
            }
            if (hasData) {
                Text(
                    text = moneyString(dayBuckets.last()),
                    style = Typography.labelMedium.copy(color = PrimaryViolet, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!hasData) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No spending recorded for the selected period.",
                    style = Typography.bodyMedium.copy(color = TextMuted)
                )
            }
            return
        }

        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val points = dayBuckets.mapIndexed { idx, valAmt ->
                    val x = (idx.toFloat() / (dayBuckets.size - 1).coerceAtLeast(1)) * width
                    val y = height - ((valAmt / maxVal).toFloat() * height * 0.85f)
                    Offset(x, y)
                }

                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val controlX1 = prev.x + (curr.x - prev.x) / 2f
                            val controlY1 = prev.y
                            val controlX2 = prev.x + (curr.x - prev.x) / 2f
                            val controlY2 = curr.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, curr.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    drawPath(fillPath, brush = fillGradient)
                    drawPath(path, color = linePrimaryColor, style = Stroke(width = 3.dp.toPx()))

                    val lastPt = points.last()
                    drawCircle(color = linePrimaryColor, radius = 5.dp.toPx(), center = lastPt)
                }
            }
        }
    }
}
