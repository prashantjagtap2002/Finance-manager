package com.example.financemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PayCycleTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun day(millis: Long): Triple<Int, Int, Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }

    private fun assertContains(range: Pair<Long, Long>, now: Long) {
        assertTrue("cycle ${day(range.first)}..${day(range.second)} must contain ${day(now)}", now in range.first..range.second)
    }

    @Test
    fun weekly_containsToday_longAfterPayday() {
        val now = at(2026, 11, 20)
        val range = PayCycle.range(PayCycleFrequency.WEEKLY, 5, now)
        assertContains(range, now)
        assertEquals(Triple(2026, 11, 19), day(range.first))
        assertEquals(Triple(2026, 11, 25), day(range.second))
    }

    @Test
    fun weekly_lastBlockStopsAtNextPayday() {
        // Paydays 5 Nov and 5 Dec: blocks from 5 Nov start 5, 12, 19, 26 Nov and 3 Dec, and that
        // last one is cut short at the next payday rather than running into the new month's week.
        val now = at(2026, 12, 3)
        val range = PayCycle.range(PayCycleFrequency.WEEKLY, 5, now)
        assertEquals(Triple(2026, 12, 3), day(range.first))
        assertEquals(Triple(2026, 12, 4), day(range.second))
        val before = PayCycle.range(PayCycleFrequency.WEEKLY, 5, at(2026, 12, 2))
        assertEquals(Triple(2026, 11, 26), day(before.first))
        assertEquals(Triple(2026, 12, 2), day(before.second))
    }

    @Test
    fun weekly_everyDayOfAYearIsInsideItsOwnCycle_andCyclesNeverOverlap() {
        for (freq in listOf(PayCycleFrequency.WEEKLY, PayCycleFrequency.BIWEEKLY, PayCycleFrequency.SEMIMONTHLY, PayCycleFrequency.MONTHLY)) {
            for (anchor in listOf(1, 5, 14, 15, 20, 28)) {
                var previous: Pair<Long, Long>? = null
                for (d in 0 until 400) {
                    val now = Calendar.getInstance().apply { clear(); set(2026, 0, 1, 12, 0, 0); add(Calendar.DAY_OF_YEAR, d) }.timeInMillis
                    val range = PayCycle.range(freq, anchor, now)
                    assertContains(range, now)
                    val prev = previous
                    if (prev != null && prev != range) {
                        assertEquals("$freq/$anchor: a new cycle must start right after the last one ends", prev.second + 1, range.first)
                    }
                    previous = range
                }
            }
        }
    }

    @Test
    fun biweekly_containsToday() {
        val now = at(2026, 11, 20)
        val range = PayCycle.range(PayCycleFrequency.BIWEEKLY, 1, now)
        assertContains(range, now)
        assertEquals(Triple(2026, 11, 15), day(range.first))
    }

    @Test
    fun semimonthly_anchorAbove14_isTwoCyclesAMonth() {
        val early = PayCycle.range(PayCycleFrequency.SEMIMONTHLY, 20, at(2026, 11, 10))
        assertEquals(Triple(2026, 11, 6), day(early.first))
        assertEquals(Triple(2026, 11, 19), day(early.second))
        val late = PayCycle.range(PayCycleFrequency.SEMIMONTHLY, 20, at(2026, 11, 25))
        assertEquals(Triple(2026, 11, 20), day(late.first))
        assertEquals(Triple(2026, 12, 5), day(late.second))
    }

    @Test
    fun semimonthly_defaultAnchor_keepsFirstAndFifteenth() {
        val range = PayCycle.range(PayCycleFrequency.SEMIMONTHLY, 1, at(2026, 11, 8))
        assertEquals(Triple(2026, 11, 1), day(range.first))
        assertEquals(Triple(2026, 11, 14), day(range.second))
    }

    @Test
    fun monthly_unchanged() {
        val range = PayCycle.range(PayCycleFrequency.MONTHLY, 1, at(2026, 11, 8))
        assertEquals(Triple(2026, 11, 1), day(range.first))
        assertEquals(Triple(2026, 11, 30), day(range.second))
    }
}
