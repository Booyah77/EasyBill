package com.example.personalbookkeeping.data.repository

import androidx.room.withTransaction
import com.example.personalbookkeeping.data.local.AiBatchEntryEntity
import com.example.personalbookkeeping.data.local.LedgerDatabase
import com.example.personalbookkeeping.data.local.LedgerEntryDao
import com.example.personalbookkeeping.data.local.toDomain
import com.example.personalbookkeeping.data.local.toEntity
import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ImportConflictException(message: String) : IllegalArgumentException(message)

class RoomLedgerRepository(
    private val dao: LedgerEntryDao,
    private val database: LedgerDatabase
) : LedgerRepository {
    override fun observeEntries(filter: LedgerFilter): Flow<List<LedgerEntry>> {
        val startDate = filter.month?.atDay(1)?.toString()
        val endDateExclusive = filter.month?.plusMonths(1)?.atDay(1)?.toString()
        return dao.observeEntries(startDate, endDateExclusive, filter.type?.name, filter.category)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeMonthlyTotals(month: YearMonth): Flow<MonthlyTotals> {
        val startDate = month.atDay(1)
        val endDateExclusive = month.plusMonths(1).atDay(1)
        return dao.observeMonthlyTotals(startDate.toString(), endDateExclusive.toString())
            .map { row -> MonthlyTotals(month, row.incomeFen, row.expenseFen) }
    }

    override fun observeAllActiveEntries(): Flow<List<LedgerEntry>> =
        dao.observeAllActiveEntries().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllActiveEntries(): List<LedgerEntry> =
        dao.getAllActiveEntries().map { it.toDomain() }

    override suspend fun getAllSyncEntries(): List<LedgerEntry> =
        dao.getAllSyncEntries().map { it.toDomain() }

    override suspend fun applyRemoteEntriesAtomically(entries: List<LedgerEntry>) {
        require(entries.map { it.id }.distinct().size == entries.size) {
            "Remote snapshot contains duplicate record IDs"
        }
        database.withTransaction {
            if (entries.isNotEmpty()) {
                dao.replaceAll(entries.map { it.toEntity() })
            }
        }
    }

    override suspend fun save(entry: LedgerEntry) {
        database.withTransaction {
            val existing = dao.getById(entry.id)
            if (existing == null) {
                dao.insert(entry.toEntity())
            } else {
                check(existing.deletedAt == null) { "Deleted entries cannot be edited" }
                val changedRows = dao.update(
                    id = entry.id,
                    type = entry.type.name,
                    amountFen = entry.amountFen,
                    currency = entry.currency,
                    date = entry.date.toString(),
                    category = entry.category,
                    note = entry.note,
                    updatedAt = entry.updatedAt.toString()
                )
                check(changedRows == 1) { "Entry was not updated" }
            }
        }
    }

    override suspend fun delete(id: String) {
        val now = Instant.now().toString()
        check(dao.markDeleted(id, now) == 1) { "Active entry was not found" }
    }

    override fun observeAiBatches(): Flow<List<AiBatch>> =
        database.aiBatchEntryDao().observeBatchesWithCurrentEntries().map { rows ->
            rows.groupBy { it.membership.batchId }.map { (id, members) ->
                AiBatch(
                    id = id,
                    savedAt = Instant.parse(members.first().membership.batchSavedAt),
                    originalCount = members.size,
                    entries = members.mapNotNull { it.entry?.takeIf { row -> row.deletedAt == null }?.toDomain() }
                )
            }.sortedWith(compareByDescending<AiBatch> { it.savedAt }.thenByDescending { it.id })
        }

    override suspend fun saveAiBatch(
        batchId: String,
        savedAt: Instant,
        entries: List<LedgerEntry>
    ): ImportCommitResult = database.withTransaction {
        require(batchId.isNotBlank() && entries.isNotEmpty()) { "AI batch must have an identity and entries" }
        require(entries.map { it.id }.distinct().size == entries.size) { "AI batch contains duplicate entry IDs" }
        val membership = entries.mapIndexed { position, entry ->
            AiBatchEntryEntity(entry.id, batchId, savedAt.toString(), position)
        }
        val historyDao = database.aiBatchEntryDao()
        val existing = historyDao.getBatchMembership(batchId)
        if (existing.isNotEmpty()) {
            if (existing != membership) throw ImportConflictException("AI batch identity has conflicting membership")
            // Current ledger values may have been edited, deleted or replaced since an uncertain commit.
            return@withTransaction ImportCommitResult(0, entries.size)
        }
        val commit = importEntries(entries)
        historyDao.insertAll(membership)
        commit
    }

    override suspend fun importEntries(entries: List<LedgerEntry>): ImportCommitResult = database.withTransaction {
        val uniqueEntries = LinkedHashMap<String, LedgerEntry>()
        var duplicateCount = 0

        entries.forEach { entry ->
            val previous = uniqueEntries.putIfAbsent(entry.id, entry)
            if (previous != null) {
                if (previous != entry) {
                    throw ImportConflictException("Conflicting records share id ${entry.id}")
                }
                duplicateCount++
            }
        }

        val newEntries = mutableListOf<LedgerEntry>()
        uniqueEntries.values.forEach { entry ->
            val existing = dao.getById(entry.id)
            if (existing == null) {
                newEntries += entry
            } else if (existing.toDomain() == entry) {
                duplicateCount++
            } else {
                throw ImportConflictException("Imported record conflicts with local record ${entry.id}")
            }
        }

        if (newEntries.isNotEmpty()) {
            dao.insertAll(newEntries.map { it.toEntity() })
        }
        ImportCommitResult(newEntries.size, duplicateCount)
    }
}
