package com.example.financemanager.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.data.*
import com.example.financemanager.domain.RecurringScheduler
import com.example.financemanager.services.widgets.daysFromToday
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.data.TransactionType

/**
 * The daily pass that executes recurring templates.
 *
 * The execution itself lives in [RecurringScheduler], which the app also runs on every launch.
 * This worker used to carry its own copy of that logic, and the copies disagreed — see the class
 * comment on the scheduler. All this one does now is provide the trigger and turn what came back
 * into notifications, which is the only part that needs Android.
 */
class RecurringTransactionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d("RecurringWorker", "Starting auto-execution check...")
            // This worker can be what starts the process, so the currency symbol and the privacy
            // flag both have to be loaded before anything formats an amount for a notification.
            FinancePreferences.init(applicationContext)
            val dao = FinanceDatabase.getDatabase(applicationContext).dao()

            // Bills the user has to pay by hand, landing within the next two days. A few get one
            // notification each, with "Mark paid" on it; a pile of them gets one summary, because
            // six notifications at 9 a.m. is noise.
            val now = System.currentTimeMillis()
            val templates = dao.getRecurringTransactionsList()
            val upcoming = templates.filter {
                !it.isPaused && !it.isAutoLog && it.type == TransactionType.EXPENSE &&
                    it.nextExecutionDate in now..(now + 2 * 24 * 60 * 60 * 1000L)
            }.sortedBy { it.nextExecutionDate }
            if (upcoming.size in 1..3) {
                upcoming.forEach { bill ->
                    NotificationHelper.notifyBillDue(
                        applicationContext,
                        bill.id,
                        bill.note.ifBlank { "Recurring payment" },
                        bill.amount,
                        dueText(bill.nextExecutionDate, now),
                        dueDate = bill.nextExecutionDate
                    )
                }
            } else if (upcoming.isNotEmpty()) {
                NotificationHelper.notifyUpcomingBills(
                    applicationContext,
                    upcoming.size,
                    upcoming.sumOf { it.amount },
                    upcoming.first().note.ifBlank { "Recurring payment" }
                )
            }

            // Anything already due: auto-logging templates are written by the scheduler, and what
            // comes back is the set that needs a human.
            val reminders = RecurringScheduler.checkAndLogRecurringTransactions(dao, now)
            val before = templates.associateBy { it.id }
            reminders.forEach { reminder ->
                // The scheduler has already moved this template past the occurrence that fell
                // due, so which one that was is worked out from the date it had going in. Mark
                // paid needs it to tell "not paid yet" from "already paid" — see BillActionReceiver.
                val dueDate = before[reminder.recurringId]?.let { latestDueOccurrence(it, now) } ?: 0L
                NotificationHelper.notifyReminder(
                    applicationContext,
                    reminder.recurringId,
                    "Reminder: ${reminder.note}",
                    "A payment of ${moneyString(reminder.amount)} is due today!",
                    recurringId = reminder.recurringId,
                    dueDate = dueDate
                )
            }

            // Auto-logged occurrences change today's total.
            FinanceWidgetProvider.refresh(applicationContext)

            Log.d("RecurringWorker", "Recurring pass done; ${reminders.size} reminder(s) raised.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Error executing recurring transactions", e)
            return Result.retry()
        }
    }

    /** The last occurrence of [rec] at or before [now], walking on from its pre-pass date. */
    private fun latestDueOccurrence(rec: RecurringTransaction, now: Long): Long {
        var due = rec.nextExecutionDate
        // Bounded like the scheduler's own catch-up, so a corrupt date can't spin here either.
        repeat(RecurringScheduler.MAX_CATCH_UP) {
            val next = RecurringScheduler.getNextIntervalDate(due, rec.interval)
            if (next > now || next <= due) return due
            due = next
        }
        return due
    }

    private fun dueText(due: Long, now: Long): String {
        // Calendar days, not YEAR * 365 + DAY_OF_YEAR: that counted 31 Dec of a leap year to
        // 1 Jan as zero days apart, so a bill due tomorrow said "today".
        val days = daysFromToday(due, now)
        return when (days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $days days"
        }
    }
}
