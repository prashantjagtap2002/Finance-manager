package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private data class Row(val id: Long, val stamp: Long, val label: String = "")

    private fun plan(
        local: List<Row>,
        remote: List<Row>,
        deleteMissing: Boolean = true,
        remoteWins: Boolean = false
    ) = planMerge(
        local = local, localId = { it.id }, localStamp = { it.stamp },
        remote = remote, remoteId = { it.id }, remoteStamp = { it.stamp },
        deleteMissing = deleteMissing, remoteWins = remoteWins
    )

    @Test
    fun aNewerLocalRowGoesUpAndANewerRemoteRowComesDown() {
        val result = plan(
            local = listOf(Row(1, stamp = 200, label = "local wins"), Row(2, stamp = 100)),
            remote = listOf(Row(1, stamp = 100), Row(2, stamp = 200, label = "remote wins"))
        )

        assertEquals(listOf(1L), result.toPush.map { it.id })
        assertEquals(listOf(2L), result.toApply.map { it.id })
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun anUnchangedRowMovesInNeitherDirection() {
        val result = plan(local = listOf(Row(1, stamp = 100)), remote = listOf(Row(1, stamp = 100)))

        // A tie means the two sides already agree; rewriting the row would only churn the flows
        // watching it.
        assertTrue(result.toPush.isEmpty())
        assertTrue(result.toApply.isEmpty())
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun aRowThisDeviceHasNeverSeenGoesUp() {
        val result = plan(local = listOf(Row(7, stamp = 50)), remote = emptyList())

        assertEquals(listOf(7L), result.toPush.map { it.id })
    }

    @Test
    fun aRowDeletedHereIsDeletedThereRatherThanComingBack() {
        // The row is on the server and not on the device, and the device is trusted about what
        // exists — this is the case that used to resurrect deleted transactions on every sync.
        val result = plan(local = emptyList(), remote = listOf(Row(5, stamp = 900)))

        assertEquals(listOf(5L), result.toDelete)
        assertTrue("a row being deleted must not also be written back", result.toApply.isEmpty())
    }

    @Test
    fun withoutDeletionAuthorityTheSameRowIsTreatedAsCreatedElsewhere() {
        val result = plan(local = emptyList(), remote = listOf(Row(5, stamp = 900)), deleteMissing = false)

        assertEquals(listOf(5L), result.toApply.map { it.id })
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun aRestoreLetsTheServerWinEvenAgainstFresherLocalRows() {
        // A fresh install's seed rows are written *now*, so they are newer than the real data in
        // the cloud. Comparing stamps would keep the seeds and the restore would quietly do
        // nothing, which is what made a reinstall come back with default categories.
        val result = plan(
            local = listOf(Row(1, stamp = 10_000, label = "seed")),
            remote = listOf(Row(1, stamp = 1, label = "real")),
            deleteMissing = false,
            remoteWins = true
        )

        assertEquals(listOf("real"), result.toApply.map { it.label })
        assertTrue("nothing local should overwrite the cloud during a restore", result.toPush.isEmpty())
    }

    @Test
    fun aRestoreStillUploadsRowsTheServerHasNeverSeen() {
        val result = plan(
            local = listOf(Row(1, stamp = 10), Row(2, stamp = 20)),
            remote = listOf(Row(1, stamp = 5)),
            deleteMissing = false,
            remoteWins = true
        )

        assertEquals(listOf(2L), result.toPush.map { it.id })
    }

    @Test
    fun aServerWithoutTheColumnNeverWins() {
        // A Supabase table that predates `updatedat` sends no stamp, so every remote row decodes
        // as 0 and the local copy stays authoritative — the behaviour the app had before.
        val result = plan(
            local = listOf(Row(1, stamp = 1_700_000_000_000)),
            remote = listOf(Row(1, stamp = 0))
        )

        assertEquals(listOf(1L), result.toPush.map { it.id })
        assertTrue(result.toApply.isEmpty())
    }
}
