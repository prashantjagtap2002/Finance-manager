package com.example.financemanager.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financemanager.domain.AccountLedger
import com.example.financemanager.data.*
import com.example.financemanager.ui.components.moneyString
import kotlinx.coroutines.flow.first
import java.util.Calendar

class RecurringTransactionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d("RecurringWorker", "Starting auto-execution check...")
            val db = FinanceDatabase.getDatabase(applicationContext)
            val dao = db.financeDao()
            
            // Get all recurring transactions
            val recurringList = dao.getRecurringTransactionsList()
            val now = System.currentTimeMillis()

            val upcoming = recurringList.filter {
                !it.isPaused && !it.isAutoLog && it.nextExecutionDate in now..(now + 2 * 24 * 60 * 60 * 1000L)
            }
            if (upcoming.isNotEmpty()) {
                NotificationHelper.notifyUpcomingBills(
                    applicationContext,
                    upcoming.size,
                    upcoming.sumOf { it.amount },
                    upcoming.first().note.ifBlank { "Recurring payment" }
                )
            }
            
            var executedCount = 0

            for (rt in recurringList) {
                if (rt.isPaused) continue

                if (rt.nextExecutionDate <= now) {
                    if (rt.isAutoLog) {
                        // Guard against double-applying: FinanceViewModel also runs
                        // RecurringScheduler on every app open, which can race this worker for
                        // the same due recurring transaction. postIfNew is idempotent — an
                        // already-logged occurrence is neither re-inserted nor re-charged.
                        AccountLedger.postIfNew(
                            dao,
                            Transaction(
                                amount = rt.amount,
                                type = rt.type,
                                categoryId = rt.categoryId,
                                sourceAccountId = rt.accountId,
                                note = rt.note.ifEmpty { "Auto-logged recurring" },
                                date = rt.nextExecutionDate,
                                isRecurring = true,
                                recurringId = rt.id,
                                isAutoLogged = true
                            )
                        )
                    } else {
                        // Just send a reminder notification
                        com.example.financemanager.services.NotificationHelper.notifyReminder(
                            applicationContext,
                            rt.id,
                            "Reminder: ${rt.note.ifEmpty { "Recurring Payment" }}",
                            "A payment of ${moneyString(rt.amount, false)} is due today!"
                        )
                    }

                    // Update the next execution date
                    val cal = Calendar.getInstance().apply { timeInMillis = rt.nextExecutionDate }
                    when (rt.interval) {
                        RecurringInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        RecurringInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        RecurringInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
                        RecurringInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
                    }
                    
                    // Catch up if it was missed for a long time
                    while (cal.timeInMillis <= now) {
                        when (rt.interval) {
                            RecurringInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                            RecurringInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                            RecurringInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
                            RecurringInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
                        }
                    }

                    dao.updateRecurringTransaction(rt.copy(nextExecutionDate = cal.timeInMillis))
                    executedCount++
                }
            }

            Log.d("RecurringWorker", "Executed $executedCount recurring transactions.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Error executing recurring transactions", e)
            return Result.retry()
        }
    }
}
