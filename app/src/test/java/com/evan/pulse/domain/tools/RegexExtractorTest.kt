package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexExtractorTest {

    @Test
    fun `empty text returns nothing`() {
        val r = RegexExtractor.extract(ExtractPreset.URL, "")
        assertTrue(r.values.isEmpty())
    }

    @Test
    fun `url trims trailing punctuation`() {
        val r = RegexExtractor.extract(
            ExtractPreset.URL,
            "见 https://a.com/x?y=1，以及 www.b.cn/z。"
        )
        assertEquals(listOf("https://a.com/x?y=1", "www.b.cn/z"), r.values)
    }

    @Test
    fun `mobile requires standalone digits`() {
        val r = RegexExtractor.extract(
            ExtractPreset.MOBILE,
            "手机 13800138000，订单号 138001380001 不算"
        )
        assertEquals(listOf("13800138000"), r.values)
    }

    @Test
    fun `email extraction`() {
        val r = RegexExtractor.extract(
            ExtractPreset.EMAIL,
            "发给 dev@example.com 或 qa.test+1@mail.co.uk。"
        )
        assertEquals(listOf("dev@example.com", "qa.test+1@mail.co.uk"), r.values)
    }

    @Test
    fun `ipv4 rejects out of range parts`() {
        val r = RegexExtractor.extract(
            ExtractPreset.IPV4,
            "10.0.0.1 与 256.1.1.1"
        )
        assertEquals(listOf("10.0.0.1"), r.values)
    }

    @Test
    fun `mac supports colon and dash`() {
        val r = RegexExtractor.extract(
            ExtractPreset.MAC,
            "00:1A:2B:3C:4D:5E / 00-1a-2b-3c-4d-5e"
        )
        assertEquals(2, r.count)
    }

    @Test
    fun `id card validates birthday and checksum char`() {
        val r = RegexExtractor.extract(
            ExtractPreset.ID_CARD,
            "11010119900307123X 与 110101199013071234"
        )
        assertEquals(listOf("11010119900307123X"), r.values)
    }

    @Test
    fun `date matches multiple separators`() {
        val r = RegexExtractor.extract(
            ExtractPreset.DATE,
            "2026-10-23、2026/1/5、2026.12.31"
        )
        assertEquals(listOf("2026-10-23", "2026/1/5", "2026.12.31"), r.values)
    }

    @Test
    fun `time supports optional seconds`() {
        val r = RegexExtractor.extract(ExtractPreset.TIME, "09:30 和 23:59:58")
        assertEquals(listOf("09:30", "23:59:58"), r.values)
    }

    @Test
    fun `uuid extraction`() {
        val r = RegexExtractor.extract(
            ExtractPreset.UUID,
            "id=3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        )
        assertEquals(listOf("3f2504e0-4f89-11d3-9a0c-0305e82c3301"), r.values)
    }

    @Test
    fun `hex color prefers six digits`() {
        val r = RegexExtractor.extract(ExtractPreset.HEX_COLOR, "#FF8800 与 #abc")
        assertEquals(listOf("#FF8800", "#abc"), r.values)
    }

    @Test
    fun `number keeps sign and decimals`() {
        val r = RegexExtractor.extract(ExtractPreset.NUMBER, "温度 -12.5，价格 30")
        assertEquals(listOf("-12.5", "30"), r.values)
    }

    @Test
    fun `chinese runs are merged`() {
        val r = RegexExtractor.extract(ExtractPreset.CHINESE, "Hello 世界你好 ok 再见")
        assertEquals(listOf("世界你好", "再见"), r.values)
    }

    @Test
    fun `distinct keeps first occurrence order`() {
        val r = RegexExtractor.extract(
            ExtractPreset.URL,
            "https://a.com https://b.com https://a.com",
            distinct = true
        )
        assertEquals(listOf("https://a.com", "https://b.com"), r.values)
    }

    @Test
    fun `distinct off keeps duplicates`() {
        val r = RegexExtractor.extract(
            ExtractPreset.URL,
            "https://a.com https://a.com",
            distinct = false
        )
        assertEquals(2, r.count)
    }

    @Test
    fun `all presets compile`() {
        ExtractPreset.entries.forEach { preset ->
            val r = RegexExtractor.extract(preset, "sample 1")
            assertEquals(preset.name, null, r.patternError)
        }
    }
}
