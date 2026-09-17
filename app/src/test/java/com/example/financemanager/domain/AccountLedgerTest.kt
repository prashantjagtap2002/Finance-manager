package com.example.financemanager.domain

import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLedgerTest {

    private fun expense(amount: Double, accountId: Long = 1L, date: Long = 1_000L) = Transaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        categoryId = 1L,
        sourceAccountId = accountId,
        note = "test",
        date = date
    )

    private fun income(amount: Double, accountId: Long = 1L, date: Long = 1_000L) = Transaction(
        amount = amount,
        type = TransactionType.INCOME,
        categoryId = 1L,
        sourceAccountId = accountId,
        note = "test",
        date = date
    )

    private fun transfer(amount: Double, from: Long, to: Long?, date: Long = 1_000L) = Transaction(
        amount = amount,
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        sourceAccountId = from,
        destinationAccountId = to,
        note = "test",
        date = date
    )

    @Test
    fun expenseDebitsAndIncomeCreditsTheSourceAccount() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        AccountLedger.post(store, expense(250.0))
        assertEquals(750.0, store.balanceOf(1L), 0.001)

        AccountLedger.post(store, income(400.0, date = 2_000L))
        assertEquals(1_150.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun transferMovesMoneyBetweenBothAccounts() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0)
            addAccount(2L, balance = 500.0)
        }

        AccountLedger.post(store, transfer(300.0, from = 1L, to = 2L))

        assertEquals(700.0, store.balanceOf(1L), 0.001)
        assertEquals(800.0, store.balanceOf(2L), 0.001)
    }

    @Test
    fun deletingATransactionPutsTheMoneyBack() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }
        val id = AccountLedger.post(store, expense(250.0))

        AccountLedger.revoke(store, store.transactions.first { it.id == id })

        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
        assertTrue(store.transactions.isEmpty())
    }

    @Test
    fun deletingATransferUnwindsBothSides() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0)
            addAccount(2L, balance = 500.0)
        }
        val id = AccountLedger.post(store, transfer(300.0, from = 1L, to = 2L))

        AccountLedger.revoke(store, store.transactions.first { it.id == id })

        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
        assertEquals(500.0, store.balanceOf(2L), 0.001)
    }

    @Test
    fun editingAnAmountAppliesOnlyTheDifference() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }
        val id = AccountLedger.post(store, expense(250.0))
        val old = store.transactions.first { it.id == id }

        AccountLedger.amend(store, old, old.copy(amount = 400.0))

        assertEquals(600.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun editingMovesMoneyWhenTheAccountChanges() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0)
            addAccount(2L, balance = 1_000.0)
        }
        val id = AccountLedger.post(store, expense(250.0, accountId = 1L))
        val old = store.transactions.first { it.id == id }

        AccountLedger.amend(store, old, old.copy(sourceAccountId = 2L))

        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
        assertEquals(750.0, store.balanceOf(2L), 0.001)
    }

    @Test
    fun editingHandlesATypeFlipFromExpenseToIncome() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }
        val id = AccountLedger.post(store, expense(250.0))
        val old = store.transactions.first { it.id == id }

        AccountLedger.amend(store, old, old.copy(type = TransactionType.INCOME))

        assertEquals(1_250.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aDuplicateOccurrenceIsNeitherReInsertedNorReCharged() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        AccountLedger.postIfNew(store, expense(250.0))
        AccountLedger.postIfNew(store, expense(250.0))

        assertEquals(1, store.transactions.size)
        assertEquals(750.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aTransactionWhoseAccountIsGoneLeavesOtherAccountsAlone() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        AccountLedger.post(store, expense(250.0, accountId = 99L))

        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun postingLeavesBalanceAndLedgerInAgreement() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0)
            addAccount(2L, balance = 500.0)
        }

        AccountLedger.post(store, expense(120.0, accountId = 1L, date = 1L))
        AccountLedger.post(store, income(80.0, accountId = 1L, date = 2L))
        AccountLedger.post(store, transfer(200.0, from = 1L, to = 2L, date = 3L))

        // Nothing drifted, so reconciliation has nothing to correct.
        assertTrue(AccountLedger.reconcile(store).isEmpty())
        assertEquals(760.0, store.balanceOf(1L), 0.001)
        assertEquals(700.0, store.balanceOf(2L), 0.001)
    }

    @Test
    fun reconcileRepairsABalanceThatDriftedOutOfBand() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }
        AccountLedger.post(store, expense(250.0))

        // Simulate the damage an older, uncentralised path left behind.
        store.updateAccount(store.accounts.getValue(1L).copy(balance = 400.0))

        val drifts = AccountLedger.reconcile(store)

        assertEquals(1, drifts.size)
        assertEquals(400.0, drifts[0].storedBalance, 0.001)
        assertEquals(750.0, drifts[0].ledgerBalance, 0.001)
        assertEquals(350.0, drifts[0].difference, 0.001)
        assertEquals(750.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aNegativeAdjustmentTakesMoneyOutAndAPositiveOnePutsItIn() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        // Buying an investment, lending a friend their share of a bill: money leaves.
        AccountLedger.postBalanceAdjustment(store, 1L, -300.0, "bought units", 1L)
        assertEquals(700.0, store.balanceOf(1L), 0.001)

        // Selling it, or being repaid: money arrives.
        AccountLedger.postBalanceAdjustment(store, 1L, 450.0, "sold units", 2L)
        assertEquals(1_150.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun anAdjustmentNeverWritesANegativeAmount() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        val outId = AccountLedger.postBalanceAdjustment(store, 1L, -300.0, "out", 1L)
        val inId = AccountLedger.postBalanceAdjustment(store, 1L, 450.0, "in", 2L)

        val outflow = store.transactions.first { it.id == outId }
        val inflow = store.transactions.first { it.id == inId }

        assertEquals(300.0, outflow.amount, 0.001)
        assertEquals(1L, outflow.sourceAccountId)
        assertEquals(null, outflow.destinationAccountId)

        // The direction lives in which side the account sits on, not in the sign of the amount.
        assertEquals(450.0, inflow.amount, 0.001)
        assertEquals(0L, inflow.sourceAccountId)
        assertEquals(1L, inflow.destinationAccountId)
    }

    @Test
    fun adjustmentsStayInAgreementWithTheLedger() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        AccountLedger.postBalanceAdjustment(store, 1L, -300.0, "out", 1L)
        AccountLedger.postBalanceAdjustment(store, 1L, 450.0, "in", 2L)

        // Both directions have to be visible to the sums reconcile derives from, or the next
        // "Recalculate balances" would treat the adjustment as drift and undo it.
        assertTrue(AccountLedger.reconcile(store).isEmpty())
        assertEquals(1_150.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun revokingAnAdjustmentPutsItBackInBothDirections() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        val outId = AccountLedger.postBalanceAdjustment(store, 1L, -300.0, "out", 1L)
        val inId = AccountLedger.postBalanceAdjustment(store, 1L, 450.0, "in", 2L)

        AccountLedger.revoke(store, store.transactions.first { it.id == outId })
        AccountLedger.revoke(store, store.transactions.first { it.id == inId })

        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun rebaseMakesTheCurrentBalanceTheTruth() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }
        AccountLedger.post(store, expense(250.0))

        // A restored backup: the balance is authoritative, the opening balance is not.
        store.updateAccount(store.accounts.getValue(1L).copy(balance = 900.0, openingBalance = 0.0))
        AccountLedger.rebaseOpeningBalances(store)

        assertEquals(1_150.0, store.accounts.getValue(1L).openingBalance, 0.001)
        assertTrue(AccountLedger.reconcile(store).isEmpty())
        assertEquals(900.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aZeroAdjustmentWritesNothing() = runTest {
        val store = FakeLedgerStore().apply { addAccount(1L, balance = 1_000.0) }

        val id = AccountLedger.postBalanceAdjustment(store, 1L, 0.0, "nothing moved", 1L)

        // A zero delta has no side to sit on, so it would land as a transfer belonging to
        // neither account - a row in the log that moves nothing.
        assertEquals("0 means no transaction was created", 0L, id)
        assertTrue(store.transactions.isEmpty())
        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun reconcileCanBeNarrowedToSomeAccounts() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0)
            addAccount(2L, balance = 1_000.0)
        }
        // Both balances drift away from what their (empty) ledgers say.
        store.updateAccount(store.accounts.getValue(1L).copy(balance = 900.0))
        store.updateAccount(store.accounts.getValue(2L).copy(balance = 800.0))

        val repaired = AccountLedger.reconcile(store, setOf(1L))

        assertEquals(1, repaired.size)
        assertEquals(1L, repaired.first().account.id)
        assertEquals("the named account is rebuilt from its ledger", 1_000.0, store.balanceOf(1L), 0.001)
        assertEquals("the others are left exactly as they were", 800.0, store.balanceOf(2L), 0.001)
    }

    @Test
    fun rebaseCanBeNarrowedToSomeAccounts() = runTest {
        val store = FakeLedgerStore().apply {
            addAccount(1L, balance = 1_000.0, openingBalance = 0.0)
            addAccount(2L, balance = 1_000.0, openingBalance = 0.0)
        }

        AccountLedger.rebaseOpeningBalances(store, setOf(2L))

        assertEquals("untouched account keeps its anchor", 0.0, store.accounts.getValue(1L).openingBalance, 0.001)
        assertEquals("named account is re-anchored", 1_000.0, store.accounts.getValue(2L).openingBalance, 0.001)
    }
}
