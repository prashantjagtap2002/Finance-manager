package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.financemanager.ui.components.IosTopAppBar
import com.example.financemanager.ui.components.rememberLargeTitleBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financemanager.domain.SpendingCalendar
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.EmptyState
import com.example.financemanager.ui.components.IosCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * A month of spending as a heatmap: darker days cost more. Tapping a day lists what was spent on
 * it. Shading is by quartile of the month's own spending days, so one rent payment doesn't wash
 * every other day out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingCalendarScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isPrivacy by viewModel.isPrivacyMode.collectAsStateWithLifecycle()

    val today = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH)) }
    val isCurrentMonth = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH)
    var selectedDay by rememberSaveable(year, month) {
        mutableIntStateOf(if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else 0)
    }

    val firstDayOfWeek = remember { Calendar.getInstance().firstDayOfWeek }
    val spending = remember(transactions, year, month) { SpendingCalendar.build(transactions, year, month, firstDayOfWeek) }
    val accountsById = remember(accounts) { accounts.associateBy { it.id } }
    val categoriesById = remember(categories) { categories.associateBy { it.id } }

    val dayTransactions = remember(transactions, year, month, selectedDay) {
        if (selectedDay == 0) emptyList()
        else {
            val (start, end) = SpendingCalendar.dayRange(year, month, selectedDay)
            transactions.filter { it.date in start until end }.sortedByDescending { it.date }
        }
    }

    // Weekend vs weekday spending per day — the pattern a heatmap hints at, put into numbers.
    val pattern = remember(spending) {
        val cal = Calendar.getInstance()
        var weekendTotal = 0.0; var weekendDays = 0
        var weekdayTotal = 0.0; var weekdayDays = 0
        val lastDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else spending.daysInMonth
        for (d in 1..lastDay) {
            cal.clear(); cal.set(year, month, d)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val total = spending.days[d]?.total ?: 0.0
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) { weekendTotal += total; weekendDays++ }
            else { weekdayTotal += total; weekdayDays++ }
        }
        Triple(
            if (weekendDays > 0) weekendTotal / weekendDays else 0.0,
            if (weekdayDays > 0) weekdayTotal / weekdayDays else 0.0,
            lastDay
        )
    }

    val monthName = remember(year, month) {
        "${DateFormatSymbols.getInstance().months[month]} $year"
    }
    val weekdayLabels = remember(firstDayOfWeek) {
        val short = DateFormatSymbols.getInstance().shortWeekdays
        (0 until 7).map { short[((firstDayOfWeek - 1 + it) % 7) + 1].take(2) }
    }

    val titleBehavior = rememberLargeTitleBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(titleBehavior.nestedScrollConnection),
        topBar = {
            IosTopAppBar(
                title = "Spending calendar",
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
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (month == 0) { month = 11; year-- } else month--
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = TextPrimary) }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthName, style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                        Text(
                            "${moneyString(spending.total, isPrivacy)} spent",
                            style = Typography.labelMedium.copy(color = TextSecondary)
                        )
                    }
                    IconButton(
                        onClick = { if (month == 11) { month = 0; year++ } else month++ },
                        enabled = !isCurrentMonth
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = if (isCurrentMonth) TextMuted.copy(alpha = 0.4f) else TextPrimary
                        )
                    }
                }
            }

            item {
                IosCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            weekdayLabels.forEach {
                                Text(
                                    it,
                                    style = Typography.labelSmall.copy(color = TextMuted),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val cells = spending.leadingBlanks + spending.daysInMonth
                        val rows = (cells + 6) / 7
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (c in 0 until 7) {
                                    val day = r * 7 + c - spending.leadingBlanks + 1
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                        if (day in 1..spending.daysInMonth) {
                                            val total = spending.days[day]?.total ?: 0.0
                                            val isFuture = isCurrentMonth && day > today.get(Calendar.DAY_OF_MONTH)
                                            DayCell(
                                                day = day,
                                                level = SpendingCalendar.level(total, spending.thresholds),
                                                selected = day == selectedDay,
                                                isToday = isCurrentMonth && day == today.get(Calendar.DAY_OF_MONTH),
                                                enabled = !isFuture,
                                                onClick = { selectedDay = day }
                                            )
                                        }
                                    }
                                }
                            }
                            if (r < rows - 1) Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Less", style = Typography.labelSmall.copy(color = TextMuted))
                            (0..SpendingCalendar.LEVELS).forEach { level ->
                                Box(
                                    Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(level))
                                )
                            }
                            Text("More", style = Typography.labelSmall.copy(color = TextMuted))
                        }
                    }
                }
            }

            if (spending.total > 0) {
                item {
                    val (weekend, weekday, _) = pattern
                    val message = when {
                        weekday <= 0.0 && weekend <= 0.0 -> null
                        weekend > weekday * 1.25 -> "Weekends run ${moneyString(weekend, isPrivacy, decimals = 0)}/day against ${moneyString(weekday, isPrivacy, decimals = 0)} on weekdays."
                        weekday > weekend * 1.25 -> "Weekdays run ${moneyString(weekday, isPrivacy, decimals = 0)}/day against ${moneyString(weekend, isPrivacy, decimals = 0)} at weekends."
                        else -> "Spending is even across the week, about ${moneyString((weekend + weekday) / 2, isPrivacy, decimals = 0)}/day."
                    }
                    if (message != null) {
                        Text(message, style = Typography.bodyMedium.copy(color = TextSecondary), modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            if (selectedDay != 0) {
                item {
                    val dayTotal = spending.days[selectedDay]?.total ?: 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$selectedDay ${DateFormatSymbols.getInstance().shortMonths[month]}".uppercase(),
                            style = iOSCaption2.copy(color = TextMuted, letterSpacing = 1.sp),
                            modifier = Modifier.weight(1f)
                        )
                        // "spent": the list below also shows the day's income and transfers, which
                        // this figure (like the heatmap) leaves out.
                        Text("${moneyString(dayTotal, isPrivacy)} spent", style = Typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                    }
                }
                // A day with only income or transfers is still a no-spend day.
                if (dayTransactions.none { it.type == com.example.financemanager.data.TransactionType.EXPENSE }) {
                    item {
                        EmptyState(
                            icon = Icons.Default.EventAvailable,
                            title = "Nothing spent",
                            message = "A no-spend day. Nice.",
                            accent = AccentGreen
                        )
                    }
                }
                if (dayTransactions.isNotEmpty()) {
                    items(dayTransactions, key = { it.id }) { tx ->
                        TransactionItem(
                            transaction = tx,
                            accountName = accountsById[tx.sourceAccountId]?.name ?: "Account",
                            categoryName = categoriesById[tx.categoryId]?.name ?: "Income/Transfer",
                            privacy = isPrivacy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: Int): Color = when (level) {
    0 -> SubtleSurface
    else -> PrimaryViolet.copy(alpha = 0.18f + 0.82f * level / SpendingCalendar.LEVELS)
}

@Composable
private fun DayCell(
    day: Int,
    level: Int,
    selected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val background = if (enabled) levelColor(level) else Color.Transparent
    // The two darkest shades need light text to stay readable.
    val textColor = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        level >= 3 -> Color.White
        else -> TextPrimary
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                when {
                    selected -> Modifier.border(2.dp, SecondaryTeal, RoundedCornerShape(8.dp))
                    isToday -> Modifier.border(1.dp, TextMuted, RoundedCornerShape(8.dp))
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$day",
            style = Typography.labelMedium.copy(color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        )
    }
}
