package com.example.financemanager.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.financemanager.domain.TransactionalStore

/**
 * The generated DAO, plus the ability to run several writes as one database transaction.
 *
 * Everything else is forwarded untouched. The only method this class owns is [transact], which
 * Room cannot generate: a method taking a suspending lambda is not a query, an insert or an
 * update, and `@Transaction` only wraps calls the DAO itself makes.
 *
 * `FinanceDatabase.dao()` hands this out in place of `financeDao()`, so every caller — the
 * repository, the background workers, the widget — writes through it and the ledger's
 * write-then-adjust pairs become atomic without any of them knowing.
 */
internal class TransactionalFinanceDao(
    private val database: RoomDatabase,
    private val delegate: FinanceDao
) : FinanceDao by delegate, TransactionalStore {

    override suspend fun <R> transact(block: suspend () -> R): R = database.withTransaction(block)
}
