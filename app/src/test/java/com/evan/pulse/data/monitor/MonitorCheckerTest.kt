package com.evan.pulse.data.monitor

import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.addressText
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpErrorType
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.net.HttpEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorCheckerTest {

    private fun target(
        type: MonitorType,
        name: String = "api",
        host: String = "example.com",
        port: Int = 80,
        url: String = "https://example.com/ping",
        statusMin: Int = 200,
        statusMax: Int = 399
    ) = MonitorTarget(
        id = 1L,
        projectId = 1L,
        name = name,
        type = type,
        host = host,
        port = port,
        url = url,
        statusMin = statusMin,
        statusMax = statusMax
    )

    @Test
    fun isValidPortAcceptsStandardRange() {
        assertTrue(MonitorChecker.isValidPort(1))
        assertTrue(MonitorChecker.isValidPort(80))
        assertTrue(MonitorChecker.isValidPort(65535))
    }

    @Test
    fun isValidPortRejectsOutOfRangeValues() {
        assertFalse(MonitorChecker.isValidPort(0))
        assertFalse(MonitorChecker.isValidPort(-1))
        assertFalse(MonitorChecker.isValidPort(65536))
    }

    @Test
    fun statusPassesChecksClosedRange() {
        assertTrue(MonitorChecker.statusPasses(200, 200, 399))
        assertTrue(MonitorChecker.statusPasses(399, 200, 399))
        assertTrue(MonitorChecker.statusPasses(204, 200, 399))
        assertFalse(MonitorChecker.statusPasses(199, 200, 399))
        assertFalse(MonitorChecker.statusPasses(400, 200, 399))
        assertFalse(MonitorChecker.statusPasses(500, 200, 399))
    }

    @Test
    fun statusRangeValidAcceptsNormalRanges() {
        assertTrue(MonitorChecker.statusRangeValid(200, 399))
        assertTrue(MonitorChecker.statusRangeValid(100, 599))
        assertTrue(MonitorChecker.statusRangeValid(200, 200))
    }

    @Test
    fun statusRangeValidRejectsBrokenRanges() {
        assertFalse(MonitorChecker.statusRangeValid(399, 200))
        assertFalse(MonitorChecker.statusRangeValid(90, 399))
        assertFalse(MonitorChecker.statusRangeValid(200, 600))
        assertFalse(MonitorChecker.statusRangeValid(100, 1000))
        assertFalse(MonitorChecker.statusRangeValid(0, 100))
    }

    @Test
    fun validationRequiresName() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_name_required),
            MonitorChecker.validation(target(MonitorType.IP, name = ""))
        )
    }

    @Test
    fun validationRejectsIpWithoutHost() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_host_required),
            MonitorChecker.validation(target(MonitorType.IP, host = ""))
        )
    }

    @Test
    fun validationAcceptsIpWithHost() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.IP)))
        assertEquals(
            TargetValidation.Valid,
            MonitorChecker.validation(target(MonitorType.IP, host = "10.0.0.5"))
        )
    }

    @Test
    fun validationRejectsPortWithoutHost() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_host_required),
            MonitorChecker.validation(target(MonitorType.PORT, host = ""))
        )
    }

    @Test
    fun validationRejectsPortOutsideRange() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_port_invalid),
            MonitorChecker.validation(target(MonitorType.PORT, port = 0))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_port_invalid),
            MonitorChecker.validation(target(MonitorType.PORT, port = 70000))
        )
    }

    @Test
    fun validationAcceptsPort() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.PORT)))
    }

    @Test
    fun validationRejectsHttpWithoutUrl() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_required),
            MonitorChecker.validation(target(MonitorType.HTTP, url = ""))
        )
    }

    @Test
    fun validationRejectsHttpWithoutHttpScheme() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_scheme),
            MonitorChecker.validation(target(MonitorType.HTTP, url = "ftp://example.com"))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_scheme),
            MonitorChecker.validation(target(MonitorType.HTTP, url = "example.com/ping"))
        )
    }

    @Test
    fun validationAcceptsHttpsScheme() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.HTTP)))
        assertEquals(
            TargetValidation.Valid,
            MonitorChecker.validation(target(MonitorType.HTTP, url = "http://10.0.0.5:8080"))
        )
    }

    @Test
    fun validationRejectsBrokenStatusRange() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_status_range),
            MonitorChecker.validation(target(MonitorType.HTTP, statusMin = 400, statusMax = 200))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_status_range),
            MonitorChecker.validation(target(MonitorType.HTTP, statusMin = 200, statusMax = 600))
        )
    }

    @Test
    fun addressTextShowsHostForIpProbe() {
        assertEquals(
            "example.com",
            target(MonitorType.IP, host = "example.com").addressText()
        )
        assertEquals(
            "10.0.0.5",
            target(MonitorType.IP, host = "10.0.0.5").addressText()
        )
    }

    @Test
    fun addressTextShowsHostAndPortForPortProbe() {
        assertEquals(
            "example.com:8443",
            target(MonitorType.PORT, host = "example.com", port = 8443).addressText()
        )
        assertEquals(
            "127.0.0.1:1",
            target(MonitorType.PORT, host = "127.0.0.1", port = 1).addressText()
        )
    }

    @Test
    fun addressTextShowsUrlForHttpProbe() {
        assertEquals(
            "https://example.com/ping",
            target(MonitorType.HTTP, url = "https://example.com/ping").addressText()
        )
        assertEquals(
            "http://10.0.0.5:8080/health",
            target(MonitorType.HTTP, url = "http://10.0.0.5:8080/health").addressText()
        )
    }

    private class FakeEngine(private val durationMs: Long) : HttpEngine {
        override suspend fun execute(config: HttpCallConfig): HttpCallOutcome =
            HttpCallOutcome.Success(
                statusCode = 200,
                statusText = "OK",
                headers = emptyList(),
                body = "",
                durationMs = durationMs,
                responseSizeBytes = 0
            )
    }

    private val passthroughResolver = object : com.evan.pulse.domain.PlaceholderResolver {
        override fun resolve(text: String, vars: Map<String, String>):
            com.evan.pulse.domain.PlaceholderResolver.ResolveResult =
            com.evan.pulse.domain.PlaceholderResolver.ResolveResult.Ok(text)
    }

    private fun httpChecker(durationMs: Long) = MonitorChecker(
        httpEngine = FakeEngine(durationMs),
        resolver = passthroughResolver,
        socketFactory = { throw IllegalStateException("HTTP probe should not touch socket") }
    )

    private class CapturingEngine : HttpEngine {
        var captured: HttpCallConfig? = null
        override suspend fun execute(config: HttpCallConfig): HttpCallOutcome {
            captured = config
            return HttpCallOutcome.Success(
                statusCode = 200,
                statusText = "OK",
                headers = emptyList(),
                body = "",
                durationMs = 10L,
                responseSizeBytes = 0
            )
        }
    }

    private val substitutingResolver = object : com.evan.pulse.domain.PlaceholderResolver {
        override fun resolve(text: String, vars: Map<String, String>):
            com.evan.pulse.domain.PlaceholderResolver.ResolveResult {
            var out = text
            for ((k, v) in vars) out = out.replace("{{$k}}", v)
            val missing = Regex("\\{\\{([A-Za-z0-9_]+)\\}\\}").findAll(out)
                .map { it.groupValues[1] }.toList()
            return if (missing.isEmpty()) {
                com.evan.pulse.domain.PlaceholderResolver.ResolveResult.Ok(out)
            } else {
                com.evan.pulse.domain.PlaceholderResolver.ResolveResult.Missing(missing)
            }
        }
    }

    @Test
    fun `check marks slow when latency exceeds threshold`() = runBlocking {
        val t = target(MonitorType.HTTP, url = "https://example.com/ping").copy(latencyThresholdMs = 500)
        val run = httpChecker(1200).check(t, emptyMap(), ProxyConfig())
        assertTrue(run.ok)
        assertTrue(run.slow)
        assertEquals(1200, run.durationMs)
    }

    @Test
    fun `check does not mark slow when latency within threshold`() = runBlocking {
        val t = target(MonitorType.HTTP, url = "https://example.com/ping").copy(latencyThresholdMs = 500)
        val run = httpChecker(200).check(t, emptyMap(), ProxyConfig())
        assertTrue(run.ok)
        assertFalse(run.slow)
    }

    @Test
    fun `check with zero threshold never marks slow`() = runBlocking {
        val t = target(MonitorType.HTTP, url = "https://example.com/ping").copy(latencyThresholdMs = 0)
        val run = httpChecker(5000).check(t, emptyMap(), ProxyConfig())
        assertTrue(run.ok)
        assertFalse(run.slow)
    }

    @Test
    fun `check sends method headers body and auth`() = runBlocking {
        val engine = CapturingEngine()
        val checker = MonitorChecker(
            httpEngine = engine,
            resolver = substitutingResolver,
            socketFactory = { throw IllegalStateException() }
        )
        val t = target(MonitorType.HTTP, url = "https://example.com/api").copy(
            method = "POST",
            headers = listOf(
                com.evan.pulse.data.model.KeyValueEntry("X-Trace", "{{traceId}}"),
                com.evan.pulse.data.model.KeyValueEntry("", "ignored"),
                com.evan.pulse.data.model.KeyValueEntry("X-Off", "v", enabled = false)
            ),
            bodyType = "JSON",
            bodyText = "{\"k\":\"{{name}}\"}",
            authType = "BEARER",
            bearerToken = "tk1"
        )
        val run = checker.check(t, mapOf("traceId" to "t-9", "name" to "v"), ProxyConfig())
        assertTrue(run.ok)
        val cfg = engine.captured!!
        assertEquals("POST", cfg.method.value)
        assertEquals("https://example.com/api", cfg.url)
        assertEquals("t-9", cfg.headers.first { it.key == "X-Trace" }.value)
        assertEquals("Bearer tk1", cfg.headers.first { it.key == "Authorization" }.value)
        assertEquals(2, cfg.headers.size)
        assertEquals(com.evan.pulse.domain.model.BodyType.JSON, cfg.bodyType)
        assertEquals("{\"k\":\"v\"}", cfg.body)
    }

    @Test
    fun `check skips body for GET`() = runBlocking {
        val engine = CapturingEngine()
        val checker = MonitorChecker(
            httpEngine = engine,
            resolver = substitutingResolver,
            socketFactory = { throw IllegalStateException() }
        )
        val t = target(MonitorType.HTTP).copy(bodyType = "JSON", bodyText = "{\"a\":1}")
        val run = checker.check(t, emptyMap(), ProxyConfig())
        assertTrue(run.ok)
        assertEquals(com.evan.pulse.domain.model.BodyType.NONE, engine.captured!!.bodyType)
        assertEquals("", engine.captured!!.body)
    }

    @Test
    fun `evaluateAssertions passes contains and not_contains`() {
        val checker = MonitorChecker(socketFactory = { throw IllegalStateException() })
        val asserts = listOf(
            com.evan.pulse.data.model.MonitorAssertion("CONTAINS", "ok"),
            com.evan.pulse.data.model.MonitorAssertion("NOT_CONTAINS", "error")
        )
        assertEquals(null, checker.evaluateAssertions("{\"status\":\"ok\"}", asserts, emptyMap()))
        assertTrue(checker.evaluateAssertions("has error inside", asserts, emptyMap()) != null)
        assertTrue(checker.evaluateAssertions("plain text", asserts, emptyMap()) != null)
    }

    @Test
    fun `evaluateAssertions jsonpath compares extracted value`() {
        val checker = MonitorChecker(socketFactory = { throw IllegalStateException() })
        val asserts = listOf(
            com.evan.pulse.data.model.MonitorAssertion("JSON_PATH", "$.data.code", "0")
        )
        assertEquals(null, checker.evaluateAssertions("{\"data\":{\"code\":\"0\"}}", asserts, emptyMap()))
        assertTrue(checker.evaluateAssertions("{\"data\":{\"code\":\"500\"}}", asserts, emptyMap()) != null)
        assertTrue(checker.evaluateAssertions("{\"other\":1}", asserts, emptyMap()) != null)
        assertTrue(checker.evaluateAssertions("not json", asserts, emptyMap()) != null)
    }

    @Test
    fun `evaluateAssertions resolves vars and reports missing`() {
        val checker = MonitorChecker(socketFactory = { throw IllegalStateException() })
        val asserts = listOf(
            com.evan.pulse.data.model.MonitorAssertion("CONTAINS", "{{token}}")
        )
        assertEquals(null, checker.evaluateAssertions("bearer abc", asserts, mapOf("token" to "abc")))
        assertTrue(checker.evaluateAssertions("bearer abc", asserts, emptyMap()) != null)
    }

    @Test
    fun `check fails on assertion even with passing status`() = runBlocking {
        val engine = object : HttpEngine {
            override suspend fun execute(config: HttpCallConfig): HttpCallOutcome =
                HttpCallOutcome.Success(
                    statusCode = 200,
                    statusText = "OK",
                    headers = emptyList(),
                    body = "{\"code\":\"E999\"}",
                    durationMs = 10L,
                    responseSizeBytes = 0
                )
        }
        val checker = MonitorChecker(
            httpEngine = engine,
            resolver = substitutingResolver,
            socketFactory = { throw IllegalStateException() }
        )
        val t = target(MonitorType.HTTP).copy(
            assertions = listOf(
                com.evan.pulse.data.model.MonitorAssertion("JSON_PATH", "$.code", "0")
            )
        )
        val run = checker.check(t, emptyMap(), ProxyConfig())
        assertFalse(run.ok)
        assertEquals(10L, run.durationMs)
    }

    @Test
    fun `check fails when placeholder missing in body`() = runBlocking {
        val engine = CapturingEngine()
        val checker = MonitorChecker(
            httpEngine = engine,
            resolver = substitutingResolver,
            socketFactory = { throw IllegalStateException() }
        )
        val t = target(MonitorType.HTTP).copy(method = "POST", bodyType = "RAW", bodyText = "token={{token}}")
        val run = checker.check(t, emptyMap(), ProxyConfig())
        assertFalse(run.ok)
        assertTrue(engine.captured == null)
    }
}
