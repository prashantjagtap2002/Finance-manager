package com.example.financemanager.domain

import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import java.util.Calendar

data class DaySpend(val day: Int, val total: Double, val count: Int)

/** One month laid out for a heatmap. */
data class SpendingMonth(
    val year: Int,
    /** 0-based, as [Calendar.MONTH]. */
    val month: Int,
    val daysInMonth: Int,
    /** Blank cells before day 1, for a week that starts on [Calendar.getFirstDayOfWeek]. */
    val leadingBlanks: Int,
    val days: Map<Int, DaySpend>,
    val total: Double,
    /** Spend at which each intensity level starts; see [SpendingCalendar.level]. */
    val thresholds: List<Double>
)

object SpendingCalendar {

    const val LEVELS = 4

    fun build(
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek
    ): SpendingMonth {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        val start = cal.timeInMillis
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leading = (cal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis

        val dayCal = Calendar.getInstance()
        val days = transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.date >= start && it.date < end }
            .groupBy { dayCal.apply { timeInMillis = it.date }.get(Calendar.DAY_OF_MONTH) }
            .mapValues { (day, txs) -> DaySpend(day, txs.sumOf { it.amount }, txs.size) }

        return SpendingMonth(
            year = year,
            month = month,
            daysInMonth = daysInMonth,
            leadingBlanks = leading,
            days = days,
            total = days.values.sumOf { it.total },
            thresholds = thresholds(days.values.map { it.total })
        )
    }

    /**
     * Level boundaries at the quartiles of the days that had any spending. Scaling against the
     * single biggest day would let one rent payment wash every other day out to the palest shade.
     */
    internal fun thresholds(totals: List<Double>): List<Double> {
        val sorted = totals.filter { it > 0.0 }.sorted()
        if (sorted.isEmpty()) return emptyList()
        return (1 until LEVELS).map { q -> sorted[((sorted.size - 1) * q) / LEVELS] }
    }

    /** 0 for no spending, then 1..[LEVELS] by quartile. */
    fun level(total: Double, thresholds: List<Double>): Int {
        if (total <= 0.0) return 0
        return 1 + thresholds.count { total > it }
    }

    /** Epoch range [start, end) of one day in the month, for listing that day's transactions. */
    fun dayRange(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }
}
