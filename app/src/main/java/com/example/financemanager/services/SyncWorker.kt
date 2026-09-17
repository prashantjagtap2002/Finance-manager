package com.example.financemanager.services

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.domain.AccountLedger
import com.example.financemanager.domain.MergePlan
import com.example.financemanager.domain.planMerge
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.remote.*
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flag to prevent concurrent sync executions.
 * This ensures only one sync runs at a time across all worker instances.
 */
private val syncInProgress = AtomicBoolean(false)

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** How many ids one delete request may carry. See [deleteRows]. */
        private const val DELETE_CHUNK_SIZE = 200

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "CloudSyncWorkerImmediate",
                ExistingWorkPolicy.KEEP, // Keep existing work to prevent overlapping syncs
                request
            )
        }
    }

    /** Runs one table's sync in isolation so a failure here doesn't block the rest. Returns the error, if any. */
    private suspend fun syncTable(name: String, block: suspend () -> Unit): Exception? {
        return try {
            block()
            null
        } catch (e: Exception) {
            Log.e("SyncWorker", "Failed syncing '$name' to Supabase", e)
            e
        }
    }

    /**
     * Whether this device has ever completed a pull from Supabase. A fresh install only has
     * seed/default data locally, so deleting cloud rows the local DB doesn't know about yet
     * would wipe the user's real cloud data before it's ever been pulled down. Deletion sync
     * only runs once we know local state has actually been reconciled with the cloud at least
     * once.
     */
    private fun hasCompletedInitialSync(context: Context): Boolean =
        context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
            .getBoolean("initial_sync_done", false)

    private fun markInitialSyncDone(context: Context) {
        context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
            .edit().putBoolean("initial_sync_done", true).apply()
    }

    /**
     * Sends [rows] up, falling back to the same rows minus [newerColumns] if the server rejects
     * them.
     *
     * The app adds columns faster than a Supabase project that was set up by hand. A table created
     * before `updatedat` — or before the SMS resolution columns — rejects the whole upsert over the
     * one field it doesn't know, taking every row with it. Dropping the unknown columns and
     * retrying costs that table its newest feature and keeps everything else working, which beats
     * failing the sync until somebody runs a migration.
     */
    private suspend inline fun <reified T : Any> upsertTolerantly(
        table: String,
        rows: List<T>,
        vararg newerColumns: String
    ) {
        if (rows.isEmpty()) return
        val client = SupabaseManager.client
        try {
            client.postgrest[table].upsert(rows)
        } catch (e: Exception) {
            Log.w("SyncWorker", "'$table' upsert rejected; retrying without ${newerColumns.toList()}", e)
            client.postgrest[table].upsert(stripColumns(rows, *newerColumns))
        }
    }

    /** The same rows as plain JSON with [columns] removed. See [upsertTolerantly]. */
    private inline fun <reified T : Any> stripColumns(rows: List<T>, vararg columns: String): List<JsonObject> =
        rows.map { row ->
            JsonObject(Json.encodeToJsonElement(row).jsonObject.filterKeys { it !in columns })
        }

    /**
     * Removes [ids] from a cloud table, so a row deleted on the device is deleted in the cloud too.
     *
     * Deletion used to be written but never called, which is why deleting anything didn't stick:
     * the next pull found the row still in the cloud, didn't have it locally any more, and inserted
     * it straight back. For transactions that was worse than a resurrection — the pull writes to
     * the DAO rather than the ledger, so the row came back without its balance effect, and the next
     * "Recalculate balances" then treated the mismatch as drift and baked the deleted spending in.
     */
    private suspend fun deleteRows(tableName: String, ids: List<Long>, userId: String) {
        if (ids.isEmpty()) return
        Log.d("SyncWorker", "Deleting ${ids.size} removed rows from '$tableName'")
        // Every id goes into the query string, so one call per few thousand rows would build a
        // request line the server rejects outright — and this runs last, after the pushes have
        // already landed, so failing here leaves the sync half-applied. Clearing a long history is
        // exactly when that happens, so the ids go up a chunk at a time.
        ids.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
            SupabaseManager.client.postgrest[tableName].delete {
                filter {
                    isIn("id", chunk)
                    eq("user_id", userId)
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        // Prevent concurrent sync executions
        if (!syncInProgress.compareAndSet(false, true)) {
            Log.w("SyncWorker", "Sync already in progress, skipping this execution")
            return Result.success()
        }

        try {
            if (!FinancePreferences.cloudSyncEnabled(applicationContext)) {
                Log.d("SyncWorker", "Cloud sync is switched off. Nothing leaves the device.")
                return Result.success()
            }

            Log.d("SyncWorker", "Starting cloud sync...")
            val userId = SupabaseManager.ensureSignedIn()
            if (userId == null) {
                Log.w("SyncWorker", "Supabase not configured or sign-in failed. Aborting sync.")
                return Result.failure()
            }

            val db = FinanceDatabase.getDatabase(applicationContext)
            val dao = db.financeDao()
            val client = SupabaseManager.client

            val errors = mutableListOf<Exception>()

            // A device that has never pulled has nothing but seed data, and seed rows are written
            // *now* — newer than the real data waiting in the cloud. So the first sync is a
            // restore: the server is authoritative for anything the two sides both have, and this
            // device gets no say in what has been deleted.
            val isInitialSync = !hasCompletedInitialSync(applicationContext)

            // Deleting cloud rows is the one irreversible thing a sync does, so it takes a second
            // opinion beyond the "have we pulled yet" flag. Every database the app creates is
            // seeded with accounts and categories, so having neither means this isn't a database
            // whose emptiness is a statement about anything — it's a wiped or half-restored one,
            // and pushing its emptiness would clear the user's cloud data.
            val localLooksReal = dao.getAccountsList().isNotEmpty() || dao.getCategoriesList().isNotEmpty()
            if (!isInitialSync && !localLooksReal) {
                Log.w("SyncWorker", "Local database looks empty; skipping deletion sync this run")
            }

            // One snapshot of the server per table, and everything else is decided from it: what
            // goes up, what comes down, what is gone. Two half-passes (push everything, then pull
            // whatever was missing) could never compare the two sides, which is why an edit made
            // anywhere could not travel and whether a deletion stuck depended entirely on which
            // half ran first. See planMerge for the two rules it follows.
            val remoteWins = isInitialSync
            val deleteMissing = !isInitialSync && localLooksReal

            // Which account rows the server overwrote. Their balances are the server's to state;
            // every other account has to re-derive its own once the pulled transactions land.
            // See the repair step at the end of the run.
            val serverOwnedAccountIds = mutableSetOf<Long>()

            suspend fun <L, R> merge(
                table: String,
                local: List<L>,
                localId: (L) -> Long,
                localStamp: (L) -> Long,
                remote: List<R>,
                remoteId: (R) -> Long,
                remoteStamp: (R) -> Long,
                push: suspend (List<L>) -> Unit,
                apply: suspend (R) -> Unit
            ): MergePlan<L, R> {
                val plan = planMerge(
                    local = local, localId = localId, localStamp = localStamp,
                    remote = remote, remoteId = remoteId, remoteStamp = remoteStamp,
                    deleteMissing = deleteMissing, remoteWins = remoteWins
                )
                plan.toApply.forEach { apply(it) }
                push(plan.toPush)
                deleteRows(table, plan.toDelete, userId)
                return plan
            }

            errors += listOfNotNull(
                syncTable("accounts") {
                    val local = dao.getAccountsList()
                    val remote = client.postgrest["accounts"]
                        .select { filter { eq("user_id", userId) } }.decodeList<AccountDto>()
                    merge(
                        "accounts", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "accounts",
                                rows.map {
                                    AccountDto(it.id, it.name, it.type.name, it.balance, it.currency, userId, it.updatedAt)
                                },
                                "updatedat"
                            )
                        },
                        apply = { dao.insertAccount(it.toEntity()) }
                    ).also { plan -> serverOwnedAccountIds += plan.toApply.map { it.id } }
                },
                syncTable("categories") {
                    val local = dao.getCategoriesList()
                    val remote = client.postgrest["categories"]
                        .select { filter { eq("user_id", userId) } }.decodeList<CategoryDto>()
                    merge(
                        "categories", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "categories",
                                rows.map {
                                    CategoryDto(
                                        it.id, it.name, it.iconName, it.colorHex, it.budgetLimit, it.isZeroBased,
                                        it.rolloverAmount, it.isRolloverEnabled, it.displayOrder, userId, it.updatedAt
                                    )
                                },
                                "updatedat"
                            )
                        },
                        apply = { dao.insertCategory(it.toEntity()) }
                    )
                },
                syncTable("transactions") {
                    val local = dao.getTransactionsList()
                    val remote = client.postgrest["transactions"]
                        .select { filter { eq("user_id", userId) } }.decodeList<TransactionDto>()
                    merge(
                        "transactions", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "transactions",
                                rows.map {
                                    TransactionDto(
                                        it.id, it.amount, it.type.name, it.categoryId, it.sourceAccountId,
                                        it.destinationAccountId, it.note, it.splitGroupId, it.date, it.currency,
                                        it.isRecurring, it.recurringId, it.isAutoLogged, it.merchantName,
                                        it.originalAmount, it.originalCurrency, it.isVerified, userId, it.updatedAt
                                    )
                                },
                                "updatedat"
                            )
                        },
                        // Written straight to the DAO rather than through AccountLedger: the
                        // accounts merged above already carry the balances these transactions
                        // produced on the other device, so replaying them would double-count.
                        apply = { dao.insertTransaction(it.toEntity()) }
                    )
                },
                syncTable("savings_goals") {
                    val local = dao.getSavingsGoalsList()
                    val remote = client.postgrest["savings_goals"]
                        .select { filter { eq("user_id", userId) } }.decodeList<SavingsGoalDto>()
                    merge(
                        "savings_goals", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "savings_goals",
                                rows.map {
                                    SavingsGoalDto(
                                        it.id, it.name, it.iconName, it.colorHex, it.targetAmount,
                                        it.savedAmount, it.targetDate, it.createdAt, userId, it.updatedAt
                                    )
                                },
                                "updatedat"
                            )
                        },
                        apply = { dao.insertSavingsGoal(it.toEntity()) }
                    )
                },
                syncTable("recurring_transactions") {
                    val local = dao.getRecurringTransactionsList()
                    val remote = client.postgrest["recurring_transactions"]
                        .select { filter { eq("user_id", userId) } }.decodeList<RecurringTransactionDto>()
                    merge(
                        "recurring_transactions", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "recurring_transactions",
                                rows.map {
                                    RecurringTransactionDto(
                                        it.id, it.amount, it.type.name, it.categoryId, it.accountId, it.interval.name,
                                        it.note, it.startDate, it.nextExecutionDate, it.isAutoLog, it.isPaused,
                                        userId, it.updatedAt
                                    )
                                },
                                "updatedat"
                            )
                        },
                        apply = { dao.insertRecurringTransaction(it.toEntity()) }
                    )
                },
                syncTable("debts") {
                    val local = dao.getDebtsList()
                    val remote = client.postgrest["debts"]
                        .select { filter { eq("user_id", userId) } }.decodeList<DebtDto>()
                    merge(
                        "debts", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "debts",
                                rows.map {
                                    DebtDto(
                                        it.id, it.personName, it.amount, it.type.name, it.isSettled, it.date,
                                        it.dueDate, it.paidAmount, it.notes, it.interestRate, it.minimumPayment,
                                        userId, it.updatedAt
                                    )
                                },
                                "updatedat"
                            )
                        },
                        apply = { dao.insertDebt(it.toEntity()) }
                    )
                },
                syncTable("sms_transactions") {
                    val local = dao.getSmsTransactionsList()
                    val bodies = local.associate { it.id to it.body }
                    val remote = client.postgrest["sms_transactions"]
                        .select { filter { eq("user_id", userId) } }.decodeList<SmsTransactionDto>()
                    merge(
                        "sms_transactions", local, { it.id }, { it.updatedAt }, remote, { it.id }, { it.updatedAt },
                        push = { rows ->
                            upsertTolerantly(
                                "sms_transactions",
                                rows.map { it.toDto(userId) },
                                "updatedat", "loggedtransactionid", "linkedsmsid", "resolution"
                            )
                        },
                        apply = { row ->
                            // The body is deliberately never uploaded, so a row coming back has
                            // none. Keep whatever text this device still holds rather than
                            // blanking it.
                            val entity = row.toEntity().copy(body = bodies[row.id] ?: "")
                            // Unlike every other table, this insert is IGNORE rather than REPLACE
                            // — it is what backs the smsHash dedupe — so an existing row has to be
                            // updated explicitly or the server's copy would be dropped in silence.
                            if (dao.insertSmsTransaction(entity) == -1L) dao.updateSmsTransaction(entity)
                        }
                    )
                },
                syncTable("balance_repair") {
                    // Two different repairs, because the two kinds of account disagree about which
                    // number is the fact.
                    //
                    // An account the server overwrote carries the balance the other device ended up
                    // with, and the cloud schema has no opening-balance column, so the anchor lands
                    // at its default. The balance is the fact: re-anchor the opening balance to it,
                    // or a later "Recalculate balances" would read the missing anchor as drift.
                    AccountLedger.rebaseOpeningBalances(dao, serverOwnedAccountIds)

                    // An account whose local row won kept its own balance — but the transaction
                    // merge above may still have pulled in rows belonging to it, written straight
                    // to the DAO and so with no balance effect applied. Rebasing here would bend
                    // the anchor to hide exactly that gap; the ledger is the fact instead, so the
                    // balance is re-derived from it.
                    val localOwned = dao.getAccountsList()
                        .map { it.id }
                        .filterNot { it in serverOwnedAccountIds }
                        .toSet()
                    AccountLedger.reconcile(dao, localOwned).forEach { drift ->
                        Log.d(
                            "SyncWorker",
                            "Rebuilt '${drift.account.name}' from the ledger after the merge: " +
                                "${drift.storedBalance} -> ${drift.ledgerBalance}"
                        )
                    }
                }
            )

            if (isInitialSync && errors.isEmpty()) markInitialSyncDone(applicationContext)

            return if (errors.isEmpty()) {
                Log.d("SyncWorker", "Cloud sync complete!")
                Result.success()
            } else {
                Log.e("SyncWorker", "Cloud sync completed with ${errors.size} error(s)")
                errors.forEach { error ->
                    Log.e("SyncWorker", "Sync error: ${error.message}", error)
                }
                Result.retry()
            }
        } finally {
            syncInProgress.set(false)
            Log.d("SyncWorker", "Sync flag reset")
        }
    }
}
