package com.example.personalbookkeeping.data.ai

import com.example.personalbookkeeping.data.repository.ImportCommitResult
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class AiPhase { IDLE, RECOGNIZING, SAVING }

sealed interface AiBookkeepingResult {
    data class Saved(val entries: List<LedgerEntry>, val commit: ImportCommitResult) : AiBookkeepingResult
    data class Failed(val failure: AiFailure, val canRetrySave: Boolean = false) : AiBookkeepingResult
    data object Busy : AiBookkeepingResult
}

class AiBookkeepingManager(
    private val configuration: AiConfiguration,
    private val client: ChatCompletionsClient,
    private val repository: LedgerRepository,
    private val decoder: AiResponseDecoder = AiResponseDecoder(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val operation = Mutex()
    private var pendingBatch: List<LedgerEntry>? = null
    val hasPendingSave: Boolean get() = pendingBatch != null

    suspend fun recognizeAndSave(text: String, onPhase: (AiPhase) -> Unit = {}): AiBookkeepingResult {
        if (!operation.tryLock()) return AiBookkeepingResult.Busy
        try {
            if (pendingBatch != null) return AiBookkeepingResult.Failed(AiFailure.SAVE, canRetrySave = true)
            if (text.isBlank() || text.length > MAX_AI_INPUT_CHARS) return AiBookkeepingResult.Failed(AiFailure.INPUT)
            // Capture before any IO; crossing midnight must not change this request's date.
            val requestDate = LocalDate.now(clock)
            onPhase(AiPhase.RECOGNIZING)
            val credentials = try {
                withContext(Dispatchers.IO) { configuration.loadCredentials() }
                    ?: return AiBookkeepingResult.Failed(AiFailure.NOT_CONFIGURED)
            } catch (exception: AiException) {
                return AiBookkeepingResult.Failed(exception.failure)
            }
            val entries = try {
                repository.getAllActiveEntries()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                return AiBookkeepingResult.Failed(AiFailure.LOCAL_READ)
            }
            val categories = LedgerCategories.available(entries)
            val response = client.complete(credentials, AiRecognitionRequest(text.trim(), requestDate, categories))
            coroutineContext.ensureActive()
            val recognized = withContext(Dispatchers.Default) {
                decoder.decode(response, categories, Instant.now(clock), newId)
            }
            coroutineContext.ensureActive()
            pendingBatch = recognized.toList()
            return savePending(onPhase)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AiException) {
            return AiBookkeepingResult.Failed(exception.failure, hasPendingSave)
        } catch (exception: Exception) {
            return AiBookkeepingResult.Failed(AiFailure.SERVICE, hasPendingSave)
        } finally {
            onPhase(AiPhase.IDLE)
            operation.unlock()
        }
    }

    suspend fun retrySave(onPhase: (AiPhase) -> Unit = {}): AiBookkeepingResult {
        if (!operation.tryLock()) return AiBookkeepingResult.Busy
        try {
            return savePending(onPhase)
        } finally {
            onPhase(AiPhase.IDLE)
            operation.unlock()
        }
    }

    private suspend fun savePending(onPhase: (AiPhase) -> Unit): AiBookkeepingResult {
        val batch = pendingBatch ?: return AiBookkeepingResult.Failed(AiFailure.INPUT)
        onPhase(AiPhase.SAVING)
        return try {
            coroutineContext.ensureActive()
            val commit = repository.importEntries(batch)
            pendingBatch = null
            AiBookkeepingResult.Saved(batch, commit)
        } catch (exception: CancellationException) {
            // Keep the same batch if cancellation races with the commit result.
            throw exception
        } catch (exception: Exception) {
            AiBookkeepingResult.Failed(AiFailure.SAVE, canRetrySave = true)
        }
    }
}
