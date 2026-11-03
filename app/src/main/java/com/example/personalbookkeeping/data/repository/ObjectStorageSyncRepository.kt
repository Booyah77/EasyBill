package com.example.personalbookkeeping.data.repository

import com.example.personalbookkeeping.data.sync.ObjectStorageConflict
import com.example.personalbookkeeping.domain.model.LedgerEntry

interface ObjectStorageSyncRepository {
    suspend fun localEntries(): List<LedgerEntry>
    suspend fun acceptedHeads(storageId: String): Map<String, String>
    suspend fun conflicts(storageId: String): List<ObjectStorageConflict>
    suspend fun saveConflicts(storageId: String, conflicts: List<ObjectStorageConflict>)
    suspend fun replaceResolvedSnapshot(storageId: String, entries: List<LedgerEntry>, acceptedHeads: Map<String, String>)
}
