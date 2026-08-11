package com.example.financemanager.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.core.PayCycleFrequency
import com.example.financemanager.core.ThemePreference
import com.example.financemanager.data.*
import com.example.financemanager.data.remote.SupabaseManager
import com.example.financemanager.domain.FinanceRepository
import com.example.financemanager.services.NotificationHelper
import com.example.financemanager.services.SyncWorker
import com.example.financemanager.domain.NlpParser
import com.example.financemanager.domain.NlpResult
import com.example.financemanager.ui.components.moneyString
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Calendar

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val prefs = application.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)

    // DB Observables
    val accounts: StateFlow<List<Account>>
    val categories: StateFlow<List<Category>>
    val transactions: StateFlow<List<Transaction>>
    val recurringTransactions: StateFlow<List<RecurringTransaction>>
    val savingsGoals: StateFlow<List<SavingsGoal>>
    val sixMonthTrendData: StateFlow<Map<String, Double>>
    val debts: StateFlow<List<Debt>>
    val groupedDebts: StateFlow<Map<String, List<Debt>>>
    val netWorthHistory: StateFlow<Map<String, Double>>

    // Spending for the CURRENT calendar month only (total + per category)
    val monthlyExpenseTotal: StateFlow<Double>
    val monthlyIncomeTotal: StateFlow<Double>
    val monthlyCategorySpend: StateFlow<Map<Long, Double>>

    // Consecutive days (ending today or yesterday) with at least one logged transaction
    val streakDays: StateFlow<Int>

    // New AI & Dashboard Flows
    val safeToSpend: StateFlow<Double>
    val unlockedBadges: StateFlow<List<String>>
    val aiRecapText: StateFlow<String?>

    // UI States
    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    private val _themePreference = MutableStateFlow(ThemePreference.DARK)
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _currencyCode = MutableStateFlow("INR")
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    private val _payCycleFrequency = MutableStateFlow(PayCycleFrequency.MONTHLY)
    val payCycleFrequency: StateFlow<PayCycleFrequency> = _payCycleFrequency.asStateFlow()

    private val _payCycleAnchorDay = MutableStateFlow(1)
    val payCycleAnchorDay: StateFlow<Int> = _payCycleAnchorDay.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _securityPin = MutableStateFlow("")
    val securityPin: StateFlow<String> = _securityPin.asStateFlow()

    val cashflowWarning: StateFlow<String?>

    // Privacy mode: masks balances across the app (persisted)
    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    // App lock preference (biometric prompt on launch, persisted)
    private val _isAppLockEnabled = MutableStateFlow(true)
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    // Calculator Keypad States
    private val _amountInput = MutableStateFlow("0")
    val amountInput: StateFlow<String> = _amountInput.asStateFlow()

    // Cloud sync (Supabase). Holds the anonymous user id once signed in, null otherwise.
    private val _cloudUserId = MutableStateFlow<String?>(null)
    val cloudUserId: StateFlow<String?> = _cloudUserId.asStateFlow()

    // SMS Transactions
    val smsTransactions: StateFlow<List<SmsTransaction>>
    val pendingSmsTransactions: StateFlow<List<SmsTransaction>>
    val ignoredSmsTransactions: StateFlow<List<SmsTransaction>>
    val approvedSmsTransactions: StateFlow<List<SmsTransaction>>

    init {
        FinancePreferences.init(application)
        val database = FinanceDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(database.financeDao())

        accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        recurringTransactions = repository.recurringTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        savingsGoals = repository.savingsGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        debts = repository.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        groupedDebts = debts.map { debtList ->
            debtList.groupBy { it.personName.trim().lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        smsTransactions = repository.smsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        pendingSmsTransactions = repository.pendingSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        ignoredSmsTransactions = repository.ignoredSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        approvedSmsTransactions = repository.approvedSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Restore persisted UI preferences
        _isPrivacyMode.value = prefs.getBoolean("privacy_mode", false)
        _isAppLockEnabled.value = prefs.getBoolean("app_lock_enabled", true)
        _isProUser.value = FinancePreferences.isProUserFlow.value
        _themePreference.value = FinancePreferences.themeModeFlow.value
        _currencyCode.value = FinancePreferences.currencyCodeFlow.value
        _scanCount.value = FinancePreferences.scanCountFlow.value
        _payCycleFrequency.value = FinancePreferences.payCycleFrequencyFlow.value
        _payCycleAnchorDay.value = FinancePreferences.payCycleAnchorDayFlow.value

        viewModelScope.launch {
            FinancePreferences.isProUserFlow.collect { _isProUser.value = it }
        }
        viewModelScope.launch {
            FinancePreferences.themeModeFlow.collect { _themePreference.value = it }
        }
        viewModelScope.launch {
            FinancePreferences.currencyCodeFlow.collect { _currencyCode.value = it }
        }
        viewModelScope.launch {
            FinancePreferences.scanCountFlow.collect { _scanCount.value = it }
        }
        viewModelScope.launch {
            FinancePreferences.payCycleFrequencyFlow.collect { _payCycleFrequency.value = it }
        }
        viewModelScope.launch {
            FinancePreferences.payCycleAnchorDayFlow.collect { _payCycleAnchorDay.value = it }
        }

        monthlyExpenseTotal = repository.transactions
            .map { txList ->
                val (start, end) = currentPayCycleRange()
                txList.filter { it.type == TransactionType.EXPENSE && it.date in start..end }
                    .sumOf { it.amount }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyIncomeTotal = repository.transactions
            .map { txList ->
                val (start, end) = currentPayCycleRange()
                txList.filter { it.type == TransactionType.INCOME && it.date in start..end }
                    .sumOf { it.amount }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyCategorySpend = repository.transactions
            .map { txList ->
                val (start, end) = currentPayCycleRange()
                txList.filter { it.type == TransactionType.EXPENSE && it.date in start..end }
                    .groupBy { it.categoryId }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        streakDays = repository.transactions
            .map { txList -> computeStreakDays(txList) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        sixMonthTrendData = repository.transactions
            .map { txList ->
                val now = Calendar.getInstance()
                now.set(Calendar.DAY_OF_MONTH, 1)
                now.add(Calendar.MONTH, -5)
                val sixMonthsAgo = now.timeInMillis

                val formatter = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())

                // Initialize all 6 months with 0.0 to ensure they appear on the chart
                val result = mutableMapOf<String, Double>()
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -5)
                for (i in 0..5) {
                    result[formatter.format(cal.time)] = 0.0
                    cal.add(Calendar.MONTH, 1)
                }

                txList.filter { it.date >= sixMonthsAgo && it.type == TransactionType.EXPENSE }
                    .groupBy { formatter.format(java.util.Date(it.date)) }
                    .forEach { (month, txs) ->
                        result[month] = txs.sumOf { it.amount }
                    }

                result
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        cashflowWarning = combine(accounts, recurringTransactions) { accs, recs ->
            val primary = accs.firstOrNull() ?: return@combine null
            val now = System.currentTimeMillis()
            val upcoming = recs.filter { !it.isPaused && it.isAutoLog && it.type == TransactionType.EXPENSE && (it.nextExecutionDate - now) in 0..(3 * 24 * 60 * 60 * 1000L) }
            for (rt in upcoming) {
                if (primary.balance < rt.amount) {
                    return@combine "Heads up! Your upcoming bill \"${rt.note.ifEmpty { "Subscription" }}\" is ${moneyString(rt.amount, false)}, but your ${primary.name} account only has ${moneyString(primary.balance, false)}."
                }
            }
            null
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

        safeToSpend = combine(
            repository.accounts,
            repository.categories,
            repository.savingsGoals,
            repository.recurringTransactions
        ) { accList, catList, goalsList, recList ->
            val totalBalance = accList.sumOf { it.balance }
            val remainingBudgets = catList.filter { it.budgetLimit > 0 }.sumOf { 
                // We'd need to subtract spent from budgetLimit ideally, but simpler: just sum budgets
                it.budgetLimit 
            }
            val remainingGoals = goalsList.sumOf { (it.targetAmount - it.savedAmount).coerceAtLeast(0.0) }
            val upcomingBills = recList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            
            // Just a rough estimate for Safe to Spend
            val safe = totalBalance - remainingBudgets - (remainingGoals * 0.1) - upcomingBills
            if (safe < 0) 0.0 else safe
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        unlockedBadges = combine(
            streakDays,
            repository.savingsGoals,
            repository.transactions
        ) { streak, goalsList, txList ->
            val badges = mutableListOf<String>()
            if (streak >= 7) badges.add("7-Day Streak")
            if (streak >= 30) badges.add("30-Day Streak")
            if (goalsList.sumOf { it.savedAmount } >= 10000) badges.add("10K Saved")
            if (txList.size >= 100) badges.add("100 Logged")
            badges
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        aiRecapText = repository.transactions.map { txList ->
            val now = System.currentTimeMillis()
            val oneWeekMs = 7L * 24 * 60 * 60 * 1000
            val thisWeekSpent = txList.filter { it.type == TransactionType.EXPENSE && it.date > now - oneWeekMs }.sumOf { it.amount }
            val lastWeekSpent = txList.filter { it.type == TransactionType.EXPENSE && it.date in (now - 2 * oneWeekMs)..(now - oneWeekMs) }.sumOf { it.amount }
            
            if (thisWeekSpent == 0.0 && lastWeekSpent == 0.0) null
            else if (thisWeekSpent < lastWeekSpent) "You spent ${moneyString(thisWeekSpent, false)} this week — that's less than last week! 🎉"
            else "You spent ${moneyString(thisWeekSpent, false)} this week. Watch your expenses to stay on track!"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        netWorthHistory = combine(repository.accounts, repository.transactions, repository.debts) { accountsList, txList, debtsList ->
            val now = Calendar.getInstance()
            now.set(Calendar.DAY_OF_MONTH, 1)
            now.add(Calendar.MONTH, -5)
            val sixMonthsAgo = now.timeInMillis

            val formatter = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            
            // Current assets and liabilities
            val currentAssets = accountsList.filter { it.type != AccountType.CREDIT_CARD }.sumOf { it.balance } + debtsList.filter { it.type == DebtType.LENT && !it.isSettled }.sumOf { it.amount }
            val currentLiabilities = accountsList.filter { it.type == AccountType.CREDIT_CARD }.sumOf { it.balance } + debtsList.filter { it.type == DebtType.BORROWED && !it.isSettled }.sumOf { it.amount }
            
            val currentNetWorth = currentAssets - currentLiabilities

            // Backtrack net worth by reversing transactions
            val result = mutableMapOf<String, Double>()
            val cal = Calendar.getInstance()
            
            var runningNetWorth = currentNetWorth
            
            // Start from current month and go backwards
            for (i in 0..5) {
                val monthStr = formatter.format(cal.time)
                result[monthStr] = runningNetWorth
                
                // Revert this month's transactions to find the previous month's net worth
                val monthStart = cal.clone() as Calendar
                monthStart.set(Calendar.DAY_OF_MONTH, 1)
                monthStart.set(Calendar.HOUR_OF_DAY, 0)
                monthStart.set(Calendar.MINUTE, 0)
                
                val monthEnd = cal.clone() as Calendar
                monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                monthEnd.set(Calendar.HOUR_OF_DAY, 23)
                monthEnd.set(Calendar.MINUTE, 59)
                
                val monthTxs = txList.filter { it.date in monthStart.timeInMillis..monthEnd.timeInMillis }
                
                val netIncomeThisMonth = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val netExpenseThisMonth = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                // Transfer doesn't affect net worth
                
                // runningNetWorth at START of this month = runningNetWorth at END of month - (netIncome - netExpense)
                runningNetWorth -= (netIncomeThisMonth - netExpenseThisMonth)
                
                cal.add(Calendar.MONTH, -1)
            }
            
            // Reverse so it's chronological
            result.toList().reversed().toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        // Check for missed recurring payments on app start
        viewModelScope.launch {
            repository.syncRecurringTransactions()
            checkUpcomingBills()
        }

        // Establish an anonymous Supabase session for cloud sync
        viewModelScope.launch {
            _cloudUserId.value = SupabaseManager.ensureSignedIn(application)
        }
    }

    // --- Pro Tier / Monetization Simulation ---
    fun toggleProUser() {
        FinancePreferences.setProUser(!_isProUser.value)
    }

    fun setThemePreference(preference: ThemePreference) {
        FinancePreferences.setThemeMode(preference)
    }

    fun setCurrency(code: String) {
        FinancePreferences.setCurrencyCode(code)
    }

    fun setPayCycle(frequency: PayCycleFrequency, anchorDay: Int) {
        FinancePreferences.setPayCycle(frequency, anchorDay)
    }

    fun consumeReceiptScanQuota(): Boolean = FinancePreferences.consumeReceiptScanQuota()

    fun remainingFreeScans(): Int = FinancePreferences.remainingFreeScans()

    // --- Security PIN Actions ---
    fun setSecurityPin(pin: String) {
        _securityPin.value = pin
        _isAppLocked.value = pin.isNotEmpty()
    }

    fun unlockApp(enteredPin: String): Boolean {
        return if (enteredPin == _securityPin.value) {
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (_securityPin.value.isNotEmpty()) {
            _isAppLocked.value = true
        }
    }

    // --- Privacy Mode & App Lock Preferences ---
    fun togglePrivacyMode() {
        _isPrivacyMode.value = !_isPrivacyMode.value
        prefs.edit().putBoolean("privacy_mode", _isPrivacyMode.value).apply()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        _isAppLockEnabled.value = enabled
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    // --- Date & Streak Helpers ---
    fun currentPayCycleRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (_payCycleFrequency.value) {
            PayCycleFrequency.WEEKLY -> {
                while (cal.get(Calendar.DAY_OF_MONTH) != _payCycleAnchorDay.value.coerceIn(1, 28)) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 7)
                start to (cal.timeInMillis - 1)
            }
            PayCycleFrequency.BIWEEKLY -> {
                while (cal.get(Calendar.DAY_OF_MONTH) != _payCycleAnchorDay.value.coerceIn(1, 28)) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val start = cal.timeInMillis
                while (start + 14L * 24 * 60 * 60 * 1000 > now && cal.timeInMillis > now) {
                    cal.add(Calendar.DAY_OF_MONTH, -14)
                }
                val normalizedStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 14)
                normalizedStart to (cal.timeInMillis - 1)
            }
            PayCycleFrequency.SEMIMONTHLY -> {
                val anchor = _payCycleAnchorDay.value.coerceIn(1, 28)
                val secondAnchor = if (anchor <= 15) 15 else minOf(anchor, 28)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                if (currentDay >= secondAnchor) {
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    start to (cal.timeInMillis - 1)
                } else if (currentDay >= anchor) {
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    val start = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    start to (cal.timeInMillis - 1)
                } else {
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.DAY_OF_MONTH, secondAnchor)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, anchor)
                    start to (cal.timeInMillis - 1)
                }
            }
            PayCycleFrequency.MONTHLY -> {
                val anchor = _payCycleAnchorDay.value.coerceIn(1, 28)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                if (currentDay < anchor) {
                    cal.add(Calendar.MONTH, -1)
                }
                cal.set(Calendar.DAY_OF_MONTH, anchor)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                start to (cal.timeInMillis - 1)
            }
        }
    }

    internal fun computeStreakDays(txList: List<Transaction>, now: Long = System.currentTimeMillis()): Int {
        if (txList.isEmpty()) return 0
        val dayMs = 24L * 60 * 60 * 1000
        // Normalize each transaction to its local calendar day
        val loggedDays = txList.map { tx ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = tx.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }.toSet()

        val todayCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var cursor = todayCal.timeInMillis
        // Streak may end today OR yesterday (don't break it before the day is over)
        if (cursor !in loggedDays) cursor -= dayMs
        var streak = 0
        while (cursor in loggedDays) {
            streak++
            cursor -= dayMs
        }
        return streak
    }

    // --- Savings Goals ---
    fun createSavingsGoal(name: String, targetAmount: Double, targetDate: Long?, iconName: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertSavingsGoal(
                SavingsGoal(
                    name = name,
                    targetAmount = targetAmount,
                    targetDate = targetDate,
                    iconName = iconName,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch { repository.deleteSavingsGoal(goal) }
    }

    /**
     * Adds money to a goal. When [fromAccountId] is provided the amount is
     * deducted from that account and logged as a transfer so history stays honest.
     */
    fun contributeToGoal(goal: SavingsGoal, amount: Double, fromAccountId: Long?) {
        if (amount <= 0) return
        viewModelScope.launch {
            val before = goal.savedAmount / goal.targetAmount
            val updated = goal.copy(savedAmount = goal.savedAmount + amount)
            repository.updateSavingsGoal(updated)

            if (fromAccountId != null) {
                repository.getAccountById(fromAccountId)?.let { acc ->
                    repository.updateAccount(acc.copy(balance = acc.balance - amount))
                }
                repository.insertTransaction(
                    Transaction(
                        amount = amount,
                        type = TransactionType.TRANSFER,
                        categoryId = 0L,
                        sourceAccountId = fromAccountId,
                        note = "Saved to goal: ${goal.name}",
                        date = System.currentTimeMillis()
                    )
                )
            }

            // Milestone notifications (50% and 100%)
            val after = updated.savedAmount / updated.targetAmount
            val context = getApplication<Application>()
            when {
                before < 1.0 && after >= 1.0 -> NotificationHelper.notifyGoalMilestone(context, goal.name, 100)
                before < 0.5 && after >= 0.5 -> NotificationHelper.notifyGoalMilestone(context, goal.name, (after * 100).toInt())
            }
        }
    }

    // --- Budget threshold alerts (80% / 100%) ---
    private suspend fun checkBudgetThreshold(categoryId: Long, addedAmount: Double) {
        if (categoryId == 0L || addedAmount <= 0) return
        val category = repository.getCategoryById(categoryId) ?: return
        if (category.budgetLimit <= 0) return

        // Read from the repository directly — the StateFlow may not be collected yet
        val (start, end) = currentPayCycleRange()
        val spentAfter = repository.transactions.first()
            .filter { it.categoryId == categoryId && it.type == TransactionType.EXPENSE && it.date in start..end }
            .sumOf { it.amount }
        val spentBefore = spentAfter - addedAmount
        val limit = category.budgetLimit
        val context = getApplication<Application>()

        when {
            spentBefore < limit && spentAfter >= limit ->
                NotificationHelper.notifyBudgetThreshold(context, category.name, spentAfter, limit, isOver = true)
            spentBefore < limit * 0.8 && spentAfter >= limit * 0.8 ->
                NotificationHelper.notifyBudgetThreshold(context, category.name, spentAfter, limit, isOver = false)
        }
    }

    // --- Upcoming bill reminders (due within 48h) ---
    private fun checkUpcomingBills() {
        viewModelScope.launch {
            // Snapshot recurring templates directly (StateFlow may not be collected yet)
            val now = System.currentTimeMillis()
            val windowEnd = now + 48L * 60 * 60 * 1000
            val upcoming = repository.recurringTransactions.first()
                .filter { it.type == TransactionType.EXPENSE && it.nextExecutionDate in now..windowEnd }
            if (upcoming.isNotEmpty()) {
                NotificationHelper.notifyUpcomingBills(
                    getApplication(),
                    upcoming.size,
                    upcoming.sumOf { it.amount },
                    upcoming.minByOrNull { it.nextExecutionDate }?.note?.ifEmpty { "Recurring bill" } ?: "Recurring bill"
                )
            }
        }
    }

    // --- Undo support: restore a just-deleted transaction ---
    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)

            // Re-apply source account balance impact
            val account = repository.getAccountById(transaction.sourceAccountId)
            if (account != null) {
                val newBalance = when (transaction.type) {
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> account.balance - transaction.amount
                    TransactionType.INCOME -> account.balance + transaction.amount
                }
                repository.updateAccount(account.copy(balance = newBalance))
            }

            // Re-apply destination account balance impact if transfer
            if (transaction.type == TransactionType.TRANSFER && transaction.destinationAccountId != null) {
                val destAccount = repository.getAccountById(transaction.destinationAccountId)
                if (destAccount != null) {
                    repository.updateAccount(destAccount.copy(balance = destAccount.balance + transaction.amount))
                }
            }
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction) {
        viewModelScope.launch {
            // 1. Revert old transaction balance impact (Source)
            val oldAccount = repository.getAccountById(oldTransaction.sourceAccountId)
            if (oldAccount != null) {
                val revertedBalance = when (oldTransaction.type) {
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> oldAccount.balance + oldTransaction.amount
                    TransactionType.INCOME -> oldAccount.balance - oldTransaction.amount
                }
                repository.updateAccount(oldAccount.copy(balance = revertedBalance))
            }

            // 2. Revert old transaction balance impact (Destination if transfer)
            if (oldTransaction.type == TransactionType.TRANSFER && oldTransaction.destinationAccountId != null) {
                val oldDestAccount = repository.getAccountById(oldTransaction.destinationAccountId)
                if (oldDestAccount != null) {
                    repository.updateAccount(oldDestAccount.copy(balance = oldDestAccount.balance - oldTransaction.amount))
                }
            }

            // 3. Apply new transaction balance impact (Source)
            val newAccount = repository.getAccountById(newTransaction.sourceAccountId)
            if (newAccount != null) {
                val newBalance = when (newTransaction.type) {
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> newAccount.balance - newTransaction.amount
                    TransactionType.INCOME -> newAccount.balance + newTransaction.amount
                }
                repository.updateAccount(newAccount.copy(balance = newBalance))
            }

            // 4. Apply new transaction balance impact (Destination if transfer)
            if (newTransaction.type == TransactionType.TRANSFER && newTransaction.destinationAccountId != null) {
                val newDestAccount = repository.getAccountById(newTransaction.destinationAccountId)
                if (newDestAccount != null) {
                    repository.updateAccount(newDestAccount.copy(balance = newDestAccount.balance + newTransaction.amount))
                }
            }

            // 5. Save updated transaction
            repository.updateTransaction(newTransaction)
        }
    }

    // --- Calculator Arithmetic Logic ---
    fun onCalculatorKeyPress(key: String) {
        val current = _amountInput.value
        when (key) {
            "C" -> _amountInput.value = "0"
            "⌫" -> {
                if (current.length <= 1) {
                    _amountInput.value = "0"
                } else {
                    _amountInput.value = current.dropLast(1)
                }
            }
            "+", "-" -> {
                // Prevent duplicate consecutive operators
                if (current.endsWith("+") || current.endsWith("-")) {
                    _amountInput.value = current.dropLast(1) + key
                } else {
                    _amountInput.value = current + key
                }
            }
            "." -> {
                // Simple dot rule check: append dot if last number doesn't have one
                val lastNum = current.split(Regex("[+\\-]")).last()
                if (!lastNum.contains(".")) {
                    _amountInput.value = current + "."
                }
            }
            else -> { // Numeric keys
                if (current == "0") {
                    _amountInput.value = key
                } else {
                    _amountInput.value = current + key
                }
            }
        }
    }

    fun evaluateAmount(): Double {
        val expr = _amountInput.value
        return try {
            val clean = expr.replace(" ", "")
            val tokens = mutableListOf<String>()
            var numberAccumulator = ""
            for (char in clean) {
                if (char == '+' || char == '-') {
                    if (numberAccumulator.isNotEmpty()) {
                        tokens.add(numberAccumulator)
                        numberAccumulator = ""
                    }
                    tokens.add(char.toString())
                } else {
                    numberAccumulator += char
                }
            }
            if (numberAccumulator.isNotEmpty()) {
                tokens.add(numberAccumulator)
            }

            if (tokens.isEmpty()) return 0.0
            var result = tokens[0].toDoubleOrNull() ?: 0.0
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                val nextVal = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0
                if (op == "+") {
                    result += nextVal
                } else if (op == "-") {
                    result -= nextVal
                }
                i += 2
            }
            result
        } catch (e: Exception) {
            0.0
        }
    }

    fun setAmount(amount: Double) {
        _amountInput.value = String.format("%.2f", amount).removeSuffix(".00")
    }

    // --- Core Transaction Insertions & Edits ---
    fun addManualTransaction(
        amount: Double,
        type: TransactionType,
        categoryId: Long,
        accountId: Long,
        note: String,
        date: Long,
        isRecurring: Boolean = false,
        recurringInterval: RecurringInterval? = null,
        destinationAccountId: Long? = null,
        isAutoLog: Boolean = true,
        subscriptionDate: Long? = null
    ) {
        viewModelScope.launch {
            // 1. Insert transaction log
            val transId = repository.insertTransaction(
                Transaction(
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    sourceAccountId = accountId,
                    destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                    note = note,
                    date = date
                )
            )

            val sourceAccount = repository.getAccountById(accountId)
            if (sourceAccount != null) {
                val newBalance = when (type) {
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> sourceAccount.balance - amount
                    TransactionType.INCOME -> sourceAccount.balance + amount
                }
                repository.updateAccount(sourceAccount.copy(balance = newBalance))
            }

            // 3. Adjust destination account balance if transfer
            if (type == TransactionType.TRANSFER && destinationAccountId != null) {
                val destinationAccount = repository.getAccountById(destinationAccountId)
                if (destinationAccount != null) {
                    repository.updateAccount(destinationAccount.copy(balance = destinationAccount.balance + amount))
                }
            }

            // 3.5 Fire envelope budget alert if this expense crosses 80%/100%
            if (type == TransactionType.EXPENSE) {
                checkBudgetThreshold(categoryId, amount)
            }

            // 4. Register recurring template if toggle is on
            if (isRecurring && recurringInterval != null) {
                val nextExec = subscriptionDate ?: run {
                    val cal = Calendar.getInstance().apply { timeInMillis = date }
                    when (recurringInterval) {
                        RecurringInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        RecurringInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        RecurringInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
                        RecurringInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
                    }
                    cal.timeInMillis
                }
                repository.insertRecurringTransaction(
                    RecurringTransaction(
                        amount = amount,
                        type = type,
                        categoryId = categoryId,
                        accountId = accountId,
                        interval = recurringInterval,
                        note = note,
                        startDate = date,
                        nextExecutionDate = nextExec,
                        isAutoLog = isAutoLog
                    )
                )
            }
            
            // Reset amount display
            _amountInput.value = "0"
        }
    }

    fun addSplitTransaction(
        totalAmount: Double,
        splits: List<Pair<Long, Double>>, // categoryId -> amount
        accountId: Long,
        note: String,
        date: Long
    ) {
        viewModelScope.launch {
            val splitGroupId = java.util.UUID.randomUUID().toString()
            for ((catId, amount) in splits) {
                repository.insertTransaction(
                    Transaction(
                        amount = amount,
                        type = TransactionType.EXPENSE,
                        categoryId = catId,
                        sourceAccountId = accountId,
                        note = note,
                        date = date,
                        splitGroupId = splitGroupId
                    )
                )
            }
            // Deduct totalAmount from account
            repository.getAccountById(accountId)?.let { acc ->
                repository.updateAccount(acc.copy(balance = acc.balance - totalAmount))
            }

            // Budget alerts per envelope touched by the split
            for ((catId, amount) in splits) {
                checkBudgetThreshold(catId, amount)
            }

            _amountInput.value = "0"
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            
            // Revert source account balance impact
            val currentAccounts = accounts.value
            val account = currentAccounts.firstOrNull { it.id == transaction.sourceAccountId }
                ?: currentAccounts.firstOrNull()

            if (account != null) {
                val revertedBalance = when (transaction.type) {
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> account.balance + transaction.amount
                    TransactionType.INCOME -> account.balance - transaction.amount
                }
                repository.updateAccount(account.copy(balance = revertedBalance))
            }

            // Revert destination account balance impact if transfer
            if (transaction.type == TransactionType.TRANSFER && transaction.destinationAccountId != null) {
                val destAccount = currentAccounts.firstOrNull { it.id == transaction.destinationAccountId }
                if (destAccount != null) {
                    repository.updateAccount(destAccount.copy(balance = destAccount.balance - transaction.amount))
                }
            }

            SyncWorker.enqueueNow(getApplication())
        }
    }

    // --- Recurring / Subscriptions ---
    fun deleteRecurringTransaction(recurring: RecurringTransaction) {
        viewModelScope.launch { repository.deleteRecurringTransaction(recurring) }
    }
    
    fun toggleSubscriptionPause(recurring: RecurringTransaction) {
        viewModelScope.launch {
            repository.updateRecurringTransaction(recurring.copy(isPaused = !recurring.isPaused))
        }
    }

    fun logAndSplitRecurring(rec: RecurringTransaction, friendName: String, friendShare: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 1. Insert expense transaction (full amount)
            val splitGroupId = java.util.UUID.randomUUID().toString()
            repository.insertTransaction(
                Transaction(
                    amount = rec.amount - friendShare,
                    type = rec.type,
                    categoryId = rec.categoryId,
                    sourceAccountId = rec.accountId,
                    note = rec.note.ifEmpty { "Split Bill" },
                    date = now,
                    splitGroupId = splitGroupId
                )
            )
            // 2. Add debt for friend
            repository.insertDebt(
                Debt(
                    personName = friendName,
                    amount = friendShare,
                    type = DebtType.LENT,
                    date = now,
                    notes = "Split for ${rec.note.ifEmpty { "Subscription" }}"
                )
            )
            // 3. Deduct total from account
            repository.getAccountById(rec.accountId)?.let { acc ->
                repository.updateAccount(acc.copy(balance = acc.balance - rec.amount))
            }
            // 4. Advance execution date
            val cal = Calendar.getInstance().apply { timeInMillis = rec.nextExecutionDate }
            when (rec.interval) {
                RecurringInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                RecurringInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                RecurringInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RecurringInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
            }
            while (cal.timeInMillis <= now) {
                when (rec.interval) {
                    RecurringInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    RecurringInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    RecurringInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
                    RecurringInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
                }
            }
            repository.updateRecurringTransaction(rec.copy(nextExecutionDate = cal.timeInMillis))
        }
    }

    // --- Envelope Budget Setup ---
    fun updateCategoryLimit(categoryId: Long, limit: Double) {
        viewModelScope.launch {
            val category = repository.getCategoryById(categoryId)
            if (category != null) {
                repository.updateCategory(category.copy(budgetLimit = limit))
            }
        }
    }

    fun createCategory(name: String, iconName: String, colorHex: String, budgetLimit: Double, isRollover: Boolean = false) {
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.displayOrder } ?: -1
            repository.insertCategory(
                Category(name = name, iconName = iconName, colorHex = colorHex, budgetLimit = budgetLimit, isRolloverEnabled = isRollover, displayOrder = maxOrder + 1)
            )
        }
    }

    fun createTemplateCategories(selectedNames: List<String>) {
        val presets = listOf(
            "Rent" to Triple("home", "#3B82F6", 0.0),
            "Groceries" to Triple("shopping_bag", "#10B981", 0.0),
            "Transport" to Triple("trending_up", "#F59E0B", 0.0),
            "Fun Money" to Triple("movie", "#EC4899", 0.0),
            "Savings" to Triple("receipt_long", "#8B5CF6", 0.0)
        )
        val existing = categories.value.map { it.name.trim().lowercase() }.toSet()
        presets.filter { it.first in selectedNames && it.first.lowercase() !in existing }
            .forEach { (name, meta) ->
                createCategory(name, meta.first, meta.second, meta.third, isRollover = name != "Rent")
            }
    }

    fun transferEnvelopeBudget(sourceCategoryId: Long, destinationCategoryId: Long, amount: Double): Boolean {
        if (amount <= 0 || sourceCategoryId == destinationCategoryId) return false
        val source = categories.value.firstOrNull { it.id == sourceCategoryId } ?: return false
        val destination = categories.value.firstOrNull { it.id == destinationCategoryId } ?: return false
        if (source.budgetLimit < amount) return false
        viewModelScope.launch {
            repository.updateCategory(source.copy(budgetLimit = source.budgetLimit - amount))
            repository.updateCategory(destination.copy(budgetLimit = destination.budgetLimit + amount))
        }
        return true
    }

    fun stuffEnvelopesForNewCycle() {
        viewModelScope.launch {
            val (currentStart, _) = currentPayCycleRange()
            val (start, end) = currentPayCycleRange(currentStart - 1)
            val spendingByCategory = repository.transactions.first()
                .filter { it.type == TransactionType.EXPENSE && it.date in start..end }
                .groupBy { it.categoryId }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }

            categories.value.forEach { category ->
                val spent = spendingByCategory[category.id] ?: 0.0
                val leftover = (category.budgetLimit - spent).coerceAtLeast(0.0)
                repository.updateCategory(
                    category.copy(
                        rolloverAmount = if (category.isRolloverEnabled) leftover else 0.0
                    )
                )
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun moveCategoryUp(category: Category) {
        val list = categories.value
        val index = list.indexOfFirst { it.id == category.id }
        if (index > 0) {
            val mutableList = list.toMutableList()
            val temp = mutableList[index - 1]
            mutableList[index - 1] = category
            mutableList[index] = temp
            
            val updated = mutableList.mapIndexed { i, cat -> cat.copy(displayOrder = i) }
            viewModelScope.launch {
                repository.updateCategories(updated)
            }
        }
    }

    fun moveCategoryDown(category: Category) {
        val list = categories.value
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 0 && index < list.size - 1) {
            val mutableList = list.toMutableList()
            val temp = mutableList[index + 1]
            mutableList[index + 1] = category
            mutableList[index] = temp
            
            val updated = mutableList.mapIndexed { i, cat -> cat.copy(displayOrder = i) }
            viewModelScope.launch {
                repository.updateCategories(updated)
            }
        }
    }

    /** Batch-reorder categories after drag-and-drop. */
    fun reorderCategories(newOrder: List<Category>) {
        val updated = newOrder.mapIndexed { i, cat -> cat.copy(displayOrder = i) }
        viewModelScope.launch {
            repository.updateCategories(updated)
        }
    }

    // --- Multi-Account Setup ---
    fun createAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch {
            repository.insertAccount(
                Account(name = name, type = type, balance = initialBalance)
            )
        }
    }

    // --- NLP Phrase parsing trigger ---
    fun parseNlpPhrase(phrase: String): NlpResult {
        return NlpParser.parse(phrase)
    }

    // --- CSV Exporter Helper ---
    fun exportTransactionsToCsv(context: Context): String? {
        val list = transactions.value
        if (list.isEmpty()) return null

        return try {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(directory, "transactions_export.csv")
            val writer = FileWriter(file)
            
            // Write CSV headers
            writer.append("ID,Amount,Type,CategoryId,AccountId,Note,Date,Merchant\n")
            
            for (t in list) {
                writer.append("${t.id},${t.amount},${t.type},${t.categoryId},${t.sourceAccountId},\"${t.note}\",${t.date},\"${t.merchantName ?: ""}\"\n")
            }
            writer.flush()
            writer.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun importTransactionsFromCsv(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                    val rows = lines.toList()
                    if (rows.isEmpty()) return@useLines
                    // Skip header (idx 0)
                    for (i in 1 until rows.size) {
                        val row = rows[i]
                        val parts = row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                        if (parts.size >= 8) {
                            val amount = parts[1].toDoubleOrNull() ?: continue
                            val type = TransactionType.valueOf(parts[2])
                            val categoryId = parts[3].toLongOrNull() ?: 1L
                            val accountId = parts[4].toLongOrNull() ?: 1L
                            val note = parts[5].trim('"')
                            val date = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                            val merchant = parts[7].trim('"').takeIf { it.isNotBlank() }

                            repository.insertTransaction(
                                Transaction(
                                    amount = amount,
                                    type = type,
                                    categoryId = categoryId,
                                    sourceAccountId = accountId,
                                    note = note,
                                    date = date,
                                    merchantName = merchant
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            val userId = com.example.financemanager.data.remote.SupabaseManager.signInWithGoogleIdToken(idToken)
            if (userId != null) {
                _cloudUserId.value = userId
                // Trigger a sync after sign-in
                val request = androidx.work.OneTimeWorkRequestBuilder<com.example.financemanager.services.SyncWorker>().build()
                androidx.work.WorkManager.getInstance(getApplication()).enqueue(request)
            }
        }
    }
    
    fun searchTransactions(query: String) = repository.searchTransactions(query)

    // --- Transaction Entries ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun simulateBankSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.delay(1000) // Simulated sync refresh time
            _isSyncing.value = false
        }
    }

    fun simulateDebugTransactionSync() {
        viewModelScope.launch {
            val merchants = listOf("Netflix", "Amazon Prime", "Swiggy Food", "Uber Ride", "Zara Shopping")
            val prices = listOf(199.0, 299.0, 450.0, 180.0, 2499.0)
            val categories = listOf(5L, 5L, 1L, 3L, 2L)

            val index = (merchants.indices).random()

            val activeAccounts = accounts.value
            val bankAccount = activeAccounts.firstOrNull { it.type == AccountType.BANK }
            val ccAccount = activeAccounts.firstOrNull { it.type == AccountType.CREDIT_CARD }

            val targetAccount = if (index == 4) ccAccount ?: bankAccount else bankAccount
            val targetAccountId = targetAccount?.id ?: 1L
            val amount = prices[index]
            val timestamp = System.currentTimeMillis()

            val transaction = Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                categoryId = categories[index],
                sourceAccountId = targetAccountId,
                note = "Simulated Sync: ${merchants[index]}",
                date = timestamp,
                isAutoLogged = true,
                merchantName = merchants[index]
            )
            // Use duplicate-safe insertion
            repository.insertTransactionIfNotExists(transaction)

            if (targetAccount != null) {
                repository.updateAccount(targetAccount.copy(balance = targetAccount.balance - amount))
            }
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val rootJson = JSONObject().apply {
                    put("version", 1)

                    // 1. Accounts
                    val accountsArray = JSONArray()
                    accounts.value.forEach { account ->
                        accountsArray.put(JSONObject().apply {
                            put("id", account.id)
                            put("name", account.name)
                            put("type", account.type.name)
                            put("balance", account.balance)
                            put("currency", account.currency)
                        })
                    }
                    put("accounts", accountsArray)

                    // 2. Categories (Envelopes)
                    val categoriesArray = JSONArray()
                    categories.value.forEach { category ->
                        categoriesArray.put(JSONObject().apply {
                            put("id", category.id)
                            put("name", category.name)
                            put("iconName", category.iconName)
                            put("colorHex", category.colorHex)
                            put("budgetLimit", category.budgetLimit)
                            put("isZeroBased", category.isZeroBased)
                            put("rolloverAmount", category.rolloverAmount)
                            put("isRolloverEnabled", category.isRolloverEnabled)
                        })
                    }
                    put("categories", categoriesArray)

                    // 3. Transactions
                    val transactionsArray = JSONArray()
                    transactions.value.forEach { trans ->
                        transactionsArray.put(JSONObject().apply {
                            put("id", trans.id)
                            put("amount", trans.amount)
                            put("type", trans.type.name)
                            put("categoryId", trans.categoryId)
                            put("sourceAccountId", trans.sourceAccountId)
                            put("destinationAccountId", trans.destinationAccountId ?: JSONObject.NULL)
                            put("note", trans.note)
                            put("date", trans.date)
                            put("currency", trans.currency)
                            put("isRecurring", trans.isRecurring)
                            put("recurringId", trans.recurringId ?: JSONObject.NULL)
                            put("isAutoLogged", trans.isAutoLogged)
                            put("merchantName", trans.merchantName ?: JSONObject.NULL)
                            put("originalAmount", trans.originalAmount ?: JSONObject.NULL)
                            put("originalCurrency", trans.originalCurrency ?: JSONObject.NULL)
                        })
                    }
                    put("transactions", transactionsArray)

                    // 4. Recurring Transactions
                    val recurringArray = JSONArray()
                    recurringTransactions.value.forEach { rec ->
                        recurringArray.put(JSONObject().apply {
                            put("id", rec.id)
                            put("amount", rec.amount)
                            put("type", rec.type.name)
                            put("categoryId", rec.categoryId)
                            put("accountId", rec.accountId)
                            put("interval", rec.interval.name)
                            put("note", rec.note)
                            put("startDate", rec.startDate)
                            put("nextExecutionDate", rec.nextExecutionDate)
                            put("isAutoLog", rec.isAutoLog)
                        })
                    }
                    put("recurringTransactions", recurringArray)

                    // 5. Savings Goals
                    val goalsArray = JSONArray()
                    savingsGoals.value.forEach { goal ->
                        goalsArray.put(JSONObject().apply {
                            put("id", goal.id)
                            put("name", goal.name)
                            put("iconName", goal.iconName)
                            put("colorHex", goal.colorHex)
                            put("targetAmount", goal.targetAmount)
                            put("savedAmount", goal.savedAmount)
                            put("targetDate", goal.targetDate ?: JSONObject.NULL)
                            put("createdAt", goal.createdAt)
                        })
                    }
                    put("savingsGoals", goalsArray)

                    // 6. Debts
                    val debtsArray = JSONArray()
                    debts.value.forEach { debt ->
                        debtsArray.put(JSONObject().apply {
                            put("id", debt.id)
                            put("personName", debt.personName)
                            put("amount", debt.amount)
                            put("type", debt.type.name)
                            put("isSettled", debt.isSettled)
                            put("date", debt.date)
                            put("dueDate", debt.dueDate ?: JSONObject.NULL)
                            put("paidAmount", debt.paidAmount)
                            put("notes", debt.notes)
                        })
                    }
                    put("debts", debtsArray)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(rootJson.toString(2))
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonStringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            jsonStringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }

                val rootJson = JSONObject(jsonStringBuilder.toString())

                // Parse Accounts
                val accountsList = mutableListOf<Account>()
                val accountsArray = rootJson.optJSONArray("accounts")
                if (accountsArray != null) {
                    for (i in 0 until accountsArray.length()) {
                        val obj = accountsArray.getJSONObject(i)
                        accountsList.add(Account(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            type = AccountType.valueOf(obj.getString("type")),
                            balance = obj.getDouble("balance"),
                            currency = obj.optString("currency", "INR")
                        ))
                    }
                }

                // Parse Categories
                val categoriesList = mutableListOf<Category>()
                val categoriesArray = rootJson.optJSONArray("categories")
                if (categoriesArray != null) {
                    for (i in 0 until categoriesArray.length()) {
                        val obj = categoriesArray.getJSONObject(i)
                        categoriesList.add(Category(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            iconName = obj.getString("iconName"),
                            colorHex = obj.getString("colorHex"),
                            budgetLimit = obj.optDouble("budgetLimit", 0.0),
                            isZeroBased = obj.optBoolean("isZeroBased", false),
                            rolloverAmount = obj.optDouble("rolloverAmount", 0.0),
                            isRolloverEnabled = obj.optBoolean("isRolloverEnabled", false)
                        ))
                    }
                }

                // Parse Transactions
                val transactionsList = mutableListOf<Transaction>()
                val transactionsArray = rootJson.optJSONArray("transactions")
                if (transactionsArray != null) {
                    for (i in 0 until transactionsArray.length()) {
                        val obj = transactionsArray.getJSONObject(i)
                        val destId = if (obj.isNull("destinationAccountId")) null else obj.getLong("destinationAccountId")
                        val recId = if (obj.isNull("recurringId")) null else obj.getLong("recurringId")
                        val merchant = if (obj.isNull("merchantName")) null else obj.getString("merchantName")
                        val origAmt = if (obj.isNull("originalAmount")) null else obj.getDouble("originalAmount")
                        val origCurr = if (obj.isNull("originalCurrency")) null else obj.getString("originalCurrency")

                        transactionsList.add(Transaction(
                            id = obj.optLong("id", 0),
                            amount = obj.getDouble("amount"),
                            type = TransactionType.valueOf(obj.getString("type")),
                            categoryId = obj.getLong("categoryId"),
                            sourceAccountId = obj.getLong("sourceAccountId"),
                            destinationAccountId = destId,
                            note = obj.optString("note", ""),
                            date = obj.getLong("date"),
                            currency = obj.optString("currency", "INR"),
                            isRecurring = obj.optBoolean("isRecurring", false),
                            recurringId = recId,
                            isAutoLogged = obj.optBoolean("isAutoLogged", false),
                            merchantName = merchant,
                            originalAmount = origAmt,
                            originalCurrency = origCurr
                        ))
                    }
                }

                // Parse Recurring Transactions
                val recurringList = mutableListOf<RecurringTransaction>()
                val recurringArray = rootJson.optJSONArray("recurringTransactions")
                if (recurringArray != null) {
                    for (i in 0 until recurringArray.length()) {
                        val obj = recurringArray.getJSONObject(i)
                        recurringList.add(RecurringTransaction(
                            id = obj.optLong("id", 0),
                            amount = obj.getDouble("amount"),
                            type = TransactionType.valueOf(obj.getString("type")),
                            categoryId = obj.getLong("categoryId"),
                            accountId = obj.getLong("accountId"),
                            interval = RecurringInterval.valueOf(obj.getString("interval")),
                            note = obj.optString("note", ""),
                            startDate = obj.getLong("startDate"),
                            nextExecutionDate = obj.getLong("nextExecutionDate"),
                            isAutoLog = obj.optBoolean("isAutoLog", true)
                        ))
                    }
                }

                // Parse Savings Goals
                val goalsList = mutableListOf<SavingsGoal>()
                val goalsArray = rootJson.optJSONArray("savingsGoals")
                if (goalsArray != null) {
                    for (i in 0 until goalsArray.length()) {
                        val obj = goalsArray.getJSONObject(i)
                        val targetDate = if (obj.isNull("targetDate")) null else obj.getLong("targetDate")
                        goalsList.add(SavingsGoal(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            iconName = obj.optString("iconName", "savings"),
                            colorHex = obj.optString("colorHex", "#10B981"),
                            targetAmount = obj.getDouble("targetAmount"),
                            savedAmount = obj.optDouble("savedAmount", 0.0),
                            targetDate = targetDate,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                }

                // Parse Debts
                val debtsList = mutableListOf<Debt>()
                val debtsArray = rootJson.optJSONArray("debts")
                if (debtsArray != null) {
                    for (i in 0 until debtsArray.length()) {
                        val obj = debtsArray.getJSONObject(i)
                        val due = if (obj.isNull("dueDate")) null else obj.getLong("dueDate")
                        debtsList.add(Debt(
                            id = obj.optLong("id", 0),
                            personName = obj.getString("personName"),
                            amount = obj.getDouble("amount"),
                            type = DebtType.valueOf(obj.getString("type")),
                            isSettled = obj.optBoolean("isSettled", false),
                            date = obj.getLong("date"),
                            dueDate = due,
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                // Wipe existing database and restore backup
                repository.clearAllTables()

                // Insert all elements back
                accountsList.forEach { repository.insertAccount(it) }
                categoriesList.forEach { repository.insertCategory(it) }
                transactionsList.forEach { repository.insertTransaction(it) }
                recurringList.forEach { repository.insertRecurringTransaction(it) }
                goalsList.forEach { repository.insertSavingsGoal(it) }
                debtsList.forEach { repository.insertDebt(it) }

                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to parse backup file")
            }
        }
    }

    // Expose repository method to fetch transactions by category, year, month
    fun getTransactionsByCategory(categoryId: Long, year: String, month: String): Flow<List<Transaction>> {
        return repository.getTransactionsByCategory(categoryId, year, month)
    }

    // --- PDF Export ---
    fun exportMonthlyPdf(context: Context, monthName: String): String? {
        val txs = transactions.value
        val accs = accounts.value
        val cats = categories.value
        return com.example.financemanager.services.PdfGenerator.generateMonthlyReport(
            context, txs, accs, cats, monthName
        )
    }

    // --- Debts ---
    fun insertDebt(debt: Debt) {
        viewModelScope.launch {
            repository.insertDebt(debt)
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch {
            repository.updateDebt(debt)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }

    fun recordDebtPayment(debt: Debt, paymentAmount: Double, account: Account) {
        viewModelScope.launch {
            val newPaidAmount = debt.paidAmount + paymentAmount
            val isNowSettled = newPaidAmount >= debt.amount

            // Update Debt
            val updatedDebt = debt.copy(
                paidAmount = newPaidAmount,
                isSettled = isNowSettled
            )
            repository.updateDebt(updatedDebt)

            // Update Account Balance
            val newBalance = if (debt.type == DebtType.LENT) {
                // "Owe Me" -> they pay me back -> account gets money
                account.balance + paymentAmount
            } else {
                // "I Owe" -> I pay them back -> account loses money
                account.balance - paymentAmount
            }
            repository.updateAccount(account.copy(balance = newBalance))

            // Log Transaction
            val txType = if (debt.type == DebtType.LENT) TransactionType.INCOME else TransactionType.EXPENSE
            val transaction = Transaction(
                amount = paymentAmount,
                type = txType,
                categoryId = 0, // Uncategorized or create a specific one
                sourceAccountId = account.id,
                note = "IOU Payment: ${debt.personName}",
                date = System.currentTimeMillis()
            )
            repository.insertTransaction(transaction)
        }
    }

    // --- Tags ---
    fun extractTags(note: String): List<String> {
        val regex = Regex("#\\w+")
        return regex.findAll(note).map { it.value }.toList()
    }

    // --- SMS Transactions ---
    fun approveSmsTransaction(
        smsTransaction: SmsTransaction,
        categoryId: Long,
        accountId: Long,
        note: String = "",
        txTypeOverride: TransactionType? = null
    ) {
        viewModelScope.launch {
            val amount = smsTransaction.amount.replace("Rs.", "").trim().toDoubleOrNull() ?: 0.0
            val txType = txTypeOverride ?: if (smsTransaction.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE

            val finalNote = note.ifEmpty {
                smsTransaction.counterparty.ifEmpty { smsTransaction.accountName }
            }

            repository.insertTransaction(
                Transaction(
                    amount = amount,
                    type = txType,
                    categoryId = categoryId,
                    sourceAccountId = accountId,
                    note = finalNote,
                    date = smsTransaction.rawTimestamp,
                    merchantName = smsTransaction.counterparty.ifEmpty { smsTransaction.sender },
                    isAutoLogged = true,
                    isVerified = true
                )
            )

            val account = repository.getAccountById(accountId)
            if (account != null) {
                val newBalance = when (txType) {
                    TransactionType.EXPENSE -> account.balance - amount
                    TransactionType.INCOME -> account.balance + amount
                    TransactionType.TRANSFER -> account.balance
                }
                repository.updateAccount(account.copy(balance = newBalance))
            }

            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = categoryId,
                    approvedAccountId = accountId
                )
            )

            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun approveSmsAsDebtPayment(
        smsTransaction: SmsTransaction,
        debt: Debt,
        paymentAmount: Double,
        account: Account
    ) {
        viewModelScope.launch {
            recordDebtPayment(debt, paymentAmount, account)
            
            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = account.id
                )
            )
            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun ignoreSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            repository.updateSmsTransaction(smsTransaction.copy(isIgnored = true))
            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun unignoreSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            repository.updateSmsTransaction(smsTransaction.copy(isIgnored = false, isApproved = false))
            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun deleteSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            repository.deleteSmsTransaction(smsTransaction)
            val primaryAccount = accounts.value.firstOrNull()
            val parsedAmt = smsTransaction.amount.toDoubleOrNull() ?: 0.0
            if (primaryAccount != null && parsedAmt > 0) {
                repository.updateAccount(primaryAccount.copy(balance = primaryAccount.balance + parsedAmt))
            }
            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun getSmsTransactionsList(): List<SmsTransaction> {
        var result = emptyList<SmsTransaction>()
        val job = viewModelScope.launch {
            result = repository.getSmsTransactionsList()
        }
        kotlinx.coroutines.runBlocking { job.join() }
        return result
    }

    fun importSmsJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            jsonStr.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val root = jsonStr.toString().trim()
                val jsonArray = when {
                    root.startsWith("[") -> JSONArray(root)
                    root.startsWith("{") -> {
                        val obj = JSONObject(root)
                        when {
                            obj.optJSONArray("messages") != null -> obj.getJSONArray("messages")
                            obj.optJSONArray("data") != null -> obj.getJSONArray("data")
                            else -> JSONArray().put(obj)
                        }
                    }
                    else -> JSONArray()
                }

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val sender = obj.optString("raw_sender", obj.optString("sender", obj.optString("address", ""))).trim()
                    val body = obj.optString("raw_message", obj.optString("message", obj.optString("body", ""))).trim()
                    val timestamp = extractSmsTimestamp(obj, i, jsonArray.length())
                    val parsed = com.example.financemanager.domain.SmsParser.parse(sender, body) ?: continue
                    val smsHash = buildSmsHash(sender, body, timestamp)

                    if (repository.getSmsTransactionByHash(smsHash) != null) continue

                    repository.insertSmsTransaction(
                        SmsTransaction(
                            smsHash = smsHash,
                            sender = parsed.rawSender,
                            body = parsed.rawMessage,
                            accountName = parsed.accountName,
                            type = parsed.type,
                            amount = parsed.amount,
                            balance = parsed.balance,
                            counterparty = parsed.counterparty,
                            reference = parsed.reference,
                            rawTimestamp = timestamp
                        )
                    )
                }

                SyncWorker.enqueueNow(getApplication())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extractSmsTimestamp(obj: JSONObject, index: Int, totalCount: Int): Long {
        val directTimestamp = listOf("timestamp", "rawTimestamp", "date", "time")
            .firstNotNullOfOrNull { key ->
                when (val value = obj.opt(key)) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                }
            }

        if (directTimestamp != null && directTimestamp > 0) return directTimestamp
        return System.currentTimeMillis() - (totalCount - index) * 60_000L
    }

    private fun buildSmsHash(sender: String, body: String, timestamp: Long): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("$sender|$body|$timestamp".toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
