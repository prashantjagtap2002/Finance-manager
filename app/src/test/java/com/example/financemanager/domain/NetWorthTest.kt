package com.example.financemanager.domain

import com.example.financemanager.data.Account
import com.example.financemanager.data.AccountType
import com.example.financemanager.data.Debt
import com.example.financemanager.data.DebtType
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthTest {

    private fun account(balance: Double, type: AccountType = AccountType.BANK) =
        Account(name = "a", type = type, balance = balance)

    @Test
    fun lendingOnAnIouDoesNotChangeNetWorth() {
        val before = NetWorth.of(listOf(account(10_000.0)), emptyList())
        // Quick Transaction with "Track as IOU": the account drops, a receivable appears.
        val after = NetWorth.of(
            listOf(account(9_500.0)),
            listOf(Debt(personName = "Ravi", amount = 500.0, type = DebtType.LENT))
        )

        assertEquals(before.netWorth, after.netWorth, 0.001)
    }

    @Test
    fun partRepaidIouCountsOnlyWhatIsStillOwed() {
        // ₹200 of the ₹500 came back into the account; only ₹300 is still a receivable.
        val result = NetWorth.of(
            listOf(account(9_700.0)),
            listOf(Debt(personName = "Ravi", amount = 500.0, paidAmount = 200.0, type = DebtType.LENT))
        )

        assertEquals(10_000.0, result.netWorth, 0.001)
    }

    @Test
    fun creditCardDebtLowersNetWorth() {
        val result = NetWorth.of(
            listOf(account(10_000.0), account(-3_000.0, AccountType.CREDIT_CARD)),
            emptyList()
        )

        assertEquals(10_000.0, result.assets, 0.001)
        assertEquals(3_000.0, result.liabilities, 0.001)
        assertEquals(7_000.0, result.netWorth, 0.001)
    }

    @Test
    fun settledAndBorrowedDebtsAreHandled() {
        val result = NetWorth.of(
            listOf(account(1_000.0)),
            listOf(
                Debt(personName = "A", amount = 400.0, type = DebtType.BORROWED),
                Debt(personName = "B", amount = 900.0, type = DebtType.LENT, isSettled = true)
            )
        )

        assertEquals(600.0, result.netWorth, 0.001)
    }
}
