package com.example.financemanager.domain

import com.example.financemanager.data.Category
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType

/** One envelope that, at the current pace, will not last the cycle. */
data class EnvelopeForecast(
    val categoryId: Long,
    val name: String,
    val limit: Double,
    val spent: Double,
    val projectedSpend: Double,
    /** When the envelope empties at the current pace; at or before `now` if it already has. */
    val runsOutAt: Long
)

data class CashForecast(
    val cycleEnd: Long,
    val daysLeft: Int,
    val currentBalance: Double,
    /** Everyday spending per day so far this cycle, with scheduled bills left out. */
    val dailyPace: Double,
    val projectedEverydaySpend: Double,
    val upcomingBills: Double,
    val upcomingIncome: Double,
    val projectedBalance: Double,
    val envelopesAtRisk: List<EnvelopeForecast>,
    /** Too few days have passed for the pace to mean much. The UI says so. */
    val isEarlyEstimate: Boolean
)

/**
 * Projects the balance to the end of the pay cycle: what is in the accounts now, less everyday
 * spending continued at this cycle's pace, less the recurring bills still to land, plus any
 * recurring income still to arrive.
 *
 * Everyday pace deliberately excludes recurring rows. Those are already counted by the schedule,
 * and a rent payment on day 1 would otherwise be extrapolated into thirty rent payments.
 */
object Forecaster {

    private const val DAY_MS = 86_400_000L

    /**
     * The pace never divides by less than this many days. On the first morning of a cycle one
     * purchase would otherwise project as that purchase every day for a month.
     */
    private const val MIN_PACE_DAYS = 3.0

    /** Below this many elapsed days the forecast is labelled an early estimate. */
    private const val EARLY_DAYS = 7.0

    /** How far back [oneOffCap] looks for what a usual purchase costs. */
    private const val CAP_HISTORY_DAYS = 90L

    /** Fewer everyday expenses than this in the history and no cap is applied at all. */
    private const val CAP_MIN_SAMPLES = 20

    /** Purchases at or below this share of the user's history count in full. */
    private const val CAP_PERCENTILE = 0.9

    /**
     * The most a single everyday expense contributes to the *pace*, or null for no cap: the 90th
     * percentile of everyday expense amounts over the last [CAP_HISTORY_DAYS].
     *
     * Without it one birthday dinner on day 6 projected as that dinner's share every day for the
     * rest of the month — on real data the September forecast expected ₹28.7k more spending when
     * ₹8.3k followed, and November showed a ₹-11.8k "shortfall" on a positive balance. Capped,
     * the same backtest projects ₹10.4k. The purchase still counts in full as already spent (it is
     * in the balance); it just isn't assumed to repeat.
     */
    internal fun oneOffCap(history: List<Transaction>, now: Long): Double? {
        val from = now - CAP_HISTORY_DAYS * DAY_MS
        val amounts = history
            .filter { it.type == TransactionType.EXPENSE && !it.isRecurring && it.date in from until now }
            .map { it.amount }
            .sorted()
        if (amounts.size < CAP_MIN_SAMPLES) return null
        return amounts[(CAP_PERCENTILE * amounts.size).toInt().coerceAtMost(amounts.size - 1)]
    }

    fun forecast(
        now: Long,
        cycleStart: Long,
        cycleEnd: Long,
        currentBalance: Double,
        cycleTransactions: List<Transaction>,
        categories: List<Category>,
        recurring: List<RecurringTransaction>,
        /** Recent transactions beyond this cycle, to learn a usual purchase size; see [oneOffCap]. */
        history: List<Transaction> = emptyList()
    ): CashForecast {
        val elapsedDays = ((now - cycleStart).toDouble() / DAY_MS).coerceAtLeast(0.0)
        val paceDays = elapsedDays.coerceAtLeast(MIN_PACE_DAYS)
        val remainingMs = (cycleEnd - now).coerceAtLeast(0L)
        val remainingDays = remainingMs.toDouble() / DAY_MS

        val expenses = cycleTransactions.filter { it.type == TransactionType.EXPENSE && it.date <= now }
        val everyday = expenses.filterNot { it.isRecurring }
        val cap = oneOffCap(history, now)
        val paceAmount: (Transaction) -> Double = { t -> if (cap == null) t.amount else minOf(t.amount, cap) }
        val dailyPace = everyday.sumOf(paceAmount) / paceDays

        val activeRecurring = recurring.filter { !it.isPaused }
        val billOccurrences = activeRecurring
            .filter { it.type == TransactionType.EXPENSE }
            .flatMap { rec -> occurrencesBetween(rec, now, cycleEnd).map { rec } }
        val upcomingBills = billOccurrences.sumOf { it.amount }
        val upcomingIncome = activeRecurring
            .filter { it.type == TransactionType.INCOME }
            .sumOf { rec -> occurrencesBetween(rec, now, cycleEnd).size * rec.amount }

        val projectedEveryday = dailyPace * remainingDays

        // Per envelope: the same pace split by category, plus that envelope's own scheduled bills.
        val spentByCategory = expenses.groupBy { it.categoryId }.mapValues { (_, t) -> t.sumOf { it.amount } }
        val everydayByCategory = everyday.groupBy { it.categoryId }.mapValues { (_, t) -> t.sumOf(paceAmount) }
        val billsByCategory = billOccurrences.groupBy { it.categoryId }.mapValues { (_, r) -> r.sumOf { it.amount } }

        val atRisk = categories
            .filter { it.budgetLimit > 0.0 }
            .mapNotNull { category ->
                val limit = category.budgetLimit + category.rolloverAmount
                val spent = spentByCategory[category.id] ?: 0.0
                val pace = (everydayByCategory[category.id] ?: 0.0) / paceDays
                val projected = spent + pace * remainingDays + (billsByCategory[category.id] ?: 0.0)
                if (projected <= limit) return@mapNotNull null
                val runsOutAt = when {
                    spent >= limit -> now
                    pace > 0.0 -> now + (((limit - spent) / pace) * DAY_MS).toLong()
                    // No everyday pace: it is a scheduled bill that tips it over, so it runs out
                    // when that bill lands.
                    else -> billOccurrences
                        .filter { it.categoryId == category.id }
                        .minOfOrNull { it.nextExecutionDate.coerceAtLeast(now) } ?: cycleEnd
                }
                EnvelopeForecast(
                    categoryId = category.id,
                    name = category.name,
                    limit = limit,
                    spent = spent,
                    projectedSpend = projected,
                    runsOutAt = runsOutAt.coerceAtMost(cycleEnd)
                )
            }
            .sortedBy { it.runsOutAt }

        return CashForecast(
            cycleEnd = cycleEnd,
            daysLeft = kotlin.math.ceil(remainingDays).toInt(),
            currentBalance = currentBalance,
            dailyPace = dailyPace,
            projectedEverydaySpend = projectedEveryday,
            upcomingBills = upcomingBills,
            upcomingIncome = upcomingIncome,
            projectedBalance = currentBalance - projectedEveryday - upcomingBills + upcomingIncome,
            envelopesAtRisk = atRisk,
            isEarlyEstimate = elapsedDays < EARLY_DAYS
        )
    }

    /**
     * Dates in (from, to] that [rec] will execute on, capped so a daily schedule stays cheap.
     *
     * An overdue template contributes every occurrence it missed, each landing on the next pass
     * (just after [from]): [RecurringScheduler] catches up all of them, not only the latest, so
     * counting an overdue monthly bill three months behind as one payment understated the cycle.
     */
    internal fun occurrencesBetween(rec: RecurringTransaction, from: Long, to: Long): List<Long> {
        val out = mutableListOf<Long>()
        var next = rec.nextExecutionDate
        while (next <= to && out.size < 400) {
            out += maxOf(next, from + 1)
            val advanced = RecurringScheduler.getNextIntervalDate(next, rec.interval)
            if (advanced <= next) break
            next = advanced
        }
        return out
    }
}
