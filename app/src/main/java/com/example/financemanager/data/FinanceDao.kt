package com.example.financemanager.data

import androidx.room.*
import com.example.financemanager.domain.LedgerStore
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao : LedgerStore {

    // Accounts
    @Query("SELECT * FROM accounts")
    fun getAccountsFlow(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    override suspend fun getAccountsList(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    override suspend fun getAccountById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    override suspend fun updateAccount(account: Account)

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

    /** Earliest transaction date on record, or null when the ledger is empty. */
    @Query("SELECT MIN(date) FROM transactions")
    suspend fun getEarliestTransactionDate(): Long?

    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun getTransactionsBetweenDatesFlow(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE note LIKE '%' || :query || '%' OR merchantName LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTransactionsFlow(query: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    override suspend fun updateTransaction(transaction: Transaction)

    @Delete
    override suspend fun deleteTransaction(transaction: Transaction)

    /**
     * Net effect on the account that funded these transactions: income adds, expenses and
     * outgoing transfers subtract. Summed in SQL so reconciliation doesn't have to load the
     * whole table.
     */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) " +
            "FROM transactions WHERE sourceAccountId = :accountId"
    )
    override suspend fun sumSourceEffect(accountId: Long): Double

    /** Money transferred *into* this account, which the source-side sum above doesn't see. */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE destinationAccountId = :accountId AND type = 'TRANSFER'"
    )
    override suspend fun sumIncomingTransfers(accountId: Long): Double

    // --- Re-pointing rows before a category or account is deleted ---
    // Nothing here relies on foreign keys: none of the tables declare any, so a plain delete
    // leaves rows pointing at an id that no longer exists.

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsInCategory(categoryId: Long): Int

    @Query("UPDATE transactions SET categoryId = :targetId WHERE categoryId = :categoryId")
    suspend fun reassignTransactionCategory(categoryId: Long, targetId: Long)

    @Query("UPDATE recurring_transactions SET categoryId = :targetId WHERE categoryId = :categoryId")
    suspend fun reassignRecurringCategory(categoryId: Long, targetId: Long)

    @Query("UPDATE merchant_rules SET categoryId = :targetId WHERE categoryId = :categoryId")
    suspend fun reassignMerchantRuleCategory(categoryId: Long, targetId: Long)

    @Query("DELETE FROM merchant_rules WHERE categoryId = :categoryId")
    suspend fun deleteMerchantRulesForCategory(categoryId: Long)

    @Query("SELECT * FROM transactions WHERE sourceAccountId = :accountId OR destinationAccountId = :accountId")
    suspend fun getTransactionsForAccount(accountId: Long): List<Transaction>

    @Query("UPDATE transactions SET sourceAccountId = :targetId WHERE sourceAccountId = :accountId")
    suspend fun reassignTransactionSourceAccount(accountId: Long, targetId: Long)

    @Query("UPDATE transactions SET destinationAccountId = :targetId WHERE destinationAccountId = :accountId")
    suspend fun reassignTransactionDestinationAccount(accountId: Long, targetId: Long)

    @Query("UPDATE recurring_transactions SET accountId = :targetId WHERE accountId = :accountId")
    suspend fun reassignRecurringAccount(accountId: Long, targetId: Long)

    @Query("UPDATE merchant_rules SET accountId = :targetId WHERE accountId = :accountId")
    suspend fun reassignMerchantRuleAccount(accountId: Long, targetId: Long)

    @Query("UPDATE sms_transactions SET approvedAccountId = :targetId WHERE approvedAccountId = :accountId")
    suspend fun reassignSmsAccount(accountId: Long, targetId: Long)

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
    override suspend fun findDuplicateTransaction(amount: Double, type: String, categoryId: Long, sourceAccountId: Long, date: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsTransaction(smsTransaction: SmsTransaction): Long

    @Update
    suspend fun updateSmsTransaction(smsTransaction: SmsTransaction)

    @Delete
    suspend fun deleteSmsTransaction(smsTransaction: SmsTransaction)

    // Merchant → category rules
    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC")
    fun getMerchantRulesFlow(): Flow<List<MerchantRule>>

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key LIMIT 1")
    suspend fun getMerchantRule(key: String): MerchantRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :key")
    suspend fun deleteMerchantRule(key: String)

    @Query("DELETE FROM merchant_rules")
    suspend fun clearMerchantRules()

    // Investments (stocks / mutual funds / SIPs)
    @Query("SELECT * FROM investments ORDER BY name ASC")
    fun getInvestmentsFlow(): Flow<List<Investment>>

    @Query("SELECT * FROM investments WHERE id = :id")
    suspend fun getInvestmentById(id: Long): Investment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: Investment): Long

    @Update
    suspend fun updateInvestment(investment: Investment)

    @Delete
    suspend fun deleteInvestment(investment: Investment)

    @Query("SELECT * FROM investment_transactions ORDER BY date DESC")
    fun getInvestmentTransactionsFlow(): Flow<List<InvestmentTransaction>>

    @Query("SELECT * FROM investment_transactions WHERE investmentId = :investmentId ORDER BY date DESC")
    fun getInvestmentTransactionsForFlow(investmentId: Long): Flow<List<InvestmentTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestmentTransaction(transaction: InvestmentTransaction): Long

    @Update
    suspend fun updateInvestmentTransaction(transaction: InvestmentTransaction)

    @Delete
    suspend fun deleteInvestmentTransaction(transaction: InvestmentTransaction)

    @Query("DELETE FROM investment_transactions WHERE investmentId = :investmentId")
    suspend fun deleteInvestmentTransactionsFor(investmentId: Long)

    // Group expenses
    @Query("SELECT * FROM expense_groups ORDER BY createdAt DESC")
    fun getExpenseGroupsFlow(): Flow<List<ExpenseGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseGroup(group: ExpenseGroup): Long

    @Update
    suspend fun updateExpenseGroup(group: ExpenseGroup)

    @Delete
    suspend fun deleteExpenseGroup(group: ExpenseGroup)

    @Query("SELECT * FROM expense_group_members WHERE groupId = :groupId ORDER BY id ASC")
    fun getExpenseGroupMembersFlow(groupId: Long): Flow<List<ExpenseGroupMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseGroupMember(member: ExpenseGroupMember): Long

    @Update
    suspend fun updateExpenseGroupMember(member: ExpenseGroupMember)

    @Delete
    suspend fun deleteExpenseGroupMember(member: ExpenseGroupMember)

    @Query("SELECT * FROM expense_group_expenses WHERE groupId = :groupId ORDER BY date DESC")
    fun getExpenseGroupExpensesFlow(groupId: Long): Flow<List<ExpenseGroupExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseGroupExpense(expense: ExpenseGroupExpense): Long

    @Update
    suspend fun updateExpenseGroupExpense(expense: ExpenseGroupExpense)

    @Delete
    suspend fun deleteExpenseGroupExpense(expense: ExpenseGroupExpense)

    @Query("DELETE FROM expense_groups")
    suspend fun clearExpenseGroups()

    @Query("DELETE FROM expense_group_members")
    suspend fun clearExpenseGroupMembers()

    @Query("DELETE FROM expense_group_expenses")
    suspend fun clearExpenseGroupExpenses()

    /** Replaces restorable core data atomically so a failed backup import rolls back completely. */
    @androidx.room.Transaction
    suspend fun replaceBackupData(
        accounts: List<Account>, categories: List<Category>, transactions: List<Transaction>,
        recurring: List<RecurringTransaction>, goals: List<SavingsGoal>, debts: List<Debt>
    ) {
        clearAccounts(); clearCategories(); clearTransactions(); clearRecurringTransactions(); clearSavingsGoals(); clearDebts()
        accounts.forEach { insertAccount(it) }; categories.forEach { insertCategory(it) }
        transactions.forEach { insertTransaction(it) }; recurring.forEach { insertRecurringTransaction(it) }
        goals.forEach { insertSavingsGoal(it) }; debts.forEach { insertDebt(it) }
    }
}
