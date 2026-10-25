package com.evan.pulse.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {

    private fun input(
        history: List<DiagnosticHistorySample> = emptyList(),
        monitors: List<DiagnosticMonitorSample> = emptyList()
    ) = DiagnosticInput(
        generatedAt = 1_700_000_000_000L,
        meta = mapOf("appVersion" to "0.2.1", "device" to "Pixel 8"),
        settings = mapOf("proxyHost" to "10.0.0.2", "webdavUrl" to "https://dav.example.com/x?pwd=abc"),
        counts = mapOf("history" to 42, "monitors" to 3),
        historyFailures = history,
        monitorFailures = monitors
    )

    @Test
    fun `输出包含四段固定结构与计数`() {
        val text = DiagnosticReport.build(input())
        assertTrue(text.contains("\"schemaVersion\""))
        assertTrue(text.contains("\"meta\""))
        assertTrue(text.contains("\"settings\""))
        assertTrue(text.contains("\"counts\""))
        assertTrue(text.contains("\"historyFailures\": []"))
        assertTrue(text.contains("\"monitors\": 3"))
    }

    @Test
    fun `设置值中的查询串被脱敏`() {
        val text = DiagnosticReport.build(input())
        assertFalse(text.contains("pwd=abc"))
        assertTrue(text.contains("dav.example.com"))
    }

    @Test
    fun `失败历史只保留脱敏后的关键字段`() {
        val text = DiagnosticReport.build(
            input(
                history = listOf(
                    DiagnosticHistorySample(
                        method = "POST",
                        url = "https://api.example.com/login?password=hunter2",
                        statusCode = 401,
                        durationMs = 320,
                        createdAt = 1_699_999_000_000L
                    )
                )
            )
        )
        assertTrue(text.contains("\"method\": \"POST\""))
        assertTrue(text.contains("password=***"))
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("\"statusCode\": 401"))
    }

    @Test
    fun `拨测失败信息中的令牌被脱敏`() {
        val text = DiagnosticReport.build(
            input(
                monitors = listOf(
                    DiagnosticMonitorSample(
                        type = "HTTP",
                        targetName = "订单服务",
                        message = "401 Unauthorized Bearer abcdef123456",
                        durationMs = 120,
                        checkedAt = 1_699_999_000_000L
                    )
                )
            )
        )
        assertTrue(text.contains("订单服务"))
        assertTrue(text.contains("Bearer ***"))
        assertFalse(text.contains("abcdef123456"))
    }

    @Test
    fun `样本数受上限约束`() {
        val many = (1..50).map {
            DiagnosticHistorySample("GET", "https://h/$it", 500, 1, 1_700_000_000_000L)
        }
        val text = DiagnosticReport.build(input(history = many))
        val occurrences = Regex("\"statusCode\": 500").findAll(text).count()
        assertEquals(DiagnosticReport.MAX_SAMPLES, occurrences)
    }

    @Test
    fun `建议文件名带时间戳且以 json 结尾`() {
        val name = DiagnosticReport.suggestFileName(1_700_000_000_000L)
        assertTrue(name.startsWith("pulse-diagnostics-"))
        assertTrue(name.endsWith(".json"))
        assertTrue(Regex("pulse-diagnostics-\\d{8}-\\d{6}\\.json").matches(name))
    }

    @Test
    fun `MIME 类型为 json`() {
        assertEquals("application/json", DiagnosticReport.MIME_TYPE)
    }
}
