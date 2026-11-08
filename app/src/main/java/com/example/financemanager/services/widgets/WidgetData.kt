package com.example.financemanager.services.widgets

import android.content.Context
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.core.PayCycle
import com.example.financemanager.data.DebtType
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.TransactionType
import com.example.financemanager.domain.Forecaster
import java.util.Calendar
import kotlin.math.roundToInt

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Midnight at the start of [time]'s day, in the device's time zone. */
internal fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Whole calendar days from today to [time]'s day: 0 today, 1 tomorrow, -2 two days ago. */
internal fun daysFromToday(time: Long, now: Long = System.currentTimeMillis()): Int =
    // Rounded rather than divided: a day that crosses a DST change is 23 or 25 hours long.
    ((startOfDay(time) - startOfDay(now)).toDouble() / DAY_MS).roundToInt()

// ---------------------------------------------------------------------------------------------
// Pay cycle: "Spending today" and "Safe to spend"
// ---------------------------------------------------------------------------------------------

internal data class WidgetEnvelope(val name: String, val spent: Double, val limit: Double) {
    val remaining get() = limit - spent
    val fraction get() = if (limit <= 0.0) 0.0 else spent / limit
}

/** Everything about the current pay cycle the widgets show, read in one pass. */
internal data class CycleSnapshot(
    val spentToday: Double,
    val spentThisCycle: Double,
    val cycleBudget: Double,
    val cycleStart: Long,
    val cycleEnd: Long,
    /** Tightest first: the least left relative to its size. */
    val envelopes: List<WidgetEnvelope>
)

/**
 * Today's spending, this pay cycle's spending, and envelopes ordered by how close each is to
 * empty. Envelope figures use the same cycle and rollover the Budget screen uses, so the two never
 * disagree. Only home-currency expenses count, as on the dashboard.
 *
 * The cycle total is the dashboard's definition, deliberately: every home-currency expense against
 * the sum of the envelope limits, rollover left out. It used to add rollover in, so the widget
 * said "on track" while the dashboard's pace bar for the same cycle said "over budget".
 */
internal suspend fun loadCycleSnapshot(context: Context, now: Long = System.currentTimeMillis()): CycleSnapshot {
    val dao = FinanceDatabase.getDatabase(context).dao()
    val (cycleStart, cycleEnd) = PayCycle.current(now)
    val homeCurrency = FinancePreferences.currencyCodeFlow.value
    val cycleExpenses = dao.getTransactionsBetweenDates(cycleStart, cycleEnd)
        .filter { it.type == TransactionType.EXPENSE && it.currency.equals(homeCurrency, ignoreCase = true) }
    val spentByCategory = cycleExpenses.groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val categories = dao.getCategoriesList()
    val envelopes = categories
        .filter { it.budgetLimit > 0 }
        .map { WidgetEnvelope(it.name, spentByCategory[it.id] ?: 0.0, it.budgetLimit + it.rolloverAmount) }
        .sortedBy { if (it.limit <= 0.0) 0.0 else it.remaining / it.limit }

    val dayStart = startOfDay(now)
    return CycleSnapshot(
        spentToday = cycleExpenses.filter { it.date >= dayStart }.sumOf { it.amount },
        spentThisCycle = cycleExpenses.sumOf { it.amount },
        cycleBudget = categories.sumOf { it.budgetLimit },
        cycleStart = cycleStart,
        cycleEnd = cycleEnd,
        envelopes = envelopes
    )
}

internal enum class PaceState { NO_BUDGET, ON_TRACK, UNDER_PACE, OVER_PACE, OVER_BUDGET }

internal data class Allowance(
    /** What can be spent per day, today included, without overrunning the budget. */
    val perDay: Double,
    /** Days left in the cycle, today included. */
    val daysLeft: Int,
    val remaining: Double,
    /** Spent minus what an even pace would have spent by now. Positive is ahead of pace. */
    val paceGap: Double,
    val state: PaceState
)

/**
 * Splits what is left of the cycle's budget evenly over the days left, today included — so the
 * figure is what today may cost, not what is left after today.
 *
 * "On track" allows a 5% band either side of an even pace: a single grocery run shouldn't flip the
 * widget to amber on the first morning of a cycle.
 */
internal fun allowanceOf(snapshot: CycleSnapshot, now: Long = System.currentTimeMillis()): Allowance {
    val budget = snapshot.cycleBudget
    // Counted in calendar days, not divided out of milliseconds: a cycle that crosses a DST
    // fall-back has a 25-hour day in it, and ceil() of that read as one more day than there is.
    val daysLeft = (daysFromToday(snapshot.cycleEnd, now) + 1).coerceAtLeast(1)
    if (budget <= 0.0) return Allowance(0.0, daysLeft, 0.0, 0.0, PaceState.NO_BUDGET)

    val remaining = budget - snapshot.spentThisCycle
    val cycleLength = (snapshot.cycleEnd + 1 - snapshot.cycleStart).coerceAtLeast(1)
    val elapsed = ((now - snapshot.cycleStart).toDouble() / cycleLength).coerceIn(0.0, 1.0)
    val paceGap = snapshot.spentThisCycle - budget * elapsed
    val band = budget * 0.05
    val state = when {
        remaining < 0 -> PaceState.OVER_BUDGET
        paceGap > band -> PaceState.OVER_PACE
        paceGap < -band -> PaceState.UNDER_PACE
        else -> PaceState.ON_TRACK
    }
    return Allowance(remaining.coerceAtLeast(0.0) / daysLeft, daysLeft, remaining, paceGap, state)
}

// ---------------------------------------------------------------------------------------------
// Last 7 days
// ---------------------------------------------------------------------------------------------

internal data class WeekSnapshot(
    /** Oldest first; the last entry is today. */
    val days: List<Pair<Long, Double>>,
    val total: Double,
    val previousTotal: Double,
    val topCategory: String?,
    val topCategoryAmount: Double
)

/** The last seven days including today, against the seven before them. */
internal suspend fun loadWeekSnapshot(context: Context, now: Long = System.currentTimeMillis()): WeekSnapshot {
    val dao = FinanceDatabase.getDatabase(context).dao()
    val homeCurrency = FinancePreferences.currencyCodeFlow.value
    val today = startOfDay(now)
    val dayStarts = (6 downTo 0).map { back ->
        Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
    }
    val previousStart = Calendar.getInstance().apply { timeInMillis = dayStarts.first(); add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

    val expenses = dao.getTransactionsBetweenDates(previousStart, now)
        .filter { it.type == TransactionType.EXPENSE && it.currency.equals(homeCurrency, ignoreCase = true) }
    val (current, previous) = expenses.partition { it.date >= dayStarts.first() }

    // Each day ends where the next begins rather than 24h later, which a DST change would break.
    val days = dayStarts.mapIndexed { i, start ->
        val end = dayStarts.getOrNull(i + 1) ?: Long.MAX_VALUE
        start to current.filter { it.date in start until end }.sumOf { it.amount }
    }
    val top = current.groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }.maxByOrNull { it.value }
    val topName = top?.let { (id, _) -> dao.getCategoriesList().firstOrNull { it.id == id }?.name }

    return WeekSnapshot(
        days = days,
        total = current.sumOf { it.amount },
        previousTotal = previous.sumOf { it.amount },
        topCategory = topName,
        topCategoryAmount = top?.value ?: 0.0
    )
}

// ---------------------------------------------------------------------------------------------
// Coming up
// ---------------------------------------------------------------------------------------------

internal enum class DueKind { BILL, INCOME, YOU_OWE, OWED_TO_YOU }

internal data class DueItem(val title: String, val date: Long, val amount: Double, val kind: DueKind)

internal data class UpcomingSnapshot(
    /** Overdue first, then soonest. */
    val items: List<DueItem>,
    /** Money leaving in the window: bills plus IOUs you have to pay. */
    val outgoing: Double,
    val owedToYou: Double,
    val youOwe: Double
)

/**
 * Recurring transactions and IOU due dates over the next [windowDays] days. Overdue IOUs are kept
 * (that's when a reminder matters most); overdue recurring rows are not, because the recurring
 * worker logs those itself. The owed totals cover every open IOU, due date or not.
 *
 * The list shows each recurring row once, at its next date, so a daily bill can't push everything
 * else off the widget. The outgoing total counts every occurrence in the window: a weekly ₹500
 * bill is four or five payments in thirty days, not one.
 */
internal suspend fun loadUpcomingSnapshot(
    context: Context,
    now: Long = System.currentTimeMillis(),
    windowDays: Int = 30
): UpcomingSnapshot {
    val dao = FinanceDatabase.getDatabase(context).dao()
    val today = startOfDay(now)
    val horizon = today + (windowDays + 1) * DAY_MS
    val categoryNames = dao.getCategoriesList().associate { it.id to it.name }

    val activeRecurring = dao.getRecurringTransactionsList()
        .filter { !it.isPaused && it.nextExecutionDate in today until horizon }
    val recurring = activeRecurring
        .map {
            DueItem(
                title = it.note.ifBlank { categoryNames[it.categoryId] ?: "Recurring" },
                date = it.nextExecutionDate,
                amount = it.amount,
                kind = if (it.type == TransactionType.INCOME) DueKind.INCOME else DueKind.BILL
            )
        }

    val openDebts = dao.getDebtsList().filter { !it.isSettled && it.amount - it.paidAmount > 0.005 }
    val debts = openDebts
        .filter { it.dueDate != null && it.dueDate < horizon }
        .map {
            val owed = it.amount - it.paidAmount
            if (it.type == DebtType.BORROWED) DueItem("Pay ${it.personName}", it.dueDate!!, owed, DueKind.YOU_OWE)
            else DueItem("${it.personName} owes you", it.dueDate!!, owed, DueKind.OWED_TO_YOU)
        }

    // occurrencesBetween covers (from, to]; every row here is due on or after today, so starting
    // one millisecond before midnight takes today in without pulling an overdue date forward.
    val billsInWindow = activeRecurring
        .filter { it.type != TransactionType.INCOME }
        .sumOf { Forecaster.occurrencesBetween(it, today - 1, horizon - 1).size * it.amount }

    val items = (recurring + debts).sortedBy { it.date }
    return UpcomingSnapshot(
        items = items,
        outgoing = billsInWindow + debts.filter { it.kind == DueKind.YOU_OWE }.sumOf { it.amount },
        owedToYou = openDebts.filter { it.type == DebtType.LENT }.sumOf { it.amount - it.paidAmount },
        youOwe = openDebts.filter { it.type == DebtType.BORROWED }.sumOf { it.amount - it.paidAmount }
    )
}
