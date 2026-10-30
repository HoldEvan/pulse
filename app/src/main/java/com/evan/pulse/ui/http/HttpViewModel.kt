package com.evan.pulse.ui.http

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.AssertionEntry
import com.evan.pulse.data.model.HistoryAssertion
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.domain.assertion.AssertionCheck
import com.evan.pulse.domain.assertion.ResponseAssertion
import com.evan.pulse.domain.assertion.ResponseAssertions
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.di.StoredCookie
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.http.HttpExportFormat
import com.evan.pulse.domain.http.exportTo
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.buildGraphqlRequestBody
import com.evan.pulse.domain.model.KeyValue
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.net.NetworkPreset
import com.evan.pulse.domain.net.NetworkThrottleConfig
import com.evan.pulse.domain.script.MutableRequest
import com.evan.pulse.domain.script.ScriptExecutor
import com.evan.pulse.domain.script.ScriptRunResult
import com.evan.pulse.domain.script.CollectionVarWriter
import com.evan.pulse.domain.script.ScriptSession
import com.evan.pulse.domain.script.ScriptForm
import com.evan.pulse.domain.script.ScriptKv
import com.evan.pulse.domain.script.TestRecord
import com.evan.pulse.domain.script.VariableScope
import com.evan.pulse.domain.script.consoleLines
import com.evan.pulse.domain.script.errorMessage
import com.evan.pulse.domain.script.testRecords
import kotlinx.coroutines.launch

class HttpViewModel : ViewModel() {

    private val resolver = ServiceLocator.placeholderResolver
    private val authApplier = ServiceLocator.authApplier
    private val executor = ServiceLocator.scriptExecutor
    private val engine = ServiceLocator.httpEngine
    private val historyStore = ServiceLocator.historyStore
    private val envStore = ServiceLocator.envStore
    private val projectStore = ServiceLocator.projectStore
    private val settingsStore = ServiceLocator.settingsStore

    val currentProjectId = projectStore.currentProjectId

    var method by mutableStateOf(HttpMethod.GET)
    var url by mutableStateOf("{{baseUrl}}/ping")
    var params by mutableStateOf(listOf(KeyValue()))
    var headers by mutableStateOf(listOf(KeyValue()))
    var bodyType by mutableStateOf(BodyType.NONE)
    var bodyText by mutableStateOf("")
    var graphqlVariables by mutableStateOf("{}")
    var formFields by mutableStateOf(listOf(FormField()))
    var authType by mutableStateOf(AuthType.NONE)
    var bearerToken by mutableStateOf("")
    var basicUsername by mutableStateOf("")
    var basicPassword by mutableStateOf("")
    var apiKeyName by mutableStateOf("")
    var apiKeyValue by mutableStateOf("")
    var preScript by mutableStateOf("")
    var postScript by mutableStateOf("")

    /** 请求超时（秒），输入框直接绑文本，非法输入回落到默认 30s */
    var timeoutSec by mutableStateOf("30")

    /** 是否套用设置页里的全局代理；关闭则本次请求强制直连 */
    var useProxy by mutableStateOf(true)

    /** 跳过 TLS 证书校验；仅用于调试自签名或内网 HTTPS 服务 */
    var insecureTls by mutableStateOf(false)

    /** 是否自动跟随 3xx 重定向；关闭后直接展示 3xx 与 Location */
    var followRedirects by mutableStateOf(true)

    /** 最大跟随跳数，输入框直接绑文本 */
    var maxRedirects by mutableStateOf("10")

    /** 弱网模拟档位；NONE 即关闭，FORCE_TIMEOUT 配合秒数生效 */
    var throttlePreset by mutableStateOf(NetworkPreset.NONE)

    /** 强制超时秒数，输入框直接绑文本，空或 0 视为不启用 */
    var forceTimeoutSec by mutableStateOf("")

    var activeTab by mutableIntStateOf(0)
    var scriptTab by mutableIntStateOf(0)
    var sending by mutableStateOf(false)
    var outcome by mutableStateOf<HttpCallOutcome?>(null)
    var missingVars by mutableStateOf<List<String>?>(null)

    /** 上一次请求里前置/后置脚本的 console 输出 */
    var scriptConsole by mutableStateOf<List<String>>(emptyList())
    var scriptTests by mutableStateOf<List<TestRecord>>(emptyList())

    /** 响应断言：每次发送后按顺序校验响应体，结果见 [assertionResults] */
    var assertions by mutableStateOf<List<ResponseAssertion>>(emptyList())
    var assertionResults by mutableStateOf<List<AssertionCheck>>(emptyList())
    var scriptError by mutableStateOf<String?>(null)

    private val collectionStore = ServiceLocator.collectionStore

    /** 当前请求所属文件夹；为空时没有文件夹变量可用 */
    private var activeCollectionId: Long? = null

    /** 与集合条目的绑定：从集合加载或保存过一次后，再保存即原地覆盖 */
    private val loadedRequest = LoadedRequestTracker(collectionStore)

    init {
        viewModelScope.launch {
            collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.HTTP) {
                    restore(req.data)
                    loadedRequest.track(req.id)
                    activeCollectionId = req.collectionId
                    collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 已绑定集合条目时原地覆盖；返回 false 表示需要弹出保存对话框 */
    fun saveOverLoaded(): Boolean = loadedRequest.saveOver { snapshot() }

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        loadedRequest.track(id)
        activeCollectionId = collectionStore.requests.value.firstOrNull { it.id == id }?.collectionId
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        HttpSnapshot.serializer(),
        HttpSnapshot(
            method = method.value,
            url = url,
            params = params.map { KeyValueEntry(it.key, it.value, it.enabled) },
            headers = headers.map { KeyValueEntry(it.key, it.value, it.enabled) },
            bodyType = bodyType.name,
            bodyText = bodyText,
            graphqlVariables = graphqlVariables,
            formFields = formFields.map { FormFieldEntry(it.name, it.value, it.isFile, it.fileName) },
            authType = authType.name,
            bearerToken = bearerToken,
            basicUsername = basicUsername,
            basicPassword = basicPassword,
            apiKeyName = apiKeyName,
            apiKeyValue = apiKeyValue,
            preScript = preScript,
            postScript = postScript,
            timeoutSec = timeoutSec,
            useProxy = useProxy,
            throttlePreset = throttlePreset.name,
            forceTimeoutSec = forceTimeoutSec,
            insecureTls = insecureTls,
            followRedirects = followRedirects,
            maxRedirects = maxRedirects.toIntOrNull()?.coerceIn(0, 20) ?: 10,
            assertions = assertions.map { AssertionEntry(it.type, it.expression, it.expected) }
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(HttpSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        method = HttpMethod.parse(snap.method)
        url = snap.url
        params = snap.params.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        bodyType = BodyType.entries.firstOrNull { it.name == snap.bodyType } ?: BodyType.NONE
        bodyText = snap.bodyText
        graphqlVariables = snap.graphqlVariables
        formFields = snap.formFields
            .map { FormField(name = it.name, value = it.value, isFile = it.isFile, fileName = it.fileName) }
            .ifEmpty { listOf(FormField()) }
        authType = AuthType.entries.firstOrNull { it.name == snap.authType } ?: AuthType.NONE
        bearerToken = snap.bearerToken
        basicUsername = snap.basicUsername
        basicPassword = snap.basicPassword
        apiKeyName = snap.apiKeyName
        apiKeyValue = snap.apiKeyValue
        preScript = snap.preScript
        postScript = snap.postScript
        timeoutSec = snap.timeoutSec.takeIf { it.isNotBlank() } ?: "30"
        useProxy = snap.useProxy
        throttlePreset = NetworkPreset.entries.firstOrNull { it.name == snap.throttlePreset } ?: NetworkPreset.NONE
        forceTimeoutSec = snap.forceTimeoutSec
        insecureTls = snap.insecureTls
        followRedirects = snap.followRedirects
        maxRedirects = snap.maxRedirects.toString()
        assertions = snap.assertions.map { ResponseAssertion(it.type, it.expression, it.expected) }
    }

    /** 活动环境变量与文件夹变量合并；文件夹变量优先，便于按集合覆盖环境默认值 */
    fun activeEnvVars(): Map<String, String> {
        val pid = currentProjectId.value
        val env = envStore.environments.value.firstOrNull { it.projectId == pid && it.isActive }
        val base = LinkedHashMap<String, String>()
        env?.variables?.forEach { (key, value) -> base[key] = value }
        base.putAll(collectionStore.resolvedVars(activeCollectionId))
        return base
    }

    /** 供输入框下方提示：把文本里的 {{var}} 逐项解析成“名字 → 解析值”，缺失的 value 为 null */
    fun placeholderHints(text: String): List<Pair<String, String?>> {
        if (text.isBlank()) return emptyList()
        val vars = activeEnvVars()
        val seen = LinkedHashMap<String, String?>()
        for (m in PlaceholderResolver.PLACEHOLDER_REGEX.findAll(text)) {
            val key = m.groupValues[1]
            seen.putIfAbsent(key, vars[key])
        }
        return seen.map { (k, v) -> k to v }
    }

    fun updateParams(index: Int, value: KeyValue) {
        params = params.mapIndexed { i, kv -> if (i == index) value else kv }
    }

    fun addParam() {
        params = params + KeyValue()
    }

    fun removeParam(index: Int) {
        params = params.filterIndexed { i, _ -> i != index }
        if (params.isEmpty()) params = listOf(KeyValue())
    }

    fun updateHeaders(index: Int, value: KeyValue) {
        headers = headers.mapIndexed { i, kv -> if (i == index) value else kv }
    }

    fun addHeader() {
        headers = headers + KeyValue()
    }

    fun removeHeader(index: Int) {
        headers = headers.filterIndexed { i, _ -> i != index }
        if (headers.isEmpty()) headers = listOf(KeyValue())
    }

    fun updateFormField(index: Int, value: FormField) {
        formFields = formFields.mapIndexed { i, f -> if (i == index) value else f }
    }

    fun addFormField() {
        formFields = formFields + FormField()
    }

    fun addFileField() {
        formFields = formFields + FormField(isFile = true)
    }

    fun setFormFieldFile(index: Int, uri: android.net.Uri) {
        val resolver = ServiceLocator.appContext.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }
        val name = displayName
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: uri.toString()
        // 尽量把一次性读授权升级为可持久化授权，历史重发/重试时文件仍可读
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        formFields = formFields.mapIndexed { i, f ->
            if (i == index) f.copy(isFile = true, fileName = name, fileUri = uri.toString()) else f
        }
    }

    fun removeFormField(index: Int) {
        formFields = formFields.filterIndexed { i, _ -> i != index }
        if (formFields.isEmpty()) formFields = listOf(FormField())
    }

    fun dismissMissing() {
        missingVars = null
    }

    /** 拼装后的调用配置与缺失变量列表；缺失变量的占位符原样保留 */
    private data class BuiltCall(
        val config: HttpCallConfig,
        val missingVars: List<String>,
        val preResult: ScriptRunResult
    )

    /** 只按界面当前编辑状态拼配置：占位符先不解析，鉴权头先合并进来 */
    private fun rawConfig(): HttpCallConfig {
        val appliedAuth = authApplier.apply(
            AuthConfig(
                type = authType,
                bearerToken = bearerToken,
                basicUsername = basicUsername,
                basicPassword = basicPassword,
                apiKeyName = apiKeyName,
                apiKeyValue = apiKeyValue
            )
        )
        return HttpCallConfig(
            method = method,
            url = url,
            headers = headers.filter { it.enabled && it.key.isNotBlank() } + appliedAuth,
            queryParams = params.filter { it.enabled && it.key.isNotBlank() },
            bodyType = bodyType,
            body = bodyText,
            graphqlVariables = graphqlVariables,
            formFields = formFields.filter { it.name.isNotBlank() && !it.isFile },
            timeoutMs = (timeoutSec.toLongOrNull()?.coerceAtLeast(1) ?: 30L) * 1000L,
            proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig(),
            throttle = NetworkThrottleConfig(
                preset = throttlePreset,
                forceTimeoutSec = forceTimeoutSec.toIntOrNull()?.coerceIn(0, 300) ?: 0
            ),
            insecureTls = insecureTls,
            followRedirects = followRedirects,
            maxRedirects = maxRedirects.toIntOrNull()?.coerceIn(0, 20) ?: 10
        )
    }

    /** 把 {{var}} 换成活动环境的值；缺的变量原样保留并收集到 [missing] */
    private fun resolveConfig(
        config: HttpCallConfig,
        vars: Map<String, String>,
        missing: MutableSet<String>
    ): HttpCallConfig {
        fun res(text: String): String = when (val r = resolver.resolve(text, vars)) {
            is PlaceholderResolver.ResolveResult.Ok -> r.resolved
            is PlaceholderResolver.ResolveResult.Missing -> {
                missing += r.keys
                text
            }
        }
        return config.copy(
            url = res(config.url),
            headers = config.headers.map { it.copy(value = res(it.value)) },
            queryParams = config.queryParams.map { it.copy(value = res(it.value)) },
            body = if (config.bodyType in listOf(BodyType.JSON, BodyType.RAW, BodyType.GRAPHQL)) {
                res(config.body)
            } else {
                config.body
            },
            graphqlVariables = if (config.bodyType == BodyType.GRAPHQL) res(config.graphqlVariables) else config.graphqlVariables,
            formFields = config.formFields.map { it.copy(value = res(it.value)) }
        )
    }

    /**
     * 用当前编辑状态拼出可发送的配置，发送与导出共用这一步，
     * 保证两者对占位符、鉴权头、参数过滤的处理完全一致。
     *
     * runScripts 为 true 时先跑前置脚本再解析占位符，
     * 这样脚本里 pm.variables.set 出来的值当次请求就能用上。
     */
    private fun buildCall(scope: VariableScope, runScripts: Boolean): BuiltCall {
        val raw = rawConfig()
        val mutable = MutableRequest(
            method = raw.method.value,
            url = raw.url,
            headers = raw.headers.map { ScriptKv(it.key, it.value) }.toMutableList(),
            params = raw.queryParams.map { ScriptKv(it.key, it.value) }.toMutableList(),
            bodyType = raw.bodyType,
            bodyText = raw.body,
            formFields = raw.formFields.map { ScriptForm(it.name, it.value) }.toMutableList()
        )

        val preResult = if (runScripts && preScript.isNotBlank()) {
            val sandbox = ScriptSession(scope, collectionScope())
            executor.runPre(preScript, mutable, sandbox)
        } else {
            ScriptRunResult.Skipped
        }

        val missing = mutableSetOf<String>()
        val config = resolveConfig(mutable.toCallConfig(raw.proxy), scope.all(), missing)
        return BuiltCall(config, missing.sorted(), preResult)
    }

    /** 文件夹变量作用域：只读写当前文件夹自身的变量 */
    private fun collectionScope(): VariableScope? {
        val id = activeCollectionId ?: return null
        return VariableScope(
            envLookup = { collectionStore.varsOf(id).filter { it.enabled && it.key.isNotBlank() }
                .associate { it.key to it.value } },
            envWriter = CollectionVarWriter(id)
        )
    }

    /** 导出当前请求为 [format] 格式文本；不跑脚本，先看原始内容 */
    fun exportText(format: HttpExportFormat): String =
        buildCall(VariableScope(::activeEnvVars, ServiceLocator.scriptEnvWriter), runScripts = false)
            .config.exportTo(format)

    fun send() {
        if (sending) return
        viewModelScope.launch {
            sending = true
            outcome = null
            missingVars = null
            scriptConsole = emptyList()
            scriptTests = emptyList()
            assertionResults = emptyList()
            scriptError = null
            try {
                // 前置与后置脚本共用同一个变量作用域，前置设的值后置能读到
                val scope = VariableScope(::activeEnvVars, ServiceLocator.scriptEnvWriter)
                val built = buildCall(scope, runScripts = true)
                if (built.missingVars.isNotEmpty()) {
                    missingVars = built.missingVars
                    scriptConsole = built.preResult.consoleLines()
                    scriptTests = built.preResult.testRecords()
                    scriptError = built.preResult.errorMessage()
                    return@launch
                }

                val result = engine.execute(withCookies(built.config))
                outcome = result
                // 历史先在这里攒好，等断言求值完再落库，详情页才能看到这次请求的断言结论
                var historyEntry: HistoryEntry? = null
                if (result is HttpCallOutcome.Success) {
                    storeResponseCookies(built.config.url, result.headers)
                    val requestBody = clipBody(renderedBody(built.config))
                    val responseBody = clipBody(result.body)
                    historyEntry =
                        HistoryEntry(
                            id = 0,
                            projectId = currentProjectId.value,
                            method = built.config.method.value,
                            url = historyUrl(built.config),
                            statusCode = result.statusCode,
                            statusText = result.statusText,
                            durationMs = result.durationMs,
                            success = result.statusCode in 200..399,
                            createdAt = System.currentTimeMillis(),
                            responseSizeBytes = result.responseSizeBytes,
                            requestHeaders = (
                                result.requestHeaders.takeIf { it.isNotEmpty() }
                                    ?: built.config.headers
                                ).map { "${it.key}: ${it.value}" },
                            requestBody = requestBody.text,
                            requestBodyTruncated = requestBody.truncated,
                            responseHeaders = result.headers.map { "${it.key}: ${it.value}" },
                            responseBody = responseBody.text,
                            responseBodyTruncated = responseBody.truncated
                        )
                }

                val postResult = if (postScript.isNotBlank()) {
                    executor.runPost(postScript, built.config, result, ScriptSession(scope, collectionScope()))
                } else {
                    ScriptRunResult.Skipped
                }
                publishScriptResult(built.preResult, postResult)
                // 断言放在脚本之后求值：前置/后置脚本 set 的变量都能被 {{变量}} 引用；
                // 请求失败（超时/断网）没有响应体可校验，结果清空
                assertionResults = if (result is HttpCallOutcome.Success) {
                    ResponseAssertions.evaluate(result.body, assertions, scope.all())
                } else {
                    emptyList()
                }
                historyEntry?.let { entry ->
                    historyStore.add(
                        entry.copy(
                            assertions = assertionResults.map { check ->
                                HistoryAssertion(
                                    type = check.assertion.type,
                                    expression = check.assertion.expression,
                                    expected = check.assertion.expected,
                                    passed = check.passed,
                                    detail = check.detail
                                )
                            }
                        )
                    )
                }
            } finally {
                sending = false
            }
        }
    }

    /** 把前置与后置脚本的输出合并到界面状态上 */
    private fun publishScriptResult(pre: ScriptRunResult, post: ScriptRunResult) {
        scriptConsole = pre.consoleLines() + post.consoleLines()
        scriptTests = pre.testRecords() + post.testRecords()
        scriptError = pre.errorMessage() ?: post.errorMessage()
    }

    fun cancel() {
        sending = false
    }

    /** 历史里记录的请求体：表单类按 name=value 摊开，便于回看提交了什么 */
    private fun renderedBody(config: HttpCallConfig): String = when (config.bodyType) {
        BodyType.NONE -> ""
        BodyType.JSON, BodyType.RAW -> config.body
        BodyType.GRAPHQL -> buildGraphqlRequestBody(config.body, config.graphqlVariables)
        BodyType.FORM_URLENCODED, BodyType.FORM_DATA -> config.formFields
            .joinToString("&") { field -> "${field.name}=${field.value}" }
    }

    /** 自动附带当前项目 Cookie 库里命中 host 的 Cookie；用户手动设置的 Cookie 头优先 */
    private fun withCookies(config: HttpCallConfig): HttpCallConfig {
        if (config.headers.any { it.enabled && it.key.equals("Cookie", ignoreCase = true) }) return config
        val host = config.url.toHttpUrlOrNull()?.host ?: return config
        val jar = ServiceLocator.cookieStore.forHost(currentProjectId.value, host)
        if (jar.isEmpty()) return config
        val value = jar.joinToString("; ") { "${it.name}=${it.value}" }
        return config.copy(headers = config.headers + KeyValue("Cookie", value))
    }

    /** 收集响应 Set-Cookie 存入当前项目 Cookie 库，供后续同域请求自动携带 */
    private fun storeResponseCookies(url: String, headers: List<KeyValue>) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        headers.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.forEach { kv ->
            Cookie.parse(httpUrl, kv.value)?.let { cookie ->
                if (cookie.name.isNotBlank()) {
                    ServiceLocator.cookieStore.upsert(
                        currentProjectId.value,
                        StoredCookie(
                            name = cookie.name,
                            value = cookie.value,
                            domain = cookie.domain,
                            path = cookie.path,
                            expiresAtMs = cookie.expiresAt
                        )
                    )
                }
            }
        }
    }

    /**
     * 历史里保存完整请求 URL：引擎发送时会把查询参数追加到 URL 后，
     * 历史记录需与实际请求一致，因此这里按同样的方式拼回参数。
     */
    private fun historyUrl(config: HttpCallConfig): String {
        val qs = config.queryParams.filter { it.enabled && it.key.isNotBlank() }
        if (qs.isEmpty()) return config.url
        val separator = if ('?' in config.url) '&' else '?'
        return config.url + separator + qs.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun clipBody(text: String): ClippedBody =
        if (text.length <= HISTORY_BODY_CHAR_CAP) ClippedBody(text, false)
        else ClippedBody(text.substring(0, HISTORY_BODY_CHAR_CAP), true)
}

/**
 * 单条历史保存的字符上限。历史上限 500 条且每次变更整体重写备份文件，
 * 正文不裁剪会让文件在几十上百 MB 量级，写入变成明显卡顿的来源。
 */
private const val HISTORY_BODY_CHAR_CAP = 16 * 1024

/** 裁剪后的正文与是否被截断的标记 */
private data class ClippedBody(val text: String, val truncated: Boolean)
