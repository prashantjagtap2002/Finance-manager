package com.example.financemanager.domain

import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastOneOffTest {

    private val day = 86_400_000L
    private val start = 1_780_000_000_000L
    private val end = start + 30 * day

    private fun expense(id: Long, amount: Double, at: Long, recurring: Boolean = false) =
        Transaction(id = id, amount = amount, type = TransactionType.EXPENSE, categoryId = 1, sourceAccountId = 1,
            date = at, isRecurring = recurring)

    /** Sixty ordinary purchases of ₹50–₹200 spread over the two months before [start]. */
    private val usualHistory = (0 until 60).map { i ->
        expense(1000L + i, 50.0 + (i % 4) * 50.0, start - (i + 1) * day)
    }

    @Test
    fun aOneOffPurchaseCountsAsSpentButIsNotProjectedForward() {
        val now = start + 7 * day
        val cycle = listOf(
            expense(1, 100.0, start + day),
            expense(2, 3500.0, start + 5 * day), // birthday dinner
            expense(3, 150.0, start + 6 * day)
        )
        val uncapped = Forecaster.forecast(now, start, end, 20_000.0, cycle, emptyList(), emptyList())
        val capped = Forecaster.forecast(now, start, end, 20_000.0, cycle, emptyList(), emptyList(), history = usualHistory + cycle)

        assertEquals(3750.0 / 7, uncapped.dailyPace, 0.001)
        // The dinner contributes the usual top purchase (₹200), not ₹3,500, to the pace.
        assertEquals((100.0 + 200.0 + 150.0) / 7, capped.dailyPace, 0.001)
        assertTrue(capped.projectedBalance > uncapped.projectedBalance)
    }

    @Test
    fun noCapWithoutEnoughHistory() {
        assertNull(Forecaster.oneOffCap(usualHistory.take(10), start))
    }

    @Test
    fun recurringRowsDoNotSetTheCap() {
        val rentHistory = (0 until 30).map { expense(5000L + it, 20_000.0, start - (it + 1) * day, recurring = true) }
        assertNull(Forecaster.oneOffCap(rentHistory, start))
    }

    @Test
    fun overdueTemplateCountsEveryMissedOccurrence() {
        // Monthly bill last due three months ago: the scheduler will catch up all three missed
        // payments on its next pass, plus whatever falls due inside the window.
        val rec = RecurringTransaction(amount = 500.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = 1,
            interval = RecurringInterval.MONTHLY, startDate = start - 100 * day, nextExecutionDate = start - 89 * day)
        val occurrences = Forecaster.occurrencesBetween(rec, start, end)
        assertTrue("got ${occurrences.size}", occurrences.size >= 3)
        assertTrue(occurrences.all { it in (start + 1)..end })
    }
}
