package com.example.personalbookkeeping.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// No ledger foreign key: sync REPLACE and deletion must preserve local provenance.
@Entity(tableName = "ai_batch_entries", indices = [Index(value = ["batchId"])])
data class AiBatchEntryEntity(
    @PrimaryKey val entryId: String,
    val batchId: String,
    val batchSavedAt: String,
    val position: Int
)

data class AiBatchEntryWithLedger(
    @Embedded val membership: AiBatchEntryEntity,
    @Embedded(prefix = "ledger_") val entry: LedgerEntryEntity?
)
