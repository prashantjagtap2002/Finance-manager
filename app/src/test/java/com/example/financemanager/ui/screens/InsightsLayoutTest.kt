package com.example.financemanager.ui.screens

import com.example.financemanager.core.InsightLayout
import com.example.financemanager.core.InsightSection
import com.example.financemanager.core.SavedInsight
import com.example.financemanager.core.resolvedInsightOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class InsightsLayoutTest {

    private val defaults = InsightSection.entries.map { it.key }

    @Test
    fun emptyLayoutUsesDefaultOrder() {
        assertEquals(defaults, resolvedInsightOrder(InsightLayout()))
    }

    @Test
    fun savedOrderWinsAndMissingSectionsAreAppended() {
        val layout = InsightLayout(order = listOf("forecast", "daily_bars"))
        val order = resolvedInsightOrder(layout)
        assertEquals(listOf("forecast", "daily_bars"), order.take(2))
        assertEquals(defaults.toSet(), order.toSet())
        assertEquals(defaults.size, order.size)
    }

    @Test
    fun unknownKeysAndRemovedChartsAreDropped() {
        val layout = InsightLayout(order = listOf("custom:gone", "no_such_section", "achievements"))
        assertEquals("achievements", resolvedInsightOrder(layout).first())
        assertEquals(defaults.size, resolvedInsightOrder(layout).size)
    }

    @Test
    fun pinnedChartCanSitAnywhere() {
        val saved = SavedInsight(id = "a", visual = "BAR", metric = "SPENDING", period = "SIX")
        val layout = InsightLayout(order = listOf("daily_bars", "custom:a", "forecast"), saved = listOf(saved))
        assertEquals(listOf("daily_bars", "custom:a", "forecast"), resolvedInsightOrder(layout).take(3))
    }

    // Wednesday 23 Sep 2026, 15:00 local.
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 23, 15, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun day(millis: Long): String = Calendar.getInstance().apply { timeInMillis = millis }.let {
        "%04d-%02d-%02d %02d:%02d".format(
            it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH),
            it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)
        )
    }

    @Test
    fun yesterdayIsTheWholePreviousDay() {
        val (start, end) = computeDynamicDateRange(DynamicDateFilter.YESTERDAY, 0, null, null, now)
        assertEquals("2026-09-22 00:00", day(start))
        assertEquals("2026-09-22 23:59", day(end))

        val (prevStart, _) = computeDynamicDateRange(DynamicDateFilter.YESTERDAY, -1, null, null, now)
        assertEquals("2026-09-21 00:00", day(prevStart))
    }

    @Test
    fun thisWeekRunsMondayToNow() {
        val (start, end) = computeDynamicDateRange(DynamicDateFilter.THIS_WEEK, 0, null, null, now)
        assertEquals("2026-09-21 00:00", day(start))
        assertEquals("2026-09-23 23:59", day(end))

        val (lastStart, lastEnd) = computeDynamicDateRange(DynamicDateFilter.THIS_WEEK, -1, null, null, now)
        assertEquals("2026-09-14 00:00", day(lastStart))
        assertEquals("2026-09-20 23:59", day(lastEnd))
    }

    @Test
    fun thisWeekOnASundayStartsTheMondayBefore() {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 27, 10, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val (start, _) = computeDynamicDateRange(DynamicDateFilter.THIS_WEEK, 0, null, null, sunday)
        assertEquals("2026-09-21 00:00", day(start))
    }

    @Test
    fun thisMonthStartsOnTheFirst() {
        val (start, end) = computeDynamicDateRange(DynamicDateFilter.THIS_MONTH, 0, null, null, now)
        assertEquals("2026-09-01 00:00", day(start))
        assertEquals("2026-09-23 23:59", day(end))
    }
}
