package com.example.financemanager.domain

import com.example.financemanager.data.*
import java.util.Calendar

/** A template that came due but logs nothing by itself, so somebody has to be told about it. */
data class DueReminder(
    val recurringId: Long,
    val note: String,
    val amount: Double
)

/**
 * Executes recurring templates that have come due.
 *
 * This is the *only* implementation. There used to be a second one inside
 * [com.example.financemanager.services.RecurringTransactionWorker], and the two disagreed about
 * nearly everything that matters: this one logged paused and reminder-only templates as if they
 * were auto-logged, while the worker honoured both flags but logged a single occurrence and then
 * fast-forwarded the date past every other one it had missed. Which behaviour a user got came down
 * to whether the daily worker or an app launch happened to run first.
 *
 * The rules, now in one place: a paused template does nothing, an auto-logging one logs every
 * occurrence it missed, and a reminder-only one advances its date and comes back as a
 * [DueReminder] for the caller to surface however it surfaces things.
 */
object RecurringScheduler {

    /**
     * The most occurrences one template may log in a single pass.
     *
     * A row whose `nextExecutionDate` is corrupt — 0, say, which is 1970 — would otherwise have a
     * daily template catching up twenty thousand occurrences, each one a real transaction against
     * a real balance. Past the cap the date is fast-forwarded without logging, which loses history
     * that was never going to be right anyway instead of manufacturing it.
     */
    const val MAX_CATCH_UP = 366

    suspend fun checkAndLogRecurringTransactions(
        dao: RecurringStore,
        currentTime: Long = System.currentTimeMillis()
    ): List<DueReminder> {
        val reminders = mutableListOf<DueReminder>()

        for (rec in dao.getRecurringTransactionsList()) {
            // A paused template is paused: it neither logs nor moves its date forward, so
            // un-pausing it picks up where it left off rather than dumping the whole gap at once.
            if (rec.isPaused) continue

            var nextExecution = rec.nextExecutionDate
            var currentRec = rec
            var logged = 0
            var wasDue = false

            while (nextExecution <= currentTime) {
                wasDue = true

                if (currentRec.isAutoLog && logged < MAX_CATCH_UP) {
                    // Log the occurrence and move the balance together. postIfNew makes this
                    // idempotent: a re-run finds the existing row and leaves the balance alone. The
                    // balance step used to sit outside the duplicate check, so every re-run of an
                    // already-logged occurrence charged the account again.
                    //
                    // A recurring TRANSFER debits its source and stops there, because
                    // RecurringTransaction carries no destination account — it needs one before a
                    // recurring transfer can credit the other side.
                    AccountLedger.postIfNew(
                        dao,
                        Transaction(
                            amount = currentRec.amount,
                            type = currentRec.type,
                            categoryId = currentRec.categoryId,
                            sourceAccountId = currentRec.accountId,
                            note = currentRec.note.ifEmpty { "Recurring transaction" },
                            date = nextExecution,
                            isRecurring = true,
                            recurringId = currentRec.id,
                            isAutoLogged = true
                        )
                    )
                    logged++
                }

                val nextDate = getNextIntervalDate(nextExecution, currentRec.interval)
                // A date that refuses to advance would spin forever. Nothing in the interval set
                // can produce one, but the loop writes rows, so it does not take that on trust.
                if (nextDate <= nextExecution) break

                currentRec = currentRec.copy(nextExecutionDate = nextDate)
                dao.updateRecurringTransaction(currentRec)
                nextExecution = nextDate
            }

            // Reminder-only templates are the user's to act on; all this can do is say so, once,
            // however many occurrences were caught up.
            if (wasDue && !currentRec.isAutoLog) {
                reminders += DueReminder(
                    recurringId = currentRec.id,
                    note = currentRec.note.ifEmpty { "Recurring Payment" },
                    amount = currentRec.amount
                )
            }
        }

        return reminders
    }

    fun getNextIntervalDate(currentDateMillis: Long, interval: RecurringInterval): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentDateMillis
        }
        when (interval) {
            RecurringInterval.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            RecurringInterval.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringInterval.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RecurringInterval.YEARLY -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
