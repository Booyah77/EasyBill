package com.example.personalbookkeeping.data.ai

import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiCategoryTest {
    private val instant = Instant.parse("2026-01-02T00:00:00Z")
    private val defaults = LedgerCategories.available(emptyList())

    @Test
    fun `new meaningful categories reuse normalized name within a batch without changing request catalog`() {
        val entries = AiResponseDecoder().decode(categoryResponse(
            CategoryWireFixture(" 公益捐赠 "), CategoryWireFixture("公益捐赠"),
            CategoryWireFixture("Pets"), CategoryWireFixture("ＰＥＴＳ")
        ), defaults, instant)
        assertEquals(listOf("公益捐赠", "公益捐赠", "Pets", "Pets"), entries.map { it.category })
        assertFalse("公益捐赠" in defaults.getValue(LedgerType.EXPENSE))
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
    }

    @Test
    fun `exact custom spelling survives decoder while opposite type only name falls back`() {
        val historic = LedgerEntry(type = LedgerType.EXPENSE, amountFen = 100L,
            category = "  宠物  ", date = LocalDate.of(2026, 1, 2), createdAt = instant)
        val longName = "这是一个来自历史账目的超过二十个字的原始自定义分类名称"
        val catalog = LedgerCategories.available(listOf(historic, historic.copy(category = longName), historic.copy(category = "工资")))
        val entries = AiResponseDecoder().decode(categoryResponse(
            CategoryWireFixture("  宠物  "), CategoryWireFixture(longName),
            CategoryWireFixture("工资"), CategoryWireFixture("餐饮", "INCOME")
        ), catalog, instant)
        assertEquals(listOf("  宠物  ", longName, "工资", "其他"), entries.map { it.category })
    }

    @Test
    fun `invalid late new category rejects the whole response`() {
        listOf("新\n分类", "新\u200B分类", "新\u0000分类", "未知", "123", "长".repeat(21)).forEach { invalid ->
            try {
                AiResponseDecoder().decode(categoryResponse(CategoryWireFixture("餐饮"), CategoryWireFixture(invalid)), defaults, instant)
                fail("An invalid late category must prevent a decoded batch")
            } catch (expected: AiException) {
                assertEquals(AiFailure.RESPONSE, expected.failure)
            }
        }
    }

    @Test
    fun `prompt distinguishes defaults and existing names and teaches item versus category without uploading ledger details`() {
        val historic = LedgerEntry(type = LedgerType.EXPENSE, amountFen = 12345L, category = "公益捐赠",
            note = "PRIVATE_LEDGER_NOTE", date = LocalDate.of(2025, 5, 5), createdAt = instant)
        val request = AiRecognitionRequest("午饭28元", LocalDate.of(2026, 1, 2), LedgerCategories.available(listOf(historic)))
        val body = Json.parseToJsonElement(completionRequestBody(AiSettings(), request)).jsonObject
        val messages = body.getValue("messages").jsonArray
        val prompt = messages[0].jsonObject.getValue("content").jsonPrimitive.content
        assertTrue(prompt.contains("默认分类 JSON"))
        assertTrue(prompt.contains("已有自定义分类 JSON"))
        assertTrue(prompt.contains("公益捐赠"))
        assertTrue(prompt.contains("鸡腿、午饭属于餐饮，手机壳属于购物"))
        assertTrue(prompt.contains("商户、品牌、商品明细写入 note"))
        assertTrue(prompt.contains("1～20"))
        assertFalse(prompt.contains("PRIVATE_LEDGER_NOTE"))
        assertFalse(prompt.contains("2025-05-05"))
        assertEquals(request.text, messages[1].jsonObject.getValue("content").jsonPrimitive.content)
    }
}

private data class CategoryWireFixture(val category: String, val type: String = "EXPENSE")

private fun categoryResponse(vararg fixtures: CategoryWireFixture): String {
    val payload = buildJsonObject {
        put("records", buildJsonArray {
            fixtures.forEach { fixture ->
                add(buildJsonObject {
                    put("type", fixture.type)
                    put("amount", "1.00")
                    put("date", "2026-01-02")
                    put("category", fixture.category)
                    put("note", "项目描述")
                })
            }
        })
    }
    return buildJsonObject {
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("finish_reason", "stop")
                put("message", buildJsonObject { put("role", "assistant"); put("content", payload.toString()) })
            })
        })
    }.toString()
}
