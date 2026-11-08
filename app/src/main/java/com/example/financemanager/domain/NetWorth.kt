package com.example.financemanager.domain

import com.example.financemanager.data.Account
import com.example.financemanager.data.Debt
import com.example.financemanager.data.DebtType

/**
 * The one definition of net worth, shared by the Net Worth headline and its history chart. They
 * used to compute it separately and disagreed: the headline left out money lent, so lending
 * through an IOU read as a loss, and the history added credit-card balances (stored negative) as
 * liabilities, so card debt raised net worth. Both also have to count what is still owed on an
 * IOU, not its original amount, or a part-repaid loan is counted twice — once as the cash that
 * came back and again as the full receivable.
 */
object NetWorth {

    data class Breakdown(val assets: Double, val liabilities: Double) {
        val netWorth: Double get() = assets - liabilities
    }

    fun of(accounts: List<Account>, debts: List<Debt>): Breakdown {
        fun outstanding(type: DebtType) = debts
            .filter { it.type == type && !it.isSettled }
            .sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }

        // An account's sign is what it is: a positive balance is money held, a negative one (a
        // card, an overdraft) is money owed — whatever the account's type says.
        val held = accounts.filter { it.balance > 0 }.sumOf { it.balance }
        val owed = accounts.filter { it.balance < 0 }.sumOf { -it.balance }
        return Breakdown(
            assets = held + outstanding(DebtType.LENT),
            liabilities = owed + outstanding(DebtType.BORROWED)
        )
    }
}
