package com.example.personalbookkeeping.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.personalbookkeeping.data.ai.AiBookkeepingManager
import com.example.personalbookkeeping.data.ai.AiConfiguration
import com.example.personalbookkeeping.data.ai.AiCredentials
import com.example.personalbookkeeping.data.ai.AiPhase
import com.example.personalbookkeeping.data.ai.AiSettings
import com.example.personalbookkeeping.data.ai.ChatCompletionsClient
import com.example.personalbookkeeping.data.repository.ImportCommitResult
import com.example.personalbookkeeping.data.repository.LedgerFilter
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiBookkeepingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = ViewModelLedgerRepository()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test
    fun `home reentry clears completed result draft and selection while keeping history`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val model = model(savedState)
        model.updateDraft("午饭10元")
        model.recognize()
        val saved = model.uiState.first { it.currentBatchId != null && !it.busy && it.savedEntries.isNotEmpty() }
        assertEquals(1, saved.savedEntries.size)
        assertEquals(1, saved.history.size)
        model.updateDraft("普通草稿")
        model.enterFromHome()
        assertEquals("", model.uiState.value.draft)
        assertEquals("", savedState.get<String>("ai_draft"))
        assertNull(model.uiState.value.currentBatchId)
        assertTrue(model.uiState.value.savedEntries.isEmpty())
        assertEquals(saved.history, model.uiState.value.history)
    }

    @Test
    fun `history updates selected current values and retains selection when all deleted`() = runTest(dispatcher) {
        val model = model()
        model.updateDraft("午饭10元")
        model.recognize()
        val saved = model.uiState.first { it.currentBatchId != null && !it.busy && it.savedEntries.isNotEmpty() }
        val batch = saved.history.single()
        val edited = batch.entries.single().copy(amountFen = 2500, note = "已更正")
        repository.history.value = listOf(batch.copy(entries = listOf(edited)))
        runCurrent()
        assertEquals(listOf(edited), model.uiState.value.savedEntries)
        repository.history.value = listOf(batch.copy(entries = emptyList()))
        runCurrent()
        assertEquals(batch.id, model.uiState.value.currentBatchId)
        assertTrue(model.uiState.value.savedEntries.isEmpty())
        assertEquals(1, model.uiState.value.history.single().originalCount)
        model.newDraft()
        assertNull(model.uiState.value.currentBatchId)
        assertEquals(1, model.uiState.value.history.size)
    }

    @Test
    fun `saved selection distinguishes delayed history from a known all deleted batch`() = runTest(dispatcher) {
        repository.publishHistoryOnSave = false
        val model = model()
        model.updateDraft("午饭10元")
        model.recognize()
        model.uiState.first { it.currentBatchId != null && !it.busy }
        runCurrent()

        val commit = repository.calls.single()
        val awaitingHistory = model.uiState.value
        assertEquals(commit.id, awaitingHistory.currentBatchId)
        assertNull(awaitingHistory.history.firstOrNull { it.id == awaitingHistory.currentBatchId })
        assertTrue(awaitingHistory.savedEntries.isEmpty())
        assertFalse(awaitingHistory.canRetrySave)

        val populatedBatch = AiBatch(commit.id, commit.savedAt, commit.entries.size, commit.entries)
        repository.history.value = listOf(populatedBatch)
        runCurrent()
        val populated = model.uiState.value
        assertEquals(commit.id, populated.currentBatchId)
        assertEquals(populatedBatch, populated.history.firstOrNull { it.id == populated.currentBatchId })
        assertEquals(commit.entries, populated.savedEntries)

        val deletedBatch = populatedBatch.copy(entries = emptyList())
        repository.history.value = listOf(deletedBatch)
        runCurrent()
        val allDeleted = model.uiState.value
        assertEquals(commit.id, allDeleted.currentBatchId)
        assertEquals(deletedBatch, allDeleted.history.firstOrNull { it.id == allDeleted.currentBatchId })
        assertEquals(commit.entries.size, allDeleted.history.single().originalCount)
        assertTrue(allDeleted.savedEntries.isEmpty())
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `restored and returning drafts survive settings loading and live history emissions`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("ai_draft" to "未提交的草稿"))
        val model = model(savedState)
        model.uiState.first { !it.loadingSettings }
        val entry = fixtureEntry()
        repository.history.value = listOf(AiBatch("old", entry.createdAt, 1, listOf(entry)))
        runCurrent()
        assertEquals("未提交的草稿", model.uiState.value.draft)
        assertNull(model.uiState.value.currentBatchId)
        assertTrue(model.uiState.value.savedEntries.isEmpty())
        assertEquals(1, model.uiState.value.history.size)
        model.newDraft()
        assertEquals("未提交的草稿", model.uiState.value.draft)
        assertEquals("未提交的草稿", savedState.get<String>("ai_draft"))
    }

    @Test
    fun `pending save protects batch and draft from home new draft and edits until retry`() = runTest(dispatcher) {
        repository.failSave = true
        val model = model()
        model.updateDraft("午饭10元")
        model.recognize()
        model.uiState.first { it.canRetrySave && !it.busy }
        model.enterFromHome()
        model.newDraft()
        model.updateDraft("不得替换")
        assertEquals("午饭10元", model.uiState.value.draft)
        assertTrue(model.uiState.value.canRetrySave)
        repository.failSave = false
        model.retrySave()
        val saved = model.uiState.first { it.currentBatchId != null && !it.busy && it.savedEntries.isNotEmpty() }
        assertEquals(repository.calls[0], repository.calls[1])
        assertEquals(repository.calls[0].id, saved.currentBatchId)
        assertFalse(saved.canRetrySave)
    }

    @Test
    fun `active recognition protects draft and cancellation returns without history`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val model = model(client = ChatCompletionsClient { _, _ -> started.complete(Unit); awaitCancellation() })
        model.updateDraft("午饭10元")
        model.recognize()
        started.await()
        model.enterFromHome()
        model.newDraft()
        assertEquals("午饭10元", model.uiState.value.draft)
        assertEquals(AiPhase.RECOGNIZING, model.uiState.value.phase)
        model.cancelRecognition()
        model.uiState.first { !it.busy }
        assertEquals("午饭10元", model.uiState.value.draft)
        assertTrue(repository.calls.isEmpty())
        assertFalse(model.uiState.value.canRetrySave)
    }

    @Test
    fun `saving protects pending batch and ignores recognition cancellation`() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        repository.saveGate = saveGate
        val model = model()
        model.updateDraft("午饭10元")
        model.recognize()
        model.uiState.first { it.phase == AiPhase.SAVING }
        model.enterFromHome()
        model.newDraft()
        model.cancelRecognition()
        assertEquals(AiPhase.SAVING, model.uiState.value.phase)
        assertEquals("午饭10元", model.uiState.value.draft)
        saveGate.complete(Unit)
        model.uiState.first { it.currentBatchId != null && !it.busy }
        assertEquals(1, repository.calls.size)
    }

    private fun model(
        savedState: SavedStateHandle = SavedStateHandle(),
        client: ChatCompletionsClient = ChatCompletionsClient { _, _ -> VIEW_MODEL_RESPONSE }
    ): AiBookkeepingViewModel {
        val configuration = ViewModelAiConfiguration()
        val manager = AiBookkeepingManager(configuration, client, repository)
        return AiBookkeepingViewModel(savedState, repository, manager, { AiSettings() }, { _, _ -> }, {}).also {
            store.put("ai", it)
        }
    }
}

private class ViewModelAiConfiguration : AiConfiguration {
    override fun loadSettings() = AiSettings()
    override fun loadCredentials() = AiCredentials(AiSettings(), "fixture-key")
}

private class ViewModelLedgerRepository : LedgerRepository {
    data class Call(val id: String, val savedAt: Instant, val entries: List<LedgerEntry>)
    val history = MutableStateFlow<List<AiBatch>>(emptyList())
    val calls = mutableListOf<Call>()
    var failSave = false
    var publishHistoryOnSave = true
    var saveGate: CompletableDeferred<Unit>? = null
    override fun observeAiBatches() = history
    override suspend fun saveAiBatch(batchId: String, savedAt: Instant, entries: List<LedgerEntry>): ImportCommitResult {
        calls += Call(batchId, savedAt, entries)
        saveGate?.await()
        if (failSave) throw IOException("Fixture save failure")
        if (publishHistoryOnSave) history.value = listOf(AiBatch(batchId, savedAt, entries.size, entries))
        return ImportCommitResult(entries.size, 0)
    }
    override fun observeEntries(filter: LedgerFilter) = flowOf(emptyList<LedgerEntry>())
    override fun observeMonthlyTotals(month: YearMonth) = flowOf(MonthlyTotals(month, 0, 0))
    override fun observeAllActiveEntries() = flowOf(emptyList<LedgerEntry>())
    override suspend fun getAllActiveEntries() = emptyList<LedgerEntry>()
    override suspend fun getAllSyncEntries() = emptyList<LedgerEntry>()
    override suspend fun applyRemoteEntriesAtomically(entries: List<LedgerEntry>) = error("Unexpected replacement")
    override suspend fun save(entry: LedgerEntry) = error("Unexpected single save")
    override suspend fun delete(id: String) = error("Unexpected delete")
    override suspend fun importEntries(entries: List<LedgerEntry>): ImportCommitResult = error("Expected atomic AI save")
}

private fun fixtureEntry() = LedgerEntry(
    type = LedgerType.EXPENSE, amountFen = 1000, category = "餐饮", date = LocalDate.of(2026, 11, 4),
    createdAt = Instant.parse("2026-11-04T01:00:00Z")
)

private const val VIEW_MODEL_RESPONSE = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"{\"records\":[{\"type\":\"EXPENSE\",\"amount\":\"10.00\",\"date\":\"2026-11-04\",\"category\":\"餐饮\",\"note\":\"午饭\"}]}"}}]}"""
