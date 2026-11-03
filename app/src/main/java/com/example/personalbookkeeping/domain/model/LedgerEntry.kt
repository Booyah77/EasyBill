package com.example.personalbookkeeping.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class LedgerType {
    INCOME,
    EXPENSE
}

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: LedgerType,
    val amountFen: Long,
    val currency: String = "CNY",
    val date: LocalDate,
    val category: String,
    val note: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(amountFen > 0) { "amountFen must be positive" }
        require(currency == "CNY") { "Only CNY is supported" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(updatedAt >= createdAt) { "updatedAt must not be earlier than createdAt" }
    }
}

data class MonthlyTotals(
    val month: YearMonth,
    val incomeFen: Long,
    val expenseFen: Long
) {
    val balanceFen: Long
        get() = incomeFen - expenseFen
}
