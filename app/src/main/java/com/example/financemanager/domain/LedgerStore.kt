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

/**
 * A store that can group several writes into one all-or-nothing unit.
 *
 * Deliberately *not* a member of [LedgerStore]: `FinanceDao` is a Room interface, and a method
 * taking a suspending lambda is not something Room can be asked to implement. The production
 * wrapper (`data/TransactionalFinanceDao.kt`) adds it around the generated DAO instead, so the
 * ledger gets real transactions on a device and the in-memory test fake needs no ceremony.
 */
interface TransactionalStore {
    suspend fun <R> transact(block: suspend () -> R): R
}

/**
 * Runs [block] as one all-or-nothing database transaction when the store supports it.
 *
 * Writing a transaction and moving the balances it affects are two statements describing one
 * event. Run apart, a crash — or a killed process — between them leaves a transaction whose money
 * never moved, or a balance with nothing behind it, and nothing in the app can tell that apart
 * from real drift afterwards. Every multi-step ledger operation goes through here so the pair
 * either both land or neither does.
 *
 * A store with no transaction support simply runs the block, which is what the unit tests want:
 * they are testing the arithmetic, not SQLite.
 */
suspend fun <R> LedgerStore.atomically(block: suspend () -> R): R =
    if (this is TransactionalStore) transact(block) else block()
