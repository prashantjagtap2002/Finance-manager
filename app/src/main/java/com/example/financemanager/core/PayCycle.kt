package com.example.financemanager.core

import java.util.Calendar

/**
 * The current pay cycle as an inclusive [start, end] range. Pure, so the widget and the bill
 * worker — which run without the view model — compute exactly the cycle the app shows.
 */
object PayCycle {
    fun range(
        frequency: PayCycleFrequency,
        anchorDay: Int,
        now: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (frequency) {
            PayCycleFrequency.WEEKLY -> steppedFromPayday(cal, anchorDay.coerceIn(1, 28), 7)
            PayCycleFrequency.BIWEEKLY -> steppedFromPayday(cal, anchorDay.coerceIn(1, 28), 14)
            PayCycleFrequency.SEMIMONTHLY -> {
                // Two paydays 14 days apart, so the halves are the same length: 1 & 15 (as it always
                // was for the default anchor), 6 & 20, 14 & 28. It used to pair the anchor with the
                // 15th, which made anchor 14 a one-day cycle and every anchor from 15 up its own
                // second payday — silently a monthly cycle.
                val raw = anchorDay.coerceIn(1, 28)
                val anchor = if (raw <= 14) raw else raw - 14
                val secondAnchor = anchor + 14
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                if (currentDay >= secondAnchor) {
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    start to (cal.timeInMillis - 1)
                } else if (currentDay >= anchor) {
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    val start = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    start to (cal.timeInMillis - 1)
                } else {
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    start to (cal.timeInMillis - 1)
                }
            }
            PayCycleFrequency.MONTHLY -> {
                val anchor = anchorDay.coerceIn(1, 28)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                if (currentDay < anchor) {
                    cal.add(Calendar.MONTH, -1)
                }
                cal.set(Calendar.DAY_OF_MONTH, anchor)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                start to (cal.timeInMillis - 1)
            }
        }
    }

    /**
     * Weekly and fortnightly cycles counted from the most recent payday ([anchor] day of month):
     * blocks of [days] days from that date, the last one cut short at the next payday so the
     * blocks never overlap and the cycle that contains today is the same whichever day asks.
     *
     * This used to take one block from the last payday and stop, so from day 8 after payday on,
     * "the current week" was a week that had already ended and today fell outside its own cycle.
     */
    private fun steppedFromPayday(today: Calendar, anchor: Int, days: Int): Pair<Long, Long> {
        val now = today.timeInMillis
        val payday = (today.clone() as Calendar).apply {
            if (get(Calendar.DAY_OF_MONTH) < anchor) add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, anchor)
        }
        val nextPayday = (payday.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
        val start = payday
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }
        // Calendar steps rather than millisecond arithmetic, so a DST change can't shift midnight.
        while (end.timeInMillis <= now) {
            start.add(Calendar.DAY_OF_YEAR, days)
            end.add(Calendar.DAY_OF_YEAR, days)
        }
        return start.timeInMillis to (minOf(end.timeInMillis, nextPayday) - 1)
    }

    /** The range for whatever cycle the user has saved. */
    fun current(now: Long = System.currentTimeMillis()): Pair<Long, Long> =
        range(FinancePreferences.payCycleFrequencyFlow.value, FinancePreferences.payCycleAnchorDayFlow.value, now)
}
