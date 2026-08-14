package com.example.financemanager.domain

import com.example.financemanager.data.Account
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
