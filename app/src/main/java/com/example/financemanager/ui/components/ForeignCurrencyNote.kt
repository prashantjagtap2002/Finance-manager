package com.example.financemanager.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.financemanager.theme.TextMuted
import com.example.financemanager.theme.Typography
import java.util.Locale

/**
 * Money held or spent in a currency other than the app's, listed beside a total rather than added
 * into it.
 *
 * The app has one home currency and renders every amount with its symbol, so a total is only
 * arithmetic while every row shares that currency. ₹100,000 + $1,000 is not 101,000 of anything,
 * and showing it as one figure is worse than showing nothing. Rows carrying another code — which
 * in practice means a backup taken under a different home currency — are reported here instead,
 * with their code spelled out so there is no doubt what the number is.
 *
 * Renders nothing at all in the normal case, where [amountsByCurrency] is empty.
 */
@Composable
fun ForeignCurrencyNote(
    amountsByCurrency: Map<String, Double>,
    privacy: Boolean,
    prefix: String = "Also"
) {
    if (amountsByCurrency.isEmpty()) return
    val text = amountsByCurrency.entries
        .sortedBy { it.key }
        .joinToString(" · ") { (code, amount) ->
            // Never through moneyString: that prints the *home* symbol, which is exactly the
            // confusion this line exists to clear up.
            if (privacy) "$code ••••••" else "$code ${String.format(Locale.US, "%,.2f", amount)}"
        }

    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "$prefix $text — not converted",
        style = Typography.labelSmall.copy(color = TextMuted)
    )
}
