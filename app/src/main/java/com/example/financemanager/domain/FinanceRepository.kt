package com.example.financemanager.domain

import com.example.financemanager.data.*
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val dao: FinanceDao) {

    // Accounts
    val accounts: Flow<List<Account>> = dao.getAccountsFlow()
    
    suspend fun getAccountById(id: Long): Account? = dao.getAccountById(id)
    
    suspend fun insertAccount(account: Account): Long = dao.insertAccount(account)
    
    suspend fun updateAccount(account: Account) = dao.updateAccount(account)
    
    suspend fun deleteAccount(account: Account) = dao.deleteAccount(account)

    // Categories (Envelopes)
    val categories: Flow<List<Category>> = dao.getCategoriesFlow()
    
    suspend fun getCategoryById(id: Long): Category? = dao.getCategoryById(id)
    
    suspend fun insertCategory(category: Category): Long = dao.insertCategory(category)
    
    suspend fun updateCategory(category: Category) = dao.updateCategory(category)
    
    suspend fun updateCategories(categories: List<Category>) = dao.updateCategories(categories)
    
    suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)

    // Transactions
    val transactions: Flow<List<Transaction>> = dao.getTransactionsFlow()
    
    fun getTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> {
        return dao.getTransactionsBetweenDatesFlow(start, end)
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return dao.searchTransactionsFlow(query)
    }
    
    suspend fun getTransactionById(id: Long): Transaction? = dao.getTransactionById(id)
    
    suspend fun insertTransaction(transaction: Transaction): Long = dao.insertTransaction(transaction)

    /**
     * Checks if a transaction with the same key fields already exists to prevent duplicates.
     * Returns the existing transaction if found, null otherwise.
     */
    suspend fun findDuplicateTransaction(amount: Double, type: String, categoryId: Long, sourceAccountId: Long, date: Long): Transaction? {
        return dao.findDuplicateTransaction(amount, type, categoryId, sourceAccountId, date)
    }

    /**
     * Inserts a transaction only if no duplicate exists. Returns the ID of the inserted
     * transaction or the existing transaction if a duplicate was found.
     */
    suspend fun insertTransactionIfNotExists(transaction: Transaction): Long {
        val existing = findDuplicateTransaction(
            transaction.amount,
            transaction.type.name,
            transaction.categoryId,
            transaction.sourceAccountId,
            transaction.date
        )
        return if (existing != null) {
            existing.id
        } else {
            dao.insertTransaction(transaction)
        }
    }
    
    suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) = dao.deleteTransaction(transaction)

    // Recurring Transactions
    val recurringTransactions: Flow<List<RecurringTransaction>> = dao.getRecurringTransactionsFlow()
    
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long {
        return dao.insertRecurringTransaction(recurringTransaction)
    }
    
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        dao.updateRecurringTransaction(recurringTransaction)
    }
    
    suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction) {
        dao.deleteRecurringTransaction(recurringTransaction)
    }
    
    // Check and log recurring bills
    suspend fun syncRecurringTransactions() {
        RecurringScheduler.checkAndLogRecurringTransactions(dao)
    }

    fun getTransactionsByCategory(categoryId: Long, year: String, month: String): Flow<List<Transaction>> {
        return dao.getTransactionsByCategory(categoryId, year, month)
    }

    // Savings Goals
    val savingsGoals: Flow<List<SavingsGoal>> = dao.getSavingsGoalsFlow()

    suspend fun getSavingsGoalById(id: Long): SavingsGoal? = dao.getSavingsGoalById(id)

    suspend fun insertSavingsGoal(goal: SavingsGoal): Long = dao.insertSavingsGoal(goal)

    suspend fun updateSavingsGoal(goal: SavingsGoal) = dao.updateSavingsGoal(goal)

    suspend fun deleteSavingsGoal(goal: SavingsGoal) = dao.deleteSavingsGoal(goal)

    suspend fun clearAllTables() {
        dao.clearAccounts()
        dao.clearCategories()
        dao.clearTransactions()
        dao.clearRecurringTransactions()
        dao.clearSavingsGoals()
        dao.clearDebts()
        dao.clearSmsTransactions()
    }

    // Debts
    val debts: Flow<List<Debt>> = dao.getDebtsFlow()

    suspend fun insertDebt(debt: Debt): Long = dao.insertDebt(debt)

    suspend fun updateDebt(debt: Debt) = dao.updateDebt(debt)

    suspend fun deleteDebt(debt: Debt) = dao.deleteDebt(debt)

    // SMS Transactions
    val smsTransactions: Flow<List<SmsTransaction>> = dao.getSmsTransactionsFlow()
    val pendingSmsTransactions: Flow<List<SmsTransaction>> = dao.getPendingSmsTransactionsFlow()
    val ignoredSmsTransactions: Flow<List<SmsTransaction>> = dao.getIgnoredSmsTransactionsFlow()
    val approvedSmsTransactions: Flow<List<SmsTransaction>> = dao.getApprovedSmsTransactionsFlow()

    suspend fun getSmsTransactionsList(): List<SmsTransaction> = dao.getSmsTransactionsList()

    suspend fun getSmsTransactionByHash(hash: String): SmsTransaction? = dao.getSmsTransactionByHash(hash)

    suspend fun insertSmsTransaction(smsTransaction: SmsTransaction): Long = dao.insertSmsTransaction(smsTransaction)

    suspend fun updateSmsTransaction(smsTransaction: SmsTransaction) = dao.updateSmsTransaction(smsTransaction)

    suspend fun deleteSmsTransaction(smsTransaction: SmsTransaction) = dao.deleteSmsTransaction(smsTransaction)
}
