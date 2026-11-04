package com.example.personalbookkeeping.data.repository

import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

data class LedgerFilter(
    val month: YearMonth? = null,
    val type: LedgerType? = null,
    val category: String? = null
)

data class ImportCommitResult(
    val importedCount: Int,
    val duplicateCount: Int
)

interface LedgerRepository {
    fun observeEntries(filter: LedgerFilter = LedgerFilter()): Flow<List<LedgerEntry>>
    fun observeMonthlyTotals(month: YearMonth): Flow<MonthlyTotals>
    fun observeAllActiveEntries(): Flow<List<LedgerEntry>>
    suspend fun getAllActiveEntries(): List<LedgerEntry>
    suspend fun getAllSyncEntries(): List<LedgerEntry>
    suspend fun applyRemoteEntriesAtomically(entries: List<LedgerEntry>)
    suspend fun save(entry: LedgerEntry)
    suspend fun delete(id: String)
    suspend fun importEntries(entries: List<LedgerEntry>): ImportCommitResult
    suspend fun saveAiBatch(batchId: String, savedAt: Instant, entries: List<LedgerEntry>): ImportCommitResult
    fun observeAiBatches(): Flow<List<AiBatch>>
}
