package com.example.financemanager.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financemanager.core.AppLock
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.core.PayCycleFrequency
import com.example.financemanager.core.ThemePreference
import com.example.financemanager.data.*
import com.example.financemanager.data.remote.SupabaseManager
import com.example.financemanager.domain.BalanceDrift
import com.example.financemanager.domain.FinanceRepository
import com.example.financemanager.services.NotificationHelper
import com.example.financemanager.services.PdfExportResult
import com.example.financemanager.services.SyncWorker
import com.example.financemanager.domain.MerchantKey
import com.example.financemanager.domain.Money
import com.example.financemanager.domain.NlpParser
import com.example.financemanager.domain.NlpResult
import com.example.financemanager.domain.RecurringScheduler
import kotlinx.coroutines.sync.withLock
import com.example.financemanager.domain.SmsLink
import com.example.financemanager.domain.SmsLinkDetector
import com.example.financemanager.domain.RESOLUTION_REVERSAL
import com.example.financemanager.domain.RESOLUTION_TRANSFER
import com.example.financemanager.domain.StockPriceService
import com.example.financemanager.domain.YahooFinanceStockPriceService
import com.example.financemanager.domain.GroupExpenseForSettlement
import com.example.financemanager.domain.GroupSettlement
import com.example.financemanager.domain.SettlementTransfer
import com.example.financemanager.ui.components.moneyString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * Per-holding aggregate derived from an [Investment] and its buy/sell/SIP transaction history.
 *
 * [gainLoss] is *unrealised* only — what the units still held would make if sold at today's price.
 * Profit already taken lives in [realizedGain] and is deliberately kept apart: folding the two
 * together, as the old average-price arithmetic did, made a holding that had been sold at a profit
 * and re-bought lower look like a loss.
 */
data class InvestmentHolding(
    val investment: Investment,
    val quantity: Double,
    val avgBuyPrice: Double,
    val investedAmount: Double,
    val currentValue: Double,
    val gainLoss: Double,
    val gainLossPercent: Double,
    /** Profit or loss on units already sold, priced against their cost at the moment of sale. */
    val realizedGain: Double,
    val hasSip: Boolean
) {
    /** Everything this holding has made, taken and untaken. */
    val totalGain: Double get() = gainLoss + realizedGain
}

/**
 * Walks one holding's trades in date order and works out what is still held, what it cost, and
 * what has already been made.
 *
 * Cost basis is path-dependent: a sale has to be priced against the average cost of the units held
 * *at that moment*, because that is the money those particular units consumed. The previous
 * calculation averaged every buy that had ever happened and multiplied by the current quantity,
 * which silently assumed no units had ever been sold — so after any partial sale both the invested
 * amount and the gain were wrong, and the realised profit was nowhere at all.
 */
internal fun computeHolding(
    investment: Investment,
    trades: List<InvestmentTransaction>
): InvestmentHolding {
    var quantity = 0.0
    var costBasis = 0.0
    var realized = 0.0

    trades.sortedWith(compareBy({ it.date }, { it.id })).forEach { trade ->
        if (trade.type == InvestmentTxnType.SELL) {
            // Selling units that aren't there is refused at entry; a historical row that slipped
            // through is capped here so the cost basis can't go negative.
            val sold = minOf(trade.quantity, quantity)
            val averageCost = if (quantity > 0.0) costBasis / quantity else 0.0
            realized += trade.amount - (sold * averageCost)
            quantity -= sold
            costBasis -= sold * averageCost
        } else {
            quantity += trade.quantity
            costBasis += trade.amount
        }
    }

    // Floating point leaves a whisper of quantity behind after a full exit; that isn't a holding.
    if (quantity <= 1e-9) {
        quantity = 0.0
        costBasis = 0.0
    }

    val currentValue = quantity * investment.currentPrice
    val unrealized = currentValue - costBasis
    return InvestmentHolding(
        investment = investment,
        quantity = quantity,
        avgBuyPrice = if (quantity > 0.0) costBasis / quantity else 0.0,
        investedAmount = costBasis,
        currentValue = currentValue,
        gainLoss = unrealized,
        gainLossPercent = if (costBasis > 0.0) (unrealized / costBasis) * 100 else 0.0,
        realizedGain = realized,
        // Either marker counts: the flag is what the log screen sets, the type is what an
        // installment is, and a row carrying one without the other is still a SIP.
        hasSip = trades.any { it.isSip || it.type == InvestmentTxnType.SIP_INSTALLMENT }
    )
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val stockPriceService: StockPriceService = YahooFinanceStockPriceService()
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
    val expenseGroups: StateFlow<List<ExpenseGroup>>
    val netWorthHistory: StateFlow<Map<String, Double>>

    // Investments (stocks / mutual funds / SIPs)
    val investments: StateFlow<List<Investment>>
    val investmentTransactions: StateFlow<List<InvestmentTransaction>>
    val portfolioHoldings: StateFlow<List<InvestmentHolding>>
    private val _isRefreshingPrices = MutableStateFlow(false)
    val isRefreshingPrices: StateFlow<Boolean> = _isRefreshingPrices.asStateFlow()

    // Spending for the current pay cycle (total + per category), in the home currency.
    val monthlyExpenseTotal: StateFlow<Double>
    val monthlyIncomeTotal: StateFlow<Double>
    val monthlyCategorySpend: StateFlow<Map<Long, Double>>

    /**
     * Money held and money spent that is *not* in the home currency, keyed by currency code.
     *
     * The app has one currency and adds amounts together freely, which is only arithmetic while
     * they all agree. A row carrying a different code — from a backup taken under another home
     * currency, say — is reported here rather than folded into a total where ₹100,000 and $1,000
     * would become 101,000 of nothing. Normally both are empty.
     */
    val foreignCurrencyBalances: StateFlow<Map<String, Double>>
    val foreignCurrencySpend: StateFlow<Map<String, Double>>

    /** Every account's balance, in the home currency only. See [foreignCurrencyBalances]. */
    val totalBalance: StateFlow<Double>

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

    val cashflowWarning: StateFlow<String?>

    // Privacy mode: masks balances across the app. FinancePreferences owns the value (moneyString
    // reads it directly); this flow just mirrors it for screens that drive a toggle off it.
    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    // App lock preferences. AppLock owns these; the flows just mirror it for the Settings screen.
    private val _isAppLockEnabled = MutableStateFlow(true)
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _hasSecurityPin = MutableStateFlow(false)
    val hasSecurityPin: StateFlow<Boolean> = _hasSecurityPin.asStateFlow()

    private val _appLockTimeoutMs = MutableStateFlow(0L)
    val appLockTimeoutMs: StateFlow<Long> = _appLockTimeoutMs.asStateFlow()

    // Calculator Keypad States
    private val _amountInput = MutableStateFlow("0")
    val amountInput: StateFlow<String> = _amountInput.asStateFlow()

    // Cloud sync (Supabase). Holds the anonymous user id once signed in, null otherwise.
    private val _cloudUserId = MutableStateFlow<String?>(null)
    val cloudUserId: StateFlow<String?> = _cloudUserId.asStateFlow()

    // Learned merchant → category rules, keyed by MerchantKey.normalize(merchant)
    val merchantRules: StateFlow<Map<String, MerchantRule>>

    // SMS Transactions
    val smsTransactions: StateFlow<List<SmsTransaction>>
    val pendingSmsTransactions: StateFlow<List<SmsTransaction>>
    val ignoredSmsTransactions: StateFlow<List<SmsTransaction>>
    val approvedSmsTransactions: StateFlow<List<SmsTransaction>>

    // Debit/credit pairs that cancel each other out — a self transfer between the user's own
    // accounts, or money that came back (refund, failed payment, IPO block released).
    val smsLinks: StateFlow<List<SmsLink>>
    private val _dismissedSmsLinkKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Summary of the last [importSmsXmlHistorical] run, or null once the caller has read it. */
    private val _historicalImportResult = MutableStateFlow<String?>(null)
    val historicalImportResult: StateFlow<String?> = _historicalImportResult.asStateFlow()

    /**
     * Why an SMS approval was refused, or null once the caller has read it.
     *
     * An approval that cannot go through has to say so. Returning quietly would leave the alert
     * sitting in the inbox with the dialog dismissed and nothing to explain why nothing happened.
     */
    private val _smsApprovalError = MutableStateFlow<String?>(null)
    val smsApprovalError: StateFlow<String?> = _smsApprovalError.asStateFlow()

    fun clearSmsApprovalError() {
        _smsApprovalError.value = null
    }

    /**
     * Why an investment action was refused, or null once the caller has read it.
     *
     * Overselling and mistyped quantities used to be dropped on the floor — the sheet closed and
     * nothing happened — or, worse, recorded as a negative holding.
     */
    private val _investmentError = MutableStateFlow<String?>(null)
    val investmentError: StateFlow<String?> = _investmentError.asStateFlow()

    fun clearInvestmentError() {
        _investmentError.value = null
    }

    init {
        FinancePreferences.init(application)
        val database = FinanceDatabase.getDatabase(application)
        repository = FinanceRepository(database.dao()) { FinancePreferences.currencyCodeFlow.value }

        accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        // Eagerly: existingMatchFor and other one-shot reads take `transactions.value` from screens
        // that never collect it (the SMS review screen among them), and a lazily-shared flow there
        // reads emptyList(), so a duplicate alert was never flagged. Every main screen needs this
        // list anyway; keeping the one shared subscription warm costs nothing extra.
        transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        recurringTransactions = repository.recurringTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        savingsGoals = repository.savingsGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        debts = repository.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        groupedDebts = debts.map { debtList ->
            debtList.groupBy { it.personName.trim().lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
        expenseGroups = repository.expenseGroups.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        investments = repository.investments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        investmentTransactions = repository.investmentTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        portfolioHoldings = combine(investments, investmentTransactions) { invs, txns ->
            val byInvestment = txns.groupBy { it.investmentId }
            invs.map { inv -> computeHolding(inv, byInvestment[inv.id].orEmpty()) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Eagerly, not WhileSubscribed: the approval screens read this through learnedRuleFor's
        // `.value` and never collect it, so a lazily-shared flow sat at emptyMap() and no learned
        // envelope was ever pre-filled. The table is one small row per merchant.
        merchantRules = repository.merchantRules
            .map { rules -> rules.associateBy { it.merchantKey } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        smsTransactions = repository.smsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        pendingSmsTransactions = repository.pendingSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        ignoredSmsTransactions = repository.ignoredSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        approvedSmsTransactions = repository.approvedSmsTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        _dismissedSmsLinkKeys.value = FinancePreferences.dismissedSmsLinks()
        smsLinks = combine(repository.smsTransactions, _dismissedSmsLinkKeys) { all, dismissed ->
            SmsLinkDetector.detect(all, dismissed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Restore persisted UI preferences
        AppLock.init(application)
        _isPrivacyMode.value = FinancePreferences.privacyMode
        _isAppLockEnabled.value = AppLock.isEnabled
        _hasSecurityPin.value = AppLock.hasPin
        _appLockTimeoutMs.value = AppLock.timeoutMs
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

        // Everything scoped to "this cycle" depends on three things, not one: the transactions,
        // and the two settings that decide where the cycle starts. Mapping over the transactions
        // alone meant changing payday frequency or anchor day left every budget number showing the
        // old cycle's totals until some unrelated transaction happened to nudge the flow.
        val payCycleScopedTransactions = combine(
            transactions,
            _payCycleFrequency,
            _payCycleAnchorDay
        ) { txList, _, _ ->
            val (start, end) = currentPayCycleRange()
            txList.filter { it.date in start..end }
        }.flowOn(Dispatchers.Default)

        monthlyExpenseTotal = payCycleScopedTransactions
            .map { cycle -> homeCurrencyTotal(cycle) { it.type == TransactionType.EXPENSE } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyIncomeTotal = payCycleScopedTransactions
            .map { cycle -> homeCurrencyTotal(cycle) { it.type == TransactionType.INCOME } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyCategorySpend = payCycleScopedTransactions
            .map { cycle ->
                cycle.filter { it.type == TransactionType.EXPENSE && isHomeCurrency(it.currency) }
                    .groupBy { it.categoryId }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        // Spending this cycle that is *not* in the home currency, per code. Adding it to the
        // totals above would be adding rupees to dollars; showing it separately is the honest
        // version, and an empty map (the normal case) costs the UI nothing.
        foreignCurrencySpend = payCycleScopedTransactions
            .map { cycle ->
                cycle.filter { it.type == TransactionType.EXPENSE && !isHomeCurrency(it.currency) }
                    .groupBy { it.currency }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        totalBalance = accounts
            .map { list -> list.filter { isHomeCurrency(it.currency) }.sumOf { it.balance } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        foreignCurrencyBalances = accounts
            .map { list ->
                list.filterNot { isHomeCurrency(it.currency) }
                    .groupBy { it.currency }
                    .mapValues { (_, accs) -> accs.sumOf { it.balance } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        streakDays = transactions
            .map { txList -> computeStreakDays(txList) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        sixMonthTrendData = transactions
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
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        // Privacy mode is an input rather than something moneyString picks up on its own: these
        // strings are built off the main thread and cached in a StateFlow, so a flag read inside
        // the builder would freeze at whatever it was when the sentence was first composed.
        cashflowWarning = combine(accounts, recurringTransactions, isPrivacyMode) { accs, recs, privacy ->
            val primary = accs.firstOrNull() ?: return@combine null
            val now = System.currentTimeMillis()
            val upcoming = recs.filter { !it.isPaused && it.isAutoLog && it.type == TransactionType.EXPENSE && (it.nextExecutionDate - now) in 0..(3 * 24 * 60 * 60 * 1000L) }
            for (rt in upcoming) {
                if (primary.balance < rt.amount) {
                    return@combine "Heads up! Your upcoming bill \"${rt.note.ifEmpty { "Subscription" }}\" is ${moneyString(rt.amount, privacy)}, but your ${primary.name} account only has ${moneyString(primary.balance, privacy)}."
                }
            }
            null
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

        safeToSpend = combine(
            accounts,
            categories,
            savingsGoals,
            recurringTransactions
        ) { accList, catList, goalsList, recList ->
            val totalBalance = accList.sumOf { it.balance }
            val remainingBudgets = catList.filter { it.budgetLimit > 0 }.sumOf { 
                // We'd need to subtract spent from budgetLimit ideally, but simpler: just sum budgets
                it.budgetLimit 
            }
            val remainingGoals = goalsList.sumOf { (it.targetAmount - it.savedAmount).coerceAtLeast(0.0) }
            val upcomingBills = recList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            
            // A rough guide, not a figure to bank on: it subtracts every envelope's *whole* limit
            // rather than what is left in it, counts a tenth of the distance to each goal, and
            // treats every recurring expense as still to come. The UI labels it an estimate for
            // exactly that reason.
            val safe = totalBalance - remainingBudgets - (remainingGoals * 0.1) - upcomingBills
            if (safe < 0) 0.0 else safe
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        unlockedBadges = combine(
            streakDays,
            savingsGoals,
            transactions
        ) { streak, goalsList, txList ->
            val badges = mutableListOf<String>()
            if (streak >= 7) badges.add("7-Day Streak")
            if (streak >= 30) badges.add("30-Day Streak")
            if (goalsList.sumOf { it.savedAmount } >= 10000) badges.add("10K Saved")
            if (txList.size >= 100) badges.add("100 Logged")
            badges
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        aiRecapText = combine(transactions, isPrivacyMode) { txList, privacy ->
            val now = System.currentTimeMillis()
            val oneWeekMs = 7L * 24 * 60 * 60 * 1000
            val thisWeekSpent = txList.filter { it.type == TransactionType.EXPENSE && it.date > now - oneWeekMs }.sumOf { it.amount }
            val lastWeekSpent = txList.filter { it.type == TransactionType.EXPENSE && it.date in (now - 2 * oneWeekMs)..(now - oneWeekMs) }.sumOf { it.amount }

            if (thisWeekSpent == 0.0 && lastWeekSpent == 0.0) null
            else if (thisWeekSpent < lastWeekSpent) "You spent ${moneyString(thisWeekSpent, privacy)} this week — that's less than last week! 🎉"
            else "You spent ${moneyString(thisWeekSpent, privacy)} this week. Watch your expenses to stay on track!"
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        netWorthHistory = combine(accounts, transactions, debts) { accountsList, txList, debtsList ->
            val now = Calendar.getInstance()
            now.set(Calendar.DAY_OF_MONTH, 1)
            now.add(Calendar.MONTH, -5)
            val sixMonthsAgo = now.timeInMillis

            val formatter = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            
            // Same figure the Net Worth headline shows, so the chart's last point matches it.
            val currentNetWorth = com.example.financemanager.domain.NetWorth.of(accountsList, debtsList).netWorth

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
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        // Check for missed recurring payments on app start
        viewModelScope.launch {
            repository.syncRecurringTransactions()
            checkUpcomingBills()
        }

        // Bring the stored currency codes in line with the home currency, once. Every amount has
        // always been displayed in that currency regardless of what the column said, so this makes
        // the data agree with the screens rather than changing what anything is worth — and it is
        // what lets the totals above add rows together honestly.
        viewModelScope.launch {
            val home = FinancePreferences.currencyCodeFlow.value
            if (FinancePreferences.currencyNormalizedFor() != home) {
                repository.normalizeCurrencies(home)
                FinancePreferences.setCurrencyNormalizedFor(home)
            }
        }

        // Establish an anonymous Supabase session for cloud sync
        viewModelScope.launch {
            _cloudUserId.value = SupabaseManager.ensureSignedIn()
        }
    }

    // --- Forecast, duplicates, receipts, widget ---

    /** Balance projected to the end of the pay cycle, and the envelopes that won't last it. */
    val forecast: StateFlow<com.example.financemanager.domain.CashForecast?> = combine(
        transactions, categories, recurringTransactions, totalBalance,
        combine(_payCycleFrequency, _payCycleAnchorDay) { f, a -> f to a }
    ) { txs, cats, recs, balance, _ ->
        val (start, end) = currentPayCycleRange()
        com.example.financemanager.domain.Forecaster.forecast(
            now = System.currentTimeMillis(),
            cycleStart = start,
            cycleEnd = end,
            currentBalance = balance,
            cycleTransactions = txs.filter { it.date in start..end && isHomeCurrency(it.currency) },
            categories = cats,
            recurring = recs,
            history = txs.filter { isHomeCurrency(it.currency) }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _dismissedDuplicates = MutableStateFlow(FinancePreferences.dismissedDuplicates())

    /** Likely double entries from the last 90 days — older ones have long since been reconciled. */
    val duplicatePairs: StateFlow<List<com.example.financemanager.domain.DuplicatePair>> =
        combine(transactions, _dismissedDuplicates) { txs, dismissed ->
            val since = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            com.example.financemanager.domain.DuplicateDetector.findPairs(txs.filter { it.date >= since }, dismissed)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissDuplicate(pair: com.example.financemanager.domain.DuplicatePair) {
        _dismissedDuplicates.value = FinancePreferences.dismissDuplicate(pair.key)
    }

    /** A hand-entered transaction this alert would duplicate, if there is one. */
    fun existingMatchFor(sms: SmsTransaction): Transaction? {
        val amount = com.example.financemanager.domain.SmsAmountFormatter.number(sms.amount).toDoubleOrNull() ?: return null
        return com.example.financemanager.domain.DuplicateDetector.findExisting(
            amount, isCredit = sms.type == "credit", date = sms.rawTimestamp, transactions = transactions.value
        )
    }

    private val receiptDao = FinanceDatabase.getDatabase(application).receiptDao()

    val transactionsWithReceipts: StateFlow<Set<Long>> = receiptDao.transactionIdsWithReceipts()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun receiptsFor(transactionId: Long): Flow<List<Receipt>> = receiptDao.receiptsFor(transactionId)

    fun attachReceipt(transactionId: Long, uri: Uri, fromCamera: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = com.example.financemanager.core.ReceiptStore.attach(getApplication(), transactionId, uri, deleteSource = fromCamera)
            onResult(ok)
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch { com.example.financemanager.core.ReceiptStore.delete(getApplication(), receipt) }
    }

    init {
        // Keep the home-screen widget in step with the data. Debounced so a bulk import redraws
        // it once instead of once per row.
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            // Debts and recurring rows feed the "Coming up" widget, so their changes count too.
            combine(
                listOf<kotlinx.coroutines.flow.Flow<Any?>>(
                    repository.transactions, repository.categories, repository.debts,
                    repository.recurringTransactions, _isPrivacyMode, _payCycleFrequency, _payCycleAnchorDay
                )
            ) { }
                .drop(1)
                .debounce(1500)
                .collect { com.example.financemanager.services.FinanceWidgetProvider.refresh(getApplication()) }
        }
        // Receipts whose transaction was deleted in an earlier session (and so is past its Undo).
        viewModelScope.launch {
            runCatching { com.example.financemanager.core.ReceiptStore.cleanUp(getApplication()) }
        }
        // When the SMS filter gets stricter, alerts it now rejects (shop credits, scams from phone
        // numbers) may already be sitting in Pending. Re-check them once per filter revision; they
        // move to Ignored, where they can still be restored.
        val prefs = getApplication<android.app.Application>()
            .getSharedPreferences("finance_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getInt(SMS_FILTER_REVISION_KEY, 0) < SMS_FILTER_REVISION) {
            recheckPendingSms { }
            prefs.edit().putInt(SMS_FILTER_REVISION_KEY, SMS_FILTER_REVISION).apply()
        }
        // Once: fill in payees the old Kotak pattern dropped (titles that read just "Kotak").
        if (prefs.getInt(SMS_PAYEE_BACKFILL_KEY, 0) < SMS_PAYEE_BACKFILL) {
            viewModelScope.launch(Dispatchers.IO) {
                val fixed = runCatching { repository.backfillSmsPayees() + repository.normalizeNegativeAdjustments() }
                    .onFailure { android.util.Log.e("FinanceViewModel", "Payee backfill failed", it) }
                    .getOrNull() ?: return@launch
                prefs.edit().putInt(SMS_PAYEE_BACKFILL_KEY, SMS_PAYEE_BACKFILL).apply()
                if (fixed > 0) SyncWorker.enqueueNow(getApplication())
            }
        }
    }

    private companion object {
        const val SMS_FILTER_REVISION_KEY = "sms_filter_revision"
        /** Bump when [com.example.financemanager.domain.SmsParser] starts rejecting more. */
        const val SMS_FILTER_REVISION = 2
        const val SMS_PAYEE_BACKFILL_KEY = "sms_payee_backfill"
        /**
         * Bump when a one-time data repair is added: a parser fix that recovers counterparties
         * existing alerts are missing (1), legacy negative balance adjustments (2). Every repair
         * in that pass is idempotent, so re-running the earlier ones is harmless.
         */
        const val SMS_PAYEE_BACKFILL = 2
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
        // The stored codes follow the setting, because the setting is what every amount is
        // displayed in. Nothing is converted — this is a relabelling, not an exchange.
        viewModelScope.launch {
            repository.normalizeCurrencies(code)
            FinancePreferences.setCurrencyNormalizedFor(code)
        }
    }

    /** Whether [code] is the currency the app is showing everything in. Blank counts as "yes". */
    private fun isHomeCurrency(code: String): Boolean =
        code.isBlank() || code.equals(_currencyCode.value, ignoreCase = true)

    /** Sums the matching transactions, skipping any that aren't in the home currency. */
    private fun homeCurrencyTotal(list: List<Transaction>, predicate: (Transaction) -> Boolean): Double =
        list.filter { predicate(it) && isHomeCurrency(it.currency) }.sumOf { it.amount }

    fun setPayCycle(frequency: PayCycleFrequency, anchorDay: Int) {
        FinancePreferences.setPayCycle(frequency, anchorDay)
    }

    fun consumeReceiptScanQuota(): Boolean = FinancePreferences.consumeReceiptScanQuota()

    fun remainingFreeScans(): Int = FinancePreferences.remainingFreeScans()

    // --- Security PIN Actions ---

    /** Stores a new unlock PIN. Hashing is slow by design, so it runs off the main thread. */
    fun setSecurityPin(pin: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { AppLock.setPin(pin) }
            _hasSecurityPin.value = AppLock.hasPin
            onDone()
        }
    }

    fun clearSecurityPin() {
        AppLock.clearPin()
        _hasSecurityPin.value = AppLock.hasPin
    }

    fun lockApp() = AppLock.lockNow()

    fun setAppLockTimeout(millis: Long) {
        AppLock.setTimeoutMs(millis)
        _appLockTimeoutMs.value = AppLock.timeoutMs
    }

    // --- Privacy Mode & App Lock Preferences ---
    fun togglePrivacyMode() {
        val enabled = !_isPrivacyMode.value
        FinancePreferences.setPrivacyMode(enabled)
        _isPrivacyMode.value = enabled
    }

    fun setAppLockEnabled(enabled: Boolean) {
        AppLock.setEnabled(enabled)
        _isAppLockEnabled.value = AppLock.isEnabled
    }

    // --- Date & Streak Helpers ---
    fun currentPayCycleRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> =
        com.example.financemanager.core.PayCycle.range(_payCycleFrequency.value, _payCycleAnchorDay.value, now)

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
            // The goal and the account move together or not at all — see
            // FinanceRepository.contributeToGoal.
            val updated = repository.contributeToGoal(goal, amount, fromAccountId)

            // Milestone notifications (50% and 100%). A goal with no target has no fractions to
            // cross — the division would be NaN and every comparison below quietly false.
            if (goal.targetAmount <= 0.0) return@launch
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
            // Re-inserting re-applies the balance effect that deleting it backed out. Guarded
            // because the insert is a REPLACE on the row's own id: a second undo of the same
            // transaction would overwrite the restored row and credit the account all over again.
            if (transaction.id != 0L && repository.getTransactionById(transaction.id) != null) return@launch
            repository.insertTransaction(transaction)
        }
    }

    /**
     * Saves an edited account. When the user corrects the balance by hand the opening balance
     * absorbs the difference, so the correction sticks instead of being undone by the next
     * reconciliation.
     */
    fun updateAccount(account: Account) {
        viewModelScope.launch {
            val existing = repository.getAccountById(account.id)
            val correction = if (existing != null) account.balance - existing.balance else 0.0
            repository.updateAccount(
                account.copy(openingBalance = account.openingBalance + correction)
            )
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(oldTransaction, newTransaction)
        }
    }

    /**
     * Splits part of an already-logged transaction off into a different envelope. Shrinks
     * [original] by [splitAmount] and inserts a new transaction for that amount under
     * [splitCategoryId], sharing a splitGroupId so both halves stay traceable to one another.
     */
    fun splitTransaction(original: Transaction, splitAmount: Double, splitCategoryId: Long) {
        if (splitAmount <= 0.0 || splitAmount >= original.amount) return
        viewModelScope.launch {
            val groupId = original.splitGroupId ?: java.util.UUID.randomUUID().toString()
            val remainder = original.copy(
                amount = original.amount - splitAmount,
                splitGroupId = groupId
            )
            repository.updateTransaction(original, remainder)
            repository.insertTransaction(
                original.copy(
                    id = 0,
                    amount = splitAmount,
                    categoryId = splitCategoryId,
                    splitGroupId = groupId
                )
            )
            checkBudgetThreshold(splitCategoryId, splitAmount)
        }
    }

    // --- Investments (stocks / mutual funds / SIPs) ---

    fun addInvestment(name: String, symbol: String?, exchange: String?, type: InvestmentType) {
        viewModelScope.launch {
            repository.insertInvestment(
                Investment(name = name, symbol = symbol, exchange = exchange, type = type)
            )
        }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch { repository.deleteInvestment(investment) }
    }

    /** Records a buy or SIP installment: adds a lot and debits the funding account via the ledger. */
    fun recordInvestmentBuy(
        investment: Investment,
        quantity: Double,
        pricePerUnit: Double,
        accountId: Long,
        date: Long,
        isSip: Boolean
    ) {
        if (quantity <= 0.0 || pricePerUnit <= 0.0) {
            _investmentError.value = "Enter a quantity and a price above zero."
            return
        }
        if (accountId <= 0L) {
            _investmentError.value = "Pick the account this money came from."
            return
        }
        viewModelScope.launch {
            val amount = Money.roundToMinorUnit(quantity * pricePerUnit)
            // The units and the cash they cost are written as one unit — see
            // FinanceRepository.recordInvestmentTrade.
            repository.recordInvestmentTrade(
                trade = InvestmentTransaction(
                    investmentId = investment.id,
                    type = if (isSip) InvestmentTxnType.SIP_INSTALLMENT else InvestmentTxnType.BUY,
                    quantity = quantity,
                    pricePerUnit = pricePerUnit,
                    amount = amount,
                    date = date,
                    sourceAccountId = accountId,
                    isSip = isSip
                ),
                cashDelta = -amount,
                note = "Invested in ${investment.name}"
            )
        }
    }

    /**
     * Records a sell and credits the sale proceeds back to the account via the ledger.
     *
     * Selling more units than are held is refused rather than recorded: it would put the holding
     * into negative quantity, which the portfolio would then price as a negative investment and
     * credit the account for money that never arrived.
     */
    fun recordInvestmentSell(
        investment: Investment,
        quantity: Double,
        pricePerUnit: Double,
        accountId: Long,
        date: Long
    ) {
        if (quantity <= 0.0 || pricePerUnit <= 0.0) {
            _investmentError.value = "Enter a quantity and a price above zero."
            return
        }
        if (accountId <= 0L) {
            _investmentError.value = "Pick the account the proceeds land in."
            return
        }
        viewModelScope.launch {
            val held = repository.heldQuantityOf(investment.id)
            if (quantity > held + 1e-9) {
                _investmentError.value =
                    "You hold ${formatQuantity(held)} unit(s) of ${investment.name}; you can't sell " +
                        "${formatQuantity(quantity)}."
                return@launch
            }
            val amount = Money.roundToMinorUnit(quantity * pricePerUnit)
            repository.recordInvestmentTrade(
                trade = InvestmentTransaction(
                    investmentId = investment.id,
                    type = InvestmentTxnType.SELL,
                    quantity = quantity,
                    pricePerUnit = pricePerUnit,
                    amount = amount,
                    date = date,
                    sourceAccountId = accountId
                ),
                cashDelta = amount,
                note = "Sold ${investment.name}"
            )
        }
    }

    /**
     * Edits a logged trade and corrects the funding account for the difference.
     *
     * Editing used to rewrite the holding row alone, leaving the account still showing the cash
     * the original trade moved — so correcting a mistyped quantity produced a balance that was
     * permanently wrong with nothing to point at.
     */
    fun updateInvestmentTransaction(transaction: InvestmentTransaction) {
        viewModelScope.launch {
            val existing = repository.getInvestmentTransactionById(transaction.id)
            if (existing == null) {
                _investmentError.value = "That trade is no longer on record."
                return@launch
            }
            if (transaction.quantity <= 0.0 || transaction.pricePerUnit <= 0.0) {
                _investmentError.value = "Enter a quantity and a price above zero."
                return@launch
            }
            if (transaction.type == InvestmentTxnType.SELL) {
                val held = repository.heldQuantityOf(transaction.investmentId, excludingTradeId = transaction.id)
                if (transaction.quantity > held + 1e-9) {
                    _investmentError.value =
                        "Only ${formatQuantity(held)} unit(s) are held once this trade is set aside."
                    return@launch
                }
            }
            val investmentName = investments.value.firstOrNull { it.id == transaction.investmentId }?.name
                ?: "investment"
            val amount = Money.roundToMinorUnit(transaction.quantity * transaction.pricePerUnit)
            repository.replaceInvestmentTrade(
                old = existing,
                updated = transaction.copy(amount = amount),
                note = if (transaction.type == InvestmentTxnType.SELL) {
                    "Sold $investmentName"
                } else {
                    "Invested in $investmentName"
                }
            )
        }
    }

    /** Deletes a logged buy/sell/SIP row and reverses its ledger effect on the funding account. */
    fun deleteInvestmentTransaction(transaction: InvestmentTransaction) {
        viewModelScope.launch {
            repository.deleteInvestmentTrade(transaction, "Reversed investment log")
        }
    }

    /** Units without a trailing `.0` on whole numbers, for the messages above. */
    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')

    /** Refreshes cached prices for every holding that has a symbol. Failures keep the old price. */
    fun refreshAllPrices() {
        viewModelScope.launch {
            _isRefreshingPrices.value = true
            try {
                investments.value.forEach { inv ->
                    val symbol = inv.symbol ?: return@forEach
                    val price = stockPriceService.getPrice(symbol, inv.exchange)
                    if (price != null) {
                        repository.updateInvestment(
                            inv.copy(currentPrice = price, lastPriceUpdate = System.currentTimeMillis())
                        )
                    }
                }
            } finally {
                _isRefreshingPrices.value = false
            }
        }
    }

    fun updateInvestmentPriceManually(investment: Investment, price: Double) {
        if (price <= 0.0) return
        viewModelScope.launch {
            repository.updateInvestment(investment.copy(currentPrice = price, lastPriceUpdate = System.currentTimeMillis()))
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
        _amountInput.value = String.format(java.util.Locale.US, "%.2f", amount).removeSuffix(".00")
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
        // The screens validate too, but this is the last door before the ledger: a NaN or an
        // infinity written here would poison the account balance permanently.
        if (!amount.isFinite() || amount <= 0.0) return
        viewModelScope.launch {
            val roundedAmount = Money.roundToMinorUnit(amount)

            // 1. A repeating entry gets its template first, so the payment logged now can say it
            // is that template's occurrence. Unlinked, it read as everyday spending — the forecast
            // then projected a monthly EMI as if it were paid every day — and nothing tied it to
            // the schedule.
            val recurringId = if (isRecurring && recurringInterval != null) {
                // "Next billing date" defaults to today, which is the payment being logged right
                // now. Left as is, the template was due immediately and the worker logged the same
                // bill a second time on its next pass; step past the occurrence just paid.
                var nextExec = subscriptionDate ?: date
                while (nextExec <= date) {
                    val advanced = RecurringScheduler.getNextIntervalDate(nextExec, recurringInterval)
                    if (advanced <= nextExec) break
                    nextExec = advanced
                }
                repository.insertRecurringTransaction(
                    RecurringTransaction(
                        amount = roundedAmount,
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
            } else null

            // 2. The transaction itself. Balances (source, and destination for transfers) move
            // with the ledger insert.
            repository.insertTransaction(
                Transaction(
                    amount = roundedAmount,
                    type = type,
                    categoryId = categoryId,
                    sourceAccountId = accountId,
                    destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                    note = note,
                    date = date,
                    isRecurring = recurringId != null,
                    recurringId = recurringId
                )
            )

            // 3. Fire envelope budget alert if this expense crosses 80%/100%
            if (type == TransactionType.EXPENSE) {
                checkBudgetThreshold(categoryId, roundedAmount)
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

            // Each split debited the account as it was recorded. If the splits don't cover the
            // whole bill, the remainder still left the account, so post it as a transfer rather
            // than adjusting the balance behind the ledger's back.
            val unallocated = totalAmount - splits.sumOf { it.second }
            if (kotlin.math.abs(unallocated) > 0.005) {
                repository.postBalanceAdjustment(
                    accountId = accountId,
                    delta = -unallocated,
                    note = note.ifEmpty { "Unallocated split remainder" },
                    date = date
                )
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
            // Backs the balance effect out of the accounts the transaction actually touched. The
            // old code fell back to "the first account" when the source was gone, which credited
            // an unrelated account.
            repository.deleteTransaction(transaction)
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
            // 1. Log the user's own share as the expense
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
            // 3. The friend's share left the account too, so it needs a ledger entry — as a
            // transfer, which moves the balance without counting as the user's spending.
            if (friendShare > 0) {
                repository.postBalanceAdjustment(
                    accountId = rec.accountId,
                    delta = -friendShare,
                    note = "Lent to $friendName for ${rec.note.ifEmpty { "Subscription" }}",
                    date = now
                )
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

    /**
     * Deletes an envelope, moving anything filed under it to [reassignToCategoryId] — 0 leaves
     * those transactions uncategorised, which still keeps them out of the wrong envelope and in
     * the totals.
     */
    fun deleteCategory(category: Category, reassignToCategoryId: Long = 0L) {
        viewModelScope.launch {
            repository.deleteCategory(category, reassignToCategoryId)
            SyncWorker.enqueueNow(getApplication())
        }
    }

    /** How many transactions would be affected by deleting [categoryId]. */
    fun countTransactionsInCategory(categoryId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(repository.countTransactionsInCategory(categoryId)) }
    }

    /** How many transactions reference [accountId] on either side. */
    fun countTransactionsForAccount(accountId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(repository.getTransactionsForAccount(accountId).size) }
    }

    /**
     * Deletes an account. With [moveToAccountId] the account is merged into that one, history and
     * balance included; without it the account's transactions are deleted too, unwinding each one
     * through the ledger so the far side of any transfer is made whole.
     */
    fun deleteAccount(account: Account, moveToAccountId: Long?) {
        viewModelScope.launch {
            if (moveToAccountId != null && moveToAccountId != account.id) {
                repository.mergeAndDeleteAccount(account, moveToAccountId)
            } else {
                repository.deleteAccountWithTransactions(account)
            }
            SyncWorker.enqueueNow(getApplication())
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
                // A brand-new account has no transactions, so its opening balance is its balance.
                Account(
                    name = name,
                    type = type,
                    balance = initialBalance,
                    openingBalance = initialBalance
                )
            )
        }
    }

    /**
     * Recomputes every account balance from its transactions and repairs any that had drifted.
     * Reports what changed so the user can see whether anything was actually wrong.
     */
    fun reconcileBalances(onDone: (List<BalanceDrift>) -> Unit) {
        viewModelScope.launch {
            val drifts = repository.reconcileBalances()
            if (drifts.isNotEmpty()) SyncWorker.enqueueNow(getApplication())
            onDone(drifts)
        }
    }

    // --- NLP Phrase parsing trigger ---
    fun parseNlpPhrase(phrase: String): NlpResult {
        return NlpParser.parse(phrase)
    }

    // --- CSV Exporter Helper ---
    /**
     * Writes every transaction to the shared Downloads folder. Suspending because it reads the
     * database rather than a cached StateFlow, which could be empty before a screen has
     * subscribed to it.
     */
    suspend fun exportTransactionsToCsv(context: Context): String? {
        val list = repository.getTransactionsOnce()
        if (list.isEmpty()) return null

        return try {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(directory, "transactions_export.csv")
            FileWriter(file).use { it.write(transactionCsv(list)) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun exportTransactionsToUri(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(transactionCsv(repository.getTransactionsOnce()))
                } ?: error("Unable to open selected file")
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Unable to export CSV") }
            }
        }
    }

    private suspend fun transactionCsv(list: List<Transaction>): String {
        val categoryById = repository.getCategoriesOnce().associateBy { it.id }
        val accountById = repository.getAccountsOnce().associateBy { it.id }
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

        return buildString {
                append("Date,Merchant,Category,Account,Amount,Note,Type,CategoryId,AccountId,Timestamp\r\n")
                list.sortedByDescending { it.date }.forEach { transaction ->
                append(csv(dateFormat.format(java.util.Date(transaction.date))))
                append(',').append(csv(transaction.merchantName.orEmpty()))
                append(',').append(csv(categoryById[transaction.categoryId]?.name.orEmpty()))
                append(',').append(csv(accountById[transaction.sourceAccountId]?.name.orEmpty()))
                append(',').append(transaction.amount)
                append(',').append(csv(transaction.note))
                append(',').append(transaction.type.name)
                append(',').append(transaction.categoryId)
                append(',').append(transaction.sourceAccountId)
                append(',').append(transaction.date).append("\r\n")
            }
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
                        if (parts.size >= 6) {
                            val legacy = parts.size < 10
                            val dateText = parts[0].trim('"')
                            val date = if (legacy) {
                                runCatching { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(dateText)?.time }.getOrNull()
                            } else parts[9].trim('"').toLongOrNull()
                            val merchant = parts[1].trim('"').takeIf { it.isNotBlank() }
                            val categoryName = parts[2].trim('"')
                            val accountName = parts[3].trim('"')
                            val amount = parts[4].trim('"').toDoubleOrNull() ?: continue
                            val note = parts[5].trim('"')
                            val type = if (!legacy) runCatching { TransactionType.valueOf(parts[6].trim('"')) }.getOrDefault(TransactionType.EXPENSE) else TransactionType.EXPENSE
                            val categoryId = if (!legacy) parts[7].trim('"').toLongOrNull() ?: 0L else categories.value.firstOrNull { it.name.equals(categoryName, true) }?.id ?: 0L
                            val accountId = if (!legacy) parts[8].trim('"').toLongOrNull() ?: 0L else accounts.value.firstOrNull { it.name.equals(accountName, true) }?.id ?: 0L

                            repository.insertTransaction(
                                Transaction(
                                    amount = amount,
                                    type = type,
                                    categoryId = categoryId,
                                    sourceAccountId = accountId,
                                    note = note,
                                    date = date ?: System.currentTimeMillis(),
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
            // Duplicate-safe: the balance only moves when a row is actually inserted.
            repository.insertTransactionIfNotExists(transaction)
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Read the database, not the StateFlows. A `stateIn(WhileSubscribed)` flow that
                // nothing has collected yet answers `.value` with its initial empty list, so an
                // export taken before any screen had subscribed wrote an empty backup and
                // reported success.
                val accountsSnapshot = repository.getAccountsOnce()
                val categoriesSnapshot = repository.getCategoriesOnce()
                val transactionsSnapshot = repository.getTransactionsOnce()
                val recurringSnapshot = repository.getRecurringOnce()
                val goalsSnapshot = repository.getSavingsGoalsOnce()
                val debtsSnapshot = repository.getDebtsOnce()
                val investmentsSnapshot = repository.getInvestmentsOnce()
                val investmentTxnsSnapshot = repository.getInvestmentTransactionsOnce()
                val merchantRulesSnapshot = repository.getMerchantRulesOnce()
                val groupsSnapshot = repository.getExpenseGroupsOnce()
                val groupMembersSnapshot = repository.getExpenseGroupMembersOnce()
                val groupExpensesSnapshot = repository.getExpenseGroupExpensesOnce()

                val rootJson = JSONObject().apply {
                    // Version 2 carries the tables version 1 left out entirely: holdings,
                    // group expenses and learned merchant rules had no backup path at all. A
                    // version 1 file still restores; it simply has nothing to say about them.
                    put("version", 2)

                    // 1. Accounts
                    val accountsArray = JSONArray()
                    accountsSnapshot.forEach { account ->
                        accountsArray.put(JSONObject().apply {
                            put("id", account.id)
                            put("name", account.name)
                            put("type", account.type.name)
                            put("balance", account.balance)
                            put("currency", account.currency)
                            put("openingBalance", account.openingBalance)
                        })
                    }
                    put("accounts", accountsArray)

                    // 2. Categories (Envelopes)
                    val categoriesArray = JSONArray()
                    categoriesSnapshot.forEach { category ->
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
                    transactionsSnapshot.forEach { trans ->
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
                            put("isVerified", trans.isVerified)
                        })
                    }
                    put("transactions", transactionsArray)

                    // 4. Recurring Transactions
                    val recurringArray = JSONArray()
                    recurringSnapshot.forEach { rec ->
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
                    goalsSnapshot.forEach { goal ->
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
                    debtsSnapshot.forEach { debt ->
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
                            put("ledgerTransactionId", debt.ledgerTransactionId)
                        })
                    }
                    put("debts", debtsArray)

                    // 7. Investments and their buy/sell/SIP legs
                    val investmentsArray = JSONArray()
                    investmentsSnapshot.forEach { inv ->
                        investmentsArray.put(JSONObject().apply {
                            put("id", inv.id)
                            put("name", inv.name)
                            put("symbol", inv.symbol ?: JSONObject.NULL)
                            put("exchange", inv.exchange ?: JSONObject.NULL)
                            put("type", inv.type.name)
                            put("currentPrice", inv.currentPrice)
                            put("lastPriceUpdate", inv.lastPriceUpdate)
                            put("currency", inv.currency)
                            put("notes", inv.notes)
                        })
                    }
                    put("investments", investmentsArray)

                    val investmentTxnsArray = JSONArray()
                    investmentTxnsSnapshot.forEach { txn ->
                        investmentTxnsArray.put(JSONObject().apply {
                            put("id", txn.id)
                            put("investmentId", txn.investmentId)
                            put("type", txn.type.name)
                            put("quantity", txn.quantity)
                            put("pricePerUnit", txn.pricePerUnit)
                            put("amount", txn.amount)
                            put("date", txn.date)
                            put("sourceAccountId", txn.sourceAccountId)
                            put("isSip", txn.isSip)
                        })
                    }
                    put("investmentTransactions", investmentTxnsArray)

                    // 8. Learned merchant rules. Losing these means re-teaching every merchant.
                    val merchantRulesArray = JSONArray()
                    merchantRulesSnapshot.forEach { rule ->
                        merchantRulesArray.put(JSONObject().apply {
                            put("merchantKey", rule.merchantKey)
                            put("categoryId", rule.categoryId)
                            put("accountId", rule.accountId)
                            put("hitCount", rule.hitCount)
                            put("updatedAt", rule.updatedAt)
                        })
                    }
                    put("merchantRules", merchantRulesArray)

                    // SMS Rules from Settings. Not a table, but rules the user wrote by hand are
                    // exactly what a reinstall should not cost them.
                    put("smsRules", com.example.financemanager.core.SmsRuleStore.exportJson())
                    put("insightLayout", com.example.financemanager.core.InsightLayoutStore.exportJson())

                    // 9. Shared-expense groups, their members and their receipts
                    val groupsArray = JSONArray()
                    groupsSnapshot.forEach { group ->
                        groupsArray.put(JSONObject().apply {
                            put("id", group.id)
                            put("name", group.name)
                            put("currency", group.currency)
                            put("createdAt", group.createdAt)
                        })
                    }
                    put("expenseGroups", groupsArray)

                    val groupMembersArray = JSONArray()
                    groupMembersSnapshot.forEach { member ->
                        groupMembersArray.put(JSONObject().apply {
                            put("id", member.id)
                            put("groupId", member.groupId)
                            put("name", member.name)
                            put("upiId", member.upiId)
                            put("isCurrentUser", member.isCurrentUser)
                        })
                    }
                    put("expenseGroupMembers", groupMembersArray)

                    val groupExpensesArray = JSONArray()
                    groupExpensesSnapshot.forEach { expense ->
                        groupExpensesArray.put(JSONObject().apply {
                            put("id", expense.id)
                            put("groupId", expense.groupId)
                            put("description", expense.description)
                            put("amount", expense.amount)
                            put("paidByMemberId", expense.paidByMemberId)
                            put("participantMemberIds", expense.participantMemberIds)
                            put("date", expense.date)
                        })
                    }
                    put("expenseGroupExpenses", groupExpensesArray)
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
                            currency = obj.optString("currency", "INR"),
                            openingBalance = obj.optDouble("openingBalance", 0.0)
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
                            originalCurrency = origCurr,
                            isVerified = obj.optBoolean("isVerified", true)
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
                            notes = obj.optString("notes", ""),
                            ledgerTransactionId = obj.optLong("ledgerTransactionId", 0L)
                        ))
                    }
                }

                // Parse Investments and their legs. Absent from a version 1 file, in which
                // case these stay empty and the restore simply clears the tables.
                val investmentsList = mutableListOf<Investment>()
                rootJson.optJSONArray("investments")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        investmentsList.add(Investment(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            symbol = if (obj.isNull("symbol")) null else obj.getString("symbol"),
                            exchange = if (obj.isNull("exchange")) null else obj.getString("exchange"),
                            type = InvestmentType.valueOf(obj.getString("type")),
                            currentPrice = obj.optDouble("currentPrice", 0.0),
                            lastPriceUpdate = obj.optLong("lastPriceUpdate", 0),
                            currency = obj.optString("currency", "INR"),
                            notes = obj.optString("notes", "")
                        ))
                    }
                }

                val investmentTxnsList = mutableListOf<InvestmentTransaction>()
                rootJson.optJSONArray("investmentTransactions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        investmentTxnsList.add(InvestmentTransaction(
                            id = obj.optLong("id", 0),
                            investmentId = obj.getLong("investmentId"),
                            type = InvestmentTxnType.valueOf(obj.getString("type")),
                            quantity = obj.getDouble("quantity"),
                            pricePerUnit = obj.getDouble("pricePerUnit"),
                            amount = obj.getDouble("amount"),
                            date = obj.getLong("date"),
                            sourceAccountId = obj.getLong("sourceAccountId"),
                            isSip = obj.optBoolean("isSip", false)
                        ))
                    }
                }

                val merchantRulesList = mutableListOf<MerchantRule>()
                rootJson.optJSONArray("merchantRules")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        merchantRulesList.add(MerchantRule(
                            merchantKey = obj.getString("merchantKey"),
                            categoryId = obj.getLong("categoryId"),
                            accountId = obj.optLong("accountId", 0),
                            hitCount = obj.optInt("hitCount", 1),
                            updatedAt = obj.optLong("updatedAt", 0)
                        ))
                    }
                }

                val groupsList = mutableListOf<ExpenseGroup>()
                rootJson.optJSONArray("expenseGroups")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        groupsList.add(ExpenseGroup(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            currency = obj.optString("currency", "INR"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                }

                val groupMembersList = mutableListOf<ExpenseGroupMember>()
                rootJson.optJSONArray("expenseGroupMembers")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        groupMembersList.add(ExpenseGroupMember(
                            id = obj.optLong("id", 0),
                            groupId = obj.getLong("groupId"),
                            name = obj.getString("name"),
                            upiId = obj.optString("upiId", ""),
                            isCurrentUser = obj.optBoolean("isCurrentUser", false)
                        ))
                    }
                }

                val groupExpensesList = mutableListOf<ExpenseGroupExpense>()
                rootJson.optJSONArray("expenseGroupExpenses")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        groupExpensesList.add(ExpenseGroupExpense(
                            id = obj.optLong("id", 0),
                            groupId = obj.getLong("groupId"),
                            description = obj.optString("description", ""),
                            amount = obj.getDouble("amount"),
                            paidByMemberId = obj.getLong("paidByMemberId"),
                            participantMemberIds = obj.optString("participantMemberIds", ""),
                            date = obj.optLong("date", System.currentTimeMillis())
                        ))
                    }
                }

                // Receipts aren't in the backup; note which rows they belong to so a restore that
                // reuses those ids for other transactions doesn't leave photos on the wrong rows.
                val receiptFingerprints = com.example.financemanager.core.ReceiptStore.fingerprints(getApplication())

                // Wipe existing database and restore backup
                repository.replaceBackupData(
                    accountsList, categoriesList, transactionsList, recurringList, goalsList,
                    debtsList, investmentsList, investmentTxnsList, merchantRulesList,
                    groupsList, groupMembersList, groupExpensesList
                )

                // Insert all elements back. Transactions bypass the ledger here — the accounts
                // above were restored with their closing balances already baked in, so replaying
                // each transaction would apply every movement twice.

                // Older backups have no opening balances, so derive them from what was restored.
                repository.rebaseOpeningBalances()
                com.example.financemanager.core.ReceiptStore.dropMismatched(getApplication(), receiptFingerprints)

                // Only once the database restore has gone through: applied earlier, a backup that
                // failed half-way still replaced the rules and layout while the data stayed put.
                rootJson.optJSONObject("smsRules")?.let {
                    com.example.financemanager.core.SmsRuleStore.importJson(getApplication(), it)
                }
                rootJson.optJSONObject("insightLayout")?.let {
                    com.example.financemanager.core.InsightLayoutStore.importJson(getApplication(), it)
                }

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
    /**
     * Writes the month's report to the shared Downloads folder, unless privacy mode is on.
     *
     * The report is the one place the app deliberately prints unmasked amounts — a masked report
     * is not a report. But it puts every real number into a file any app with storage access can
     * read, which is the opposite of what someone who just turned privacy mode on is asking for,
     * so the export waits for them to turn it off rather than quietly opting out of it.
     */
    suspend fun exportMonthlyPdf(context: Context, monthName: String): PdfExportResult {
        if (FinancePreferences.privacyMode) return PdfExportResult.BlockedByPrivacyMode
        // Straight from the database: a report built off an uncollected StateFlow would be a
        // page of zeroes presented as this month's finances.
        val path = com.example.financemanager.services.PdfGenerator.generateMonthlyReport(
            context,
            repository.getTransactionsOnce(),
            repository.getAccountsOnce(),
            repository.getCategoriesOnce(),
            monthName
        )
        return if (path != null) PdfExportResult.Saved(path) else PdfExportResult.Failed
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

    /** What deleting [debt] would give back, for the confirmation dialog. See [FinanceRepository.refundOnDelete]. */
    suspend fun refundOnDelete(debt: Debt): Transaction? = repository.refundOnDelete(debt)

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }

    // --- Group expenses ---
    fun expenseGroupMembers(groupId: Long): Flow<List<ExpenseGroupMember>> =
        repository.expenseGroupMembers(groupId)

    fun expenseGroupExpenses(groupId: Long): Flow<List<ExpenseGroupExpense>> =
        repository.expenseGroupExpenses(groupId)

    fun createExpenseGroup(name: String, currentUserName: String, upiId: String) {
        viewModelScope.launch {
            val groupId = repository.insertExpenseGroup(ExpenseGroup(name = name.trim()))
            repository.insertExpenseGroupMember(
                ExpenseGroupMember(
                    groupId = groupId,
                    name = currentUserName.trim(),
                    upiId = upiId.trim(),
                    isCurrentUser = true
                )
            )
        }
    }

    fun addExpenseGroupMember(groupId: Long, name: String, upiId: String) {
        viewModelScope.launch {
            repository.insertExpenseGroupMember(
                ExpenseGroupMember(groupId = groupId, name = name.trim(), upiId = upiId.trim())
            )
        }
    }

    fun addExpenseGroupExpense(
        groupId: Long,
        description: String,
        amount: Double,
        paidByMemberId: Long,
        participantMemberIds: List<Long>
    ) {
        if (amount <= 0.0 || participantMemberIds.isEmpty()) return
        viewModelScope.launch {
            repository.insertExpenseGroupExpense(
                ExpenseGroupExpense(
                    groupId = groupId,
                    description = description.trim(),
                    amount = amount,
                    paidByMemberId = paidByMemberId,
                    participantMemberIds = participantMemberIds.distinct().joinToString(",")
                )
            )
        }
    }

    fun deleteExpenseGroupExpense(expense: ExpenseGroupExpense) {
        viewModelScope.launch { repository.deleteExpenseGroupExpense(expense) }
    }

    fun deleteExpenseGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            expenseGroupMembers(group.id).first().forEach { repository.deleteExpenseGroupMember(it) }
            expenseGroupExpenses(group.id).first().forEach { repository.deleteExpenseGroupExpense(it) }
            repository.deleteExpenseGroup(group)
        }
    }

    fun calculateGroupSettlements(
        members: List<ExpenseGroupMember>,
        expenses: List<ExpenseGroupExpense>
    ): List<SettlementTransfer> = GroupSettlement.calculate(
        members = members.map { it.id },
        expenses = expenses.map {
            GroupExpenseForSettlement(
                amount = it.amount,
                paidByMemberId = it.paidByMemberId,
                participantIds = it.participantMemberIds.split(",").mapNotNull(String::toLongOrNull)
            )
        }
    )

    /**
     * Logs a manual payment that is really a loan: someone asked for money, the user paid it, and
     * the account drops while an IOU records that it is owed back. [type] LENT takes the money out
     * of [accountId]; BORROWED puts it in.
     */
    fun addIouTransaction(
        amount: Double,
        type: DebtType,
        accountId: Long,
        personName: String,
        note: String,
        date: Long,
        dueDate: Long?
    ) {
        val cleanName = personName.trim()
        if (amount <= 0.0 || accountId <= 0L || cleanName.isEmpty()) return
        viewModelScope.launch {
            repository.openIou(
                Debt(
                    personName = cleanName,
                    amount = Money.roundToMinorUnit(amount),
                    type = type,
                    date = date,
                    dueDate = dueDate,
                    notes = note.trim()
                ),
                accountId = accountId
            )
            SyncWorker.enqueueNow(getApplication())
        }
    }

    fun recordDebtPayment(debt: Debt, paymentAmount: Double, account: Account) {
        viewModelScope.launch {
            applyDebtPayment(debt, paymentAmount, account, System.currentTimeMillis())
        }
    }

    /**
     * Records a repayment against [debt], moves [account]'s balance with it, and returns the id of
     * the ledger row so a caller that may have to undo the whole approval can hold on to it.
     *
     * The movement is a balance adjustment rather than an income or expense. Repaying a loan you
     * took is not spending and being repaid on one you made is not earning — money that was only
     * ever passing through was landing in the envelope and income totals, which is the same reason
     * [createIouFromSms] records the principal this way.
     */
    private suspend fun applyDebtPayment(
        debt: Debt,
        paymentAmount: Double,
        account: Account,
        date: Long
    ): Long {
        // Money in when they repay a loan you made, money out when you repay one you took. The
        // debt row and the balance are written as one unit, so a process kill can no longer leave
        // a repayment recorded that no account ever paid.
        val loggedId = repository.applyDebtPayment(
            debt = debt,
            paymentAmount = paymentAmount,
            accountId = account.id,
            delta = if (debt.type == DebtType.LENT) paymentAmount else -paymentAmount,
            date = date
        )
        SyncWorker.enqueueNow(getApplication())
        return loggedId
    }

    override fun onCleared() {
        super.onCleared()
        // The price service owns an HTTP client with its own connection and thread pools.
        stockPriceService.close()
    }

    // --- Tags ---
    fun extractTags(note: String): List<String> {
        val regex = Regex("#\\w+")
        return regex.findAll(note).map { it.value }.toList()
    }

    // --- SMS Transactions ---

    /**
     * The category and account this merchant was filed under last time, or null if it's new.
     * Used to pre-fill the approval sheet so repeat merchants need no input at all.
     */
    fun learnedRuleFor(merchant: String): MerchantRule? {
        val rules = merchantRules.value
        val key = MerchantKey.bestMatch(MerchantKey.normalize(merchant), rules.keys) ?: return null
        return rules[key]
    }

    fun forgetMerchantRule(merchantKey: String) {
        viewModelScope.launch { repository.deleteMerchantRule(merchantKey) }
    }

    /**
     * Undoes whatever an alert was previously filed as, so re-filing it can't leave the old
     * transaction behind or strand the alert it was paired with.
     *
     * Every approval path runs this first. Only the transfer path used to, which meant re-filing
     * an alert any other way — or restoring one that had been approved and then ignored — logged
     * the amount a second time and orphaned the first row.
     */
    private suspend fun clearPreviousFiling(smsTransaction: SmsTransaction) {
        repository.revokeLoggedTransaction(smsTransaction)
        repository.detachSmsPartner(smsTransaction)
    }

    fun approveSmsTransaction(
        smsTransaction: SmsTransaction,
        categoryId: Long,
        accountId: Long,
        note: String = "",
        txTypeOverride: TransactionType? = null
    ) {
        val amount = com.example.financemanager.domain.SmsAmountFormatter.number(smsTransaction.amount)
            .toDoubleOrNull() ?: 0.0
        // An alert whose amount didn't parse has nothing to log. Approving it anyway wrote a zero
        // transaction, marked the alert resolved and taught a merchant rule off it — all three
        // silently. The sibling approval paths already refused this; this one was the gap.
        if (amount <= 0.0 || accountId <= 0L) {
            _smsApprovalError.value = if (amount <= 0.0) {
                "Couldn't read an amount from this alert. Log it by hand instead."
            } else {
                "Pick the account this money moved through."
            }
            return
        }
        viewModelScope.launch { smsApprovalLock.withLock {
            // Re-read the row under the lock. Two approvals of the same alert (a swipe and a tap
            // during its exit animation) each held a copy saying "not yet logged", so both
            // inserted a transaction and moved the balance twice, and Undo could only find the
            // second. Serialised, the second one now sees the first one's filing and stops —
            // unless its copy *is* that filing, which is a deliberate re-file and still allowed.
            val fresh = repository.getSmsTransactionById(smsTransaction.id) ?: return@withLock
            if (fresh.isApproved && fresh.loggedTransactionId != 0L && fresh.loggedTransactionId != smsTransaction.loggedTransactionId) {
                return@withLock
            }
            clearPreviousFiling(fresh)
            val txType = txTypeOverride ?: if (smsTransaction.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE

            val finalNote = note.ifEmpty {
                smsTransaction.counterparty.ifEmpty { smsTransaction.accountName }
            }

            val loggedId = repository.insertTransaction(
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

            // Balance moved by the ledger insert above.

            repository.updateSmsTransaction(
                fresh.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = categoryId,
                    approvedAccountId = accountId,
                    loggedTransactionId = loggedId,
                    linkedSmsId = 0,
                    resolution = ""
                )
            )

            // Learn the filing decision so the next alert from this merchant arrives categorised.
            repository.rememberMerchantCategory(smsTransaction.counterparty, categoryId, accountId)

            SyncWorker.enqueueNow(getApplication())
        } }
    }

    /** Serialises [approveSmsTransaction] so two approvals of one alert can't both log it. */
    private val smsApprovalLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Creates an IOU from an SMS approval. The SMS still needs to affect the account balance,
     * but the movement is recorded as a transfer-side ledger row so it does not inflate an
     * envelope's spending or income totals.
     *
     * A debit means the user lent money (the person owes them); a credit means the user received
     * borrowed money (the user owes the person).
     */
    fun createIouFromSms(
        smsTransaction: SmsTransaction,
        accountId: Long,
        personName: String,
        amount: Double,
        notes: String = ""
    ) {
        if (accountId <= 0 || personName.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            clearPreviousFiling(smsTransaction)
            val type = if (smsTransaction.type == "credit") DebtType.BORROWED else DebtType.LENT
            val cleanName = personName.trim()
            val cleanNotes = notes.trim().ifEmpty { "Created from bank SMS" }
            repository.insertDebt(
                Debt(
                    personName = cleanName,
                    amount = amount,
                    type = type,
                    date = smsTransaction.rawTimestamp,
                    notes = cleanNotes
                )
            )
            // Borrowing puts money into the account; lending takes it out.
            val loggedId = repository.postBalanceAdjustment(
                accountId = accountId,
                delta = if (type == DebtType.BORROWED) amount else -amount,
                note = "IOU with $cleanName",
                date = smsTransaction.rawTimestamp
            )
            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = accountId,
                    loggedTransactionId = loggedId
                )
            )
            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Logs a single alert as a transfer between the user's own accounts.
     *
     * The matched-pair flow needs both legs, but plenty of self transfers only ever produce one
     * alert — the receiving bank stays quiet, or its SMS never arrives — so the debit sits in the
     * inbox with nothing to pair it with and approving it counts the move as spending. Here the
     * user names the other side themselves and the lone alert becomes exactly the TRANSFER row a
     * matched pair would have produced: no envelope, no effect on net worth.
     *
     * [smsTransaction] is whichever leg the bank did send, so a credit alert names the
     * destination and a debit alert the source; the account the user picked is the silent side.
     * Anything this alert was already logged as is revoked first, so re-filing an expense as a
     * transfer doesn't leave the spending behind.
     */
    fun approveSmsAsTransfer(
        smsTransaction: SmsTransaction,
        fromAccountId: Long,
        toAccountId: Long,
        note: String = ""
    ) {
        if (fromAccountId <= 0 || toAccountId <= 0 || fromAccountId == toAccountId) return
        val amount = com.example.financemanager.domain.SmsAmountFormatter.number(smsTransaction.amount)
            .toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) return
        viewModelScope.launch {
            clearPreviousFiling(smsTransaction)

            val fromName = accounts.value.find { it.id == fromAccountId }?.name ?: "account"
            val toName = accounts.value.find { it.id == toAccountId }?.name ?: "account"
            val loggedId = repository.insertTransaction(
                Transaction(
                    amount = amount,
                    type = TransactionType.TRANSFER,
                    categoryId = 0L,
                    sourceAccountId = fromAccountId,
                    destinationAccountId = toAccountId,
                    note = note.ifEmpty { "Self transfer: $fromName to $toName" },
                    date = smsTransaction.rawTimestamp,
                    isAutoLogged = true,
                    isVerified = true
                )
            )

            // The alert is filed against its own side of the move — the side the bank actually
            // told us about. No merchant rule is learned: a transfer has no envelope to remember.
            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = if (smsTransaction.type == "credit") toAccountId else fromAccountId,
                    loggedTransactionId = loggedId,
                    linkedSmsId = 0,
                    resolution = RESOLUTION_TRANSFER
                )
            )

            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Logs a matched pair as one transfer between the user's own accounts.
     *
     * The two alerts describe a single movement, so they become a single TRANSFER row: money
     * leaves [fromAccountId] and lands in [toAccountId], with neither side touching an envelope.
     * Anything either leg was already logged as is revoked first, so approving the debit as an
     * expense before the credit arrived doesn't leave the spending behind.
     */
    fun resolveSmsLinkAsTransfer(
        link: SmsLink,
        fromAccountId: Long,
        toAccountId: Long,
        note: String = ""
    ) {
        if (fromAccountId <= 0 || toAccountId <= 0 || fromAccountId == toAccountId) return
        viewModelScope.launch {
            clearPreviousFiling(link.debit)
            clearPreviousFiling(link.credit)

            val fromName = accounts.value.find { it.id == fromAccountId }?.name ?: "account"
            val toName = accounts.value.find { it.id == toAccountId }?.name ?: "account"
            val loggedId = repository.insertTransaction(
                Transaction(
                    amount = link.amount,
                    type = TransactionType.TRANSFER,
                    categoryId = 0L,
                    sourceAccountId = fromAccountId,
                    destinationAccountId = toAccountId,
                    note = note.ifEmpty { "Self transfer: $fromName to $toName" },
                    date = link.debit.rawTimestamp,
                    isAutoLogged = true,
                    isVerified = true
                )
            )

            repository.updateSmsTransaction(
                link.debit.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = fromAccountId,
                    loggedTransactionId = loggedId,
                    linkedSmsId = link.credit.id,
                    resolution = RESOLUTION_TRANSFER
                )
            )
            // The credit leg carries no transaction of its own — the transfer above already
            // credits the destination account.
            repository.updateSmsTransaction(
                link.credit.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = toAccountId,
                    loggedTransactionId = 0L,
                    linkedSmsId = link.debit.id,
                    resolution = RESOLUTION_TRANSFER
                )
            )

            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Settles a matched pair as money that came back — a refund, a failed payment, or an IPO
     * block released because nothing was allotted.
     *
     * Nothing is logged at all: the account ended up exactly where it started, so a pair of
     * offsetting rows would only add noise, and any expense already recorded for the debit leg is
     * removed. That is what stops an unallotted IPO from sitting in the spending total forever.
     */
    fun resolveSmsLinkAsReversal(link: SmsLink) {
        viewModelScope.launch {
            clearPreviousFiling(link.debit)
            clearPreviousFiling(link.credit)

            repository.updateSmsTransaction(
                link.debit.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    loggedTransactionId = 0L,
                    linkedSmsId = link.credit.id,
                    resolution = RESOLUTION_REVERSAL
                )
            )
            repository.updateSmsTransaction(
                link.credit.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    loggedTransactionId = 0L,
                    linkedSmsId = link.debit.id,
                    resolution = RESOLUTION_REVERSAL
                )
            )

            SyncWorker.enqueueNow(getApplication())
        }
    }

    /** Hides a suggested pair for good — the two alerts are unrelated. */
    fun dismissSmsLink(link: SmsLink) {
        _dismissedSmsLinkKeys.value = FinancePreferences.dismissSmsLink(link.key)
    }

    /** Brings every dismissed suggestion back, for when one was waved away by mistake. */
    fun restoreDismissedSmsLinks() {
        _dismissedSmsLinkKeys.value.forEach { FinancePreferences.restoreSmsLink(it) }
        _dismissedSmsLinkKeys.value = FinancePreferences.dismissedSmsLinks()
    }

    fun approveSmsAsDebtPayment(
        smsTransaction: SmsTransaction,
        debt: Debt,
        paymentAmount: Double,
        account: Account
    ) {
        viewModelScope.launch {
            // Dated and linked like every other approval: the payment carries the alert's own
            // timestamp, and the row it created is remembered so undoing the approval can find it.
            // Stamping it with "now" instead left nothing that [revokeLoggedTransaction] could
            // match, so the payment survived the undo.
            clearPreviousFiling(smsTransaction)
            val loggedId = applyDebtPayment(debt, paymentAmount, account, smsTransaction.rawTimestamp)

            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isApproved = true,
                    isIgnored = false,
                    approvedCategoryId = 0L,
                    approvedAccountId = account.id,
                    loggedTransactionId = loggedId,
                    linkedSmsId = 0,
                    resolution = ""
                )
            )
            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Re-reads every pending alert with the current parser, after the user changes their SMS
     * rules. Only pending ones: an approved alert already became a transaction, and changing its
     * fields under it would leave the two disagreeing.
     *
     * An alert the new rules no longer recognise is left alone rather than deleted — a rule
     * being edited is often half-written, and the alert is still real. One an Ignore rule now
     * matches is moved to Ignored, where it can be restored.
     */
    fun recheckPendingSms(onDone: (String) -> Unit) {
        viewModelScope.launch {
            var updated = 0
            var ignored = 0
            val pending = repository.getSmsTransactionsList()
                // Rows from the notification listener carry a package name as sender and the
                // notification text as body. Re-read as SMS they match no bank and get moved to
                // Ignored, so they are left to the parser that produced them.
                .filter {
                    !it.isApproved && !it.isIgnored && it.body.isNotBlank() &&
                        it.sender !in com.example.financemanager.domain.NotificationTransactionParser.TRUSTED_PACKAGES
                }
            for (sms in pending) {
                val result = com.example.financemanager.domain.SmsParser.parseDetailed(sms.sender, sms.body)
                val parsed = result.parsed
                if (parsed == null) {
                    val source = result.source
                    val rule = (source as? com.example.financemanager.domain.SmsParseSource.Custom)?.rule
                    // Marketing and shop/app "credits" are positively identified as not being bank
                    // alerts, unlike a merely unrecognised message, so they are filed away too.
                    if (rule?.action == com.example.financemanager.domain.SmsRuleAction.IGNORE ||
                        source == com.example.financemanager.domain.SmsParseSource.PromoFiltered ||
                        source == com.example.financemanager.domain.SmsParseSource.NotFromBank
                    ) {
                        repository.updateSmsTransaction(sms.copy(isIgnored = true))
                        ignored++
                    }
                    continue
                }
                val refreshed = sms.copy(
                    accountName = parsed.accountName,
                    type = parsed.type,
                    amount = parsed.amount,
                    balance = parsed.balance,
                    counterparty = parsed.counterparty,
                    reference = parsed.reference
                )
                if (refreshed != sms) {
                    repository.updateSmsTransaction(refreshed)
                    updated++
                }
            }
            if (updated > 0 || ignored > 0) SyncWorker.enqueueNow(getApplication())
            onDone(
                when {
                    pending.isEmpty() -> "No pending alerts with message text to re-check"
                    updated == 0 && ignored == 0 -> "Checked ${pending.size} pending alert(s) — nothing changed"
                    else -> "Updated $updated, ignored $ignored of ${pending.size} pending alert(s)"
                }
            )
        }
    }

    fun ignoreSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            repository.updateSmsTransaction(smsTransaction.copy(isIgnored = true))
            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Brings an ignored alert back to the inbox as pending.
     *
     * An alert that was approved before it was ignored still owns the transaction that approval
     * created, so clearing [SmsTransaction.isApproved] on its own left the transaction in place
     * with nothing pointing at it — and the alert, now pending again, could be approved a second
     * time for the same money.
     */
    /**
     * Undo for the quick-review screen. Re-reads the alert first: the copy the screen holds was
     * taken before it was approved, so it doesn't know which transaction the approval created and
     * restoring from it would leave that transaction behind.
     */
    fun returnSmsToPending(smsId: Long) {
        viewModelScope.launch {
            val fresh = repository.getSmsTransactionById(smsId) ?: return@launch
            unignoreSmsTransaction(fresh)
        }
    }

    fun unignoreSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            clearPreviousFiling(smsTransaction)
            repository.updateSmsTransaction(
                smsTransaction.copy(
                    isIgnored = false,
                    isApproved = false,
                    loggedTransactionId = 0L,
                    linkedSmsId = 0L,
                    resolution = ""
                )
            )
            SyncWorker.enqueueNow(getApplication())
        }
    }

    /**
     * Discards a parsed SMS. This only removes the inbox row — it deliberately leaves balances
     * alone, because an unapproved SMS never moved one, and an approved one is represented by a
     * real transaction that has to be deleted on its own to be reverted.
     */
    fun deleteSmsTransaction(smsTransaction: SmsTransaction) {
        viewModelScope.launch {
            repository.deleteSmsTransaction(smsTransaction)
            SyncWorker.enqueueNow(getApplication())
        }
    }

    suspend fun getSmsTransactionsList(): List<SmsTransaction> = repository.getSmsTransactionsList()

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

    /**
     * Backfills transaction history from an SMS Backup & Restore XML export, for the period
     * before the user started using the app.
     *
     * Only messages older than the earliest transaction already on record are considered — the
     * cutoff is derived from the ledger itself, so anything the user has entered (manually or via
     * the normal SMS review flow) is never touched or duplicated. Matched accounts (by loose
     * name match, e.g. "Kotak" against an existing "Kotak Bank") get their transactions posted
     * without moving their current balance, since that already reflects today's reality; the
     * balance shift instead lands on [FinanceRepository.rebaseOpeningBalances] pushing the
     * opening balance further into the past. A brand new account created purely to hold this
     * history has no such "today" to protect, so its balance is left to accumulate normally.
     */
    fun importSmsXmlHistorical(context: Context, uri: Uri) {
        viewModelScope.launch {
            var imported = 0
            var duplicates = 0
            var accountsCreated = 0
            try {
                val cutoff = repository.getEarliestTransactionDate() ?: Long.MAX_VALUE
                val knownAccounts = repository.getAccountsOnce().toMutableList()
                val newlyCreatedAccountIds = mutableSetOf<Long>()

                suspend fun findOrCreateAccount(parsedName: String): Account {
                    val trimmed = parsedName.trim().ifEmpty { "Imported" }
                    knownAccounts.firstOrNull {
                        it.name.contains(trimmed, ignoreCase = true) || trimmed.contains(it.name, ignoreCase = true)
                    }?.let { return it }

                    val isWallet = Regex("pay|wallet|paytm", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
                    val newId = repository.insertAccount(
                        Account(
                            name = trimmed,
                            type = if (isWallet) AccountType.WALLET else AccountType.BANK,
                            balance = 0.0,
                            openingBalance = 0.0
                        )
                    )
                    val created = Account(id = newId, name = trimmed, type = if (isWallet) AccountType.WALLET else AccountType.BANK, balance = 0.0, openingBalance = 0.0)
                    knownAccounts.add(created)
                    newlyCreatedAccountIds.add(newId)
                    accountsCreated++
                    return created
                }

                // Read once: each imported alert looks its payee up against these.
                val importRuleKeys = repository.getMerchantRulesOnce().map { it.merchantKey }.toSet()

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(inputStream, null)
                    var eventType = parser.eventType
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "sms") {
                            val sender = parser.getAttributeValue(null, "address") ?: ""
                            val body = parser.getAttributeValue(null, "body") ?: ""
                            val timestamp = parser.getAttributeValue(null, "date")?.toLongOrNull()

                            if (timestamp != null && timestamp < cutoff && sender.isNotBlank() && body.isNotBlank()) {
                                val parsed = com.example.financemanager.domain.SmsParser.parse(sender, body)
                                val amountValue = parsed?.amount?.removePrefix("Rs.")?.replace(",", "")?.trim()?.toDoubleOrNull()

                                if (parsed != null && amountValue != null && amountValue > 0.0) {
                                    val smsHash = buildSmsHash(parsed.rawSender, parsed.rawMessage, timestamp)
                                    if (repository.getSmsTransactionByHash(smsHash) == null) {
                                        val account = findOrCreateAccount(parsed.accountName)
                                        val merchantKey = MerchantKey.bestMatch(
                                            MerchantKey.normalize(parsed.counterparty),
                                            importRuleKeys
                                        )
                                        val categoryId = merchantKey
                                            ?.let { repository.getMerchantRule(it) }
                                            ?.categoryId ?: 0L
                                        val txType = if (parsed.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE

                                        val transaction = Transaction(
                                            amount = amountValue,
                                            type = txType,
                                            categoryId = categoryId,
                                            sourceAccountId = account.id,
                                            note = parsed.counterparty.ifEmpty { parsed.accountName },
                                            date = timestamp,
                                            isAutoLogged = true,
                                            isVerified = true,
                                            merchantName = parsed.counterparty.ifEmpty { parsed.rawSender }
                                        )
                                        val loggedId = if (account.id in newlyCreatedAccountIds) {
                                            repository.insertTransaction(transaction)
                                        } else {
                                            repository.insertTransactionFromBackup(transaction)
                                        }

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
                                                rawTimestamp = timestamp,
                                                isApproved = true,
                                                approvedCategoryId = categoryId,
                                                approvedAccountId = account.id,
                                                loggedTransactionId = loggedId
                                            )
                                        )
                                        imported++
                                    } else {
                                        duplicates++
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }

                // Existing accounts kept their current balance pinned above; fold the historical
                // net effect into their opening balance instead so the ledger invariant holds.
                repository.rebaseOpeningBalances()

                _historicalImportResult.value =
                    "Imported $imported historical transaction(s)" +
                        (if (accountsCreated > 0) " into $accountsCreated new account(s)" else "") +
                        (if (duplicates > 0) ", skipped $duplicates already on record" else "") + "."

                SyncWorker.enqueueNow(getApplication())
            } catch (e: Exception) {
                e.printStackTrace()
                _historicalImportResult.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearHistoricalImportResult() {
        _historicalImportResult.value = null
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
        // Locale.ROOT: "%02x" with the default locale renders its digits in whatever numbering
        // system the device is set to, so the same message would hash differently after a locale
        // change and the dedupe index would stop recognising it.
        return hashBytes.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }
}
