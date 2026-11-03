package com.example.personalbookkeeping.data.transfer

import com.example.personalbookkeeping.domain.model.LedgerEntry

enum class LedgerFileFormat {
    CSV,
    JSON
}

data class ImportIssue(
    val row: Int?,
    val field: String?,
    val reason: String
)

data class ImportPreview(
    val format: LedgerFileFormat,
    val entries: List<LedgerEntry>,
    val issues: List<ImportIssue>,
    val repeatedRows: Int
) {
    val canImport: Boolean
        get() = issues.isEmpty()
}
