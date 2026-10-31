package com.evan.pulse.domain.runner

import com.evan.pulse.data.model.RequestType
import com.evan.pulse.domain.script.TestRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunReportExporterTest {

    private fun step(
        name: String,
        success: Boolean,
        status: Int? = 200,
        attempts: Int = 1,
        error: String? = null,
        tests: List<TestRecord> = emptyList()
    ) = CollectionRunner.Step(
        requestId = 1L,
        name = name,
        type = RequestType.HTTP,
        method = "GET",
        url = "https://example.com/api",
        statusCode = status,
        durationMs = 12L,
        success = success,
        bodyPreview = "",
        error = error,
        console = emptyList(),
        tests = tests,
        attempts = attempts
    )

    @Test
    fun `json report includes summary and step fields`() {
        val result = CollectionRunner.RunResult(
            listOf(
                step("ok", true),
                step("bad", false, status = 500, attempts = 2, error = "boom")
            )
        )
        val json = RunReportExporter.render(
            RunReportFormat.JSON, "demo", result, 1_700_000_000_000L
        )
        assertTrue(json.contains("\"collection\""))
        assertTrue(json.contains("\"passed\": 1"))
        assertTrue(json.contains("\"failed\": 1"))
        assertTrue(json.contains("\"name\": \"ok\""))
        assertTrue(json.contains("\"attempts\": 2"))
        assertTrue(json.contains("\"error\": \"boom\""))
        assertTrue(json.contains("\"statusCode\": 500"))
    }

    @Test
    fun `json report renders null status and error`() {
        val result = CollectionRunner.RunResult(
            listOf(step("net", false, status = null, error = null))
        )
        val json = RunReportExporter.render(
            RunReportFormat.JSON, "demo", result, 1_700_000_000_000L
        )
        assertTrue(json.contains("\"statusCode\": null"))
        assertTrue(json.contains("\"error\": null"))
    }

    @Test
    fun `html report escapes markup and marks pass fail`() {
        val result = CollectionRunner.RunResult(
            listOf(
                step("<b>a&b</b>", true, tests = listOf(TestRecord("t<1>", true, ""))),
                step("bad", false, status = 500, error = "<script>")
            )
        )
        val html = RunReportExporter.render(
            RunReportFormat.HTML, "demo<x>", result, 1_700_000_000_000L
        )
        assertTrue(html.contains("&lt;b&gt;a&amp;b&lt;/b&gt;"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("demo&lt;x&gt;"))
        assertTrue(html.contains("PASS"))
        assertTrue(html.contains("FAIL"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `suggested name carries format extension`() {
        val name = RunReportExporter.suggestedName(RunReportFormat.JSON, 1_700_000_000_000L)
        assertTrue(name.startsWith("run-report_"))
        assertTrue(name.endsWith(".json"))
    }
}
