package com.evan.pulse.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseExportTest {

    @Test
    fun `content type parameters are stripped and lowercased`() {
        assertEquals("application/json", ResponseExport.baseMime("Application/JSON; charset=utf-8"))
        assertEquals("text/html", ResponseExport.baseMime("text/html;charset=UTF-8"))
    }

    @Test
    fun `missing content type falls back to binary`() {
        assertEquals("application/octet-stream", ResponseExport.baseMime(null))
        assertEquals("application/octet-stream", ResponseExport.baseMime("   "))
        assertEquals("bin", ResponseExport.extensionFor(null))
    }

    @Test
    fun `known mime types map to their extension`() {
        assertEquals("json", ResponseExport.extensionFor("application/json"))
        assertEquals("png", ResponseExport.extensionFor("image/png"))
        assertEquals("html", ResponseExport.extensionFor("text/html"))
        assertEquals("pdf", ResponseExport.extensionFor("application/pdf"))
    }

    @Test
    fun `unknown mime falls back by family`() {
        assertEquals("txt", ResponseExport.extensionFor("text/markdown"))
        assertEquals("avif", ResponseExport.extensionFor("image/avif"))
        assertEquals("json", ResponseExport.extensionFor("application/problem+json"))
        assertEquals("bin", ResponseExport.extensionFor("application/vnd.custom"))
    }

    @Test
    fun `suggested name carries a timestamp and the right extension`() {
        val name = ResponseExport.suggestedName("application/json", now = 0L)
        assertTrue(name.startsWith("response_"))
        assertTrue(name.endsWith(".json"))
        assertEquals("response_19700101_000000.json", name)
    }

    @Test
    fun `raw bytes win over text so exports are not truncated`() {
        val raw = ByteArray(100_000) { 'a'.code.toByte() }
        val bytes = ResponseExport.bytesOf("short preview", raw)
        assertEquals(100_000, bytes.size)
    }

    @Test
    fun `text is encoded as utf8 when raw bytes are absent`() {
        val bytes = ResponseExport.bytesOf("中文", null)
        assertEquals("中文", bytes.toString(Charsets.UTF_8))
        assertEquals(6, bytes.size)
    }
}
