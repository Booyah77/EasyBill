package com.example.personalbookkeeping.data.local

import androidx.room.Entity

@Entity(tableName = "object_sync_conflicts", primaryKeys = ["storageId", "recordId", "revisionId"])
data class ObjectSyncConflictEntity(
    val storageId: String,
    val recordId: String,
    val revisionId: String,
    val revisionJson: String
)
