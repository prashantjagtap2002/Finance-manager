package com.example.financemanager.services

import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.domain.RecurringScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BillPaymentTest {

    private val day = 24L * 60 * 60 * 1000

    private fun date(year: Int, month: Int, dayOfMonth: Int): Long = Calendar.getInstance().apply {
        set(year, month, dayOfMonth, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val nov10 = date(2026, Calendar.NOVEMBER, 10)
    private val dec10 = RecurringScheduler.getNextIntervalDate(nov10, RecurringInterval.MONTHLY)

    @Test
    fun `a bill nobody has handled is paid and moved on`() {
        assertEquals(BillPayment.Action.PAY_AND_ADVANCE, BillPayment.decide(nov10, nov10, alreadyPaid = false))
    }

    @Test
    fun `a second tap after paying does nothing`() {
        // The first tap moved the schedule to December and logged a payment in November's window.
        assertEquals(BillPayment.Action.NOTHING, BillPayment.decide(dec10, nov10, alreadyPaid = true))
    }

    @Test
    fun `a stale due-soon notification after the due date pays without skipping a month`() {
        // The scheduler moved the date to December when November fell due; nothing was paid yet.
        assertEquals(BillPayment.Action.PAY_ONLY, BillPayment.decide(dec10, nov10, alreadyPaid = false))
    }

    @Test
    fun `a schedule moved back since the notification is left alone`() {
        val oct10 = date(2026, Calendar.OCTOBER, 10)
        assertEquals(BillPayment.Action.NOTHING, BillPayment.decide(oct10, nov10, alreadyPaid = false))
    }

    @Test
    fun `the payment window takes in an early payment and stops at the next occurrence`() {
        val (start, end) = BillPayment.paymentWindow(nov10, dec10)
        assertTrue("paid from the due-tomorrow notification", nov10 - day in start until end)
        assertEquals(dec10, end)
    }

    @Test
    fun `a daily bill's window can't reach back over yesterday's payment`() {
        val tomorrow = RecurringScheduler.getNextIntervalDate(nov10, RecurringInterval.DAILY)
        val (start, _) = BillPayment.paymentWindow(nov10, tomorrow)
        val yesterdaysPaymentOnTime = nov10 - day
        assertTrue(yesterdaysPaymentOnTime < start)
    }

    @Test
    fun `previous interval undoes next interval`() {
        RecurringInterval.values().forEach { interval ->
            val next = RecurringScheduler.getNextIntervalDate(nov10, interval)
            assertEquals(interval.name, nov10, BillPayment.previousIntervalDate(next, interval))
        }
    }
}
