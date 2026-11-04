package com.example.personalbookkeeping.ui.screens

import com.example.personalbookkeeping.domain.model.AiBatch
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AiBatchPresentationTest {
    @Test
    fun `missing history batch is loading while a present empty batch is deleted`() {
        assertEquals("正在读取本批记录…", aiBatchResultTitle(null))
        val batch = AiBatch("batch", Instant.parse("2026-01-02T00:00:00Z"), 2, emptyList())
        assertEquals("本批记录已全部删除", aiBatchResultTitle(batch))
        assertEquals("已记入账本 2 笔", aiBatchResultTitle(batch.copy(entries = listOf(batchPresentationEntry(100L)))))
    }

    @Test
    fun `edited batch totals exceeding Long remain exact without floating point rounding`() {
        val entries = listOf(
            batchPresentationEntry(Long.MAX_VALUE), batchPresentationEntry(Long.MAX_VALUE),
            batchPresentationEntry(Long.MAX_VALUE, LedgerType.INCOME),
            batchPresentationEntry(Long.MAX_VALUE, LedgerType.INCOME),
            batchPresentationEntry(1L, LedgerType.INCOME)
        )
        assertEquals("支出 ¥184,467,440,737,095,516.14 · 收入 ¥184,467,440,737,095,516.15", aiBatchTotalText(entries))
    }

    @Test
    fun `totals retain cents and separate types with zero totals`() {
        assertEquals("支出 ¥0.01 · 收入 ¥0.00", aiBatchTotalText(listOf(batchPresentationEntry(1L))))
        assertEquals("支出 ¥0.00 · 收入 ¥0.00", aiBatchTotalText(emptyList()))
    }
}

private fun batchPresentationEntry(amountFen: Long, type: LedgerType = LedgerType.EXPENSE): LedgerEntry = LedgerEntry(
    type = type, amountFen = amountFen, category = "其他", date = LocalDate.of(2026, 1, 2),
    createdAt = Instant.parse("2026-01-02T00:00:00Z")
)
