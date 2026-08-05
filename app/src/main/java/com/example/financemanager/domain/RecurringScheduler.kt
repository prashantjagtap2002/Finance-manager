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
                // 1. Check for duplicate to prevent UNIQUE constraint errors
                val existingTransaction = dao.findDuplicateTransaction(
                    amount = currentRec.amount,
                    type = currentRec.type.name,
                    categoryId = currentRec.categoryId,
                    sourceAccountId = currentRec.accountId,
                    date = nextExecution
                )

                // Only insert if no duplicate exists (idempotent operation)
                if (existingTransaction == null) {
                    // 2. Create a new transaction log
                    val transaction = Transaction(
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
                    dao.insertTransaction(transaction)
                }

                // 2. Adjust account balance
                val account = dao.getAccountById(currentRec.accountId)
                if (account != null) {
                    val newBalance = when (currentRec.type) {
                        TransactionType.EXPENSE -> account.balance - currentRec.amount
                        TransactionType.INCOME -> account.balance + currentRec.amount
                        TransactionType.TRANSFER -> account.balance // Transfers should have destination, handled separately
                    }
                    dao.updateAccount(account.copy(balance = newBalance))
                }

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
