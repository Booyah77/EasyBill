package com.example.personalbookkeeping.data.local

import androidx.room.Entity

@Entity(tableName = "object_sync_record_states", primaryKeys = ["storageId", "recordId"])
data class ObjectSyncRecordStateEntity(
    val storageId: String,
    val recordId: String,
    val revisionId: String
)
