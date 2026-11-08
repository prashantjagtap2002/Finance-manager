package com.example.financemanager.services.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AllowanceTest {

    /** A 30-day cycle starting at midnight on 1 Oct, and "now" at noon on day [day] (1-based). */
    private fun cycle(spent: Double, budget: Double, day: Int): Pair<CycleSnapshot, Long> {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 30) }.timeInMillis - 1
        val now = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, day - 1)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
        return CycleSnapshot(0.0, spent, budget, start, end, emptyList()) to now
    }

    @Test
    fun `splits what is left over the days left, today included`() {
        val (snapshot, now) = cycle(spent = 5_000.0, budget = 15_000.0, day = 11)
        val allowance = allowanceOf(snapshot, now)
        assertEquals(20, allowance.daysLeft)
        assertEquals(500.0, allowance.perDay, 0.001)
        assertEquals(10_000.0, allowance.remaining, 0.001)
    }

    @Test
    fun `the last day of the cycle gets everything that is left`() {
        val (snapshot, now) = cycle(spent = 14_000.0, budget = 15_000.0, day = 30)
        val allowance = allowanceOf(snapshot, now)
        assertEquals(1, allowance.daysLeft)
        assertEquals(1_000.0, allowance.perDay, 0.001)
    }

    @Test
    fun `an even pace within five percent reads as on track`() {
        // Half way through day 16 of 30 is 51.7% of the cycle; 7,600 of 15,000 is 50.7%.
        val (snapshot, now) = cycle(spent = 7_600.0, budget = 15_000.0, day = 16)
        assertEquals(PaceState.ON_TRACK, allowanceOf(snapshot, now).state)
    }

    @Test
    fun `spending well ahead of an even pace is flagged`() {
        val (snapshot, now) = cycle(spent = 9_000.0, budget = 15_000.0, day = 6)
        assertEquals(PaceState.OVER_PACE, allowanceOf(snapshot, now).state)
    }

    @Test
    fun `over budget never offers a negative allowance`() {
        val (snapshot, now) = cycle(spent = 16_000.0, budget = 15_000.0, day = 20)
        val allowance = allowanceOf(snapshot, now)
        assertEquals(PaceState.OVER_BUDGET, allowance.state)
        assertEquals(0.0, allowance.perDay, 0.001)
        assertEquals(-1_000.0, allowance.remaining, 0.001)
    }

    @Test
    fun `a cycle crossing the end of daylight saving has no extra day`() {
        // New York falls back on 1 Nov 2026, so the cycle's first day is 25 hours long. Dividing
        // milliseconds and rounding up used to count 31 days left in a 30-day month.
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val start = Calendar.getInstance().apply {
                set(2026, Calendar.NOVEMBER, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis - 1
            val now = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
            val snapshot = CycleSnapshot(0.0, 0.0, 3_000.0, start, end, emptyList())
            assertEquals(30, allowanceOf(snapshot, now).daysLeft)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `no envelope budgets means nothing to divide`() {
        val (snapshot, now) = cycle(spent = 3_000.0, budget = 0.0, day = 5)
        assertEquals(PaceState.NO_BUDGET, allowanceOf(snapshot, now).state)
    }
}
