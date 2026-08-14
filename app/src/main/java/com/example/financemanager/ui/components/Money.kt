package com.example.financemanager.ui.components

import com.example.financemanager.core.FinancePreferences

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
    return if (decimals <= 0) {
        "$prefix${String.format("%,.0f", amount)}"
    } else {
        "$prefix${String.format("%,.${decimals}f", amount)}"
    }
}
