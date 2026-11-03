package com.example.personalbookkeeping.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerEntryDao {
    @Query(
        """SELECT * FROM ledger_entries
            WHERE deletedAt IS NULL
                AND (:startDate IS NULL OR date >= :startDate)
                AND (:endDateExclusive IS NULL OR date < :endDateExclusive)
                AND (:type IS NULL OR type = :type)
                AND (:category IS NULL OR category = :category)
            ORDER BY date DESC, createdAt DESC, id DESC"""
    )
    fun observeEntries(
        startDate: String?,
        endDateExclusive: String?,
        type: String?,
        category: String?
    ): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE deletedAt IS NULL ORDER BY date DESC, createdAt DESC, id DESC")
    fun observeAllActiveEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE deletedAt IS NULL ORDER BY date DESC, createdAt DESC, id DESC")
    suspend fun getAllActiveEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries ORDER BY date DESC, createdAt DESC, id DESC")
    suspend fun getAllSyncEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<LedgerEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(entries: List<LedgerEntryEntity>)

    @Query(
        """UPDATE ledger_entries
            SET type = :type, amountFen = :amountFen, currency = :currency, date = :date,
                category = :category, note = :note, updatedAt = :updatedAt, deletedAt = NULL
            WHERE id = :id"""
    )
    suspend fun update(
        id: String,
        type: String,
        amountFen: Long,
        currency: String,
        date: String,
        category: String,
        note: String,
        updatedAt: String
    ): Int

    @Query("UPDATE ledger_entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun markDeleted(id: String, deletedAt: String): Int

    @Query(
        """SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountFen ELSE 0 END), 0) AS incomeFen,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountFen ELSE 0 END), 0) AS expenseFen
            FROM ledger_entries
            WHERE deletedAt IS NULL AND date >= :startDate AND date < :endDateExclusive"""
    )
    fun observeMonthlyTotals(startDate: String, endDateExclusive: String): Flow<MonthlyTotalsRow>
}

data class MonthlyTotalsRow(
    val incomeFen: Long,
    val expenseFen: Long
)
