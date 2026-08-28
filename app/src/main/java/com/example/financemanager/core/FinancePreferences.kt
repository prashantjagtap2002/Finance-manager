package com.example.financemanager.core

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val label: String
)

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class PayCycleFrequency {
    WEEKLY,
    BIWEEKLY,
    SEMIMONTHLY,
    MONTHLY
}

object FinancePreferences {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_CURRENCY_CODE = "currency_code"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_IS_PRO_USER = "is_pro_user"
    private const val KEY_SCAN_MONTH = "scan_month"
    private const val KEY_SCAN_COUNT = "scan_count"
    private const val KEY_PAY_CYCLE_FREQUENCY = "pay_cycle_frequency"
    private const val KEY_PAY_CYCLE_ANCHOR_DAY = "pay_cycle_anchor_day"
    private const val KEY_PRIVACY_MODE = "privacy_mode"
    private const val KEY_DISMISSED_SMS_LINKS = "dismissed_sms_links"
    private const val KEY_DASHBOARD_SECTIONS = "dashboard_sections"

    const val FREE_SCAN_LIMIT = 10

    val supportedCurrencies = listOf(
        CurrencyOption("INR", "\u20B9", "Indian Rupee"),
        CurrencyOption("USD", "$", "US Dollar"),
        CurrencyOption("EUR", "\u20AC", "Euro"),
        CurrencyOption("GBP", "\u00A3", "British Pound"),
        CurrencyOption("BRL", "R$", "Brazilian Real"),
        CurrencyOption("AED", "AED", "UAE Dirham")
    )

    private lateinit var appContext: Context

    private val _currencyCodeFlow = MutableStateFlow("INR")
    val currencyCodeFlow = _currencyCodeFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(ThemePreference.DARK)
    val themeModeFlow = _themeModeFlow.asStateFlow()

    private val _isProUserFlow = MutableStateFlow(false)
    val isProUserFlow = _isProUserFlow.asStateFlow()

    private val _scanCountFlow = MutableStateFlow(0)
    val scanCountFlow = _scanCountFlow.asStateFlow()

    private val _payCycleFrequencyFlow = MutableStateFlow(PayCycleFrequency.MONTHLY)
    val payCycleFrequencyFlow = _payCycleFrequencyFlow.asStateFlow()

    private val _payCycleAnchorDayFlow = MutableStateFlow(1)
    val payCycleAnchorDayFlow = _payCycleAnchorDayFlow.asStateFlow()

    /**
     * Privacy mode masks every amount the app renders. It is backed by Compose snapshot state
     * rather than a StateFlow because [com.example.financemanager.ui.components.moneyString]
     * reads it as a default argument: a snapshot read inside a composable subscribes that
     * composable, so every masked amount recomposes the instant the toggle flips — without each
     * screen having to thread the flag down to its leaves.
     */
    private val _privacyMode = mutableStateOf(false)
    val privacyMode: Boolean get() = _privacyMode.value

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        _currencyCodeFlow.value = prefs.getString(KEY_CURRENCY_CODE, "INR") ?: "INR"
        _themeModeFlow.value = prefs.getString(KEY_THEME_MODE, ThemePreference.DARK.name)
            ?.let { runCatching { ThemePreference.valueOf(it) }.getOrDefault(ThemePreference.DARK) }
            ?: ThemePreference.DARK
        _isProUserFlow.value = prefs.getBoolean(KEY_IS_PRO_USER, false)
        syncScanMonthIfNeeded()
        _scanCountFlow.value = prefs().getInt(KEY_SCAN_COUNT, 0)
        _payCycleFrequencyFlow.value = prefs.getString(KEY_PAY_CYCLE_FREQUENCY, PayCycleFrequency.MONTHLY.name)
            ?.let { runCatching { PayCycleFrequency.valueOf(it) }.getOrDefault(PayCycleFrequency.MONTHLY) }
            ?: PayCycleFrequency.MONTHLY
        _payCycleAnchorDayFlow.value = prefs.getInt(KEY_PAY_CYCLE_ANCHOR_DAY, 1).coerceAtLeast(1)
        _privacyMode.value = prefs.getBoolean(KEY_PRIVACY_MODE, false)
    }

    fun setPrivacyMode(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
        _privacyMode.value = enabled
    }

    fun dashboardSections(): Set<String> = prefs().getStringSet(
        KEY_DASHBOARD_SECTIONS,
        setOf("accounts", "budgets", "transactions", "intelligence")
    )?.toSet() ?: emptySet()

    fun setDashboardSections(sections: Set<String>) {
        prefs().edit().putStringSet(KEY_DASHBOARD_SECTIONS, sections).apply()
    }

    /**
     * Debit/credit pairs the user has said are unrelated, keyed by the two SMS hashes.
     *
     * Kept out of the database on purpose: it records a judgement about a *suggestion*, not about
     * the alerts themselves, and it has to survive the suggestion being recomputed from scratch
     * every time the inbox changes.
     */
    fun dismissedSmsLinks(): Set<String> =
        prefs().getStringSet(KEY_DISMISSED_SMS_LINKS, emptySet())?.toSet() ?: emptySet()

    fun dismissSmsLink(key: String): Set<String> {
        val updated = dismissedSmsLinks() + key
        // A fresh set instance: SharedPreferences returns its own live copy, and mutating that
        // one in place is documented not to persist.
        prefs().edit().putStringSet(KEY_DISMISSED_SMS_LINKS, updated).apply()
        return updated
    }

    fun restoreSmsLink(key: String): Set<String> {
        val updated = dismissedSmsLinks() - key
        prefs().edit().putStringSet(KEY_DISMISSED_SMS_LINKS, updated).apply()
        return updated
    }

    fun currencyOption(code: String): CurrencyOption {
        return supportedCurrencies.firstOrNull { it.code == code } ?: supportedCurrencies.first()
    }

    fun currentCurrencySymbol(): String = currencyOption(_currencyCodeFlow.value).symbol

    fun setCurrencyCode(code: String) {
        val normalized = supportedCurrencies.firstOrNull { it.code == code }?.code ?: "INR"
        prefs().edit().putString(KEY_CURRENCY_CODE, normalized).apply()
        _currencyCodeFlow.value = normalized
    }

    fun setThemeMode(themePreference: ThemePreference) {
        prefs().edit().putString(KEY_THEME_MODE, themePreference.name).apply()
        _themeModeFlow.value = themePreference
    }

    fun setProUser(isPro: Boolean) {
        prefs().edit().putBoolean(KEY_IS_PRO_USER, isPro).apply()
        _isProUserFlow.value = isPro
    }

    fun setPayCycle(frequency: PayCycleFrequency, anchorDay: Int) {
        prefs().edit()
            .putString(KEY_PAY_CYCLE_FREQUENCY, frequency.name)
            .putInt(KEY_PAY_CYCLE_ANCHOR_DAY, anchorDay.coerceAtLeast(1))
            .apply()
        _payCycleFrequencyFlow.value = frequency
        _payCycleAnchorDayFlow.value = anchorDay.coerceAtLeast(1)
    }

    fun remainingFreeScans(): Int {
        syncScanMonthIfNeeded()
        return if (_isProUserFlow.value) Int.MAX_VALUE else (FREE_SCAN_LIMIT - _scanCountFlow.value).coerceAtLeast(0)
    }

    fun consumeReceiptScanQuota(): Boolean {
        syncScanMonthIfNeeded()
        if (_isProUserFlow.value) return true
        if (_scanCountFlow.value >= FREE_SCAN_LIMIT) return false
        val next = _scanCountFlow.value + 1
        prefs().edit().putInt(KEY_SCAN_COUNT, next).apply()
        _scanCountFlow.value = next
        return true
    }

    fun updatePayCycle(freq: String, anchor: Int) {
        val frequency = runCatching { PayCycleFrequency.valueOf(freq.uppercase()) }.getOrDefault(PayCycleFrequency.MONTHLY)
        setPayCycle(frequency, anchor)
    }

    var payCycleFrequency: String
        get() = _payCycleFrequencyFlow.value.name
        set(value) {
            updatePayCycle(value, _payCycleAnchorDayFlow.value)
        }

    var payCycleAnchorDate: Int
        get() = _payCycleAnchorDayFlow.value
        set(value) {
            setPayCycle(_payCycleFrequencyFlow.value, value)
        }

    private fun syncScanMonthIfNeeded() {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val prefs = prefs()
        val savedMonth = prefs.getString(KEY_SCAN_MONTH, null)
        if (savedMonth != monthKey) {
            prefs.edit()
                .putString(KEY_SCAN_MONTH, monthKey)
                .putInt(KEY_SCAN_COUNT, 0)
                .apply()
            _scanCountFlow.value = 0
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
