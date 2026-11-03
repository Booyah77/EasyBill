package com.example.personalbookkeeping.data.ai

import com.example.personalbookkeeping.domain.model.LedgerCategories
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiSettingsTest {
    @Test
    fun `normalizes base versioned and full completion URLs`() {
        assertEquals("https://api.deepseek.com/chat/completions", AiSettings().completionUrl.toString())
        assertEquals("https://example.com/v1/chat/completions", AiSettings("https://example.com/v1/").completionUrl.toString())
        assertEquals("https://example.com/proxy/v1/chat/completions",
            AiSettings("https://example.com/proxy/v1/chat/completions/").completionUrl.toString())
        assertEquals(DEFAULT_AI_MODEL, AiSettings().model)
    }

    @Test
    fun `rejects credential bearing non HTTPS ambiguous and malformed URLs`() {
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com/?key=secret",
            "https://example.com/#secret", "https://", "https://example.com:0", "https://example.com:99999",
            "https://example.com/a/../b", "https://example.com/%2e%2e/", "file:///tmp/key").forEach { url ->
            try {
                AiSettings(url)
                fail("Should reject invalid URL")
            } catch (exception: AiException) {
                assertEquals(AiFailure.INVALID_CONFIG, exception.failure)
                assertFalse(exception.message.orEmpty().contains("secret"))
            }
        }
        try {
            AiCredentials(AiSettings(), "key\r\nHeader: injected")
            fail("Should reject header injection")
        } catch (exception: AiException) {
            assertEquals(AiFailure.INVALID_CONFIG, exception.failure)
        }
    }

    @Test
    fun `request keeps user text separate and thinking is only disabled on official DeepSeek`() {
        val date = LocalDate.of(2026, 11, 3)
        val text = "鸡腿10元晚上吃饭20手机壳10元"
        val request = AiRecognitionRequest(text, date, LedgerCategories.available(emptyList()))
        val official = Json.parseToJsonElement(completionRequestBody(AiSettings(), request)).jsonObject
        assertEquals("disabled", official.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("false", official.getValue("stream").jsonPrimitive.content)
        assertEquals("json_object", official.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        val messages = official.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertTrue(messages[0].jsonObject.getValue("content").jsonPrimitive.content.contains(date.toString()))
        assertTrue(messages[0].jsonObject.getValue("content").jsonPrimitive.content.contains("EXPENSE"))
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(text, messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        listOf("https://example.com/v1", "https://api.deepseek.com.example.com", "https://api.deepseek.com:8443").forEach { url ->
            val portable = Json.parseToJsonElement(completionRequestBody(AiSettings(url), request)).jsonObject
            assertFalse(portable.containsKey("thinking"))
        }
    }
}
