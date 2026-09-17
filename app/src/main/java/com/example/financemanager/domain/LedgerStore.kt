package com.example.financemanager.domain

import com.example.financemanager.data.Account
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.Transaction

/**
 * The narrow slice of storage [AccountLedger] needs. `FinanceDao` implements it, so production
 * code passes the DAO straight through; tests supply an in-memory fake and exercise the balance
 * arithmetic off-device, which is where the drift bugs lived.
 */
interface LedgerStore {

    suspend fun getAccountById(id: Long): Account?

    suspend fun getAccountsList(): List<Account>

    suspend fun updateAccount(account: Account)

    suspend fun insertTransaction(transaction: Transaction): Long

    suspend fun updateTransaction(transaction: Transaction)

    suspend fun deleteTransaction(transaction: Transaction)

    suspend fun findDuplicateTransaction(
        amount: Double,
        type: String,
        categoryId: Long,
        sourceAccountId: Long,
        date: Long
    ): Transaction?

    suspend fun sumSourceEffect(accountId: Long): Double

    suspend fun sumIncomingTransfers(accountId: Long): Double
}

/**
 * What [RecurringScheduler] needs on top of [LedgerStore]: the templates, and the ability to move
 * one forward. Kept separate so [LedgerStore] stays the narrow thing [AccountLedger] asks for, and
 * so the catch-up rules can be tested against an in-memory store rather than a device.
 */
interface RecurringStore : LedgerStore {

    suspend fun getRecurringTransactionsList(): List<RecurringTransaction>

    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction)
}
