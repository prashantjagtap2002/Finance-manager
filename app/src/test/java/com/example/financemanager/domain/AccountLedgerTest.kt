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
}
