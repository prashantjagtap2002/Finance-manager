package com.example.financemanager.services

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.remote.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
private data class IdOnly(val id: Long)

/**
 * Flag to prevent concurrent sync executions.
 * This ensures only one sync runs at a time across all worker instances.
 */
private val syncInProgress = AtomicBoolean(false)

@OptIn(DelicateCoroutinesApi::class)
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
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
     * Deletes rows from a Supabase table whose IDs are NOT present in [localIds].
     * This ensures that records deleted locally also get deleted from the cloud.
     */
    private suspend fun deleteRemovedRows(tableName: String, localIds: Set<Long>, userId: String) {
        val client = SupabaseManager.client
        // Fetch all IDs currently in the cloud for this user
        val remoteIds = client.postgrest[tableName]
            .select(Columns.list("id")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<IdOnly>()
            .map { it.id }
            .toSet()

        val idsToDelete = remoteIds - localIds
        if (idsToDelete.isNotEmpty()) {
            Log.d("SyncWorker", "Deleting ${idsToDelete.size} removed rows from '$tableName'")
            client.postgrest[tableName].delete {
                filter {
                    isIn("id", idsToDelete.toList())
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
            Log.d("SyncWorker", "Starting cloud sync...")
            val userId = SupabaseManager.ensureSignedIn(applicationContext)
            if (userId == null) {
                Log.w("SyncWorker", "Supabase not configured or sign-in failed. Aborting sync.")
                return Result.failure()
            }

            val db = FinanceDatabase.getDatabase(applicationContext, GlobalScope)
            val dao = db.financeDao()
            val client = SupabaseManager.client

            val errors = mutableListOf<Exception>()

            // 1. Pull missing cloud data to local database first
            errors += listOfNotNull(
                syncTable("accounts_pull") {
                    val remote = client.postgrest["accounts"].select { filter { eq("user_id", userId) } }.decodeList<AccountDto>()
                    val localIds = dao.getAccountsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertAccount(it.toEntity()) }
                },
                syncTable("categories_pull") {
                    val remote = client.postgrest["categories"].select { filter { eq("user_id", userId) } }.decodeList<CategoryDto>()
                    val localIds = dao.getCategoriesList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertCategory(it.toEntity()) }
                },
                syncTable("transactions_pull") {
                    val remote = client.postgrest["transactions"].select { filter { eq("user_id", userId) } }.decodeList<TransactionDto>()
                    val localIds = dao.getTransactionsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertTransaction(it.toEntity()) }
                },
                syncTable("savings_goals_pull") {
                    val remote = client.postgrest["savings_goals"].select { filter { eq("user_id", userId) } }.decodeList<SavingsGoalDto>()
                    val localIds = dao.getSavingsGoalsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertSavingsGoal(it.toEntity()) }
                },
                syncTable("recurring_transactions_pull") {
                    val remote = client.postgrest["recurring_transactions"].select { filter { eq("user_id", userId) } }.decodeList<RecurringTransactionDto>()
                    val localIds = dao.getRecurringTransactionsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertRecurringTransaction(it.toEntity()) }
                },
                syncTable("debts_pull") {
                    val remote = client.postgrest["debts"].select { filter { eq("user_id", userId) } }.decodeList<DebtDto>()
                    val localIds = dao.getDebtsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertDebt(it.toEntity()) }
                },
                syncTable("sms_transactions_pull") {
                    val remote = client.postgrest["sms_transactions"].select { filter { eq("user_id", userId) } }.decodeList<SmsTransactionDto>()
                    val localIds = dao.getSmsTransactionsList().map { it.id }.toSet()
                    remote.forEach { if (!localIds.contains(it.id)) dao.insertSmsTransaction(it.toEntity()) }
                }
            )

            // 2. Push local updates to cloud
            errors += listOfNotNull(
                syncTable("accounts") {
                    val accounts = dao.getAccountsList().map {
                        AccountDto(it.id, it.name, it.type.name, it.balance, it.currency, userId)
                    }
                    if (accounts.isNotEmpty()) client.postgrest["accounts"].upsert(accounts)
                },
                syncTable("categories") {
                    val categories = dao.getCategoriesList().map {
                        CategoryDto(it.id, it.name, it.iconName, it.colorHex, it.budgetLimit, it.isZeroBased, it.rolloverAmount, it.isRolloverEnabled, it.displayOrder, userId)
                    }
                    if (categories.isNotEmpty()) client.postgrest["categories"].upsert(categories)
                },
                syncTable("transactions") {
                    val transactions = dao.getTransactionsList().map {
                        TransactionDto(
                            it.id, it.amount, it.type.name, it.categoryId, it.sourceAccountId, it.destinationAccountId,
                            it.note, it.splitGroupId, it.date, it.currency, it.isRecurring, it.recurringId,
                            it.isAutoLogged, it.merchantName, it.originalAmount, it.originalCurrency, userId
                        )
                    }
                    if (transactions.isNotEmpty()) client.postgrest["transactions"].upsert(transactions)
                },
                syncTable("savings_goals") {
                    val goals = dao.getSavingsGoalsList().map {
                        SavingsGoalDto(it.id, it.name, it.iconName, it.colorHex, it.targetAmount, it.savedAmount, it.targetDate, it.createdAt, userId)
                    }
                    if (goals.isNotEmpty()) client.postgrest["savings_goals"].upsert(goals)
                },
                syncTable("recurring_transactions") {
                    val recurring = dao.getRecurringTransactionsList().map {
                        RecurringTransactionDto(it.id, it.amount, it.type.name, it.categoryId, it.accountId, it.interval.name, it.note, it.startDate, it.nextExecutionDate, it.isAutoLog, it.isPaused, userId)
                    }
                    if (recurring.isNotEmpty()) client.postgrest["recurring_transactions"].upsert(recurring)
                },
                syncTable("debts") {
                    val debts = dao.getDebtsList().map {
                        DebtDto(it.id, it.personName, it.amount, it.type.name, it.isSettled, it.date, it.dueDate, it.paidAmount, it.notes, it.interestRate, it.minimumPayment, userId)
                    }
                    if (debts.isNotEmpty()) client.postgrest["debts"].upsert(debts)
                },
                syncTable("sms_transactions") {
                    val smsTxs = dao.getSmsTransactionsList().map {
                        SmsTransactionDto(
                            it.id, it.smsHash, it.sender, it.body, it.accountName, it.type,
                            it.amount, it.balance, it.counterparty, it.reference,
                            it.rawTimestamp, it.createdAt, it.isApproved, it.isIgnored,
                            it.approvedCategoryId, it.approvedAccountId, userId
                        )
                    }
                    if (smsTxs.isNotEmpty()) client.postgrest["sms_transactions"].upsert(smsTxs)
                }
            )

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
