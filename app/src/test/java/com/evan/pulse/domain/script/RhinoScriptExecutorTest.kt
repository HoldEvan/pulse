package com.evan.pulse.domain.script

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpErrorType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JSON_BODY = """{"name":"pulse","count":3}"""

class RhinoScriptExecutorTest {

    private val executor = RhinoScriptExecutor()

    /** 变量写入的落地处，等价于 UI 上的活动环境 */
    private class Recorder : ScriptEnvWriter {
        val vars = linkedMapOf<String, String>()

        override fun replaceActiveVars(variables: List<Pair<String, String>>) {
            vars.clear()
            for ((key, value) in variables) vars[key] = value
        }

        override fun removeActiveVar(name: String) {
            vars.remove(name)
        }
    }

    private class Scope(seed: Map<String, String>) {
        val writer = Recorder()
        val session = ScriptSession(VariableScope({ writer.vars }, writer))

        init {
            writer.vars.putAll(seed)
        }
    }

    private fun request(
        method: String = "GET",
        url: String = "https://api.example.com/v1/users",
        headers: List<Pair<String, String>> = emptyList(),
        params: List<Pair<String, String>> = emptyList(),
        bodyType: BodyType = BodyType.NONE,
        bodyText: String = "",
        formFields: List<Pair<String, String>> = emptyList()
    ) = MutableRequest(
        method = method,
        url = url,
        headers = headers.map { ScriptKv(it.first, it.second) }.toMutableList(),
        params = params.map { ScriptKv(it.first, it.second) }.toMutableList(),
        bodyType = bodyType,
        bodyText = bodyText,
        formFields = formFields.map { ScriptForm(it.first, it.second) }.toMutableList()
    )

    private fun callConfig(
        method: HttpMethod = HttpMethod.GET,
        url: String = "https://api.example.com/v1/users"
    ) = HttpCallConfig(method = method, url = url)

    private fun success(
        statusCode: Int = 200,
        statusText: String = "OK",
        headers: List<Pair<String, String>> = listOf("content-type" to "application/json"),
        body: String = JSON_BODY,
        durationMs: Long = 42L,
        responseSizeBytes: Long = 24L
    ) = HttpCallOutcome.Success(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers.map { KeyValue(it.first, it.second) },
        body = body,
        durationMs = durationMs,
        responseSizeBytes = responseSizeBytes
    )

    private fun runPre(
        script: String,
        request: MutableRequest = request(),
        seed: Map<String, String> = emptyMap()
    ): Triple<MutableRequest, Scope, ScriptRunResult> {
        val scope = Scope(seed)
        return Triple(request, scope, executor.runPre(script, request, scope.session))
    }

    private fun runPost(
        script: String,
        outcome: HttpCallOutcome = success(),
        seed: Map<String, String> = emptyMap()
    ): Pair<Scope, ScriptRunResult> {
        val scope = Scope(seed)
        return scope to executor.runPost(script, callConfig(), outcome, scope.session)
    }

    private fun requireOk(result: ScriptRunResult): ScriptRunResult.Ok =
        result as? ScriptRunResult.Ok ?: error("expected Ok but got $result")

    @Test
    fun blankScriptIsSkipped() {
        val scope = Scope(emptyMap())
        assertEquals(ScriptRunResult.Skipped, executor.runPre("", request(), scope.session))
        assertEquals(
            ScriptRunResult.Skipped,
            executor.runPost("   ", callConfig(), success(), scope.session)
        )
    }

    @Test
    fun preScriptEditsRequestMethodHeadersUrlAndBody() {
        val (request, _, result) = runPre(
            """
            pm.request.setMethod('post')
            pm.request.method = 'PUT'
            pm.request.setHeader('X-Trace', 'abc123')
            pm.request.headers.remove('Accept')
            pm.request.url.setQuery('page=1&size=20')
            pm.request.body.mode = 'raw'
            pm.request.body.text = 'hello'
            """.trimIndent(),
            request(headers = listOf("Accept" to "application/json"))
        )
        requireOk(result)
        assertEquals("PUT", request.method)
        assertEquals(listOf(ScriptKv("X-Trace", "abc123")), request.headers)
        assertEquals("https://api.example.com/v1/users?page=1&size=20", request.url)
        assertEquals(BodyType.RAW, request.bodyType)
        assertEquals("hello", request.bodyText)
        val config = request.toCallConfig(ProxyConfig())
        assertEquals(HttpMethod.PUT, config.method)
        assertEquals("https://api.example.com/v1/users?page=1&size=20", config.url)
        assertEquals(listOf(KeyValue("X-Trace", "abc123", true)), config.headers)
        assertEquals(BodyType.RAW, config.bodyType)
        assertEquals("hello", config.body)
    }

    @Test
    fun queryAddRemoveRoundTripsThroughTheUrl() {
        val (request, _, result) = runPre(
            """
            console.log('before=' + pm.request.url.query.count)
            console.log('x=' + pm.request.url.query.get('x'))
            console.log('hasX=' + pm.request.url.query.has('x'))
            pm.request.url.query.add('y', '2')
            console.log('afterAdd=' + pm.request.url.query.toQuery())
            pm.request.url.query.remove('x')
            console.log('afterRemove=' + pm.request.url.query.toQuery())
            console.log('count=' + pm.request.url.query.count)
            """.trimIndent(),
            request(url = "https://api.example.com/v1/users?x=1")
        )
        val lines = requireOk(result).console
        assertEquals(
            listOf("before=1", "x=1", "hasX=true", "afterAdd=x=1&y=2", "afterRemove=y=2", "count=1"),
            lines
        )
        assertEquals("https://api.example.com/v1/users?y=2", request.url)
        assertTrue("params must be emptied once the query moves into the URL", request.params.isEmpty())
    }

    @Test
    fun urlViewExposesEveryComponent() {
        val (_, _, result) = runPre(
            """
            console.log('scheme=' + pm.request.url.getScheme())
            console.log('host=' + pm.request.url.getHost())
            console.log('hostname=' + pm.request.url.getHostname())
            console.log('port=' + pm.request.url.getPort())
            console.log('path=' + pm.request.url.getPath())
            console.log('query=' + pm.request.url.getQuery())
            console.log('hash=' + pm.request.url.getHash())
            console.log('user=' + pm.request.url.getCredentials().username)
            console.log('pass=' + pm.request.url.getCredentials().password)
            console.log('requestQuery=' + pm.request.query)
            """.trimIndent(),
            request(url = "https://bob:secret@api.example.com:8443/v1/users?page=1&size=20#top")
        )
        assertEquals(
            listOf(
                "scheme=https",
                "host=api.example.com:8443",
                "hostname=api.example.com",
                "port=8443",
                "path=/v1/users",
                "query=page=1&size=20",
                "hash=top",
                "user=bob",
                "pass=secret",
                "requestQuery=page=1&size=20"
            ),
            requireOk(result).console
        )
    }

    @Test
    fun postScriptReadsResponseView() {
        val (_, result) = runPost(
            """
            console.log('code=' + pm.response.statusCode)
            console.log('status=' + pm.response.status)
            console.log('size=' + pm.response.size)
            console.log('time=' + pm.response.time)
            console.log('duration=' + pm.response.duration)
            console.log('contentType=' + pm.response.contentType)
            console.log('isJson=' + pm.response.isJson())
            console.log('name=' + pm.response.json().name)
            console.log('count=' + pm.response.jsonp('count'))
            console.log('header=' + pm.response.headers.get('content-type'))
            console.log('len=' + pm.response.text().length)
            console.log('toJson=' + pm.response.to().json().name)
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "code=200",
                "status=OK",
                "size=24",
                "time=42",
                "duration=42",
                "contentType=application/json",
                "isJson=true",
                "name=pulse",
                "count=3",
                "header=application/json",
                "len=${JSON_BODY.length}",
                "toJson=pulse"
            ),
            requireOk(result).console
        )
    }

    @Test
    fun pmTestRecordsPassAndFail() {
        val (_, result) = runPost(
            """
            pm.test('status is 200', function () {
              if (pm.response.statusCode !== 200) throw new Error('not 200')
            })
            pm.test('body has pulse', function () {
              var name = pm.response.json().name
              if (name !== 'pulse') throw new Error('name was ' + name)
            })
            pm.test('deliberately failing', function () {
              throw new Error('boom')
            })
            """.trimIndent()
        )
        val ok = requireOk(result)
        assertEquals(
            listOf(
                TestRecord("status is 200", true, ""),
                TestRecord("body has pulse", true, ""),
                TestRecord("deliberately failing", false, "Error: boom")
            ),
            ok.tests
        )
        assertEquals(
            listOf(
                "test: PASS status is 200",
                "test: PASS body has pulse",
                "test: FAIL deliberately failing"
            ),
            ok.console
        )
    }

    @Test
    fun bodyJsonRoundTripsThroughTheRequest() {
        val (request, _, result) = runPre(
            """
            pm.request.body.mode = 'json'
            pm.request.body.json = { name: 'pulse', tags: [1, 2] }
            console.log('mode=' + pm.request.body.mode)
            console.log('text=' + pm.request.getBody())
            """.trimIndent()
        )
        val lines = requireOk(result).console
        assertEquals("mode=raw", lines[0])
        assertEquals("""{"name":"pulse","tags":[1,2]}""", lines[1].removePrefix("text="))
        assertEquals(BodyType.JSON, request.bodyType)
    }

    @Test
    fun scopeMergesScriptWritesOverTheSeededEnvironment() {
        val (_, scope, result) = runPre(
            """
            pm.variables.set('base', 'overwritten')
            pm.variables.set('added', '1')
            """.trimIndent(),
            seed = mapOf("base" to "seeded")
        )
        assertEquals(ScriptRunResult.Ok(emptyList(), emptyList()), result)
        assertEquals(mapOf("base" to "overwritten", "added" to "1"), scope.session.scope.all())
        assertEquals(scope.session.scope.all(), scope.writer.vars.toMap())
    }

    @Test
    fun variableScopeIsSharedByVariablesEnvironmentAndGlobals() {
        val (_, scope, result) = runPre(
            """
            pm.variables.set('token', 'abc')
            pm.variables.unshift('env', 'prod')
            console.log('token=' + pm.variables.get('token'))
            console.log('hasEnv=' + pm.variables.has('env'))
            console.log('count=' + pm.variables.count)
            pm.environment.set('fromEnv', '1')
            console.log('viaGlobals=' + pm.globals.get('fromEnv'))
            pm.variables.delete('token')
            console.log('gone=' + pm.variables.has('token'))
            """.trimIndent()
        )
        val ok = requireOk(result)
        assertEquals(
            listOf("token=abc", "hasEnv=true", "count=2", "viaGlobals=1", "gone=false"),
            ok.console
        )
        assertTrue(scope.writer.vars.containsKey("fromEnv"))
        assertTrue(scope.writer.vars.containsKey("env"))
        assertNull(scope.session.scope.get("token"))
    }

    @Test
    fun consoleLevelsGetTheirPrefix() {
        val (_, _, result) = runPre(
            """
            console.log('a', 'b', 3)
            console.info('i')
            console.warn('w')
            console.error('e')
            console.debug('d')
            """.trimIndent()
        )
        assertEquals(
            listOf("a b 3", "info: i", "warn: w", "error: e", "debug: d"),
            requireOk(result).console
        )
    }

    @Test
    fun pmConsoleIsGoneAndConsoleIsNative() {
        val (_, _, result) = runPre(
            """
            console.log(typeof pm.console)
            console.log(typeof pm.log)
            console.log(typeof console.log)
            """.trimIndent()
        )
        assertEquals(
            listOf("undefined", "undefined", "function"),
            requireOk(result).console
        )
    }

    @Test
    fun responseHeadersAreReadOnly() {
        val (_, result) = runPost(
            """
            pm.response.headers.remove('content-type')
            console.log('count=' + pm.response.headers.count)
            """.trimIndent()
        )
        val ok = requireOk(result)
        assertEquals(listOf("warn: pm.response.headers 是只读的，无法删除", "count=1"), ok.console)
    }

    @Test
    fun postScriptOnFailedCallHasNoResponse() {
        val (_, result) = runPost(
            "console.log('has=' + (typeof pm.response))",
            outcome = HttpCallOutcome.Failure(HttpErrorType.NETWORK, "connection refused")
        )
        assertEquals(listOf("has=undefined"), requireOk(result).console)
    }

    @Test
    fun runtimeAndSyntaxErrorsBecomeFailedResults() {
        val scope = Scope(emptyMap())
        val runtime = executor.runPre(
            "console.log('before')\nthrow new Error('kaboom')\n",
            request(),
            scope.session
        )
        assertTrue(runtime is ScriptRunResult.Failed)
        val failed = runtime as ScriptRunResult.Failed
        assertTrue("expected kaboom in ${failed.message}", failed.message.contains("kaboom"))
        assertEquals(listOf("before"), failed.console)

        val syntax = executor.runPre("var ", request(), scope.session)
        assertTrue(syntax is ScriptRunResult.Failed)
        assertTrue((syntax as ScriptRunResult.Failed).message.isNotBlank())
    }

    @Test
    fun infiniteLoopIsCutOffByTheTimeout() {
        val started = System.nanoTime()
        val result = executor.runPre("while (true) {}", request(), Scope(emptyMap()).session)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        assertTrue(result is ScriptRunResult.Failed)
        assertTrue("expected timeout message in ${result}", (result as ScriptRunResult.Failed).message.contains("超时"))
        assertTrue("timeout took ${elapsed}ms", elapsed < 10_000)
    }
}
