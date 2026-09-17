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

    /**
     * Marks a row as changed now, for the cloud merge to compare against the server's copy. The
     * ledger stamps for the same reason the repository does: it writes through [LedgerStore]
     * straight to the DAO, which deliberately never stamps anything itself.
     */
    private fun Account.touched() = copy(updatedAt = System.currentTimeMillis())

    private fun Transaction.touched() = copy(updatedAt = System.currentTimeMillis())

    /** How [transaction] moves the account that funded it. */
    fun sourceDelta(transaction: Transaction): Double = when (transaction.type) {
        TransactionType.INCOME -> transaction.amount
        TransactionType.EXPENSE, TransactionType.TRANSFER -> -transaction.amount
    }

    /** Writes the transaction and applies it to the balances. Returns the new row id. */
    suspend fun post(dao: LedgerStore, transaction: Transaction): Long {
        val id = dao.insertTransaction(transaction.touched())
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
        dao.updateTransaction(new.touched())
        applyBalances(dao, new, +1)
    }

    /**
     * Moves money without recording a categorised expense or income — the portion of a bill a
     * friend owes back, the cash leg of an investment trade, IOU principal. Logged as a transfer
     * so it lands in the ledger without inflating either total.
     *
     * [delta] is how the account's balance should move: negative for money leaving, positive for
     * money arriving. The row itself always carries a positive amount, with the account on the
     * source side for an outflow and on the destination side for an inflow, so both ledger sums
     * ([LedgerStore.sumSourceEffect] and [LedgerStore.sumIncomingTransfers]) keep agreeing with
     * the balance it produced.
     *
     * The previous signature took "cash that left the account" as a positive number, but three of
     * its five callers passed a signed delta instead — so buying an investment credited the
     * funding account and selling debited it. Taking the delta explicitly is what those callers
     * were already reaching for, and it is the only reading that can express both directions.
     */
    suspend fun postBalanceAdjustment(
        dao: LedgerStore,
        accountId: Long,
        delta: Double,
        note: String,
        date: Long
    ): Long {
        // A zero delta has no side to sit on: it would land as a zero-amount transfer belonging to
        // neither account, cluttering the log with a row that moves nothing. 0 is already how a
        // caller records "no transaction was created".
        if (kotlin.math.abs(delta) <= EPSILON) return 0L
        return post(
            dao,
            Transaction(
                // Never a negative amount: a transfer's direction is carried by which side the
                // account sits on, and a negative amount would render as one in the log.
                amount = kotlin.math.abs(delta),
                type = TransactionType.TRANSFER,
                categoryId = 0L,
                sourceAccountId = if (delta < 0) accountId else 0L,
                destinationAccountId = if (delta > 0) accountId else null,
                note = note,
                date = date
            )
        )
    }

    private suspend fun applyBalances(dao: LedgerStore, transaction: Transaction, sign: Int) {
        // A missing account means the transaction is already orphaned; applying its delta to some
        // other account (as the old delete path did, falling back to "the first account") is worse
        // than leaving it alone.
        dao.getAccountById(transaction.sourceAccountId)?.let { account ->
            dao.updateAccount(account.copy(balance = account.balance + sign * sourceDelta(transaction)).touched())
        }

        if (transaction.type == TransactionType.TRANSFER) {
            transaction.destinationAccountId?.let { destinationId ->
                dao.getAccountById(destinationId)?.let { destination ->
                    dao.updateAccount(
                        destination.copy(balance = destination.balance + sign * transaction.amount).touched()
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
     *
     * [onlyAccountIds] narrows it to a subset, which is what a sync needs: an account whose row
     * the server did *not* overwrite still has to absorb the transactions the same sync pulled in,
     * and it is the only side of the ledger that can be trusted to say what its balance should be.
     */
    suspend fun reconcile(dao: LedgerStore, onlyAccountIds: Set<Long>? = null): List<BalanceDrift> {
        val drifts = mutableListOf<BalanceDrift>()
        dao.getAccountsList().filter { onlyAccountIds == null || it.id in onlyAccountIds }.forEach { account ->
            val expected = ledgerBalanceOf(dao, account)
            if (kotlin.math.abs(expected - account.balance) > EPSILON) {
                drifts += BalanceDrift(account, account.balance, expected)
                dao.updateAccount(account.copy(balance = expected).touched())
            }
        }
        return drifts
    }

    /**
     * The mirror of [reconcile]: trusts the balances and rewrites the opening balances to match.
     * Used after a restore or a manual balance correction, where the new balance is the fact and
     * the opening balance is what has to give.
     *
     * [onlyAccountIds] narrows it to a subset. A sync passes the accounts whose rows came down
     * from the server, because those balances are authoritative; applying it to an account the
     * server did not send would bend its anchor to hide real drift instead of repairing it.
     */
    suspend fun rebaseOpeningBalances(dao: LedgerStore, onlyAccountIds: Set<Long>? = null) {
        dao.getAccountsList().filter { onlyAccountIds == null || it.id in onlyAccountIds }.forEach { account ->
            val fromTransactions = dao.sumSourceEffect(account.id) + dao.sumIncomingTransfers(account.id)
            val rebased = account.balance - fromTransactions
            // Only write when the anchor actually moves. This runs after every pull, and writing
            // unconditionally would restamp every account on every sync — making each one look
            // freshly edited, so a genuine edit from the server could never win the comparison.
            if (kotlin.math.abs(rebased - account.openingBalance) > EPSILON) {
                dao.updateAccount(account.copy(openingBalance = rebased).touched())
            }
        }
    }
}
