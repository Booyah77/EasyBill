package com.example.personalbookkeeping.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LedgerCategoriesTest {
    @Test
    fun `default categories follow approved order without renaming historic transfers`() {
        assertEquals(listOf("餐饮", "交通", "购物", "居家", "通讯", "娱乐", "医疗", "教育", "人情", "其他"), LedgerCategories.expense)
        assertEquals(listOf("工资", "奖金", "兼职", "理财", "礼金", "其他"), LedgerCategories.income)
        val catalog = LedgerCategories.available(listOf(categoryEntry("转账", LedgerType.INCOME)))
        assertTrue("转账" in catalog.getValue(LedgerType.INCOME))
        assertEquals("转账", LedgerCategories.resolve(LedgerType.INCOME, "转账", catalog))
    }

    @Test
    fun `new names trim edges and normalize against existing same type spelling`() {
        val catalog = LedgerCategories.available(listOf(categoryEntry("Pets")))
        assertEquals("公益捐赠", LedgerCategories.resolve(LedgerType.EXPENSE, " 公益捐赠 ", catalog))
        assertEquals("Pets", LedgerCategories.resolve(LedgerType.EXPENSE, " ＰＥＴＳ ", catalog))
        assertEquals("餐饮", LedgerCategories.resolve(LedgerType.EXPENSE, " 餐饮 ", catalog))
    }

    @Test
    fun `untouched long whitespace and legacy placeholder names remain exact same type data`() {
        val legacyNames = listOf("  宠物  ", "这是从历史账目导入并且超过二十个字的原始分类名称", "未知")
        val catalog = LedgerCategories.available(legacyNames.map { categoryEntry(it) })
        legacyNames.forEach { name ->
            assertEquals(name, LedgerCategories.resolve(LedgerType.EXPENSE, name, catalog))
        }
        assertEquals("  宠物  ", LedgerCategories.resolve(LedgerType.EXPENSE, "宠物", catalog))
    }

    @Test
    fun `type fallback never masks an exact same type custom name`() {
        val catalog = LedgerCategories.available(listOf(categoryEntry("工资"), categoryEntry("副业", LedgerType.INCOME)))
        assertEquals("工资", LedgerCategories.resolve(LedgerType.EXPENSE, "工资", catalog))
        assertEquals("其他", LedgerCategories.resolve(LedgerType.EXPENSE, "副业", catalog))
        assertEquals("其他", LedgerCategories.resolve(LedgerType.INCOME, "餐饮", catalog))
    }

    @Test
    fun `opposite only legacy long and placeholder names fall back before new name validation`() {
        val legacyNames = listOf("这是从历史账目导入并且超过二十个字的原始分类名称", "未知")
        val catalog = LedgerCategories.available(legacyNames.map { categoryEntry(it, LedgerType.INCOME) })
        legacyNames.forEach { name ->
            assertEquals("其他", LedgerCategories.resolve(LedgerType.EXPENSE, name, catalog))
            assertEquals(name, LedgerCategories.resolve(LedgerType.INCOME, name, catalog))
            assertEquals("其他", LedgerCategories.resolve(LedgerType.EXPENSE, " $name ", catalog))
        }
        assertEquals("未知", LedgerCategories.resolve(LedgerType.EXPENSE, "未知",
            LedgerCategories.available(listOf(categoryEntry("未知"), categoryEntry("未知", LedgerType.INCOME)))))
        legacyNames.forEach { name ->
            try {
                LedgerCategories.resolve(LedgerType.EXPENSE, name, LedgerCategories.available(emptyList()))
                fail("Truly new invalid name must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertEquals(LedgerCategories.NEW_NAME_HINT, expected.message)
            }
        }
    }

    @Test
    fun `invalid new names reject controls invisible characters placeholders and excessive length`() {
        val invalid = listOf("", " ", "123", "!!!", "未分类", "自定义", "ＮＵＬＬ", "unknown", "n/a",
            "公益\n捐赠", "公益\r捐赠", "公益\t捐赠", "公益\u0000捐赠", "公益\u200B捐赠", "公益\uFEFF捐赠",
            "公益\u2028捐赠", "长".repeat(21))
        invalid.forEach { name ->
            try {
                LedgerCategories.validateNewName(name)
                fail("Invalid new category was accepted")
            } catch (expected: IllegalArgumentException) {
                assertEquals(LedgerCategories.NEW_NAME_HINT, expected.message)
            }
        }
        assertEquals("字".repeat(20), LedgerCategories.validateNewName("字".repeat(20)))
    }

    @Test
    fun `catalog is type scoped and ignores deleted records`() {
        val active = categoryEntry("宠物")
        val deleted = categoryEntry("已删除分类").copy(deletedAt = Instant.parse("2026-01-03T00:00:00Z"))
        val catalog = LedgerCategories.available(listOf(active, deleted))
        assertTrue("宠物" in catalog.getValue(LedgerType.EXPENSE))
        assertFalse("宠物" in catalog.getValue(LedgerType.INCOME))
        assertFalse("已删除分类" in catalog.getValue(LedgerType.EXPENSE))
    }
}

private fun categoryEntry(name: String, type: LedgerType = LedgerType.EXPENSE): LedgerEntry = LedgerEntry(
    type = type,
    amountFen = 100L,
    category = name,
    date = LocalDate.of(2026, 1, 2),
    createdAt = Instant.parse("2026-01-02T00:00:00Z")
)
