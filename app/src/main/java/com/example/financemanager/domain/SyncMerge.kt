package com.example.financemanager.domain

/** What one table's two-way merge decided, worked out before anything is written anywhere. */
data class MergePlan<L, R>(
    /** Local rows the server's copy is older than, or has never seen. */
    val toPush: List<L>,
    /** Server rows to write over the local copy. */
    val toApply: List<R>,
    /** Server row ids this device recorded a deletion for. */
    val toDelete: List<Long>
)

/**
 * Decides which rows go up, which come down, and which server rows are gone — for one table, from
 * one snapshot of each side.
 *
 * Kept pure and out of [com.example.financemanager.services.SyncWorker] because this is the part
 * that can be *wrong*, and being wrong here means either losing an edit or deleting real data. A
 * function over two lists can be tested exhaustively; the same logic spread through seven network
 * blocks cannot be tested at all.
 *
 * Two rules decide everything:
 *
 * **Which copy of a row wins** is whichever was changed later, by `updatedAt`. That is what makes
 * an edit travel: before rows carried a stamp, a pull could only add rows it had never seen, so a
 * change made anywhere else was invisible forever. Ties go to the local row — it is already
 * written, and re-applying an identical row would only churn the flows watching it. [remoteWins]
 * overrides this for a restore, where the server is authoritative by definition: a fresh install's
 * seed rows carry the *current* time and would otherwise outrank the real data they collide with.
 *
 * **Whether a row was deleted** is answered by [locallyDeletedIds] — the tombstones this device
 * wrote when the user actually deleted something — and by nothing else. Absence used to be the
 * answer, which is a different question with the same shape: a row missing locally is *either* one
 * this device deleted *or* one another device created and this device has not pulled yet, and the
 * two are indistinguishable without a record of the deletion. Reading absence as deletion meant a
 * transaction entered on a second device could be wiped out of the cloud by the first one before
 * it ever arrived. Now a row that is missing locally and carries no tombstone is imported, so the
 * worst a wrong guess can do is bring a row back rather than destroy one.
 */
fun <L, R> planMerge(
    local: List<L>,
    localId: (L) -> Long,
    localStamp: (L) -> Long,
    remote: List<R>,
    remoteId: (R) -> Long,
    remoteStamp: (R) -> Long,
    locallyDeletedIds: Set<Long>,
    remoteWins: Boolean
): MergePlan<L, R> {
    val localById = local.associateBy(localId)
    val remoteById = remote.associateBy(remoteId)

    val toPush = local.filter { row ->
        val counterpart = remoteById[localId(row)]
        when {
            counterpart == null -> true
            remoteWins -> false
            else -> localStamp(row) > remoteStamp(counterpart)
        }
    }

    val toApply = remote.filter { row ->
        val counterpart = localById[remoteId(row)]
        when {
            // Absent locally with no tombstone: another device made this, so take it. Absent with
            // a tombstone: this device deleted it, so it is on its way out instead.
            counterpart == null -> remoteId(row) !in locallyDeletedIds
            remoteWins -> true
            else -> remoteStamp(row) > localStamp(counterpart)
        }
    }

    // Only rows this device holds a deletion record for, and only while the server still has them.
    val toDelete = remote.map(remoteId).filter { it in locallyDeletedIds && !localById.containsKey(it) }

    return MergePlan(toPush = toPush, toApply = toApply, toDelete = toDelete)
}
