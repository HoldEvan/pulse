package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {

    private fun roundTrip(text: String, correction: QrErrorCorrection = QrErrorCorrection.MEDIUM): QrDecodeResult {
        val matrix = QrCodec.encode(text, correction).matrix!!
        val pixels = matrix.toPixels(scale = 6, margin = 2)
        val side = matrix.size + 4
        return QrCodec.decode(pixels, side * 6, side * 6)
    }

    @Test
    fun encodeReturnsSquareMatrix() {
        val result = QrCodec.encode("https://example.com")
        assertNull(result.error)
        val matrix = result.matrix
        assertNotNull(matrix)
        assertTrue(matrix!!.size >= 21)
    }

    @Test
    fun encodeOfEmptyTextReportsEmpty() {
        val result = QrCodec.encode("")
        assertEquals("empty", result.error)
        assertNull(result.matrix)
    }

    @Test
    fun encodeRejectsOversizedInput() {
        val result = QrCodec.encode("x".repeat(QrCodec.MAX_INPUT_BYTES + 1))
        assertEquals("tooLong", result.error)
        assertNull(result.matrix)
    }

    @Test
    fun encodeAcceptsAsciiAtCapacity() {
        val text = "x".repeat(2000)
        assertNull(QrCodec.encode(text).error)
    }

    @Test
    fun roundTripAsciiText() {
        val decoded = roundTrip("https://api.example.com/v1/users?id=7")
        assertNull(decoded.error)
        assertEquals("https://api.example.com/v1/users?id=7", decoded.text)
        assertEquals("QR_CODE", decoded.format)
    }

    @Test
    fun roundTripChineseText() {
        val decoded = roundTrip("扫码登录 Pulse 调试工具")
        assertNull(decoded.error)
        assertEquals("扫码登录 Pulse 调试工具", decoded.text)
    }

    @Test
    fun roundTripMultilineText() {
        val text = "line1\nline2\nline3"
        assertEquals(text, roundTrip(text).text)
    }

    @Test
    fun everyCorrectionLevelDecodes() {
        QrErrorCorrection.entries.forEach { level ->
            val decoded = roundTrip("pulse-${level.label}", level)
            assertNull("level ${level.label}", decoded.error)
            assertEquals("pulse-${level.label}", decoded.text)
        }
    }

    @Test
    fun higherCorrectionProducesLargerMatrix() {
        val low = QrCodec.encode("x".repeat(200), QrErrorCorrection.LOW).matrix!!
        val high = QrCodec.encode("x".repeat(200), QrErrorCorrection.HIGH).matrix!!
        assertTrue(high.size > low.size)
    }

    @Test
    fun matrixHasDarkAndLightModules() {
        val matrix = QrCodec.encode("pulse").matrix!!
        var dark = 0
        var light = 0
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) dark++ else light++
            }
        }
        assertTrue(dark > 0)
        assertTrue(light > 0)
    }

    @Test
    fun finderPatternCornersAreDark() {
        val matrix = QrCodec.encode("pulse").matrix!!
        // 左上角定位图案的外框必为深色
        assertTrue(matrix.isDark(0, 0))
        assertTrue(matrix.isDark(matrix.size - 1, 0))
        assertTrue(matrix.isDark(0, matrix.size - 1))
    }

    @Test
    fun pixelsIncludeQuietZone() {
        val matrix = QrCodec.encode("pulse").matrix!!
        val scale = 4
        val margin = 2
        val side = (matrix.size + margin * 2) * scale
        val pixels = matrix.toPixels(scale = scale, margin = margin)
        assertEquals(side * side, pixels.size)
        // 左上角落在白边内
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }

    @Test
    fun decodeOfBlankPixelsReportsNotFound() {
        val side = 64
        val result = QrCodec.decode(IntArray(side * side) { 0xFFFFFFFF.toInt() }, side, side)
        assertEquals("notFound", result.error)
    }

    @Test
    fun decodeOfMismatchedBufferReportsInvalid() {
        val result = QrCodec.decode(IntArray(10), 4, 4)
        assertEquals("invalid", result.error)
    }

    @Test
    fun decodeOfZeroSizeReportsInvalid() {
        assertEquals("invalid", QrCodec.decode(IntArray(0), 0, 0).error)
    }
}
