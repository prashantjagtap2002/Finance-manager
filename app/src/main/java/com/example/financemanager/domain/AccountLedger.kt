package com.example.financemanager.domain

import com.example.financemanager.data.Account
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType

/** An account whose stored balance disagrees with what its transactions add up to. */
data class BalanceDrift(
    val account: Account,
    val storedBalance: Double,
    val ledgerBalance: Double
) {
    val difference: Double get() = ledgerBalance - storedBalance
}

/**
 * The only place account balances are allowed to move.
 *
 * Every path that creates, edits or removes a [Transaction] goes through [post], [amend] or
 * [revoke], which write the row and adjust the affected accounts together. Balance arithmetic
 * used to be duplicated across a dozen call sites, so any path that forgot a step left the
 * account permanently wrong with nothing to detect it.
 *
 * The invariant is `balance == openingBalance + net effect of transactions`, and [reconcile]
 * re-derives it to repair drift left by earlier versions.
 *
 * It works against [LedgerStore] rather than living on the repository, because the background
 * workers (recurring execution, the notification listener) hold the DAO directly — and because
 * a narrow port keeps the arithmetic testable without a database.
 */
object AccountLedger {

    /** Rounding slack — anything smaller is floating-point noise, not real drift. */
    private const val EPSILON = 0.005

    /** How [transaction] moves the account that funded it. */
    fun sourceDelta(transaction: Transaction): Double = when (transaction.type) {
        TransactionType.INCOME -> transaction.amount
        TransactionType.EXPENSE, TransactionType.TRANSFER -> -transaction.amount
    }

    /** Writes the transaction and applies it to the balances. Returns the new row id. */
    suspend fun post(dao: LedgerStore, transaction: Transaction): Long {
        val id = dao.insertTransaction(transaction)
        applyBalances(dao, transaction, +1)
        return id
    }

    /**
     * [post], but a no-op when an identical transaction already exists. Returns the existing id,
     * and critically does *not* touch balances in that case — the previous duplicate-safe insert
     * adjusted the balance either way, so a re-delivered SMS or a repeated sync double-charged.
     */
    suspend fun postIfNew(dao: LedgerStore, transaction: Transaction): Long {
        val existing = dao.findDuplicateTransaction(
            transaction.amount,
            transaction.type.name,
            transaction.categoryId,
            transaction.sourceAccountId,
            transaction.date
        )
        if (existing != null) return existing.id
        return post(dao, transaction)
    }

    /** Removes the transaction and backs its effect out of the balances. */
    suspend fun revoke(dao: LedgerStore, transaction: Transaction) {
        dao.deleteTransaction(transaction)
        applyBalances(dao, transaction, -1)
    }

    /**
     * Replaces [old] with [new], backing out the old effect before applying the new one. Handles
     * the amount, the type and the account all changing at once.
     */
    suspend fun amend(dao: LedgerStore, old: Transaction, new: Transaction) {
        applyBalances(dao, old, -1)
        dao.updateTransaction(new)
        applyBalances(dao, new, +1)
    }

    /**
     * Moves money without recording a categorised expense — used for the portion of a bill a
     * friend owes back, where the cash really did leave the account but isn't the user's spending.
     * Logged as a transfer so it lands in the ledger without inflating expense totals.
     */
    suspend fun postSideEffect(
        dao: LedgerStore,
        accountId: Long,
        amount: Double,
        note: String,
        date: Long
    ): Long = post(
        dao,
        Transaction(
            amount = amount,
            type = TransactionType.TRANSFER,
            categoryId = 0L,
            sourceAccountId = accountId,
            note = note,
            date = date
        )
    )

    private suspend fun applyBalances(dao: LedgerStore, transaction: Transaction, sign: Int) {
        // A missing account means the transaction is already orphaned; applying its delta to some
        // other account (as the old delete path did, falling back to "the first account") is worse
        // than leaving it alone.
        dao.getAccountById(transaction.sourceAccountId)?.let { account ->
            dao.updateAccount(account.copy(balance = account.balance + sign * sourceDelta(transaction)))
        }

        if (transaction.type == TransactionType.TRANSFER) {
            transaction.destinationAccountId?.let { destinationId ->
                dao.getAccountById(destinationId)?.let { destination ->
                    dao.updateAccount(
                        destination.copy(balance = destination.balance + sign * transaction.amount)
                    )
                }
            }
        }
    }

    /** What [account]'s balance should be, derived from its opening balance and its transactions. */
    suspend fun ledgerBalanceOf(dao: LedgerStore, account: Account): Double =
        account.openingBalance +
            dao.sumSourceEffect(account.id) +
            dao.sumIncomingTransfers(account.id)

    /**
     * Recomputes every balance from the ledger and writes back any that had drifted, returning
     * what was corrected. Trusts the transactions — use this to repair balances damaged by the
     * scattered arithmetic in earlier versions.
     */
    suspend fun reconcile(dao: LedgerStore): List<BalanceDrift> {
        val drifts = mutableListOf<BalanceDrift>()
        dao.getAccountsList().forEach { account ->
            val expected = ledgerBalanceOf(dao, account)
            if (kotlin.math.abs(expected - account.balance) > EPSILON) {
                drifts += BalanceDrift(account, account.balance, expected)
                dao.updateAccount(account.copy(balance = expected))
            }
        }
        return drifts
    }

    /**
     * The mirror of [reconcile]: trusts the balances and rewrites the opening balances to match.
     * Used after a restore or a manual balance correction, where the new balance is the fact and
     * the opening balance is what has to give.
     */
    suspend fun rebaseOpeningBalances(dao: LedgerStore) {
        dao.getAccountsList().forEach { account ->
            val fromTransactions = dao.sumSourceEffect(account.id) + dao.sumIncomingTransfers(account.id)
            dao.updateAccount(account.copy(openingBalance = account.balance - fromTransactions))
        }
    }
}
