package com.example.personalbookkeeping.ui.screens

import com.example.personalbookkeeping.data.transfer.LedgerFileCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFormatHelpTest {
    @Test
    fun `displayed CSV example decodes including quoted note and exact v1 header`() {
        val preview = LedgerFileCodec().decodeCsv(FileFormatHelp.csvExample)
        assertTrue(preview.issues.toString(), preview.canImport)
        assertEquals(FileFormatHelp.entries, preview.entries)
        assertEquals("id,type,amount,currency,date,category,note,createdAt,updatedAt",
            FileFormatHelp.csvExample.lineSequence().first())
        assertTrue(FileFormatHelp.csvExample.contains("\"午饭,含饮料\""))
        assertFalse(FileFormatHelp.csvExample.startsWith("\uFEFF"))
    }

    @Test
    fun `displayed JSON example decodes with exact root and string record values`() {
        val preview = LedgerFileCodec().decodeJson(FileFormatHelp.jsonExample)
        assertTrue(preview.issues.toString(), preview.canImport)
        assertEquals(FileFormatHelp.entries, preview.entries)
        val root = Json.parseToJsonElement(FileFormatHelp.jsonExample).jsonObject
        assertEquals(setOf("schemaVersion", "currency", "exportedAt", "records"), root.keys)
        assertFalse(root.getValue("schemaVersion").jsonPrimitive.isString)
        assertEquals("1", root.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals(FileFormatHelp.exportedAt.toString(), root.getValue("exportedAt").jsonPrimitive.content)
        val record = root.getValue("records").jsonArray.single().jsonObject
        assertEquals(setOf("id", "type", "amount", "date", "category", "note", "createdAt", "updatedAt"), record.keys)
        assertTrue(record.values.all { it.jsonPrimitive.isString })
        assertEquals("28.00", record.getValue("amount").jsonPrimitive.content)
    }

    @Test
    fun `examples are deterministic actual codec exports and reimport without issues`() {
        val codec = LedgerFileCodec()
        assertEquals(codec.encodeCsv(FileFormatHelp.entries).removePrefix("\uFEFF"), FileFormatHelp.csvExample)
        assertEquals(codec.encodeJson(FileFormatHelp.entries, FileFormatHelp.exportedAt), FileFormatHelp.jsonExample)
        val csvEntries = codec.decodeCsv(FileFormatHelp.csvExample).entries
        val reimport = codec.decodeJson(codec.encodeJson(csvEntries, FileFormatHelp.exportedAt))
        assertTrue(reimport.canImport)
        assertEquals(FileFormatHelp.entries, reimport.entries)
    }
}
