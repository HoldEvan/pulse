package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 同一份点阵在不同放大倍数下都要能识别。
 *
 * 放大倍数会改变二值化时的分块采样，某些倍数下 zxing 的检测器会漏掉定位图案，
 * 因此这里把常见倍数与四种纠错级别交叉验证一遍。
 */
class QrCodecScaleTest {

    private val scales = listOf(2, 3, 4, 6, 10)

    private fun roundTrip(text: String, level: QrErrorCorrection, scale: Int): QrDecodeResult {
        val matrix = QrCodec.encode(text, level).matrix!!
        val side = matrix.size + 4
        val pixels = matrix.toPixels(scale = scale, margin = 2)
        return QrCodec.decode(pixels, side * scale, side * scale)
    }

    @Test
    fun asciiRoundTripsAtEveryScaleAndLevel() {
        QrErrorCorrection.entries.forEach { level ->
            scales.forEach { scale ->
                val decoded = roundTrip("pulse-$scale", level, scale)
                assertNull("level ${level.label} scale $scale", decoded.error)
                assertEquals("pulse-$scale", decoded.text)
            }
        }
    }

    @Test
    fun chineseRoundTripsAtEveryScaleAndLevel() {
        val text = "扫码登录 Pulse 调试工具"
        QrErrorCorrection.entries.forEach { level ->
            scales.forEach { scale ->
                val decoded = roundTrip(text, level, scale)
                assertNull("level ${level.label} scale $scale", decoded.error)
                assertEquals(text, decoded.text)
            }
        }
    }

    @Test
    fun longContentRoundTripsAtEveryScale() {
        val text = "https://api.example.com/v1/users?page=1&size=50&sort=created_at"
        scales.forEach { scale ->
            val decoded = roundTrip(text, QrErrorCorrection.MEDIUM, scale)
            assertNull("scale $scale", decoded.error)
            assertEquals(text, decoded.text)
        }
    }

    @Test
    fun invertedColorsStillDecode() {
        val text = "inverted-qr"
        val matrix = QrCodec.encode(text).matrix!!
        val side = matrix.size + 4
        val scale = 6
        val pixels = matrix.toPixels(
            scale = scale,
            margin = 2,
            dark = 0xFFFFFFFF.toInt(),
            light = 0xFF000000.toInt()
        )
        val decoded = QrCodec.decode(pixels, side * scale, side * scale)
        assertNull(decoded.error)
        assertEquals(text, decoded.text)
    }
}
