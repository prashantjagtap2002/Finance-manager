package com.example.financemanager.domain

import com.example.financemanager.data.*
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val dao: FinanceDao) {

    // Accounts
    val accounts: Flow<List<Account>> = dao.getAccountsFlow()
    
    suspend fun getAccountById(id: Long): Account? = dao.getAccountById(id)

    suspend fun getAccountsOnce(): List<Account> = dao.getAccountsList()

    suspend fun insertAccount(account: Account): Long = dao.insertAccount(account)
    
    suspend fun updateAccount(account: Account) = dao.updateAccount(account)
    
    suspend fun deleteAccount(account: Account) = dao.deleteAccount(account)

    // Categories (Envelopes)
    val categories: Flow<List<Category>> = dao.getCategoriesFlow()
    
    suspend fun getCategoryById(id: Long): Category? = dao.getCategoryById(id)
    
    suspend fun insertCategory(category: Category): Long = dao.insertCategory(category)
    
    suspend fun updateCategory(category: Category) = dao.updateCategory(category)
    
    suspend fun updateCategories(categories: List<Category>) = dao.updateCategories(categories)
    
    suspend fun countTransactionsInCategory(categoryId: Long): Int =
        dao.countTransactionsInCategory(categoryId)

    /**
     * Deletes a category after re-pointing everything that referenced it at [reassignToCategoryId]
     * (0 meaning uncategorised). Deleting the row on its own used to leave its transactions on a
     * dangling id: they disappeared from every category breakdown while still counting toward the
     * expense total, so Insights silently stopped adding up.
     */
    suspend fun deleteCategory(category: Category, reassignToCategoryId: Long) {
        dao.reassignTransactionCategory(category.id, reassignToCategoryId)
        dao.reassignRecurringCategory(category.id, reassignToCategoryId)
        if (reassignToCategoryId > 0L) {
            dao.reassignMerchantRuleCategory(category.id, reassignToCategoryId)
        } else {
            // A learned rule pointing at "uncategorised" would teach nothing.
            dao.deleteMerchantRulesForCategory(category.id)
        }
        dao.deleteCategory(category)
    }

    suspend fun getTransactionsForAccount(accountId: Long): List<Transaction> =
        dao.getTransactionsForAccount(accountId)

    /**
     * Deletes an account by merging it into [targetAccountId]: its transactions, templates and
     * rules move across, and the target absorbs both its balance and its opening balance, which
     * keeps `balance == openingBalance + transactions` true on the other side.
     */
    suspend fun mergeAndDeleteAccount(account: Account, targetAccountId: Long) {
        val target = dao.getAccountById(targetAccountId) ?: return
        dao.reassignTransactionSourceAccount(account.id, targetAccountId)
        dao.reassignTransactionDestinationAccount(account.id, targetAccountId)
        dao.reassignRecurringAccount(account.id, targetAccountId)
        dao.reassignMerchantRuleAccount(account.id, targetAccountId)
        dao.reassignSmsAccount(account.id, targetAccountId)
        dao.updateAccount(
            target.copy(
                balance = target.balance + account.balance,
                openingBalance = target.openingBalance + account.openingBalance
            )
        )
        dao.deleteAccount(account)
    }

    /**
     * Deletes an account along with its history. Each transaction goes back through the ledger so
     * that transfers give the money back to the account on the other side instead of leaving it
     * short.
     */
    suspend fun deleteAccountWithTransactions(account: Account) {
        dao.getTransactionsForAccount(account.id).forEach { AccountLedger.revoke(dao, it) }
        dao.reassignRecurringAccount(account.id, 0L)
        dao.reassignMerchantRuleAccount(account.id, 0L)
        dao.reassignSmsAccount(account.id, 0L)
        dao.deleteAccount(account)
    }

    // Transactions
    val transactions: Flow<List<Transaction>> = dao.getTransactionsFlow()
    
    fun getTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> {
        return dao.getTransactionsBetweenDatesFlow(start, end)
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return dao.searchTransactionsFlow(query)
    }
    
    suspend fun getTransactionById(id: Long): Transaction? = dao.getTransactionById(id)

    /** Earliest transaction date on record, or null when the ledger is empty. */
    suspend fun getEarliestTransactionDate(): Long? = dao.getEarliestTransactionDate()
    
    /**
     * Records a transaction and moves the affected account balances with it. Callers must not
     * adjust balances themselves — see [AccountLedger].
     */
    suspend fun insertTransaction(transaction: Transaction): Long = AccountLedger.post(dao, transaction)

    /**
     * Checks if a transaction with the same key fields already exists to prevent duplicates.
     * Returns the existing transaction if found, null otherwise.
     */
    suspend fun findDuplicateTransaction(amount: Double, type: String, categoryId: Long, sourceAccountId: Long, date: Long): Transaction? {
        return dao.findDuplicateTransaction(amount, type, categoryId, sourceAccountId, date)
    }

    /**
     * Inserts a transaction only if no duplicate exists. Returns the ID of the inserted
     * transaction, or of the existing one if a duplicate was found — in which case balances are
     * left alone.
     */
    suspend fun insertTransactionIfNotExists(transaction: Transaction): Long =
        AccountLedger.postIfNew(dao, transaction)

    /** Edits a transaction, rolling its old balance effect back and the new one forward. */
    suspend fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction) {
        AccountLedger.amend(dao, oldTransaction, newTransaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) = AccountLedger.revoke(dao, transaction)

    /**
     * Writes a transaction verbatim without moving any balance. Only for bulk restores, where the
     * accounts are being written back with their final balances already in them — replaying each
     * transaction through the ledger there would apply every movement a second time.
     */
    suspend fun insertTransactionFromBackup(transaction: Transaction): Long =
        dao.insertTransaction(transaction)

    /** See [AccountLedger.postSideEffect]. */
    suspend fun postLedgerSideEffect(accountId: Long, amount: Double, note: String, date: Long): Long =
        AccountLedger.postSideEffect(dao, accountId, amount, note, date)

    /** Repairs balances that disagree with their transactions; returns what was corrected. */
    suspend fun reconcileBalances(): List<BalanceDrift> = AccountLedger.reconcile(dao)

    /** Re-anchors opening balances so the current balances become the ledger's truth. */
    suspend fun rebaseOpeningBalances() = AccountLedger.rebaseOpeningBalances(dao)

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
        dao.clearMerchantRules()
        clearExpenseGroups()
    }

    suspend fun replaceBackupData(
        accounts: List<Account>, categories: List<Category>, transactions: List<Transaction>,
        recurring: List<RecurringTransaction>, goals: List<SavingsGoal>, debts: List<Debt>
    ) = dao.replaceBackupData(accounts, categories, transactions, recurring, goals, debts)

    // Debts
    val debts: Flow<List<Debt>> = dao.getDebtsFlow()

    suspend fun insertDebt(debt: Debt): Long = dao.insertDebt(debt)

    suspend fun updateDebt(debt: Debt) = dao.updateDebt(debt)

    suspend fun deleteDebt(debt: Debt) = dao.deleteDebt(debt)

    // Group expenses
    val expenseGroups: Flow<List<ExpenseGroup>> = dao.getExpenseGroupsFlow()
    suspend fun insertExpenseGroup(group: ExpenseGroup): Long = dao.insertExpenseGroup(group)
    suspend fun updateExpenseGroup(group: ExpenseGroup) = dao.updateExpenseGroup(group)
    suspend fun deleteExpenseGroup(group: ExpenseGroup) = dao.deleteExpenseGroup(group)
    fun expenseGroupMembers(groupId: Long): Flow<List<ExpenseGroupMember>> = dao.getExpenseGroupMembersFlow(groupId)
    suspend fun insertExpenseGroupMember(member: ExpenseGroupMember): Long = dao.insertExpenseGroupMember(member)
    suspend fun updateExpenseGroupMember(member: ExpenseGroupMember) = dao.updateExpenseGroupMember(member)
    suspend fun deleteExpenseGroupMember(member: ExpenseGroupMember) = dao.deleteExpenseGroupMember(member)
    fun expenseGroupExpenses(groupId: Long): Flow<List<ExpenseGroupExpense>> = dao.getExpenseGroupExpensesFlow(groupId)
    suspend fun insertExpenseGroupExpense(expense: ExpenseGroupExpense): Long = dao.insertExpenseGroupExpense(expense)
    suspend fun updateExpenseGroupExpense(expense: ExpenseGroupExpense) = dao.updateExpenseGroupExpense(expense)
    suspend fun deleteExpenseGroupExpense(expense: ExpenseGroupExpense) = dao.deleteExpenseGroupExpense(expense)

    suspend fun clearExpenseGroups() {
        dao.clearExpenseGroupExpenses()
        dao.clearExpenseGroupMembers()
        dao.clearExpenseGroups()
    }

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

    /**
     * Removes the transaction an approved SMS created and gives its balance effect back.
     *
     * Used when a pair of alerts turns out to cancel out — a self transfer logged as an expense,
     * or an IPO block that was released — so the spending it inflated goes away with it.
     * Approvals made before [SmsTransaction.loggedTransactionId] existed are matched on the same
     * fields the approval wrote, which is the best that can be done for those older rows.
     */
    suspend fun revokeLoggedTransaction(sms: SmsTransaction) {
        val logged = if (sms.loggedTransactionId > 0) {
            dao.getTransactionById(sms.loggedTransactionId)
        } else if (!sms.isApproved) {
            null
        } else {
            val amount = SmsLinkDetector.amountOf(sms) ?: return
            val type = if (sms.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE
            dao.findDuplicateTransaction(
                amount,
                type.name,
                sms.approvedCategoryId,
                sms.approvedAccountId,
                sms.rawTimestamp
            )
        }
        logged?.let { AccountLedger.revoke(dao, it) }
    }

    // Merchant → category rules
    val merchantRules: Flow<List<MerchantRule>> = dao.getMerchantRulesFlow()

    suspend fun getMerchantRule(key: String): MerchantRule? = dao.getMerchantRule(key)

    suspend fun upsertMerchantRule(rule: MerchantRule) = dao.upsertMerchantRule(rule)

    suspend fun deleteMerchantRule(key: String) = dao.deleteMerchantRule(key)

    /**
     * Records the category (and account) a merchant was filed under. Called on every approval, so
     * a merchant that keeps landing in the same category climbs [MerchantRule.hitCount] and a
     * re-categorisation simply overwrites the old answer.
     */
    suspend fun rememberMerchantCategory(merchant: String, categoryId: Long, accountId: Long) {
        val key = MerchantKey.normalize(merchant)
        if (key.isEmpty() || categoryId <= 0L) return
        val existing = dao.getMerchantRule(key)
        dao.upsertMerchantRule(
            MerchantRule(
                merchantKey = key,
                categoryId = categoryId,
                accountId = accountId,
                hitCount = if (existing?.categoryId == categoryId) existing.hitCount + 1 else 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // Investments (stocks / mutual funds / SIPs)
    val investments: Flow<List<Investment>> = dao.getInvestmentsFlow()
    val investmentTransactions: Flow<List<InvestmentTransaction>> = dao.getInvestmentTransactionsFlow()

    fun getInvestmentTransactionsFor(investmentId: Long): Flow<List<InvestmentTransaction>> =
        dao.getInvestmentTransactionsForFlow(investmentId)

    suspend fun getInvestmentById(id: Long): Investment? = dao.getInvestmentById(id)

    suspend fun insertInvestment(investment: Investment): Long = dao.insertInvestment(investment)

    suspend fun updateInvestment(investment: Investment) = dao.updateInvestment(investment)

    suspend fun insertInvestmentTransaction(transaction: InvestmentTransaction): Long =
        dao.insertInvestmentTransaction(transaction)

    suspend fun updateInvestmentTransaction(transaction: InvestmentTransaction) =
        dao.updateInvestmentTransaction(transaction)

    suspend fun deleteInvestmentTransaction(transaction: InvestmentTransaction) =
        dao.deleteInvestmentTransaction(transaction)

    /** Deletes a holding along with every buy/sell/SIP installment logged against it. */
    suspend fun deleteInvestment(investment: Investment) {
        dao.deleteInvestmentTransactionsFor(investment.id)
        dao.deleteInvestment(investment)
    }
}
