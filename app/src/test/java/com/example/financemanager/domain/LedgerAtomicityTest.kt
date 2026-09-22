package com.example.financemanager.domain

import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every ledger write and the balance move it implies have to land together.
 *
 * They were two separate statements: a crash — or a process kill, which Android does routinely —
 * between them left a transaction whose money never moved, or a balance with nothing behind it.
 * Neither is distinguishable afterwards from genuine drift, so "Recalculate balances" would go on
 * to bake the wrong number in. These tests hold the pairing in place, because the failure it
 * guards against is invisible in any test that only checks the end state of a run that completed.
 */
class LedgerAtomicityTest {

    private fun expense(amount: Double, accountId: Long = 1L, date: Long = 1_000L) = Transaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        categoryId = 1L,
        sourceAccountId = accountId,
        date = date
    )

    @Test
    fun postingWritesTheRowAndTheBalanceInOneUnit() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1_000.0)

        AccountLedger.post(store, expense(250.0))

        assertEquals("nothing may be written outside a transaction", 0, store.writesOutsideTransaction)
        assertTrue(store.deepestTransaction >= 1)
        assertEquals(750.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun revokingAndAmendingAreAtomicToo() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1_000.0)

        val id = AccountLedger.post(store, expense(250.0))
        val old = store.transactions.first { it.id == id }
        AccountLedger.amend(store, old, old.copy(amount = 400.0))
        AccountLedger.revoke(store, store.transactions.first { it.id == id })

        assertEquals(0, store.writesOutsideTransaction)
        assertEquals(1_000.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aBalanceAdjustmentCarriesItsTransferRowWithIt() = runTest {
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1_000.0)

        AccountLedger.postBalanceAdjustment(store, 1L, -300.0, "bought units", 1L)

        assertEquals(0, store.writesOutsideTransaction)
        assertEquals(700.0, store.balanceOf(1L), 0.001)
        assertEquals(1, store.transactions.size)
    }

    @Test
    fun theDuplicateCheckAndTheInsertShareOneTransaction() = runTest {
        // Two SMS deliveries of the same alert race each other. Checking outside the transaction
        // let both find nothing and both post, which is the double-charge the check exists to stop.
        val store = FakeLedgerStore()
        store.addAccount(1L, balance = 1_000.0)

        AccountLedger.postIfNew(store, expense(250.0))
        AccountLedger.postIfNew(store, expense(250.0))

        assertEquals(0, store.writesOutsideTransaction)
        assertEquals(1, store.transactions.size)
        assertEquals(750.0, store.balanceOf(1L), 0.001)
    }

    @Test
    fun aStoreWithNoTransactionSupportStillWorks() = runTest {
        // `atomically` is a capability, not a requirement: the arithmetic has to hold for a plain
        // LedgerStore too, which is what the recurring-scheduler tests hand it.
        val plain = object : LedgerStore by FakeLedgerStore() {}
        // Nothing to assert beyond "it runs and returns" — the point is that the extension does
        // not require TransactionalStore.
        assertEquals(emptyList<BalanceDrift>(), AccountLedger.reconcile(plain))
    }
}
