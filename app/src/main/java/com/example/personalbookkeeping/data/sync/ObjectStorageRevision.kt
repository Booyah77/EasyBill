package com.example.personalbookkeeping.data.sync

import com.example.personalbookkeeping.domain.model.LedgerEntry

data class ObjectStorageRevision(
    val schemaVersion: Int,
    val recordId: String,
    val revisionId: String,
    val parentRevisionIds: List<String>,
    val entry: LedgerEntry
)

data class ObjectStorageConflict(val recordId: String, val revisions: List<ObjectStorageRevision>)

fun canonicalRecordId(id: String): Boolean = runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)
