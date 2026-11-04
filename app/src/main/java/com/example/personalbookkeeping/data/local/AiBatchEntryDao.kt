package com.example.personalbookkeeping.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiBatchEntryDao {
    @Query("SELECT * FROM ai_batch_entries WHERE batchId = :batchId ORDER BY position")
    suspend fun getBatchMembership(batchId: String): List<AiBatchEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<AiBatchEntryEntity>)

    @Query(
        """SELECT membership.*, ledger.id AS ledger_id, ledger.type AS ledger_type,
            ledger.amountFen AS ledger_amountFen, ledger.currency AS ledger_currency,
            ledger.date AS ledger_date, ledger.category AS ledger_category, ledger.note AS ledger_note,
            ledger.createdAt AS ledger_createdAt, ledger.updatedAt AS ledger_updatedAt,
            ledger.deletedAt AS ledger_deletedAt
            FROM ai_batch_entries AS membership
            LEFT JOIN ledger_entries AS ledger ON membership.entryId = ledger.id
            ORDER BY membership.batchId, membership.position"""
    )
    fun observeBatchesWithCurrentEntries(): Flow<List<AiBatchEntryWithLedger>>
}
