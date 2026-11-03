package com.example.personalbookkeeping.data.repository

import androidx.room.withTransaction
import com.example.personalbookkeeping.data.local.LedgerDatabase
import com.example.personalbookkeeping.data.local.ObjectSyncConflictEntity
import com.example.personalbookkeeping.data.local.ObjectSyncRecordStateEntity
import com.example.personalbookkeeping.data.local.toDomain
import com.example.personalbookkeeping.data.local.toEntity
import com.example.personalbookkeeping.data.sync.ObjectStorageConflict
import com.example.personalbookkeeping.data.sync.ObjectStorageRevisionCodec
import com.example.personalbookkeeping.domain.model.LedgerEntry

class RoomObjectStorageSyncRepository(private val database: LedgerDatabase) : ObjectStorageSyncRepository {
    private val ledger = database.ledgerEntryDao()
    private val state = database.objectSyncStateDao()
    private val codec = ObjectStorageRevisionCodec()

    override suspend fun localEntries(): List<LedgerEntry> = ledger.getAllSyncEntries().map { it.toDomain() }

    override suspend fun acceptedHeads(storageId: String): Map<String, String> =
        state.states(storageId).associate { it.recordId to it.revisionId }

    override suspend fun conflicts(storageId: String): List<ObjectStorageConflict> =
        state.conflicts(storageId).groupBy { it.recordId }.map { (recordId, variants) ->
            ObjectStorageConflict(recordId, variants.map { codec.decode(it.revisionJson.toByteArray(Charsets.UTF_8)) })
        }

    override suspend fun saveConflicts(storageId: String, conflicts: List<ObjectStorageConflict>) {
        database.withTransaction {
            state.clearConflicts(storageId)
            state.putConflicts(conflicts.flatMap { conflict -> conflict.revisions.map { revision ->
                ObjectSyncConflictEntity(storageId, conflict.recordId, revision.revisionId,
                    codec.encode(revision).toString(Charsets.UTF_8))
            } })
        }
    }

    override suspend fun replaceResolvedSnapshot(storageId: String, entries: List<LedgerEntry>, acceptedHeads: Map<String, String>) {
        require(entries.map { it.id }.toSet() == acceptedHeads.keys)
        database.withTransaction {
            val currentIds = ledger.getAllSyncEntries().map { it.id }.toSet()
            require(entries.map { it.id }.toSet().containsAll(currentIds)) { "Snapshot would lose local record IDs" }
            ledger.replaceAll(entries.map { it.toEntity() })
            state.clearStates(storageId)
            state.putStates(acceptedHeads.map { (recordId, revisionId) ->
                ObjectSyncRecordStateEntity(storageId, recordId, revisionId)
            })
            state.clearConflicts(storageId)
        }
    }
}
