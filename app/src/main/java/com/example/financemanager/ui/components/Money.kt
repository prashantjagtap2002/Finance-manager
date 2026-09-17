package com.example.financemanager.ui.components

import com.example.financemanager.core.FinancePreferences
import java.util.Locale

/**
 * Formats a money amount, masking it when privacy mode is on so balances
 * can't be shoulder-surfed in public.
 *
 * [privacy] defaults to the user's live setting so on-screen amounts are masked unless a caller
 * deliberately opts out — surfaces that must always show real numbers (PDF exports) pass `false`
 * explicitly. Reading the default inside a composable subscribes it to the toggle, so amounts
 * mask and unmask without the screen threading the flag through every child.
 */
fun moneyString(amount: Double, privacy: Boolean = FinancePreferences.privacyMode, decimals: Int = 2): String {
    val symbol = FinancePreferences.currentCurrencySymbol()
    val prefix = if (symbol.length > 1) "$symbol " else symbol
    if (privacy) return "${prefix.trimEnd()}\u2022\u2022\u2022\u2022\u2022\u2022"
    // Locale.US rather than the default: the grouping and decimal marks here are chosen to go
    // with the currency symbol above, and a default-locale format would render the digits
    // themselves in whatever numbering system the device is set to.
    return if (decimals <= 0) {
        "$prefix${String.format(Locale.US, "%,.0f", amount)}"
    } else {
        "$prefix${String.format(Locale.US, "%,.${decimals}f", amount)}"
    }
}
