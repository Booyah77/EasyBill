package com.example.personalbookkeeping.data.transfer

import com.example.personalbookkeeping.data.repository.ImportCommitResult
import com.example.personalbookkeeping.data.repository.ImportConflictException
import com.example.personalbookkeeping.data.repository.LedgerRepository
import java.time.Instant

data class ImportAttempt(
    val preview: ImportPreview,
    val commitResult: ImportCommitResult? = null
)

class LedgerFileTransfer(
    private val repository: LedgerRepository,
    private val codec: LedgerFileCodec = LedgerFileCodec()
) {
    fun previewCsv(content: String): ImportPreview = codec.decodeCsv(content)

    fun previewJson(content: String): ImportPreview = codec.decodeJson(content)

    suspend fun commit(preview: ImportPreview): ImportAttempt {
        require(preview.canImport) { "An import with validation errors cannot be committed" }
        return try {
            ImportAttempt(preview, repository.importEntries(preview.entries))
        } catch (exception: ImportConflictException) {
            val issue = ImportIssue(null, "id", exception.message ?: "Imported data conflicts with local data")
            ImportAttempt(preview.copy(issues = preview.issues + issue))
        }
    }

    suspend fun exportCsv(): String = codec.encodeCsv(repository.getAllActiveEntries())

    suspend fun exportJson(exportedAt: Instant = Instant.now()): String =
        codec.encodeJson(repository.getAllActiveEntries(), exportedAt)
}
