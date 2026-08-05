package com.example.financemanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // Accounts
    @Query("SELECT * FROM accounts")
    fun getAccountsFlow(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAccountsList(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    // Categories (Envelopes)
    @Query("SELECT * FROM categories ORDER BY displayOrder ASC, id ASC")
    fun getCategoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Delete
    suspend fun deleteCategory(category: Category)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions")
    suspend fun getTransactionsList(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getTransactionsBetweenDatesFlow(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE note LIKE '%' || :query || '%' OR merchantName LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTransactionsFlow(query: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // Recurring Transactions
    @Query("SELECT * FROM recurring_transactions")
    fun getRecurringTransactionsFlow(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getRecurringTransactionsList(): List<RecurringTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long

    @Update
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction)

    @Delete
    suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction)

    @Query("SELECT * FROM transactions WHERE categoryId = :category AND strftime('%Y', date/1000, 'unixepoch') = :year AND strftime('%m', date/1000, 'unixepoch') = :month")
    fun getTransactionsByCategory(category: Long, year: String, month: String): Flow<List<Transaction>>

    // Savings Goals
    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC")
    fun getSavingsGoalsFlow(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getSavingsGoalsList(): List<SavingsGoal>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getSavingsGoalById(id: Long): SavingsGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoal): Long

    @Update
    suspend fun updateSavingsGoal(goal: SavingsGoal)

    @Delete
    suspend fun deleteSavingsGoal(goal: SavingsGoal)

    @Query("DELETE FROM savings_goals")
    suspend fun clearSavingsGoals()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM recurring_transactions")
    suspend fun clearRecurringTransactions()

    // Debts
    @Query("SELECT * FROM debts ORDER BY date DESC")
    fun getDebtsFlow(): Flow<List<Debt>>

    @Query("SELECT * FROM debts")
    suspend fun getDebtsList(): List<Debt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt): Long

    @Update
    suspend fun updateDebt(debt: Debt)

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Query("DELETE FROM debts")
    suspend fun clearDebts()

    @Query("DELETE FROM sms_transactions")
    suspend fun clearSmsTransactions()

    // SMS Transactions
    @Query("SELECT * FROM sms_transactions ORDER BY rawTimestamp DESC")
    fun getSmsTransactionsFlow(): Flow<List<SmsTransaction>>

    @Query("SELECT * FROM sms_transactions WHERE isApproved = 0 AND isIgnored = 0 ORDER BY rawTimestamp DESC")
    fun getPendingSmsTransactionsFlow(): Flow<List<SmsTransaction>>

    @Query("SELECT * FROM sms_transactions WHERE isIgnored = 1 ORDER BY rawTimestamp DESC")
    fun getIgnoredSmsTransactionsFlow(): Flow<List<SmsTransaction>>

    @Query("SELECT * FROM sms_transactions WHERE isApproved = 1 ORDER BY rawTimestamp DESC")
    fun getApprovedSmsTransactionsFlow(): Flow<List<SmsTransaction>>

    @Query("SELECT * FROM sms_transactions")
    suspend fun getSmsTransactionsList(): List<SmsTransaction>

    @Query("SELECT * FROM sms_transactions WHERE smsHash = :hash LIMIT 1")
    suspend fun getSmsTransactionByHash(hash: String): SmsTransaction?

    // Check for duplicate transactions based on key fields to prevent UNIQUE constraint errors
    @Query("SELECT * FROM transactions WHERE amount = :amount AND type = :type AND categoryId = :categoryId AND sourceAccountId = :sourceAccountId AND date = :date LIMIT 1")
    suspend fun findDuplicateTransaction(amount: Double, type: String, categoryId: Long, sourceAccountId: Long, date: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsTransaction(smsTransaction: SmsTransaction): Long

    @Update
    suspend fun updateSmsTransaction(smsTransaction: SmsTransaction)

    @Delete
    suspend fun deleteSmsTransaction(smsTransaction: SmsTransaction)
}
