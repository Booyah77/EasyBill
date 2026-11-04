package com.example.personalbookkeeping.data.ai

import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

const val MAX_AI_INPUT_CHARS = 6000
const val MAX_AI_RESPONSE_BYTES = 131072
const val MAX_AI_RECORDS = 100

@Serializable
private data class CompletionEnvelope(val choices: List<CompletionChoice>)

@Serializable
private data class CompletionChoice(val finish_reason: String? = null, val message: CompletionMessage)

@Serializable
private data class CompletionMessage(val role: String, val content: String? = null, val refusal: String? = null)

@Serializable
private data class RecognitionPayload(val records: List<RecognizedRecord>)

@Serializable
private data class RecognizedRecord(
    val type: String,
    val amount: String,
    val date: String,
    val category: String,
    val note: String
)

class AiResponseDecoder {
    private val envelopeJson = Json { ignoreUnknownKeys = true }
    private val resultJson = Json { ignoreUnknownKeys = false; coerceInputValues = false }

    fun decode(
        response: String,
        categories: Map<LedgerType, List<String>>,
        createdAt: Instant,
        newId: () -> String = { UUID.randomUUID().toString() }
    ): List<LedgerEntry> = try {
        require(response.toByteArray(Charsets.UTF_8).size <= MAX_AI_RESPONSE_BYTES)
        val choice = envelopeJson.decodeFromString<CompletionEnvelope>(response).choices.single()
        require(choice.finish_reason == "stop" && choice.message.role == "assistant" && choice.message.refusal == null)
        val content = choice.message.content ?: error("Missing content")
        require(content.isNotBlank())
        // Serialization can accept numeric JSON primitives for String properties; enforce the wire contract first.
        val payload = resultJson.parseToJsonElement(content).jsonObject
        val wireRecords = payload.getValue("records").jsonArray
        require(wireRecords.isNotEmpty() && wireRecords.size <= MAX_AI_RECORDS)
        wireRecords.forEach { element ->
            val fields = element.jsonObject
            require(fields.keys == setOf("type", "amount", "date", "category", "note"))
            require(fields.values.all { it is JsonPrimitive && it.isString })
        }
        val records = resultJson.decodeFromString<RecognitionPayload>(content).records
        require(records.isNotEmpty() && records.size <= MAX_AI_RECORDS)
        var total = 0L
        val batchCategories = LedgerType.entries.associateWith { type ->
            categories.getValue(type).toMutableList()
        }
        records.map { record ->
            val type = LedgerType.valueOf(record.type)
            require(record.amount.length <= 30 && record.amount.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?")))
            val fen = BigDecimal(record.amount).movePointRight(2).longValueExact()
            require(fen > 0)
            total = Math.addExact(total, fen)
            require(record.date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val date = LocalDate.parse(record.date)
            val category = LedgerCategories.resolve(type, record.category, batchCategories)
            if (category !in batchCategories.getValue(type)) batchCategories.getValue(type).add(category)
            val note = record.note.trim()
            require(note.isNotBlank() && note.length <= 500 && note.none { it == '\u0000' })
            LedgerEntry(
                id = newId(), type = type, amountFen = fen, date = date,
                category = category, note = note, createdAt = createdAt, updatedAt = createdAt
            )
        }.also { entries -> require(entries.map { it.id }.distinct().size == entries.size) }
    } catch (exception: Exception) {
        throw AiException(AiFailure.RESPONSE)
    }
}
