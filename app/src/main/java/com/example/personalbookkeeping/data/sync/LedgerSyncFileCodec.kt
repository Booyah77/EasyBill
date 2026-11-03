package com.example.personalbookkeeping.data.sync

import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class LedgerSyncFileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }

    fun encode(entry: LedgerEntry): ByteArray {
        require(isCanonicalUuid(entry.id)) { "Record ID is not a canonical UUID" }
        return json.encodeToString(
            SyncRecord(
                schemaVersion = SCHEMA_VERSION,
                id = entry.id,
                type = entry.type.name,
                amountFen = entry.amountFen,
                currency = entry.currency,
                date = entry.date.toString(),
                category = entry.category,
                note = entry.note,
                createdAt = entry.createdAt.toString(),
                updatedAt = entry.updatedAt.toString(),
                deletedAt = entry.deletedAt?.toString()
            )
        ).toByteArray(Charsets.UTF_8)
    }

    fun decode(expectedId: String, bytes: ByteArray): LedgerEntry {
        if (!isCanonicalUuid(expectedId)) throw InvalidSyncRecordException("Record path has an invalid ID")
        val record = try {
            json.decodeFromString<SyncRecord>(bytes.toString(Charsets.UTF_8))
        } catch (exception: Exception) {
            throw InvalidSyncRecordException("Sync record JSON is invalid", exception)
        }
        if (record.schemaVersion != SCHEMA_VERSION || record.id != expectedId) {
            throw InvalidSyncRecordException("Sync record schema or ID does not match its path")
        }
        if (record.currency != CURRENCY) throw InvalidSyncRecordException("Sync record currency is unsupported")
        val type = runCatching { LedgerType.valueOf(record.type) }.getOrNull()
            ?: throw InvalidSyncRecordException("Sync record type is invalid")
        val date = runCatching { LocalDate.parse(record.date) }.getOrNull()
            ?.takeIf { it.toString() == record.date }
            ?: throw InvalidSyncRecordException("Sync record date is invalid")
        val createdAt = runCatching { Instant.parse(record.createdAt) }.getOrNull()
            ?: throw InvalidSyncRecordException("Sync record creation timestamp is invalid")
        val updatedAt = runCatching { Instant.parse(record.updatedAt) }.getOrNull()
            ?: throw InvalidSyncRecordException("Sync record update timestamp is invalid")
        val deletedAt = record.deletedAt?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
                ?: throw InvalidSyncRecordException("Sync record deletion timestamp is invalid")
        }
        return try {
            LedgerEntry(
                id = record.id,
                type = type,
                amountFen = record.amountFen,
                currency = record.currency,
                date = date,
                category = record.category,
                note = record.note,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt
            )
        } catch (exception: IllegalArgumentException) {
            throw InvalidSyncRecordException("Sync record fields are invalid", exception)
        }
    }

    private fun isCanonicalUuid(value: String): Boolean = runCatching {
        UUID.fromString(value).toString() == value
    }.getOrDefault(false)

    @Serializable
    private data class SyncRecord(
        val schemaVersion: Int,
        val id: String,
        val type: String,
        val amountFen: Long,
        val currency: String,
        val date: String,
        val category: String,
        val note: String,
        val createdAt: String,
        val updatedAt: String,
        val deletedAt: String?
    )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val CURRENCY = "CNY"
    }
}

internal class InvalidSyncRecordException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
