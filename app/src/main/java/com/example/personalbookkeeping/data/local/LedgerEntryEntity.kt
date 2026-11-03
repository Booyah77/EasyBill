package com.example.personalbookkeeping.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
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

fun LedgerEntryEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = id,
    type = LedgerType.valueOf(type),
    amountFen = amountFen,
    currency = currency,
    date = LocalDate.parse(date),
    category = category,
    note = note,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    deletedAt = deletedAt?.let(Instant::parse)
)

fun LedgerEntry.toEntity(): LedgerEntryEntity = LedgerEntryEntity(
    id = id,
    type = type.name,
    amountFen = amountFen,
    currency = currency,
    date = date.toString(),
    category = category,
    note = note,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString()
)
