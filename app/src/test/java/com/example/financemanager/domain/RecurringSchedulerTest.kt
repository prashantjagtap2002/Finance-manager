package com.example.financemanager.domain

import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catch-up rules, which used to exist twice and disagree — see [RecurringScheduler].
 *
 * Every case here is one of the disagreements: whether a paused template runs, whether a
 * reminder-only one gets logged as if it were automatic, and how many of the occurrences a long
 * absence missed actually make it into the ledger.
 */
class RecurringSchedulerTest {

    private val day = 24L * 60 * 60 * 1000

    private fun template(
        id: Long = 1L,
        amount: Double = 100.0,
        nextExecutionDate: Long,
        interval: RecurringInterval = RecurringInterval.DAILY,
        isAutoLog: Boolean = true,
        isPaused: Boolean = false
    ) = RecurringTransaction(
        id = id,
        amount = amount,
        type = TransactionType.EXPENSE,
        categoryId = 3L,
        accountId = 1L,
        interval = interval,
        note = "Netflix",
        startDate = nextExecutionDate,
        nextExecutionDate = nextExecutionDate,
        isAutoLog = isAutoLog,
        isPaused = isPaused
    )

    @Test
    fun logsEveryMissedOccurrence_notJustOne() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 100L * day
        store.recurring[1L] = template(nextExecutionDate = now - 3 * day)

        RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        // Due at -3d, -2d, -1d and today: the worker's old copy logged one and skipped the rest.
        assertEquals(4, store.transactions.size)
        assertEquals(1000.0 - 4 * 100.0, store.balanceOf(1L), 0.001)
        assertTrue(
            "the template must end up scheduled in the future",
            store.recurring.getValue(1L).nextExecutionDate > now
        )
    }

    @Test
    fun pausedTemplateNeitherLogsNorMovesOn() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 100L * day
        val due = now - 3 * day
        store.recurring[1L] = template(nextExecutionDate = due, isPaused = true)

        val reminders = RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        assertEquals("a paused template logs nothing", 0, store.transactions.size)
        assertEquals(1000.0, store.balanceOf(1L), 0.001)
        assertTrue(reminders.isEmpty())
        assertEquals(
            "un-pausing must pick up where it left off, not dump the gap",
            due,
            store.recurring.getValue(1L).nextExecutionDate
        )
    }

    @Test
    fun reminderOnlyTemplateIsReportedAndNotLogged() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 100L * day
        store.recurring[1L] = template(nextExecutionDate = now - 2 * day, isAutoLog = false)

        val reminders = RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        assertEquals("nothing may be logged for a reminder-only template", 0, store.transactions.size)
        assertEquals(1000.0, store.balanceOf(1L), 0.001)
        assertEquals("one reminder, however many occurrences were caught up", 1, reminders.size)
        assertEquals(1L, reminders.first().recurringId)
        assertEquals(100.0, reminders.first().amount, 0.001)
        assertTrue(store.recurring.getValue(1L).nextExecutionDate > now)
    }

    @Test
    fun notYetDueTemplateIsLeftAlone() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 100L * day
        val future = now + 2 * day
        store.recurring[1L] = template(nextExecutionDate = future)

        val reminders = RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        assertEquals(0, store.transactions.size)
        assertTrue(reminders.isEmpty())
        assertEquals(future, store.recurring.getValue(1L).nextExecutionDate)
    }

    @Test
    fun rerunIsIdempotent_balanceIsNotChargedTwice() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 100L * day
        store.recurring[1L] = template(nextExecutionDate = now - 2 * day)

        RecurringScheduler.checkAndLogRecurringTransactions(store, now)
        val afterFirst = store.balanceOf(1L)
        val loggedFirst = store.transactions.size

        // The daily worker and an app launch race for the same due template.
        RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        assertEquals(loggedFirst, store.transactions.size)
        assertEquals(afterFirst, store.balanceOf(1L), 0.001)
    }

    @Test
    fun corruptDateIsCappedRatherThanManufacturingYearsOfHistory() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1000.0)
        val now = 20_000L * day // ~2024 against an epoch-zero template
        store.recurring[1L] = template(nextExecutionDate = 0L)

        RecurringScheduler.checkAndLogRecurringTransactions(store, now)

        assertEquals(
            "the catch-up must stop at the cap",
            RecurringScheduler.MAX_CATCH_UP,
            store.transactions.size
        )
        assertTrue(
            "the template still has to end up current",
            store.recurring.getValue(1L).nextExecutionDate > now
        )
    }
}
