package com.example.personalbookkeeping.data.transfer

import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

class LedgerFileCodec {
    private val json = Json { prettyPrint = true }

    fun decodeCsv(content: String): ImportPreview {
        val issues = mutableListOf<ImportIssue>()
        val rows = try {
            parseCsv(content.removePrefix("\uFEFF"))
        } catch (exception: CsvFormatException) {
            return ImportPreview(
                LedgerFileFormat.CSV,
                emptyList(),
                listOf(ImportIssue(exception.row, null, exception.message ?: "Malformed CSV")),
                0
            )
        }

        if (rows.isEmpty()) {
            return invalid(LedgerFileFormat.CSV, "CSV file is empty")
        }
        if (rows.first() != CSV_HEADERS) {
            issues += ImportIssue(1, null, "Header must exactly match the v1 CSV schema")
            return ImportPreview(LedgerFileFormat.CSV, emptyList(), issues, 0)
        }

        val parsed = mutableListOf<Pair<Int, LedgerEntry>>()
        rows.drop(1).forEachIndexed { index, row ->
            val rowNumber = index + 2
            if (row.size != CSV_HEADERS.size) {
                issues += ImportIssue(rowNumber, null, "Expected ${CSV_HEADERS.size} columns but found ${row.size}")
            } else {
                val values = CSV_HEADERS.zip(row).toMap()
                if (values["currency"] != CURRENCY) {
                    issues += ImportIssue(rowNumber, "currency", "Only CNY is supported")
                }
                parseRecord(values, rowNumber, issues)?.let { parsed += rowNumber to it }
            }
        }
        val (entries, repeatedRows) = removeExactRepeatedIds(parsed, issues)
        return ImportPreview(LedgerFileFormat.CSV, entries, issues, repeatedRows)
    }

    fun decodeJson(content: String): ImportPreview {
        val root = try {
            json.parseToJsonElement(content) as? JsonObject
                ?: return invalid(LedgerFileFormat.JSON, "Root value must be an object")
        } catch (exception: Exception) {
            return invalid(LedgerFileFormat.JSON, "Invalid JSON: ${exception.message ?: "parse error"}")
        }

        val issues = mutableListOf<ImportIssue>()
        val rootKeys = setOf("schemaVersion", "currency", "exportedAt", "records")
        reportUnexpectedKeys(root, rootKeys, null, issues)
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
        if (schemaVersion != 1) {
            issues += ImportIssue(null, "schemaVersion", "Only schema version 1 is supported")
        }
        val currency = (root["currency"] as? JsonPrimitive)?.contentOrNull
        if (currency != CURRENCY) {
            issues += ImportIssue(null, "currency", "Only CNY is supported")
        }
        val exportedAt = (root["exportedAt"] as? JsonPrimitive)?.contentOrNull
        if (exportedAt == null || parseInstant(exportedAt) == null) {
            issues += ImportIssue(null, "exportedAt", "Expected an ISO-8601 timestamp")
        }
        val records = root["records"] as? JsonArray
        if (records == null) {
            issues += ImportIssue(null, "records", "Expected an array")
            return ImportPreview(LedgerFileFormat.JSON, emptyList(), issues, 0)
        }

        val parsed = mutableListOf<Pair<Int, LedgerEntry>>()
        records.forEachIndexed { index, value ->
            val rowNumber = index + 1
            val record = value as? JsonObject
            if (record == null) {
                issues += ImportIssue(rowNumber, null, "Record must be an object")
            } else {
                reportUnexpectedKeys(record, JSON_RECORD_KEYS, rowNumber, issues)
                val values = JSON_RECORD_KEYS.associateWith { key ->
                    (record[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
                parseRecord(values, rowNumber, issues)?.let { parsed += rowNumber to it }
            }
        }
        val (entries, repeatedRows) = removeExactRepeatedIds(parsed, issues)
        return ImportPreview(LedgerFileFormat.JSON, entries, issues, repeatedRows)
    }

    fun encodeCsv(entries: List<LedgerEntry>): String = buildString {
        append('\uFEFF')
        append(CSV_HEADERS.joinToString(",") { csvEscape(it) })
        append("\r\n")
        entries.filter { it.deletedAt == null }.forEach { entry ->
            val values = listOf(
                entry.id,
                entry.type.name,
                formatAmount(entry.amountFen),
                entry.currency,
                entry.date.toString(),
                entry.category,
                entry.note,
                entry.createdAt.toString(),
                entry.updatedAt.toString()
            )
            append(values.joinToString(",") { csvEscape(it) })
            append("\r\n")
        }
    }

    fun encodeJson(entries: List<LedgerEntry>, exportedAt: Instant = Instant.now()): String {
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("currency", CURRENCY)
            put("exportedAt", exportedAt.toString())
            put("records", buildJsonArray {
                entries.filter { it.deletedAt == null }.forEach { entry ->
                    add(buildJsonObject {
                        put("id", entry.id)
                        put("type", entry.type.name)
                        put("amount", formatAmount(entry.amountFen))
                        put("date", entry.date.toString())
                        put("category", entry.category)
                        put("note", entry.note)
                        put("createdAt", entry.createdAt.toString())
                        put("updatedAt", entry.updatedAt.toString())
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun parseRecord(
        values: Map<String, String?>,
        row: Int,
        issues: MutableList<ImportIssue>
    ): LedgerEntry? {
        val beforeCount = issues.size
        val idValue = values["id"]
        val id = idValue?.let(::parseUuid)
        if (id == null) issues += ImportIssue(row, "id", "Expected a canonical UUID")

        val typeValue = values["type"]
        val type = typeValue?.let { value ->
            runCatching { LedgerType.valueOf(value) }.getOrNull()
        }
        if (type == null) issues += ImportIssue(row, "type", "Expected INCOME or EXPENSE")

        val amountValue = values["amount"]
        val amountFen = amountValue?.let(::parseAmountFen)
        if (amountFen == null) issues += ImportIssue(row, "amount", "Expected a positive decimal amount with exactly two places")

        val dateValue = values["date"]
        val date = dateValue?.let(::parseDate)
        if (date == null) issues += ImportIssue(row, "date", "Expected a valid date in YYYY-MM-DD format")

        val category = values["category"]
        if (category.isNullOrBlank()) issues += ImportIssue(row, "category", "Category is required")

        val note = values["note"]
        if (note == null) issues += ImportIssue(row, "note", "Note field is required; use an empty string for no note")

        val createdAtValue = values["createdAt"]
        val createdAt = createdAtValue?.let(::parseInstant)
        if (createdAt == null) issues += ImportIssue(row, "createdAt", "Expected an ISO-8601 timestamp")

        val updatedAtValue = values["updatedAt"]
        val updatedAt = updatedAtValue?.let(::parseInstant)
        if (updatedAt == null) issues += ImportIssue(row, "updatedAt", "Expected an ISO-8601 timestamp")

        if (issues.size > beforeCount) return null
        if (updatedAt!! < createdAt!!) {
            issues += ImportIssue(row, "updatedAt", "Must not be earlier than createdAt")
            return null
        }
        return runCatching {
            LedgerEntry(
                id = id!!,
                type = type!!,
                amountFen = amountFen!!,
                currency = CURRENCY,
                date = date!!,
                category = category!!,
                note = note!!,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }.getOrElse { exception ->
            issues += ImportIssue(row, null, exception.message ?: "Invalid record")
            null
        }
    }

    private fun removeExactRepeatedIds(
        rows: List<Pair<Int, LedgerEntry>>,
        issues: MutableList<ImportIssue>
    ): Pair<List<LedgerEntry>, Int> {
        val seen = LinkedHashMap<String, LedgerEntry>()
        var repeatedRows = 0
        rows.forEach { (rowNumber, entry) ->
            val previous = seen.putIfAbsent(entry.id, entry)
            if (previous != null) {
                if (previous == entry) {
                    repeatedRows++
                } else {
                    issues += ImportIssue(rowNumber, "id", "This ID appears with conflicting record values")
                }
            }
        }
        return seen.values.toList() to repeatedRows
    }

    private fun reportUnexpectedKeys(
        value: JsonObject,
        allowed: Set<String>,
        row: Int?,
        issues: MutableList<ImportIssue>
    ) {
        value.keys.filterNot(allowed::contains).forEach { key ->
            issues += ImportIssue(row, key, "Unknown field")
        }
        allowed.filterNot(value::containsKey).forEach { key ->
            issues += ImportIssue(row, key, "Required field is missing")
        }
    }

    private fun invalid(format: LedgerFileFormat, reason: String): ImportPreview =
        ImportPreview(format, emptyList(), listOf(ImportIssue(null, null, reason)), 0)

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun formatAmount(amountFen: Long): String {
        val yuan = amountFen / 100
        val fen = amountFen % 100
        return String.format(Locale.ROOT, "%d.%02d", yuan, fen)
    }

    private fun parseAmountFen(value: String): Long? {
        if (!AMOUNT_PATTERN.matches(value)) return null
        return runCatching {
            BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    private fun parseDate(value: String): LocalDate? = runCatching {
        LocalDate.parse(value).takeIf { it.toString() == value }
    }.getOrNull()

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    private fun parseUuid(value: String): String? = runCatching {
        UUID.fromString(value).toString().takeIf { it == value }
    }.getOrNull()

    private fun parseCsv(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var index = 0
        var rowNumber = 1
        var inQuotes = false
        var afterQuote = false
        var atFieldStart = true

        fun finishField() {
            row += field.toString()
            field.setLength(0)
            atFieldStart = true
            afterQuote = false
        }

        fun finishRow() {
            finishField()
            records += row
            row = mutableListOf()
            rowNumber++
        }

        while (index < content.length) {
            val character = content[index]
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < content.length && content[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                        afterQuote = true
                    }
                } else if (character == '\r') {
                    field.append(character)
                    if (index + 1 < content.length && content[index + 1] == '\n') {
                        field.append('\n')
                        index++
                    }
                    rowNumber++
                } else if (character == '\n') {
                    field.append(character)
                    rowNumber++
                } else {
                    field.append(character)
                }
            } else {
                when {
                    afterQuote && character != ',' && character != '\r' && character != '\n' ->
                        throw CsvFormatException(rowNumber, "Unexpected character after closing quote")
                    character == ',' -> finishField()
                    character == '\r' || character == '\n' -> {
                        finishRow()
                        if (character == '\r' && index + 1 < content.length && content[index + 1] == '\n') index++
                    }
                    character == '"' && atFieldStart -> inQuotes = true
                    character == '"' -> throw CsvFormatException(rowNumber, "Quote must begin at the start of a field")
                    else -> {
                        field.append(character)
                        atFieldStart = false
                    }
                }
            }
            index++
        }

        if (inQuotes) throw CsvFormatException(rowNumber, "Unclosed quoted field")
        if (field.isNotEmpty() || row.isNotEmpty() || afterQuote) finishRow()
        return records
    }

    private class CsvFormatException(val row: Int, message: String) : IllegalArgumentException(message)

    private companion object {
        const val CURRENCY = "CNY"
        val CSV_HEADERS = listOf(
            "id", "type", "amount", "currency", "date", "category", "note", "createdAt", "updatedAt"
        )
        val JSON_RECORD_KEYS = setOf("id", "type", "amount", "date", "category", "note", "createdAt", "updatedAt")
        val AMOUNT_PATTERN = Regex("(?:0|[1-9][0-9]*)\\.[0-9]{2}")
    }
}
