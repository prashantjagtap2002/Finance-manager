package com.example.financemanager.domain

import com.example.financemanager.data.*
import java.util.Calendar

object RecurringScheduler {

    suspend fun checkAndLogRecurringTransactions(dao: FinanceDao, currentTime: Long = System.currentTimeMillis()) {
        val recurringList = dao.getRecurringTransactionsList()
        for (rec in recurringList) {
            var nextExecution = rec.nextExecutionDate
            var currentRec = rec
            
            while (nextExecution <= currentTime) {
                // 1/2. Log the occurrence and move the balance together. postIfNew makes this
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

                // 3. Compute next execution date
                val nextDate = getNextIntervalDate(nextExecution, currentRec.interval)
                currentRec = currentRec.copy(nextExecutionDate = nextDate)
                dao.updateRecurringTransaction(currentRec)
                
                nextExecution = nextDate
            }
        }
    }

    private fun getNextIntervalDate(currentDateMillis: Long, interval: RecurringInterval): Long {
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
