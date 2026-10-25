package com.evan.pulse.domain.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryPayloadTest {

    private val sample = byteArrayOf(0x00, 0x1F, 0x7F, (-128).toByte(), (-1).toByte())

    @Test
    fun hexEncodesUppercase() {
        assertEquals("001F7F80FF", BinaryPayload.hex(sample))
    }

    @Test
    fun hexDecodeAcceptsSpacesColonsAndPrefix() {
        val decoded = BinaryPayload.decode("0x00 1f:7F 80 FF", BinaryEncoding.HEX).getOrThrow()
        assertArrayEquals(sample, decoded)
    }

    @Test
    fun hexDecodeRejectsOddLengthAndBadDigits() {
        assertNull(BinaryPayload.decode("ABC", BinaryEncoding.HEX).getOrNull())
        assertNull(BinaryPayload.decode("ZZ", BinaryEncoding.HEX).getOrNull())
        assertNull(BinaryPayload.decode("   ", BinaryEncoding.HEX).getOrNull())
    }

    @Test
    fun base64RoundTrips() {
        val text = "Pulse WebSocket 二进制"
        val bytes = text.toByteArray()
        val encoded = BinaryPayload.base64(bytes)
        assertArrayEquals(bytes, BinaryPayload.decode(encoded, BinaryEncoding.BASE64).getOrThrow())
    }

    @Test
    fun base64DecodeRejectsInvalidInput() {
        assertNull(BinaryPayload.decode("not base64!!", BinaryEncoding.BASE64).getOrNull())
        assertNull(BinaryPayload.decode("", BinaryEncoding.BASE64).getOrNull())
    }

    @Test
    fun previewTruncatesLongPayloads() {
        val long = ByteArray(BinaryPayload.PREVIEW_LIMIT + 10) { 0x41 }
        val preview = BinaryPayload.preview(long, BinaryEncoding.HEX)
        assertTrue(preview.endsWith("…"))
        assertEquals(BinaryPayload.PREVIEW_LIMIT * 2, preview.length - 1)
    }

    @Test
    fun previewKeepsShortPayloadsWhole() {
        val preview = BinaryPayload.preview(byteArrayOf(0x41, 0x42), BinaryEncoding.HEX)
        assertEquals("4142", preview)
    }
}
