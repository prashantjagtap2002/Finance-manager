package com.example.financemanager.domain

import com.example.financemanager.data.*
import kotlinx.coroutines.flow.Flow

class FinanceRepository(
    private val dao: FinanceDao,
    /**
     * The one currency the app works in, read fresh each time so a change in Settings is picked up
     * without rebuilding anything.
     *
     * Passed in rather than read from `core.FinancePreferences` directly, which would be the only
     * thing pulling the domain layer up into the app's preference storage. The default keeps the
     * historical value for callers that have no preferences to consult — the unit tests.
     */
    private val homeCurrency: () -> String = { "INR" }
) {

    /**
     * Rows go to the DAO carrying a fresh `updatedAt`, because everything that reaches the
     * repository is a local edit and the cloud merge decides which side is newer by comparing
     * those stamps. The DAO itself never stamps: [com.example.financemanager.services.SyncWorker]
     * writes through it directly, and a row arriving from the server has to keep the timestamp it
     * was saved with or it would look locally edited on arrival and be pushed straight back.
     */
    private fun now() = System.currentTimeMillis()

    // --- Deletion records ---
    // A cloud sync cannot tell "deleted here" from "created on another device and not pulled yet"
    // by looking at what is missing, and guessing wrong in the deleting direction destroys data
    // that was never on this device. So every delete the *user* asks for writes a tombstone, and
    // the sync removes a cloud row only when one says to. See
    // [com.example.financemanager.data.DeletedRecord].

    /** Records that [rowId] of [table] was deleted here, so the next sync removes it in the cloud. */
    private suspend fun tombstone(table: String, rowId: Long) {
        if (rowId <= 0L) return
        dao.insertDeletedRecord(DeletedRecord(tableName = table, rowId = rowId))
    }

    /**
     * Forgets a tombstone because the row is back — an undo, or a restore that wrote the same id
     * again. Left in place it would have the next sync delete the revived row from the cloud.
     */
    private suspend fun revive(table: String, rowId: Long) {
        if (rowId <= 0L) return
        dao.forgetDeletedRecord(table, rowId)
    }

    /** Row ids deleted on this device for [table]. Read by the sync, written by nothing else. */
    suspend fun deletedRowIds(table: String): Set<Long> = dao.getDeletedRowIds(table).toSet()

    /**
     * Drops tombstones older than [retentionMillis].
     *
     * They exist to survive the gap between a delete and the next successful sync, not forever.
     * The window is deliberately generous, and a tombstone that does expire only means a row could
     * come back — which is the failure direction this whole mechanism exists to choose.
     */
    suspend fun pruneDeletionRecords(retentionMillis: Long) {
        dao.pruneDeletedRecords(now() - retentionMillis)
    }

    /**
     * Forgets every tombstone. Used after a bulk replacement (a backup restore), where the file is
     * the statement of what exists and nothing in it should be treated as deleted.
     */
    suspend fun forgetAllDeletionRecords() = dao.clearDeletedRecords()

    // Accounts
    val accounts: Flow<List<Account>> = dao.getAccountsFlow()
    
    suspend fun getAccountById(id: Long): Account? = dao.getAccountById(id)

    suspend fun getAccountsOnce(): List<Account> = dao.getAccountsList()

    suspend fun insertAccount(account: Account): Long = dao.atomically {
        val id = dao.insertAccount(account.copy(currency = homeCurrency(), updatedAt = now()))
        // An id being written again is an id that is no longer deleted.
        revive(SyncTables.ACCOUNTS, id)
        id
    }
    
    suspend fun updateAccount(account: Account) = dao.updateAccount(account.copy(updatedAt = now()))
    
    suspend fun deleteAccount(account: Account) = dao.atomically {
        dao.deleteAccount(account)
        tombstone(SyncTables.ACCOUNTS, account.id)
    }

    // Categories (Envelopes)
    val categories: Flow<List<Category>> = dao.getCategoriesFlow()
    
    suspend fun getCategoryById(id: Long): Category? = dao.getCategoryById(id)
    
    suspend fun insertCategory(category: Category): Long = dao.atomically {
        val id = dao.insertCategory(category.copy(updatedAt = now()))
        revive(SyncTables.CATEGORIES, id)
        id
    }
    
    suspend fun updateCategory(category: Category) = dao.updateCategory(category.copy(updatedAt = now()))
    
    suspend fun updateCategories(categories: List<Category>) =
        dao.updateCategories(categories.map { it.copy(updatedAt = now()) })
    
    suspend fun countTransactionsInCategory(categoryId: Long): Int =
        dao.countTransactionsInCategory(categoryId)

    /**
     * Deletes a category after re-pointing everything that referenced it at [reassignToCategoryId]
     * (0 meaning uncategorised). Deleting the row on its own used to leave its transactions on a
     * dangling id: they disappeared from every category breakdown while still counting toward the
     * expense total, so Insights silently stopped adding up.
     */
    suspend fun deleteCategory(category: Category, reassignToCategoryId: Long) = dao.atomically {
        dao.reassignTransactionCategory(category.id, reassignToCategoryId)
        dao.reassignRecurringCategory(category.id, reassignToCategoryId)
        if (reassignToCategoryId > 0L) {
            dao.reassignMerchantRuleCategory(category.id, reassignToCategoryId)
        } else {
            // A learned rule pointing at "uncategorised" would teach nothing.
            dao.deleteMerchantRulesForCategory(category.id)
        }
        dao.deleteCategory(category)
        tombstone(SyncTables.CATEGORIES, category.id)
    }

    suspend fun getTransactionsForAccount(accountId: Long): List<Transaction> =
        dao.getTransactionsForAccount(accountId)

    // --- One-shot snapshots ---
    // Everything that writes a file (backup, CSV, PDF) reads through these rather than off a
    // StateFlow. A `stateIn(WhileSubscribed)` flow that nothing has collected yet still answers
    // `.value` — with its initial empty list — so an export taken before any screen had subscribed
    // wrote an empty file and reported success.

    suspend fun getCategoriesOnce(): List<Category> = dao.getCategoriesList()

    suspend fun getTransactionsOnce(): List<Transaction> = dao.getTransactionsList()

    suspend fun getRecurringOnce(): List<RecurringTransaction> = dao.getRecurringTransactionsList()

    suspend fun getSavingsGoalsOnce(): List<SavingsGoal> = dao.getSavingsGoalsList()

    suspend fun getDebtsOnce(): List<Debt> = dao.getDebtsList()

    suspend fun getInvestmentsOnce(): List<Investment> = dao.getInvestmentsList()

    suspend fun getInvestmentTransactionsOnce(): List<InvestmentTransaction> =
        dao.getInvestmentTransactionsList()

    suspend fun getMerchantRulesOnce(): List<MerchantRule> = dao.getMerchantRulesList()

    suspend fun getExpenseGroupsOnce(): List<ExpenseGroup> = dao.getExpenseGroupsList()

    suspend fun getExpenseGroupMembersOnce(): List<ExpenseGroupMember> =
        dao.getExpenseGroupMembersList()

    suspend fun getExpenseGroupExpensesOnce(): List<ExpenseGroupExpense> =
        dao.getExpenseGroupExpensesList()

    /**
     * Deletes an account by merging it into [targetAccountId]: its transactions, templates and
     * rules move across, and the target absorbs both its balance and its opening balance, which
     * keeps `balance == openingBalance + transactions` true on the other side.
     */
    suspend fun mergeAndDeleteAccount(account: Account, targetAccountId: Long) = dao.atomically {
        val target = dao.getAccountById(targetAccountId) ?: return@atomically
        dao.reassignTransactionSourceAccount(account.id, targetAccountId)
        dao.reassignTransactionDestinationAccount(account.id, targetAccountId)
        dao.reassignRecurringAccount(account.id, targetAccountId)
        dao.reassignMerchantRuleAccount(account.id, targetAccountId)
        dao.reassignSmsAccount(account.id, targetAccountId)
        dao.updateAccount(
            target.copy(
                balance = Money.roundToMinorUnit(target.balance + account.balance),
                openingBalance = Money.roundToMinorUnit(target.openingBalance + account.openingBalance),
                updatedAt = now()
            )
        )
        dao.deleteAccount(account)
        tombstone(SyncTables.ACCOUNTS, account.id)
    }

    /**
     * Deletes an account along with its history. Each transaction goes back through the ledger so
     * that transfers give the money back to the account on the other side instead of leaving it
     * short.
     */
    suspend fun deleteAccountWithTransactions(account: Account) = dao.atomically {
        dao.getTransactionsForAccount(account.id).forEach {
            AccountLedger.revoke(dao, it)
            tombstone(SyncTables.TRANSACTIONS, it.id)
        }
        dao.reassignRecurringAccount(account.id, 0L)
        dao.reassignMerchantRuleAccount(account.id, 0L)
        dao.reassignSmsAccount(account.id, 0L)
        dao.deleteAccount(account)
        tombstone(SyncTables.ACCOUNTS, account.id)
    }

    // Transactions
    val transactions: Flow<List<Transaction>> = dao.getTransactionsFlow()
    
    fun getTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> {
        return dao.getTransactionsBetweenDatesFlow(start, end)
    }

    /**
     * Free-text search. The query is escaped so that `%` and `_` typed by the user are matched
     * literally — unescaped, a search for "%" returned the entire ledger.
     */
    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return dao.searchTransactionsFlow(escapeLikeLiteral(query))
    }

    /** Escapes the LIKE wildcards, and the escape character itself, in user-typed text. */
    private fun escapeLikeLiteral(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            if (ch == '\\' || ch == '%' || ch == '_') append('\\')
            append(ch)
        }
    }
    
    suspend fun getTransactionById(id: Long): Transaction? = dao.getTransactionById(id)

    /** Earliest transaction date on record, or null when the ledger is empty. */
    suspend fun getEarliestTransactionDate(): Long? = dao.getEarliestTransactionDate()
    
    /**
     * Records a transaction and moves the affected account balances with it. Callers must not
     * adjust balances themselves — see [AccountLedger].
     */
    suspend fun insertTransaction(transaction: Transaction): Long = dao.atomically {
        // Stamped with the home currency because this is the path the *user* creates rows on, and
        // there is no way in the app to enter an amount in anything else. Restores come in through
        // insertTransactionFromBackup and replaceBackupData, which leave the stored code alone —
        // that is what lets a file brought over from another home currency stay recognisably
        // foreign instead of being silently absorbed into the totals.
        val id = AccountLedger.post(dao, transaction.copy(currency = homeCurrency()))
        // Undo re-inserts a deleted row under its own id; without this the next sync would read
        // the stale tombstone and delete the restored transaction out of the cloud.
        revive(SyncTables.TRANSACTIONS, id)
        id
    }

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

    /**
     * Repairs SMS-logged transactions whose payee the parser missed, and the alerts behind them.
     *
     * Kotak's "Sent Rs.X from Kotak Bank A/c X1234 to SHRI SAI VENUE PARKI on 07-11-26" was read
     * with a one-word payee pattern, so any name with a space came out empty: the alert had no
     * counterparty, the transaction's title fell back to the account name ("Kotak") and its
     * merchant to the SMS sender id. The parser is fixed; this re-reads each approved alert with
     * no counterparty and fills in what it now finds.
     *
     * Only the title and merchant change, and the title only where it is still the untouched
     * fallback — a title the user typed is left alone. Amounts, accounts and dates are never
     * touched, so no balance moves. Returns how many transactions were updated.
     */
    suspend fun backfillSmsPayees(): Int = dao.atomically {
        var updated = 0
        dao.getSmsTransactionsList()
            .filter { it.isApproved && it.counterparty.isBlank() && it.loggedTransactionId > 0L && it.body.isNotBlank() }
            .forEach { sms ->
                val payee = SmsParser.parse(sms.sender, sms.body)?.counterparty?.takeIf { it.isNotBlank() } ?: return@forEach
                dao.updateSmsTransaction(sms.copy(counterparty = payee, updatedAt = now()))
                val tx = dao.getTransactionById(sms.loggedTransactionId) ?: return@forEach
                val titleIsFallback = tx.note.isBlank() || tx.note == sms.accountName
                val merchantIsFallback = tx.merchantName.isNullOrBlank() || tx.merchantName == sms.sender
                if (!titleIsFallback && !merchantIsFallback) return@forEach
                dao.updateTransaction(
                    tx.copy(
                        note = if (titleIsFallback) payee else tx.note,
                        merchantName = if (merchantIsFallback) payee else tx.merchantName,
                        updatedAt = now()
                    )
                )
                updated++
            }
        updated
    }

    /**
     * Rewrites balance adjustments stored with a negative amount — from before
     * [postBalanceAdjustment] took a signed delta — into the shape it writes today: a positive
     * amount landing on the destination side with no source account.
     *
     * Such a row (source = A, no destination, amount −X) already credits A by X, because a
     * source-side TRANSFER subtracts its amount; the rewrite (source 0, destination A, amount X)
     * credits A by exactly the same X as an incoming transfer. No balance moves — the point is
     * that the row stops displaying as a negative transfer out. Returns how many were rewritten.
     */
    suspend fun normalizeNegativeAdjustments(): Int = dao.atomically {
        var fixed = 0
        dao.getTransactionsList()
            .filter { it.type == TransactionType.TRANSFER && it.amount < 0.0 && it.destinationAccountId == null && it.sourceAccountId > 0L }
            .forEach { row ->
                dao.updateTransaction(
                    row.copy(
                        amount = -row.amount,
                        sourceAccountId = 0L,
                        destinationAccountId = row.sourceAccountId,
                        updatedAt = now()
                    )
                )
                fixed++
            }
        fixed
    }

    suspend fun deleteTransaction(transaction: Transaction) = dao.atomically {
        AccountLedger.revoke(dao, transaction)
        tombstone(SyncTables.TRANSACTIONS, transaction.id)
    }

    /**
     * Writes a transaction verbatim without moving any balance. Only for bulk restores, where the
     * accounts are being written back with their final balances already in them — replaying each
     * transaction through the ledger there would apply every movement a second time.
     */
    suspend fun insertTransactionFromBackup(transaction: Transaction): Long =
        dao.insertTransaction(transaction)

    /** See [AccountLedger.postBalanceAdjustment]. [delta] is negative out, positive in. */
    suspend fun postBalanceAdjustment(accountId: Long, delta: Double, note: String, date: Long): Long =
        AccountLedger.postBalanceAdjustment(dao, accountId, delta, note, date)

    /** Repairs balances that disagree with their transactions; returns what was corrected. */
    suspend fun reconcileBalances(): List<BalanceDrift> = AccountLedger.reconcile(dao)

    /** Re-anchors opening balances so the current balances become the ledger's truth. */
    suspend fun rebaseOpeningBalances() = AccountLedger.rebaseOpeningBalances(dao)

    // Recurring Transactions
    val recurringTransactions: Flow<List<RecurringTransaction>> = dao.getRecurringTransactionsFlow()
    
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long = dao.atomically {
        val id = dao.insertRecurringTransaction(recurringTransaction.copy(updatedAt = now()))
        revive(SyncTables.RECURRING_TRANSACTIONS, id)
        id
    }
    
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        dao.updateRecurringTransaction(recurringTransaction.copy(updatedAt = now()))
    }
    
    suspend fun deleteRecurringTransaction(recurringTransaction: RecurringTransaction) = dao.atomically {
        dao.deleteRecurringTransaction(recurringTransaction)
        tombstone(SyncTables.RECURRING_TRANSACTIONS, recurringTransaction.id)
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

    suspend fun insertSavingsGoal(goal: SavingsGoal): Long = dao.atomically {
        val id = dao.insertSavingsGoal(goal.copy(updatedAt = now()))
        revive(SyncTables.SAVINGS_GOALS, id)
        id
    }

    suspend fun updateSavingsGoal(goal: SavingsGoal) = dao.updateSavingsGoal(goal.copy(updatedAt = now()))

    /**
     * Moves [amount] into [goal], optionally taking it out of an account, as one unit.
     *
     * The goal's saved amount and the account's balance are two halves of one statement. Written
     * separately, a process death between them leaves the goal claiming money the account was
     * never asked for — and nothing afterwards can tell that apart from a contribution the user
     * made from cash. Returns the goal as it now stands so the caller can work out which
     * milestones were crossed.
     */
    suspend fun contributeToGoal(goal: SavingsGoal, amount: Double, fromAccountId: Long?): SavingsGoal =
        dao.atomically {
            val updated = goal.copy(savedAmount = Money.roundToMinorUnit(goal.savedAmount + amount))
            dao.updateSavingsGoal(updated.copy(updatedAt = now()))
            if (fromAccountId != null) {
                // The transfer itself debits the account — the ledger handles that.
                insertTransaction(
                    Transaction(
                        amount = amount,
                        type = TransactionType.TRANSFER,
                        categoryId = 0L,
                        sourceAccountId = fromAccountId,
                        note = "Saved to goal: ${goal.name}",
                        date = now()
                    )
                )
            }
            updated
        }

    suspend fun deleteSavingsGoal(goal: SavingsGoal) = dao.atomically {
        dao.deleteSavingsGoal(goal)
        tombstone(SyncTables.SAVINGS_GOALS, goal.id)
    }

    /** Empties every table the app owns. "All" has to mean all — a leftover holding would
     *  reappear against accounts that no longer exist. */
    suspend fun clearAllTables() {
        dao.clearAccounts()
        dao.clearCategories()
        dao.clearTransactions()
        dao.clearRecurringTransactions()
        dao.clearSavingsGoals()
        dao.clearDebts()
        dao.clearSmsTransactions()
        dao.clearMerchantRules()
        dao.clearInvestmentTransactions()
        dao.clearInvestments()
        clearExpenseGroups()
    }

    suspend fun replaceBackupData(
        accounts: List<Account>, categories: List<Category>, transactions: List<Transaction>,
        recurring: List<RecurringTransaction>, goals: List<SavingsGoal>, debts: List<Debt>,
        investments: List<Investment> = emptyList(),
        investmentTransactions: List<InvestmentTransaction> = emptyList(),
        merchantRules: List<MerchantRule> = emptyList(),
        groups: List<ExpenseGroup> = emptyList(),
        groupMembers: List<ExpenseGroupMember> = emptyList(),
        groupExpenses: List<ExpenseGroupExpense> = emptyList()
    ) = dao.atomically {
        dao.replaceBackupData(
            accounts, categories, transactions, recurring, goals, debts,
            investments, investmentTransactions, merchantRules, groups, groupMembers, groupExpenses
        )
        // A restore is a statement of what exists, not of what was deleted. Any tombstone left
        // over would have the next sync delete a row the file just put back.
        dao.clearDeletedRecords()
    }

    /**
     * Re-stamps every stored currency code to [code], the user's home currency.
     *
     * The app has one currency: it is a single setting, every amount on screen is rendered with
     * its symbol, and no screen has ever offered a per-account or per-transaction choice. The
     * per-row `currency` columns were therefore always the hardcoded default, meaning nothing,
     * while totals added the numbers together as if they agreed — which is only valid if they
     * actually do. Making the code match what the app has always displayed is what makes those
     * sums honest; not one amount is touched.
     *
     * Rows that arrive from elsewhere carrying a different code — a backup taken under another
     * home currency — are left to [com.example.financemanager.ui.viewmodel.FinanceViewModel]'s
     * per-currency totals to report separately rather than being silently absorbed. Returns how
     * many rows were re-stamped.
     */
    suspend fun normalizeCurrencies(code: String): Int = dao.atomically {
        val stamp = now()
        dao.normalizeAccountCurrency(code, stamp) + dao.normalizeTransactionCurrency(code, stamp)
    }

    // Debts
    val debts: Flow<List<Debt>> = dao.getDebtsFlow()

    suspend fun insertDebt(debt: Debt): Long = dao.atomically {
        val id = dao.insertDebt(debt.copy(updatedAt = now()))
        revive(SyncTables.DEBTS, id)
        id
    }

    suspend fun updateDebt(debt: Debt) = dao.updateDebt(debt.copy(updatedAt = now()))

    /**
     * Opens an IOU and moves [accountId] by its principal, as one unit: lending takes the money
     * out, borrowing puts it in. The movement is a balance adjustment, not an expense or income —
     * money lent is still the user's, so it must not land in an envelope's spending. Returns the
     * new debt's id.
     */
    suspend fun openIou(debt: Debt, accountId: Long): Long = dao.atomically {
        val ledgerId = postBalanceAdjustment(
            accountId = accountId,
            delta = if (debt.type == DebtType.BORROWED) debt.amount else -debt.amount,
            note = if (debt.type == DebtType.LENT) "Lent to ${debt.personName}" else "Borrowed from ${debt.personName}",
            date = debt.date
        )
        // Kept on the debt so deleting it can undo exactly this movement.
        insertDebt(debt.copy(ledgerTransactionId = ledgerId))
    }

    /**
     * The ledger row deleting [debt] would revoke, or null if deleting it leaves balances alone.
     *
     * Only an IOU that moved money when it was opened, and that nothing has been repaid on, gives
     * its money back. Once a repayment is recorded, that money really came back into an account,
     * so deleting is a write-off or a clean-up, not an undo: reversing the principal then would
     * leave the account richer than it ever was. A missing row means the user already deleted the
     * movement from the log themselves, and refunding again would count it twice.
     */
    suspend fun refundOnDelete(debt: Debt): Transaction? {
        if (debt.ledgerTransactionId <= 0L || debt.paidAmount > Money.EPSILON) return null
        return dao.getTransactionById(debt.ledgerTransactionId)
    }

    /**
     * Records a repayment against [debt] and moves the account with it, as one unit. Returns the
     * id of the ledger row so a caller that may have to undo the whole thing can hold on to it.
     *
     * [delta] is how the account's balance should move: positive when they repay a loan you made,
     * negative when you repay one you took. Split across two writes, a crash between them left a
     * debt marked part-paid with no money having moved, or the reverse.
     */
    suspend fun applyDebtPayment(debt: Debt, paymentAmount: Double, accountId: Long, delta: Double, date: Long): Long =
        dao.atomically {
            val newPaidAmount = Money.roundToMinorUnit(debt.paidAmount + paymentAmount)
            dao.updateDebt(
                debt.copy(
                    paidAmount = newPaidAmount,
                    isSettled = newPaidAmount >= debt.amount - Money.EPSILON,
                    updatedAt = now()
                )
            )
            postBalanceAdjustment(accountId, delta, "IOU Payment: ${debt.personName}", date)
        }

    suspend fun deleteDebt(debt: Debt) = dao.atomically {
        refundOnDelete(debt)?.let { movement ->
            AccountLedger.revoke(dao, movement)
            tombstone(SyncTables.TRANSACTIONS, movement.id)
        }
        dao.deleteDebt(debt)
        tombstone(SyncTables.DEBTS, debt.id)
    }

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

    suspend fun getSmsTransactionById(id: Long): SmsTransaction? = dao.getSmsTransactionById(id)

    /**
     * Unpicks the other leg of a resolved pair, sending it back to the inbox unapproved.
     *
     * A pair shares a single transaction — the debit leg holds it and the credit leg holds none —
     * so re-filing or un-ignoring one leg on its own left the other claiming a resolution whose
     * transaction had just been deleted, or still owning the transaction the resolution was made
     * of. Whichever leg holds it, it goes with the pairing.
     */
    suspend fun detachSmsPartner(sms: SmsTransaction) {
        if (sms.linkedSmsId <= 0L) return
        val partner = dao.getSmsTransactionById(sms.linkedSmsId) ?: return
        if (partner.linkedSmsId != sms.id) return
        revokeLoggedTransaction(partner)
        dao.updateSmsTransaction(
            partner.copy(
                isApproved = false,
                loggedTransactionId = 0L,
                linkedSmsId = 0L,
                resolution = "",
                updatedAt = now()
            )
        )
    }

    suspend fun insertSmsTransaction(smsTransaction: SmsTransaction): Long = dao.atomically {
        val id = dao.insertSmsTransaction(smsTransaction.copy(updatedAt = now()))
        revive(SyncTables.SMS_TRANSACTIONS, id)
        id
    }

    suspend fun updateSmsTransaction(smsTransaction: SmsTransaction) =
        dao.updateSmsTransaction(smsTransaction.copy(updatedAt = now()))

    suspend fun deleteSmsTransaction(smsTransaction: SmsTransaction) = dao.atomically {
        dao.deleteSmsTransaction(smsTransaction)
        tombstone(SyncTables.SMS_TRANSACTIONS, smsTransaction.id)
    }

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
        logged?.let {
            AccountLedger.revoke(dao, it)
            tombstone(SyncTables.TRANSACTIONS, it.id)
        }
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

    suspend fun getInvestmentTransactionById(id: Long): InvestmentTransaction? =
        dao.getInvestmentTransactionById(id)

    /** One holding's trades, oldest first — the order cost basis has to be walked in. */
    suspend fun getInvestmentTradesOnce(investmentId: Long): List<InvestmentTransaction> =
        dao.getInvestmentTransactionsFor(investmentId)

    /**
     * Units of [investmentId] currently held, optionally ignoring one trade — which is what an
     * edit needs, since the row being changed must not count against its own replacement.
     */
    suspend fun heldQuantityOf(investmentId: Long, excludingTradeId: Long = 0L): Double =
        dao.getInvestmentTransactionsFor(investmentId)
            .filter { it.id != excludingTradeId }
            .sumOf { if (it.type == InvestmentTxnType.SELL) -it.quantity else it.quantity }

    /**
     * Records a buy or a sell together with the cash that moved, as one unit.
     *
     * The holding row and the account balance were two independent writes, so a crash between them
     * left units owned that were never paid for, or cash gone with nothing bought. The ledger row
     * id is kept on the trade ([InvestmentTransaction.ledgerTransactionId]) so editing or deleting
     * the trade later can undo the exact movement it made instead of guessing at one.
     *
     * [cashDelta] is how the funding account moves: negative for a buy, positive for a sell.
     */
    suspend fun recordInvestmentTrade(
        trade: InvestmentTransaction,
        cashDelta: Double,
        note: String
    ): Long = dao.atomically {
        val ledgerId = postBalanceAdjustment(trade.sourceAccountId, cashDelta, note, trade.date)
        dao.insertInvestmentTransaction(trade.copy(ledgerTransactionId = ledgerId))
    }

    /**
     * Replaces a logged trade, correcting the funding account for the difference.
     *
     * Editing used to rewrite the holding row alone, so changing a buy from 10 units to 100 left
     * the account still showing the original ten units' worth of cash gone — a silent, permanent
     * disagreement between the portfolio and the balance.
     */
    suspend fun replaceInvestmentTrade(
        old: InvestmentTransaction,
        updated: InvestmentTransaction,
        note: String
    ) = dao.atomically {
        revokeTradeCash(old, "Reversed: $note")
        val cashDelta = if (updated.type == InvestmentTxnType.SELL) updated.amount else -updated.amount
        val newLedgerId = postBalanceAdjustment(updated.sourceAccountId, cashDelta, note, updated.date)
        // A legacy row that had no ledger link gets one from here on.
        dao.updateInvestmentTransaction(updated.copy(ledgerTransactionId = newLedgerId))
    }

    /** Deletes a logged trade and gives the funding account its money back, as one unit. */
    suspend fun deleteInvestmentTrade(trade: InvestmentTransaction, note: String) = dao.atomically {
        revokeTradeCash(trade, note)
        dao.deleteInvestmentTransaction(trade)
    }

    /**
     * Backs out the cash leg of [trade]. When the trade recorded which ledger row it created, that
     * exact row is revoked; older trades that predate the link get an offsetting adjustment
     * instead, which lands on the same balance but leaves both rows in the history.
     */
    private suspend fun revokeTradeCash(trade: InvestmentTransaction, note: String): Long {
        val linked = trade.ledgerTransactionId.takeIf { it > 0L }?.let { dao.getTransactionById(it) }
        if (linked != null) {
            AccountLedger.revoke(dao, linked)
            tombstone(SyncTables.TRANSACTIONS, linked.id)
            return 0L
        }
        // Undoing a sell takes the proceeds back out; undoing a buy returns the cash.
        val reversal = if (trade.type == InvestmentTxnType.SELL) -trade.amount else trade.amount
        return postBalanceAdjustment(trade.sourceAccountId, reversal, note, now())
    }

    /**
     * Deletes a holding along with every buy/sell/SIP installment logged against it.
     *
     * The cash legs those trades posted are deliberately left in the ledger: that money really did
     * leave and arrive, the account balances are built on it, and removing the record of a holding
     * is not a claim that its purchases never happened.
     */
    suspend fun deleteInvestment(investment: Investment) = dao.atomically {
        dao.deleteInvestmentTransactionsFor(investment.id)
        dao.deleteInvestment(investment)
    }
}
