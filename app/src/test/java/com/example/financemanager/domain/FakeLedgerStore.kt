package com.example.financemanager.domain

import com.example.financemanager.data.Account
import com.example.financemanager.data.AccountType
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType

/**
 * In-memory [RecurringStore] for the balance and recurring tests. The sum queries mirror the SQL
 * in `FinanceDao` so the reconciliation tests exercise the same definition of "what the
 * transactions add up to".
 */
class FakeLedgerStore : RecurringStore, TransactionalStore {

    val accounts = mutableMapOf<Long, Account>()
    val recurring = mutableMapOf<Long, RecurringTransaction>()
    val transactions = mutableListOf<Transaction>()
    private var nextTransactionId = 1L

    /**
     * How deep inside [transact] the store currently is, and how deep it has ever been.
     *
     * A real database gives the ledger all-or-nothing writes; here they stand in for it, so a test
     * can assert that a write and the balance move it implies happened inside one unit rather than
     * as two statements a crash could land between.
     */
    var transactionDepth = 0
        private set
    var deepestTransaction = 0
        private set

    override suspend fun <R> transact(block: suspend () -> R): R {
        transactionDepth++
        deepestTransaction = maxOf(deepestTransaction, transactionDepth)
        try {
            return block()
        } finally {
            transactionDepth--
        }
    }

    /** Whether every write since the store was created happened inside a transaction. */
    val writesOutsideTransaction: Int get() = looseWrites
    private var looseWrites = 0

    private fun recordWrite() {
        if (transactionDepth == 0) looseWrites++
    }

    fun addAccount(id: Long, balance: Double, openingBalance: Double = balance): Account {
        val account = Account(
            id = id,
            name = "Account $id",
            type = AccountType.BANK,
            balance = balance,
            openingBalance = openingBalance
        )
        accounts[id] = account
        return account
    }

    fun balanceOf(id: Long): Double = accounts.getValue(id).balance

    override suspend fun getAccountById(id: Long): Account? = accounts[id]

    override suspend fun getAccountsList(): List<Account> = accounts.values.toList()

    override suspend fun updateAccount(account: Account) {
        recordWrite()
        accounts[account.id] = account
    }

    override suspend fun insertTransaction(transaction: Transaction): Long {
        recordWrite()
        val id = if (transaction.id != 0L) transaction.id else nextTransactionId++
        transactions.add(transaction.copy(id = id))
        return id
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        recordWrite()
        val index = transactions.indexOfFirst { it.id == transaction.id }
        if (index >= 0) transactions[index] = transaction else transactions.add(transaction)
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        recordWrite()
        transactions.removeAll { it.id == transaction.id }
    }

    override suspend fun findDuplicateTransaction(
        amount: Double,
        type: String,
        categoryId: Long,
        sourceAccountId: Long,
        date: Long
    ): Transaction? = transactions.firstOrNull {
        it.amount == amount &&
            it.type.name == type &&
            it.categoryId == categoryId &&
            it.sourceAccountId == sourceAccountId &&
            it.date == date
    }

    override suspend fun sumSourceEffect(accountId: Long): Double =
        transactions.filter { it.sourceAccountId == accountId }
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

    override suspend fun sumIncomingTransfers(accountId: Long): Double =
        transactions.filter { it.destinationAccountId == accountId && it.type == TransactionType.TRANSFER }
            .sumOf { it.amount }

    override suspend fun getRecurringTransactionsList(): List<RecurringTransaction> =
        recurring.values.toList()

    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurring[recurringTransaction.id] = recurringTransaction
    }
}
