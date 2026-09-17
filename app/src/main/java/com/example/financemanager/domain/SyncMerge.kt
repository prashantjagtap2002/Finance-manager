package com.example.financemanager.domain

/** What one table's two-way merge decided, worked out before anything is written anywhere. */
data class MergePlan<L, R>(
    /** Local rows the server's copy is older than, or has never seen. */
    val toPush: List<L>,
    /** Server rows to write over the local copy. */
    val toApply: List<R>,
    /** Server row ids this device no longer has. */
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
 * **Whether a row exists** is decided locally, because nothing records a deletion. A server row
 * missing from this device reads as "deleted here" when [deleteMissing] is on, and as "created
 * elsewhere" when it is off. Getting that backwards is what made deletions come back from the
 * dead, so only a caller that knows the local database is trustworthy passes true.
 */
fun <L, R> planMerge(
    local: List<L>,
    localId: (L) -> Long,
    localStamp: (L) -> Long,
    remote: List<R>,
    remoteId: (R) -> Long,
    remoteStamp: (R) -> Long,
    deleteMissing: Boolean,
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
            // Absent locally: either this device deleted it or another device created it, and
            // deleteMissing is the caller telling us which.
            counterpart == null -> !deleteMissing
            remoteWins -> true
            else -> remoteStamp(row) > localStamp(counterpart)
        }
    }

    val toDelete = if (deleteMissing) {
        remote.map(remoteId).filter { !localById.containsKey(it) }
    } else {
        emptyList()
    }

    return MergePlan(toPush = toPush, toApply = toApply, toDelete = toDelete)
}
