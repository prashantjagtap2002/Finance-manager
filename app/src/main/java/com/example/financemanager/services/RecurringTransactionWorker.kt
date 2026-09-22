package com.example.financemanager.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.data.*
import com.example.financemanager.domain.RecurringScheduler
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

            // Bills the user has to pay by hand, landing within the next two days.
            val now = System.currentTimeMillis()
            val upcoming = dao.getRecurringTransactionsList().filter {
                !it.isPaused && !it.isAutoLog && it.type == TransactionType.EXPENSE &&
                    it.nextExecutionDate in now..(now + 2 * 24 * 60 * 60 * 1000L)
            }
            if (upcoming.isNotEmpty()) {
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
            reminders.forEach { reminder ->
                NotificationHelper.notifyReminder(
                    applicationContext,
                    reminder.recurringId,
                    "Reminder: ${reminder.note}",
                    "A payment of ${moneyString(reminder.amount)} is due today!"
                )
            }

            Log.d("RecurringWorker", "Recurring pass done; ${reminders.size} reminder(s) raised.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Error executing recurring transactions", e)
            return Result.retry()
        }
    }
}
