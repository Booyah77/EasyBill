package com.example.personalbookkeeping.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.personalbookkeeping.data.repository.ImportCommitResult
import com.example.personalbookkeeping.data.repository.ImportConflictException
import com.example.personalbookkeeping.data.repository.RoomLedgerRepository
import com.example.personalbookkeeping.data.repository.RoomObjectStorageSyncRepository
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiBatchPersistenceTest {
    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LedgerDatabase::class.java)
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val savedAt = Instant.parse("2026-11-04T01:00:00Z")

    @Test
    fun migration1To3PreservesActiveEntriesAndTombstones() {
        migrateAndVerify(1)
    }

    @Test
    fun migration2To3PreservesEntriesTombstonesAndObjectState() {
        migrateAndVerify(2)
    }

    private fun migrateAndVerify(from: Int) {
        val name = "ai-history-migration-$from"
        context.deleteDatabase(name)
        migrations.createDatabase(name, from).apply {
            execSQL("INSERT INTO ledger_entries VALUES ('active', 'EXPENSE', 1000, 'CNY', '2026-11-04', ' 转账 ', '原值', '2026-11-04T01:00:00Z', '2026-11-04T01:00:00Z', NULL)")
            execSQL("INSERT INTO ledger_entries VALUES ('deleted', 'INCOME', 2000, 'CNY', '2026-11-03', '转账', '墓碑', '2026-11-03T01:00:00Z', '2026-11-04T01:00:00Z', '2026-11-04T01:00:00Z')")
            if (from == 2) {
                execSQL("INSERT INTO object_sync_record_states VALUES ('target', 'active', 'head')")
                execSQL("INSERT INTO object_sync_conflicts VALUES ('target', 'active', 'variant', 'opaque-revision-json')")
            }
            close()
        }
        migrations.runMigrationsAndValidate(name, 3, true, LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3).apply {
            query("SELECT id, category, deletedAt FROM ledger_entries ORDER BY id").use { rows ->
                assertEquals(2, rows.count)
                assertTrue(rows.moveToNext())
                assertEquals("active", rows.getString(0))
                assertEquals(" 转账 ", rows.getString(1))
                assertTrue(rows.isNull(2))
                assertTrue(rows.moveToNext())
                assertEquals("deleted", rows.getString(0))
                assertEquals("转账", rows.getString(1))
                assertEquals("2026-11-04T01:00:00Z", rows.getString(2))
            }
            query("SELECT * FROM ai_batch_entries").use { assertEquals(0, it.count) }
            query("SELECT revisionId FROM object_sync_record_states").use { rows ->
                assertEquals(if (from == 2) 1 else 0, rows.count)
                if (from == 2) { assertTrue(rows.moveToFirst()); assertEquals("head", rows.getString(0)) }
            }
            query("SELECT revisionJson FROM object_sync_conflicts").use { rows ->
                assertEquals(if (from == 2) 1 else 0, rows.count)
                if (from == 2) { assertTrue(rows.moveToFirst()); assertEquals("opaque-revision-json", rows.getString(0)) }
            }
            close()
        }
    }

    @Test
    fun membershipFailureRollsBackNewLedgerRows() = runBlocking {
        withDatabase { database, repository ->
            // A trigger fails after ledger inserts, proving the real transaction rolls them back.
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_history BEFORE INSERT ON ai_batch_entries BEGIN SELECT RAISE(ABORT, 'injected membership failure'); END"
            )
            try {
                repository.saveAiBatch("batch", savedAt, listOf(entry("one"), entry("two")))
                fail("Membership failure must abort the transaction")
            } catch (expected: android.database.sqlite.SQLiteException) {
                assertTrue(repository.getAllSyncEntries().isEmpty())
                assertTrue(database.aiBatchEntryDao().getBatchMembership("batch").isEmpty())
            }
        }
    }

    @Test
    fun laterLedgerConflictLeavesOriginalUnchangedWithoutPartialRowsOrMembership() = runBlocking {
        withDatabase { database, repository ->
            val original = entry("existing")
            repository.save(original)
            val newEntry = entry("new-before-conflict")
            val conflicting = original.copy(amountFen = 2500, note = "不得覆盖原值")
            try {
                repository.saveAiBatch("conflicting-batch", savedAt, listOf(newEntry, conflicting))
                fail("A later conflicting ledger row must reject the whole batch")
            } catch (expected: ImportConflictException) {
                assertEquals(listOf(original), repository.getAllSyncEntries())
                assertEquals(listOf(original), repository.getAllActiveEntries())
                assertTrue(database.aiBatchEntryDao().getBatchMembership("conflicting-batch").isEmpty())
                assertTrue(repository.observeAiBatches().first().isEmpty())
            }
        }
    }

    @Test
    fun uncertainRetryPreservesEditsDeletesOrderAndAllDeletedHistory() = runBlocking {
        withDatabase { database, repository ->
            val original = listOf(entry("second"), entry("first"))
            repository.saveAiBatch("batch", savedAt, original)
            val edited = original[0].copy(amountFen = 2500, note = "已更正", updatedAt = savedAt.plusSeconds(1))
            repository.save(edited)
            repository.delete(original[1].id)
            assertEquals(ImportCommitResult(0, 2), repository.saveAiBatch("batch", savedAt, original))
            var batch = repository.observeAiBatches().first().single()
            assertEquals(2, batch.originalCount)
            assertEquals(listOf(edited), batch.entries)
            assertEquals(listOf("second", "first"), database.aiBatchEntryDao().getBatchMembership("batch").map { it.entryId })
            repository.delete(edited.id)
            repository.saveAiBatch("batch", savedAt, original)
            batch = repository.observeAiBatches().first().single()
            assertEquals("batch", batch.id)
            assertEquals(savedAt, batch.savedAt)
            assertEquals(2, batch.originalCount)
            assertTrue(batch.entries.isEmpty())
            assertTrue(repository.getAllSyncEntries().all { it.deletedAt != null })
            val totals = repository.observeMonthlyTotals(java.time.YearMonth.of(2026, 11)).first()
            assertEquals(0L, totals.incomeFen)
            assertEquals(0L, totals.expenseFen)
            try {
                repository.saveAiBatch("batch", savedAt, original.reversed())
                fail("Changed membership order must fail")
            } catch (expected: ImportConflictException) {
                assertTrue(repository.getAllSyncEntries().all { it.deletedAt != null })
            }
        }
    }

    @Test
    fun historyOrdersBatchesBySavedTimeAndEntriesByOriginalPosition() = runBlocking {
        withDatabase { _, repository ->
            repository.saveAiBatch("older", savedAt, listOf(entry("z"), entry("a")))
            repository.saveAiBatch("newer", savedAt.plusSeconds(1), listOf(entry("middle")))
            val batches = repository.observeAiBatches().first()
            assertEquals(listOf("newer", "older"), batches.map { it.id })
            assertEquals(listOf("z", "a"), batches.last().entries.map { it.id })
            try {
                repository.saveAiBatch("older", savedAt.plusSeconds(1), listOf(entry("z"), entry("a")))
                fail("Changed batch save time must conflict")
            } catch (expected: ImportConflictException) {
                assertEquals(batches, repository.observeAiBatches().first())
            }
        }
    }

    @Test
    fun membershipCollisionRollsBackAndBothSyncReplacementPathsRetainHistory() = runBlocking {
        withDatabase { database, repository ->
            val original = entry("one")
            repository.saveAiBatch("batch", savedAt, listOf(original))
            try {
                repository.saveAiBatch("other", savedAt, listOf(entry("new"), original))
                fail("One ledger record cannot acquire a second batch membership")
            } catch (expected: android.database.sqlite.SQLiteException) {
                assertEquals(listOf(original), repository.getAllSyncEntries())
                assertTrue(database.aiBatchEntryDao().getBatchMembership("other").isEmpty())
            }
            val gitVersion = original.copy(note = "Git 更正", updatedAt = savedAt.plusSeconds(1))
            repository.applyRemoteEntriesAtomically(listOf(gitVersion))
            assertEquals(listOf(gitVersion), repository.observeAiBatches().first().single().entries)
            val objectVersion = original.copy(deletedAt = savedAt.plusSeconds(2), updatedAt = savedAt.plusSeconds(2))
            RoomObjectStorageSyncRepository(database).replaceResolvedSnapshot("target", listOf(objectVersion), mapOf(original.id to "head"))
            val batch = repository.observeAiBatches().first().single()
            assertEquals(1, batch.originalCount)
            assertTrue(batch.entries.isEmpty())
            assertEquals(1, database.aiBatchEntryDao().getBatchMembership("batch").size)
        }
    }

    @Test
    fun historySurvivesDatabaseReopenWithoutFabricatingLegacyProvenance() = runBlocking {
        val name = "ai-history-reopen-${java.util.UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
        val original = open()
        try {
            val repository = RoomLedgerRepository(original.ledgerEntryDao(), original)
            repository.save(entry("manual"))
            repository.saveAiBatch("batch", savedAt, listOf(entry("ai")))
        } finally {
            original.close()
        }
        val reopened = open()
        try {
            val repository = RoomLedgerRepository(reopened.ledgerEntryDao(), reopened)
            assertEquals(2, repository.getAllActiveEntries().size)
            assertEquals(listOf("ai"), repository.observeAiBatches().first().single().entries.map { it.id })
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun withDatabase(block: suspend (LedgerDatabase, RoomLedgerRepository) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            block(database, RoomLedgerRepository(database.ledgerEntryDao(), database))
        } finally {
            database.close()
        }
    }

    private fun entry(id: String) = LedgerEntry(
        id = id, type = LedgerType.EXPENSE, amountFen = 1000, date = LocalDate.of(2026, 11, 4),
        category = "餐饮", note = "午饭", createdAt = savedAt, updatedAt = savedAt
    )
}
