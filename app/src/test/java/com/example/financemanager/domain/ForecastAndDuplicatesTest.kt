package com.example.financemanager.domain

import com.example.financemanager.data.Category
import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ForecastAndDuplicatesTest {

    private val day = 86_400_000L
    private val start = 1_780_000_000_000L
    private val end = start + 30 * day

    private fun expense(id: Long, amount: Double, at: Long, category: Long = 1, auto: Boolean = false, recurring: Boolean = false, note: String = "", account: Long = 1) =
        Transaction(id = id, amount = amount, type = TransactionType.EXPENSE, categoryId = category, sourceAccountId = account,
            date = at, isAutoLogged = auto, isRecurring = recurring, note = note)

    // --- Forecast ---

    @Test
    fun projectsEverydayPaceBillsAndIncome() {
        val now = start + 10 * day
        val txs = listOf(expense(1, 1000.0, start + day), expense(2, 9000.0, start + 2 * day, recurring = true))
        val rent = RecurringTransaction(id = 1, amount = 500.0, type = TransactionType.EXPENSE, categoryId = 2, accountId = 1,
            interval = RecurringInterval.MONTHLY, startDate = start, nextExecutionDate = now + 5 * day, isAutoLog = true)
        val salary = rent.copy(id = 2, amount = 2000.0, type = TransactionType.INCOME, nextExecutionDate = now + 6 * day)

        val f = Forecaster.forecast(now, start, end, 10_000.0, txs, emptyList(), listOf(rent, salary))

        // Pace counts only the everyday ₹1000 over 10 days, not the recurring ₹9000.
        assertEquals(100.0, f.dailyPace, 0.001)
        assertEquals(2000.0, f.projectedEverydaySpend, 0.001)
        assertEquals(500.0, f.upcomingBills, 0.001)
        assertEquals(2000.0, f.upcomingIncome, 0.001)
        assertEquals(10_000.0 - 2000.0 - 500.0 + 2000.0, f.projectedBalance, 0.001)
        assertEquals(20, f.daysLeft)
    }

    @Test
    fun earlyCycleDoesNotExtrapolateOneDayIntoAMonth() {
        val now = start + day / 2
        val f = Forecaster.forecast(now, start, end, 0.0, listOf(expense(1, 300.0, start)), emptyList(), emptyList())
        assertEquals(100.0, f.dailyPace, 0.001) // divided by the 3-day floor, not half a day
        assertTrue(f.isEarlyEstimate)
    }

    @Test
    fun flagsEnvelopeThatRunsOutAndWhen() {
        val now = start + 10 * day
        val food = Category(id = 1, name = "Food", iconName = "", colorHex = "#000000", budgetLimit = 3000.0)
        val f = Forecaster.forecast(now, start, end, 0.0, listOf(expense(1, 2000.0, start + day)), listOf(food), emptyList())
        val risk = f.envelopesAtRisk.single()
        assertEquals(6000.0, risk.projectedSpend, 0.001)
        // ₹1000 left at ₹200/day → 5 days from now.
        assertEquals(now + 5 * day, risk.runsOutAt)
    }

    @Test
    fun dailyBillIsCountedOncePerRemainingDay() {
        val rec = RecurringTransaction(amount = 10.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = 1,
            interval = RecurringInterval.DAILY, startDate = start, nextExecutionDate = start + day)
        assertEquals(30, Forecaster.occurrencesBetween(rec, start, end).size)
    }

    // --- Duplicates ---

    @Test
    fun pairsManualEntryWithSmsLoggedCopy() {
        val manual = expense(1, 250.0, start)
        val sms = expense(2, 250.0, start + 60_000, auto = true)
        val pairs = DuplicateDetector.findPairs(listOf(sms, manual))
        assertEquals(1, pairs.size)
        assertEquals(1L, pairs[0].first.id)
    }

    @Test
    fun twoSimilarPurchasesFromSameSourceAreNotFlagged() {
        val tea1 = expense(1, 20.0, start, note = "Tea")
        val tea2 = expense(2, 20.0, start + 60_000, note = "Snacks")
        assertTrue(DuplicateDetector.findPairs(listOf(tea1, tea2)).isEmpty())
    }

    @Test
    fun outsideWindowDismissedOrRecurringAreSkipped() {
        val a = expense(1, 99.0, start)
        val late = expense(2, 99.0, start + DuplicateDetector.WINDOW_MS + 1, auto = true)
        assertTrue(DuplicateDetector.findPairs(listOf(a, late)).isEmpty())

        val near = expense(3, 99.0, start + 1000, auto = true)
        assertTrue(DuplicateDetector.findPairs(listOf(a, near), dismissed = setOf("1:3")).isEmpty())

        val rec = expense(4, 99.0, start + 1000, auto = true, recurring = true)
        assertTrue(DuplicateDetector.findPairs(listOf(a, rec)).isEmpty())
    }

    @Test
    fun findsExistingManualEntryForIncomingAlert() {
        val manual = expense(1, 480.0, start)
        assertNotNull(DuplicateDetector.findExisting(480.0, isCredit = false, date = start + 120_000, transactions = listOf(manual)))
        assertNull(DuplicateDetector.findExisting(480.0, isCredit = true, date = start + 120_000, transactions = listOf(manual)))
        assertNull(DuplicateDetector.findExisting(481.0, isCredit = false, date = start, transactions = listOf(manual)))
    }

    // --- Calendar ---

    @Test
    fun calendarBucketsByDayAndLevelsByQuartile() {
        val cal = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 1, 12, 0) }
        val d1 = cal.timeInMillis
        val txs = listOf(
            expense(1, 100.0, d1),
            expense(2, 50.0, d1 + 1000),
            expense(3, 5000.0, d1 + 4 * day),
            Transaction(id = 4, amount = 999.0, type = TransactionType.INCOME, categoryId = 0, sourceAccountId = 1, date = d1)
        )
        val month = SpendingCalendar.build(txs, 2026, Calendar.OCTOBER, firstDayOfWeek = Calendar.MONDAY)
        assertEquals(31, month.daysInMonth)
        assertEquals(3, month.leadingBlanks) // 1 Oct 2026 is a Thursday
        assertEquals(150.0, month.days.getValue(1).total, 0.001)
        assertEquals(2, month.days.getValue(1).count)
        assertEquals(5150.0, month.total, 0.001)
        assertEquals(0, SpendingCalendar.level(0.0, month.thresholds))
        assertTrue(SpendingCalendar.level(5000.0, month.thresholds) > SpendingCalendar.level(150.0, month.thresholds))
    }
}
