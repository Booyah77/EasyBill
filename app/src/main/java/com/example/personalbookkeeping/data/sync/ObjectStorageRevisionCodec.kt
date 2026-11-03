package com.example.personalbookkeeping.data.sync

import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ObjectStorageRevisionCodec {
    private val json = Json { isLenient = false; ignoreUnknownKeys = false }

    fun create(entry: LedgerEntry, parents: List<String>): ObjectStorageRevision {
        require(canonicalRecordId(entry.id) && parents.distinct().size == parents.size)
        val sorted = parents.sorted()
        val id = sha256(content(entry, sorted).toString().toByteArray(Charsets.UTF_8))
        return ObjectStorageRevision(1, entry.id, id, sorted, entry)
    }

    fun encode(revision: ObjectStorageRevision): ByteArray {
        require(create(revision.entry, revision.parentRevisionIds) == revision)
        return buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("recordId", JsonPrimitive(revision.recordId))
            put("revisionId", JsonPrimitive(revision.revisionId))
            put("parentRevisionIds", JsonArray(revision.parentRevisionIds.map(::JsonPrimitive)))
            put("entry", entryJson(revision.entry))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): ObjectStorageRevision {
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.keys == setOf("schemaVersion", "recordId", "revisionId", "parentRevisionIds", "entry"))
        require(root.getValue("schemaVersion").jsonPrimitive.int == 1)
        val recordId = root.getValue("recordId").jsonPrimitive.content
        val revisionId = root.getValue("revisionId").jsonPrimitive.content
        val parents = root.getValue("parentRevisionIds").jsonArray.map { it.jsonPrimitive.content }
        require(parents == parents.distinct().sorted() && parents.all { it.matches(Regex("[0-9a-f]{64}")) })
        val entry = parseEntry(root.getValue("entry").jsonObject)
        require(entry.id == recordId)
        return create(entry, parents).also { require(it.revisionId == revisionId) }
    }

    fun key(settings: ObjectStorageSettings, revision: ObjectStorageRevision): String =
        "${settings.keyPrefix}${revision.recordId}/revisions/${revision.revisionId}.json"

    fun validateKey(settings: ObjectStorageSettings, key: String, revision: ObjectStorageRevision) {
        require(key == key(settings, revision)) { "Revision path does not match content" }
    }

    private fun content(entry: LedgerEntry, parents: List<String>): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(1))
        put("recordId", JsonPrimitive(entry.id))
        put("parentRevisionIds", JsonArray(parents.map(::JsonPrimitive)))
        put("entry", entryJson(entry))
    }

    private fun entryJson(entry: LedgerEntry): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entry.id))
        put("type", JsonPrimitive(entry.type.name))
        put("amountFen", JsonPrimitive(entry.amountFen))
        put("currency", JsonPrimitive(entry.currency))
        put("date", JsonPrimitive(entry.date.toString()))
        put("category", JsonPrimitive(entry.category))
        put("note", JsonPrimitive(entry.note))
        put("createdAt", JsonPrimitive(entry.createdAt.toString()))
        put("updatedAt", JsonPrimitive(entry.updatedAt.toString()))
        put("deletedAt", entry.deletedAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
    }

    private fun parseEntry(value: JsonObject): LedgerEntry {
        require(value.keys == setOf("id", "type", "amountFen", "currency", "date", "category", "note", "createdAt", "updatedAt", "deletedAt"))
        fun string(name: String) = value.getValue(name).jsonPrimitive.content
        return LedgerEntry(
            id = string("id"), type = LedgerType.valueOf(string("type")),
            amountFen = value.getValue("amountFen").jsonPrimitive.long,
            currency = string("currency"), date = LocalDate.parse(string("date")),
            category = string("category"), note = string("note"),
            createdAt = Instant.parse(string("createdAt")), updatedAt = Instant.parse(string("updatedAt")),
            deletedAt = value.getValue("deletedAt").takeIf { it !is JsonNull }?.let { Instant.parse(it.jsonPrimitive.content) }
        )
    }
}
