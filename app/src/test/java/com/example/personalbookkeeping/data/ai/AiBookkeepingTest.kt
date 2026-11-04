package com.example.personalbookkeeping.data.ai

import com.example.personalbookkeeping.data.repository.ImportCommitResult
import com.example.personalbookkeeping.data.repository.LedgerFilter
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.domain.model.MonthlyTotals
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiBookkeepingTest {
    private val instant = Instant.parse("2026-11-03T16:30:00Z")
    private val clock = Clock.fixed(instant, ZoneId.of("Asia/Shanghai"))
    private val requestDate = LocalDate.of(2026, 11, 4)
    private val credentials = AiCredentials(AiSettings(), "test-key")
    private val configured = FakeAiConfiguration(credentials)
    private val defaults = LedgerCategories.available(emptyList())

    @Test
    fun `example saves three item descriptions atomically and captures local date`() = runTest {
        val repository = FakeRepository()
        var captured: AiRecognitionRequest? = null
        val phases = mutableListOf<AiPhase>()
        val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, request ->
            captured = request
            envelope(listOf(record("10.00", "餐饮", "鸡腿"), record("20", "餐饮", "晚上吃饭"), record("10", "购物", "手机壳")))
        }, repository, clock = clock)

        val result = manager.recognizeAndSave("鸡腿10元晚上吃饭20手机壳10元", phases::add)

        assertTrue(result is AiBookkeepingResult.Saved)
        assertEquals(1, repository.importCalls.size)
        assertEquals(listOf(1000L, 2000L, 1000L), repository.rows.values.map { it.amountFen })
        assertEquals(listOf("鸡腿", "晚上吃饭", "手机壳"), repository.rows.values.map { it.note })
        assertTrue(repository.rows.values.all { it.type == LedgerType.EXPENSE && it.date == requestDate })
        assertEquals(requestDate, captured!!.localDate)
        assertEquals(listOf(AiPhase.RECOGNIZING, AiPhase.SAVING, AiPhase.IDLE), phases)
        assertFalse(manager.hasPendingSave)
    }

    @Test
    fun `precision and explicit income date remain exact`() {
        val entries = AiResponseDecoder().decode(envelope(listOf(
            record("0.01", "餐饮", "糖"), record("10.20", "餐饮", "午饭"),
            record("90071992547409.91", "工资", "补发工资", "INCOME", "2026-10-31")
        )), defaults, instant)
        assertEquals(listOf(1L, 1020L, 9007199254740991L), entries.map { it.amountFen })
        assertEquals(LedgerType.INCOME, entries.last().type)
        assertEquals(LocalDate.of(2026, 10, 31), entries.last().date)
    }

    @Test
    fun `invalid late record prevents every repository write`() = runTest {
        val invalidRecords = listOf(
            record("1.001", "餐饮", "精度错误"), record("0", "餐饮", "零"),
            record("-2", "餐饮", "负数"), record("1e2", "餐饮", "科学计数"),
            record("92233720368547758.08", "餐饮", "单笔溢出"), record("92233720368547758.07", "餐饮", "整批溢出"),
            record("10", "餐饮", "日期错误", date = "2026-02-30"),
            record("10", "餐饮", "类型错误", type = "OTHER"), record("10", "餐饮", ""),
            """{"type":"EXPENSE","date":"2026-11-04","category":"餐饮","note":"没有金额"}""",
            """{"type":"EXPENSE","amount":10,"date":"2026-11-04","category":"餐饮","note":"数字而非字符串"}"""
        )
        invalidRecords.forEach { invalid ->
            val repository = FakeRepository()
            val manager = AiBookkeepingManager(configured,
                ChatCompletionsClient { _, _ -> envelope(listOf(record("10", "餐饮", "合法第一笔"), invalid)) },
                repository, clock = clock)
            assertEquals(AiBookkeepingResult.Failed(AiFailure.RESPONSE), manager.recognizeAndSave("两笔账"))
            assertTrue(repository.rows.isEmpty())
            assertTrue(repository.importCalls.isEmpty())
        }
    }

    @Test
    fun `strict response rejects incomplete empty oversized and malformed output`() {
        val valid = envelope(listOf(record("10", "餐饮", "午饭")))
        val invalid = listOf(
            valid.replace("\"stop\"", "\"length\""),
            valid.replace("\"stop\"", "\"content_filter\""),
            envelope(emptyList()), "{", "{}",
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"  "}}]}""",
            envelope(List(MAX_AI_RECORDS + 1) { record("1", "餐饮", "午饭") }),
            " ".repeat(MAX_AI_RESPONSE_BYTES + 1),
            envelope(listOf(record("1", "餐饮", "午饭").dropLast(1) + ",\"unexpected\":true}"))
        )
        invalid.forEach { expectFailure(AiFailure.RESPONSE) { AiResponseDecoder().decode(it, defaults, instant) } }
    }

    @Test
    fun `custom categories are separated by type with new names preserved and only names leave device`() = runTest {
        val history = listOf(
            ledger("宠物", LedgerType.EXPENSE, "private-note-must-stay-local"),
            ledger("副业", LedgerType.INCOME), ledger("已删除分类", LedgerType.EXPENSE).copy(deletedAt = instant)
        )
        val repository = FakeRepository(history)
        var request: AiRecognitionRequest? = null
        val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, incoming ->
            request = incoming
            envelope(listOf(record("10", "宠物", "猫粮"), record("20", "副业", "外快", "INCOME"),
                record("1", "副业", "类型不匹配"), record("2", "公益捐赠", "模糊项目")))
        }, repository, clock = clock)
        val result = manager.recognizeAndSave("本段文字") as AiBookkeepingResult.Saved
        assertEquals(listOf("宠物", "副业", "其他", "公益捐赠"), result.entries.map { it.category })
        assertTrue("宠物" in request!!.categories.getValue(LedgerType.EXPENSE))
        assertFalse("副业" in request!!.categories.getValue(LedgerType.EXPENSE))
        assertFalse("已删除分类" in request!!.categories.getValue(LedgerType.EXPENSE))
        val body = completionRequestBody(credentials.settings, request!!)
        assertTrue(body.contains("本段文字"))
        assertFalse(body.contains("private-note-must-stay-local"))
        assertFalse(body.contains(history.first().id))
        assertFalse(body.contains(credentials.apiKey))
    }

    @Test
    fun `exact imported category names survive whitespace without crossing types`() {
        val categories = LedgerCategories.available(listOf(ledger(" 宠物 ", LedgerType.EXPENSE)))
        val entries = AiResponseDecoder().decode(envelope(listOf(
            record("10", " 宠物 ", "猫粮"),
            record("20", " 餐饮 ", "晚饭"),
            record("30", " 宠物 ", "类型不匹配", "INCOME")
        )), categories, instant)

        assertEquals(listOf(" 宠物 ", "餐饮", "其他"), entries.map { it.category })
    }

    @Test
    fun `missing configuration and API failures never write`() = runTest {
        var apiCalls = 0
        val repository = FakeRepository()
        val missing = FakeAiConfiguration()
        val client = ChatCompletionsClient { _, _ -> apiCalls++; error("Should not call") }
        assertEquals(AiBookkeepingResult.Failed(AiFailure.NOT_CONFIGURED),
            AiBookkeepingManager(missing, client, repository).recognizeAndSave("午饭10元"))
        assertEquals(0, apiCalls)
        listOf(AiFailure.AUTH, AiFailure.BALANCE, AiFailure.RATE_LIMIT, AiFailure.NETWORK, AiFailure.TIMEOUT).forEach { failure ->
            val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, _ -> throw AiException(failure) }, repository)
            assertEquals(AiBookkeepingResult.Failed(failure), manager.recognizeAndSave("午饭10元"))
        }
        assertTrue(repository.importCalls.isEmpty())
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `save retry reuses IDs timestamps and decoded batch even after uncertain commit`() = runTest {
        val repository = FakeRepository().apply { failAfterFirstCommit = true }
        var apiCalls = 0
        val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, _ ->
            apiCalls++
            envelope(listOf(record("10", "餐饮", "鸡腿"), record("20", "餐饮", "晚饭")))
        }, repository, clock = clock)
        assertEquals(AiBookkeepingResult.Failed(AiFailure.SAVE, true), manager.recognizeAndSave("鸡腿10晚饭20"))
        assertEquals(AiBookkeepingResult.Failed(AiFailure.SAVE, true), manager.recognizeAndSave("不得重新识别"))
        val initial = repository.rows.values.toList()
        val edited = initial[0].copy(amountFen = 2500, note = "更正鸡腿", updatedAt = instant.plusSeconds(1))
        val deleted = initial[1].copy(deletedAt = instant.plusSeconds(2), updatedAt = instant.plusSeconds(2))
        repository.rows[edited.id] = edited
        repository.rows[deleted.id] = deleted
        val result = manager.retrySave() as AiBookkeepingResult.Saved
        assertEquals(1, apiCalls)
        assertEquals(repository.importCalls[0], repository.importCalls[1])
        assertEquals(repository.batchCalls[0], repository.batchCalls[1])
        assertEquals(repository.batchCalls[0].id, result.batchId)
        assertEquals(listOf(edited, deleted), repository.rows.values.toList())
        assertEquals(2, repository.rows.size)
        assertEquals(ImportCommitResult(0, 2), result.commit)
        assertFalse(manager.hasPendingSave)
    }

    @Test
    fun `cancellation after an uncertain commit retains the batch for deduplicated retry`() = runTest {
        val committed = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply {
            afterCommit = { committed.complete(Unit); awaitCancellation() }
        }
        var apiCalls = 0
        val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, _ ->
            apiCalls++
            envelope(listOf(record("10", "餐饮", "午饭")))
        }, repository, clock = clock)
        val first = launch { manager.recognizeAndSave("午饭10元") }
        committed.await()
        first.cancelAndJoin()

        assertTrue(manager.hasPendingSave)
        assertEquals(1, repository.rows.size)
        repository.afterCommit = null
        val result = manager.retrySave() as AiBookkeepingResult.Saved
        assertEquals(1, apiCalls)
        assertEquals(repository.importCalls[0], repository.importCalls[1])
        assertEquals(ImportCommitResult(0, 1), result.commit)
        assertEquals(1, repository.rows.size)
        assertFalse(manager.hasPendingSave)
    }

    @Test
    fun `overlapping taps are blocked and cancellation releases operation without writes`() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val repository = FakeRepository()
        val manager = AiBookkeepingManager(configured, ChatCompletionsClient { _, _ ->
            started.complete(Unit)
            response.await()
        }, repository)
        val first = launch { manager.recognizeAndSave("午饭10") }
        started.await()
        assertEquals(AiBookkeepingResult.Busy, manager.recognizeAndSave("午饭10"))
        assertEquals(AiBookkeepingResult.Busy, manager.retrySave())
        first.cancelAndJoin()
        assertTrue(repository.importCalls.isEmpty())
        response.complete(envelope(listOf(record("10", "餐饮", "午饭"))))
        assertTrue(manager.recognizeAndSave("午饭10") is AiBookkeepingResult.Saved)
        assertEquals(1, repository.rows.size)
    }

    private fun record(amount: String, category: String, note: String, type: String = "EXPENSE", date: String = requestDate.toString()) =
        buildJsonObject {
            put("type", type); put("amount", amount); put("date", date); put("category", category); put("note", note)
        }.toString()

    private fun envelope(records: List<String>): String {
        val content = "{\"records\":[${records.joinToString(",") }]}"
        return buildJsonObject {
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("finish_reason", "stop")
                    put("message", buildJsonObject { put("role", "assistant"); put("content", JsonPrimitive(content)) })
                })
            })
        }.toString()
    }

    private fun ledger(category: String, type: LedgerType, note: String = "旧记录") = LedgerEntry(
        type = type, amountFen = 1, date = requestDate, category = category, note = note,
        createdAt = instant, updatedAt = instant
    )

    private fun expectFailure(expected: AiFailure, action: () -> Unit) {
        try {
            action()
            fail("Expected sanitized AI failure")
        } catch (exception: AiException) {
            assertEquals(expected, exception.failure)
        }
    }
}

// Named fixture avoids a Kotlin FIR lint crash while resolving anonymous inner classifiers.
private class FakeAiConfiguration(private val credentials: AiCredentials? = null) : AiConfiguration {
    override fun loadSettings(): AiSettings? = credentials?.settings
    override fun loadCredentials(): AiCredentials? = credentials
}

private class FakeRepository(initial: List<LedgerEntry> = emptyList()) : LedgerRepository {
    val rows = initial.associateByTo(linkedMapOf()) { it.id }
    val importCalls = mutableListOf<List<LedgerEntry>>()
    data class BatchCall(val id: String, val savedAt: Instant, val entries: List<LedgerEntry>)
    val batchCalls = mutableListOf<BatchCall>()
    private val batches = linkedMapOf<String, BatchCall>()
    var failAfterFirstCommit = false
    var afterCommit: (suspend () -> Unit)? = null
    override suspend fun getAllActiveEntries() = rows.values.filter { it.deletedAt == null }
    override suspend fun getAllSyncEntries() = rows.values.toList()
    override fun observeEntries(filter: LedgerFilter): Flow<List<LedgerEntry>> = flowOf(rows.values.toList())
    override fun observeAllActiveEntries(): Flow<List<LedgerEntry>> = flowOf(rows.values.toList())
    override fun observeMonthlyTotals(month: YearMonth): Flow<MonthlyTotals> = flowOf(MonthlyTotals(month, 0, 0))
    override suspend fun save(entry: LedgerEntry) { error("AI must use batch import") }
    override suspend fun delete(id: String) { error("AI must not delete records") }
    override suspend fun applyRemoteEntriesAtomically(entries: List<LedgerEntry>) { error("AI must not replace the snapshot") }
    override fun observeAiBatches(): Flow<List<AiBatch>> = flowOf(emptyList())
    override suspend fun importEntries(entries: List<LedgerEntry>): ImportCommitResult = error("AI must save ledger and membership together")
    override suspend fun saveAiBatch(batchId: String, savedAt: Instant, entries: List<LedgerEntry>): ImportCommitResult {
        importCalls += entries.toList()
        val call = BatchCall(batchId, savedAt, entries.toList())
        batchCalls += call
        batches[batchId]?.let { committed ->
            check(committed.id == call.id && committed.savedAt == call.savedAt && committed.entries.map { it.id } == entries.map { it.id })
            return ImportCommitResult(0, entries.size)
        }
        check(entries.all { rows[it.id] == null || rows[it.id] == it })
        val newEntries = entries.filter { it.id !in rows }
        rows.putAll(newEntries.associateBy { it.id })
        batches[batchId] = call
        afterCommit?.invoke()
        if (failAfterFirstCommit) {
            failAfterFirstCommit = false
            throw IOException("Simulated lost commit acknowledgement")
        }
        return ImportCommitResult(newEntries.size, entries.size - newEntries.size)
    }
}
