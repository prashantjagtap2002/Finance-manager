package com.example.financemanager.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.Transaction
import com.example.financemanager.domain.FinanceRepository
import com.example.financemanager.domain.RecurringScheduler
import com.example.financemanager.domain.atomically
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections

/**
 * "Mark paid" on a bill reminder: logs the payment and, for a bill that hasn't come due yet, moves
 * its schedule on one interval so tomorrow's reminder doesn't ask about money already paid.
 *
 * Every notification says which occurrence it is about ([EXTRA_DUE_DATE]), and what a tap does is
 * decided against the schedule as it stands *at tap time* — see [BillPayment.decide]. It used to
 * trust the notification: a "due tomorrow" left in the shade until after the due date paid the
 * bill and then pushed the schedule a second interval on, silently skipping next month; tapping
 * that one and the "due today" one, or double-tapping either, logged the money twice.
 */
class BillActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_PAID = "com.example.financemanager.MARK_BILL_PAID"
        const val EXTRA_RECURRING_ID = "recurring_id"
        /** The `nextExecutionDate` of the occurrence the notification was raised for. */
        const val EXTRA_DUE_DATE = "due_date"
        /** Only read for notifications posted before [EXTRA_DUE_DATE] existed. */
        const val EXTRA_ADVANCE = "advance_schedule"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Bills with a tap being handled right now. The database transaction already makes two
         * taps serialise; this just stops the second from even queueing behind the first.
         */
        private val inFlight: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_PAID) return
        val recurringId = intent.getLongExtra(EXTRA_RECURRING_ID, -1L)
        val dueDateExtra = intent.getLongExtra(EXTRA_DUE_DATE, 0L)
        val advance = intent.getBooleanExtra(EXTRA_ADVANCE, true)
        if (recurringId < 0) return
        if (!inFlight.add(recurringId)) return

        FinancePreferences.init(context)
        val pending = goAsync()
        scope.launch {
            try {
                val dao = FinanceDatabase.getDatabase(context).dao()
                val repository = FinanceRepository(dao) { FinancePreferences.currencyCodeFlow.value }
                val now = System.currentTimeMillis()

                // Decide and write in one transaction, so the state the decision read is the
                // state the write lands on.
                val action = dao.atomically {
                    val rec = dao.getRecurringTransactionsList().firstOrNull { it.id == recurringId }
                        ?: return@atomically BillPayment.Action.NOTHING
                    val dueDate = when {
                        dueDateExtra > 0L -> dueDateExtra
                        // Posted by an older build: "due soon" ones were about the scheduled date,
                        // "due today" ones about the occurrence the scheduler had just moved past.
                        advance -> rec.nextExecutionDate
                        else -> BillPayment.previousIntervalDate(rec.nextExecutionDate, rec.interval)
                    }
                    val nextAfterDue = RecurringScheduler.getNextIntervalDate(dueDate, rec.interval)
                    val (windowStart, windowEnd) = BillPayment.paymentWindow(dueDate, nextAfterDue)
                    val alreadyPaid = dao.getTransactionsBetweenDates(windowStart, windowEnd).any {
                        it.recurringId == rec.id && !it.isAutoLogged && it.date < windowEnd
                    }

                    val decided = BillPayment.decide(rec.nextExecutionDate, dueDate, alreadyPaid)
                    if (decided != BillPayment.Action.NOTHING) {
                        repository.insertTransaction(
                            Transaction(
                                amount = rec.amount,
                                type = rec.type,
                                categoryId = rec.categoryId,
                                sourceAccountId = rec.accountId,
                                note = rec.note.ifBlank { "Bill payment" },
                                date = now,
                                isRecurring = true,
                                recurringId = rec.id
                            )
                        )
                    }
                    if (decided == BillPayment.Action.PAY_AND_ADVANCE) {
                        repository.updateRecurringTransaction(rec.copy(nextExecutionDate = nextAfterDue))
                    }
                    decided
                }

                // Paid or not, every notification for this bill is now stale.
                NotificationHelper.cancelBillNotifications(context, recurringId)
                if (action != BillPayment.Action.NOTHING) {
                    FinanceWidgetProvider.refresh(context)
                    SyncWorker.enqueueNow(context.applicationContext)
                } else {
                    Log.i("BillActionReceiver", "Mark paid for bill $recurringId was already handled")
                }
            } finally {
                inFlight.remove(recurringId)
                pending.finish()
            }
        }
    }
}

/** The rules behind a "Mark paid" tap, kept free of Android so they can be tested directly. */
internal object BillPayment {

    enum class Action {
        /** The occurrence is still the scheduled one: log it and move the schedule on. */
        PAY_AND_ADVANCE,
        /** The scheduler already moved past the occurrence, unpaid: log it, leave the date alone. */
        PAY_ONLY,
        /** Already paid, or the schedule no longer matches the notification at all. */
        NOTHING
    }

    /** Bill notifications go up to two days before the due date; a day of slack on top. */
    private const val EARLY_PAYMENT_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * [scheduledNext] is the template's `nextExecutionDate` at tap time, [dueDate] the occurrence
     * the notification was about.
     *
     * - Equal: nothing has handled this occurrence yet, so this tap pays it and advances.
     * - Scheduled later: either the scheduler moved past it when it fell due (unpaid) or an
     *   earlier tap paid and advanced it. [alreadyPaid] tells the two apart.
     * - Scheduled earlier: the user moved the schedule back since; the notification describes a
     *   bill that no longer exists, and paying it would be a guess.
     */
    fun decide(scheduledNext: Long, dueDate: Long, alreadyPaid: Boolean): Action = when {
        alreadyPaid -> Action.NOTHING
        scheduledNext == dueDate -> Action.PAY_AND_ADVANCE
        scheduledNext > dueDate -> Action.PAY_ONLY
        else -> Action.NOTHING
    }

    /**
     * When a payment for the occurrence on [dueDate] could have been logged: from a little before
     * it (paid from the "due soon" notification) up to the next occurrence. The early part is
     * capped at half an interval, so for a daily bill it can't reach back over yesterday's payment.
     */
    fun paymentWindow(dueDate: Long, nextAfterDue: Long): Pair<Long, Long> {
        val lead = minOf(EARLY_PAYMENT_MS, (nextAfterDue - dueDate) / 2)
        return (dueDate - lead) to nextAfterDue
    }

    /** One [interval] before [date]; the inverse of [RecurringScheduler.getNextIntervalDate]. */
    fun previousIntervalDate(date: Long, interval: RecurringInterval): Long =
        Calendar.getInstance().apply {
            timeInMillis = date
            when (interval) {
                RecurringInterval.DAILY -> add(Calendar.DAY_OF_YEAR, -1)
                RecurringInterval.WEEKLY -> add(Calendar.WEEK_OF_YEAR, -1)
                RecurringInterval.MONTHLY -> add(Calendar.MONTH, -1)
                RecurringInterval.YEARLY -> add(Calendar.YEAR, -1)
            }
        }.timeInMillis
}
