package com.example.financemanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId ORDER BY createdAt ASC")
    fun receiptsFor(transactionId: Long): Flow<List<Receipt>>

    /** Which transactions have at least one receipt, so lists can mark them. */
    @Query("SELECT DISTINCT transactionId FROM receipts")
    fun transactionIdsWithReceipts(): Flow<List<Long>>

    @Insert
    suspend fun insert(receipt: Receipt): Long

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("SELECT * FROM receipts WHERE transactionId NOT IN (SELECT id FROM transactions)")
    suspend fun orphans(): List<Receipt>

    @Query("SELECT fileName FROM receipts")
    suspend fun allFileNames(): List<String>

    @Query("SELECT transactionId FROM receipts")
    suspend fun allTransactionIds(): List<Long>

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId")
    suspend fun receiptsForOnce(transactionId: Long): List<Receipt>
}
