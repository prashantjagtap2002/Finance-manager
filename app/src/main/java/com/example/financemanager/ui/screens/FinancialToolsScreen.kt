package com.example.financemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.*
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSTopAppBar
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private data class MerchantStat(val name: String, val total: Double, val visits: Int, val trend: Double, val tags: Set<String>)
private data class CalendarEvent(val day: Int, val title: String, val amount: Double, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialToolsScreen(viewModel: FinanceViewModel, onNavigateBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val recurring by viewModel.recurringTransactions.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val privacy by viewModel.isPrivacyMode.collectAsState()
    var extraSaving by remember { mutableFloatStateOf(2500f) }
    var monthOffset by remember { mutableIntStateOf(0) }

    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    val income = transactions.filter { it.type == TransactionType.INCOME }
    val monthlyIncome = income.filter { it.date >= monthStart(0) }.sumOf { it.amount } / 1.0
    val monthlyExpense = expenses.filter { it.date >= monthStart(0) }.sumOf { it.amount } / 1.0
    val savingsRate = if (monthlyIncome > 0) ((monthlyIncome - monthlyExpense) / monthlyIncome).coerceIn(0.0, 1.0) else 0.0
    val liabilities = accounts.filter { it.type == AccountType.CREDIT_CARD }.sumOf { it.balance } + debts.filter { it.type == DebtType.BORROWED && !it.isSettled }.sumOf { it.amount }
    val assets = accounts.filter { it.type != AccountType.CREDIT_CARD }.sumOf { it.balance }
    val debtRatio = if (assets + liabilities > 0) liabilities / (assets + liabilities) else 0.0
    val budgeted = categories.filter { it.budgetLimit > 0 }.sumOf { it.budgetLimit }
    val currentSpend = expenses.filter { it.date >= monthStart(0) }.sumOf { it.amount }
    val budgetScore = if (budgeted > 0) (1.0 - (currentSpend - budgeted).coerceAtLeast(0.0) / budgeted).coerceIn(0.0, 1.0) else 0.5
    val emergencyMonths = if (monthlyExpense > 0) assets / monthlyExpense else 0.0
    val score = ((savingsRate * 35 + (1 - debtRatio) * 25 + budgetScore * 25 + (emergencyMonths / 6).coerceIn(0.0, 1.0) * 15) * 100).roundToInt().coerceIn(0, 100)
    val baseNetWorth = assets - liabilities
    val monthlySurplus = monthlyIncome - monthlyExpense
    val previousMonthStart = monthStart(-1)
    val currentMonthStart = monthStart(0)
    val merchants = expenses.groupBy { (it.merchantName ?: it.note.substringBefore(" ")).trim().ifBlank { "Unknown merchant" } }
        .map { (name, txs) ->
            val current = txs.filter { it.date >= currentMonthStart }.sumOf { it.amount }
            val previous = txs.filter { it.date >= previousMonthStart && it.date < currentMonthStart }.sumOf { it.amount }
            MerchantStat(name, txs.sumOf { it.amount }, txs.size, if (previous > 0) (current - previous) / previous else 0.0, txs.flatMap { hashtagRegex.findAll(it.note).map { m -> m.value.lowercase() }.toList() }.toSet())
        }
        .sortedByDescending { it.total }.take(10)
    val displayedMonth = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    val events = calendarEvents(displayedMonth, recurring, debts, income, expenses)

    Scaffold(
        topBar = { iOSTopAppBar("Financial Intelligence", navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        containerColor = DeepBackground
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionTitle("AI Financial Health") }
            item {
                iOSCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("$score", style = MaterialTheme.typography.displaySmall, color = if (score >= 70) AccentGreen else WarningAmber, fontWeight = FontWeight.Bold)
                            Column { Text("out of 100", color = TextMuted); Text(if (score >= 70) "Healthy momentum" else "Room to improve", color = TextPrimary) }
                        }
                        LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth(), color = AccentGreen, trackColor = SubtleSurface)
                        Text("Savings ${(savingsRate * 100).roundToInt()}%  •  Debt ${(debtRatio * 100).roundToInt()}%  •  Buffer ${"%.1f".format(emergencyMonths)} months", color = TextSecondary)
                    }
                }
            }
            item { SectionTitle("What-if simulator") }
            item {
                iOSCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Save ₹${extraSaving.roundToInt()} more each month", color = TextPrimary)
                        Slider(value = extraSaving, onValueChange = { extraSaving = it }, valueRange = 0f..10000f, steps = 19)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(6, 12, 36).forEach { months ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${months}m", color = TextMuted); Text(moneyString(baseNetWorth + monthlySurplus * months + extraSaving * months, privacy), color = AccentGreen) }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Merchant intelligence") }
            items(merchants, key = { it.name }) { merchant ->
                iOSCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, null, tint = PrimaryViolet)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(merchant.name, color = TextPrimary, fontWeight = FontWeight.SemiBold); Text("${merchant.visits} visits • avg ${moneyString(merchant.total / merchant.visits, privacy)}", color = TextSecondary); Text("MoM ${if (merchant.trend >= 0) "+" else ""}${(merchant.trend * 100).roundToInt()}%", color = if (merchant.trend <= 0) AccentGreen else WarningAmber); if (merchant.tags.isNotEmpty()) Text(merchant.tags.joinToString("  "), color = SecondaryTeal) }
                        Text(moneyString(merchant.total, privacy), color = TextPrimary)
                    }
                }
            }
            item { SectionTitle("Financial calendar") }
            item {
                iOSCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { monthOffset-- }) { Icon(Icons.Default.ChevronLeft, "Previous") }; Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedMonth.time), color = TextPrimary, fontWeight = FontWeight.SemiBold); IconButton(onClick = { monthOffset++ }) { Icon(Icons.Default.ChevronRight, "Next") } }
                        CalendarGrid(displayedMonth, events)
                        events.forEach { event ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatusDot(event.status); Text("${event.day}  ${event.title}", Modifier.weight(1f), color = TextPrimary); Text(moneyString(event.amount, privacy), color = TextSecondary) }
                        }
                        if (events.isEmpty()) Text("No scheduled items this month", color = TextMuted)
                        Text("● Paid   ● Due soon   ● Overdue", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private val hashtagRegex = Regex("#[A-Za-z0-9_-]+")
private fun monthStart(offset: Int): Long = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, offset) }.timeInMillis
@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun StatusDot(status: String) { Text("●", color = when (status) { "Paid" -> AccentGreen; "Overdue" -> AlertRed; else -> WarningAmber }) }

@Composable
private fun CalendarGrid(month: Calendar, events: List<CalendarEvent>) {
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstColumn = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val eventDays = events.groupBy { it.day }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall) }
        }
        repeat((firstColumn + days + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) { column ->
                    val day = week * 7 + column - firstColumn + 1
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.toString(), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                                if (eventDays[day].orEmpty().isNotEmpty()) StatusDot(eventDays[day]!!.first().status)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calendarEvents(month: Calendar, recurring: List<RecurringTransaction>, debts: List<Debt>, income: List<Transaction>, expenses: List<Transaction>): List<CalendarEvent> {
    val y = month.get(Calendar.YEAR); val m = month.get(Calendar.MONTH)
    fun day(time: Long) = Calendar.getInstance().apply { timeInMillis = time }.let { if (it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m) it.get(Calendar.DAY_OF_MONTH) else 0 }
    val now = System.currentTimeMillis()
    return (recurring.filter { !it.isPaused }.mapNotNull { d -> day(d.nextExecutionDate).takeIf { it > 0 }?.let { CalendarEvent(it, if (d.type == TransactionType.INCOME) "Paycheck" else d.note.ifBlank { "Recurring payment" }, d.amount, if (d.nextExecutionDate < now) "Overdue" else if (d.nextExecutionDate < now + 7 * 86400000L) "Due soon" else "Due") } } +
        debts.mapNotNull { d -> d.dueDate?.let { due -> day(due).takeIf { it > 0 }?.let { CalendarEvent(it, "${d.personName} due", d.amount - d.paidAmount, if (due < now && !d.isSettled) "Overdue" else "Due") } } } +
        income.mapNotNull { t -> day(t.date).takeIf { it > 0 }?.let { CalendarEvent(it, "Paycheck received", t.amount, "Paid") } } +
        expenses.mapNotNull { t -> day(t.date).takeIf { it > 0 && t.isRecurring }?.let { CalendarEvent(it, "Paid bill", t.amount, "Paid") } }).sortedBy { it.day }
}
