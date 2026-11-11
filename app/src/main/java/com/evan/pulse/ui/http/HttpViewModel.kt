package com.evan.pulse.ui.http

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.ui.common.tlsConfigOf
import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.AssertionEntry
import com.evan.pulse.data.model.HistoryAssertion
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.domain.assertion.AssertionCheck
import com.evan.pulse.domain.assertion.AssertionTarget
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

    private val collectionStore = ServiceLocator.collectionStore

    private var nextTabId = 1

    /** 所有请求标签，至少保留一个；每个标签自带请求配置与响应 */
    val tabs = mutableStateListOf(newTab())

    var selectedIndex by mutableIntStateOf(0)
        private set

    val selected: HttpTab get() = tabs[selectedIndex.coerceIn(0, tabs.lastIndex)]

    val canAddTab: Boolean get() = tabs.size < MAX_TABS

    /** 编辑区的子 Tab（参数/请求体/...），与请求标签无关，全局一份 */
    var activeTab by mutableIntStateOf(0)
    var scriptTab by mutableIntStateOf(0)

    // 以下成员都指向所选标签，页面其余部分无需感知多标签
    var method: HttpMethod
        get() = selected.method
        set(value) { selected.method = value }

    var url: String
        get() = selected.url
        set(value) { selected.url = value }

    var params: List<KeyValue>
        get() = selected.params
        set(value) { selected.params = value }

    var headers: List<KeyValue>
        get() = selected.headers
        set(value) { selected.headers = value }

    var bodyType: BodyType
        get() = selected.bodyType
        set(value) { selected.bodyType = value }

    var bodyText: String
        get() = selected.bodyText
        set(value) { selected.bodyText = value }

    var graphqlVariables: String
        get() = selected.graphqlVariables
        set(value) { selected.graphqlVariables = value }

    var formFields: List<FormField>
        get() = selected.formFields
        set(value) { selected.formFields = value }

    var authType: AuthType
        get() = selected.authType
        set(value) { selected.authType = value }

    var bearerToken: String
        get() = selected.bearerToken
        set(value) { selected.bearerToken = value }

    var basicUsername: String
        get() = selected.basicUsername
        set(value) { selected.basicUsername = value }

    var basicPassword: String
        get() = selected.basicPassword
        set(value) { selected.basicPassword = value }

    var apiKeyName: String
        get() = selected.apiKeyName
        set(value) { selected.apiKeyName = value }

    var apiKeyValue: String
        get() = selected.apiKeyValue
        set(value) { selected.apiKeyValue = value }

    var preScript: String
        get() = selected.preScript
        set(value) { selected.preScript = value }

    var postScript: String
        get() = selected.postScript
        set(value) { selected.postScript = value }

    var timeoutSec: String
        get() = selected.timeoutSec
        set(value) { selected.timeoutSec = value }

    var connectTimeoutSec: String
        get() = selected.connectTimeoutSec
        set(value) { selected.connectTimeoutSec = value }

    var readTimeoutSec: String
        get() = selected.readTimeoutSec
        set(value) { selected.readTimeoutSec = value }

    var bodyFileUri: String?
        get() = selected.bodyFileUri
        set(value) { selected.bodyFileUri = value }

    var bodyFileName: String?
        get() = selected.bodyFileName
        set(value) { selected.bodyFileName = value }

    var useProxy: Boolean
        get() = selected.useProxy
        set(value) { selected.useProxy = value }

    var insecureTls: Boolean
        get() = selected.insecureTls
        set(value) { selected.insecureTls = value }

    var customCaPem: String
        get() = selected.customCaPem
        set(value) { selected.customCaPem = value }

    var clientCertPem: String
        get() = selected.clientCertPem
        set(value) { selected.clientCertPem = value }

    var clientKeyPem: String
        get() = selected.clientKeyPem
        set(value) { selected.clientKeyPem = value }

    var followRedirects: Boolean
        get() = selected.followRedirects
        set(value) { selected.followRedirects = value }

    var maxRedirects: String
        get() = selected.maxRedirects
        set(value) { selected.maxRedirects = value }

    var throttlePreset: NetworkPreset
        get() = selected.throttlePreset
        set(value) { selected.throttlePreset = value }

    var forceTimeoutSec: String
        get() = selected.forceTimeoutSec
        set(value) { selected.forceTimeoutSec = value }

    var assertions: List<ResponseAssertion>
        get() = selected.assertions
        set(value) { selected.assertions = value }

    var sending: Boolean
        get() = selected.sending
        set(value) { selected.sending = value }

    var outcome: HttpCallOutcome?
        get() = selected.outcome
        set(value) { selected.outcome = value }

    var assertionResults: List<AssertionCheck>
        get() = selected.assertionResults
        set(value) { selected.assertionResults = value }

    var scriptConsole: List<String>
        get() = selected.scriptConsole
        set(value) { selected.scriptConsole = value }

    var scriptTests: List<TestRecord>
        get() = selected.scriptTests
        set(value) { selected.scriptTests = value }

    var scriptError: String?
        get() = selected.scriptError
        set(value) { selected.scriptError = value }

    var missingVars: List<String>?
        get() = selected.missingVars
        set(value) { selected.missingVars = value }

    private fun newTab() = HttpTab(nextTabId++, LoadedRequestTracker(collectionStore))

    fun selectTab(index: Int) {
        if (index in tabs.indices) selectedIndex = index
    }

    fun addTab() {
        if (!canAddTab) return
        tabs.add(newTab())
        selectedIndex = tabs.lastIndex
    }

    /** 关闭标签会取消该标签正在进行的请求；关掉最后一个时换成一个空白标签 */
    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        tabs[index].job?.cancel()
        tabs.removeAt(index)
        if (tabs.isEmpty()) tabs.add(newTab())
        selectedIndex = when {
            selectedIndex > index -> selectedIndex - 1
            else -> selectedIndex.coerceAtMost(tabs.lastIndex)
        }
    }

    override fun onCleared() {
        tabs.forEach { it.job?.cancel() }
    }

    private companion object {
        /** 同时保留的请求标签上限；每个标签都完整保留请求与响应 */
        const val MAX_TABS = 5
    }

    init {
        viewModelScope.launch {
            collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.HTTP) {
                    // 当前标签还是空白就直接填进去，否则开一个新标签，避免覆盖手上的请求
                    val target = if (selected.isIdle() || !canAddTab) {
                        selected
                    } else {
                        newTab().also { tabs.add(it); selectedIndex = tabs.lastIndex }
                    }
                    restoreInto(target, req.data)
                    target.loadedRequest.track(req.id)
                    target.activeCollectionId = req.collectionId
                    collectionStore.consumePending(req)
                }
            }
        }
    }


    /** 当前所属集合条目 id；保存对话框据此切换为原地更新 */
    fun loadedRequestId(): Long? = selected.loadedRequest.requestId

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        selected.loadedRequest.track(id)
        selected.activeCollectionId = collectionStore.requests.value.firstOrNull { it.id == id }?.collectionId
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
            connectTimeoutSec = connectTimeoutSec,
            readTimeoutSec = readTimeoutSec,
            bodyFileUri = bodyFileUri,
            bodyFileName = bodyFileName,
            useProxy = useProxy,
            throttlePreset = throttlePreset.name,
            forceTimeoutSec = forceTimeoutSec,
            insecureTls = insecureTls,
            customCaPem = customCaPem,
            clientCertPem = clientCertPem,
            clientKeyPem = clientKeyPem,
            followRedirects = followRedirects,
            maxRedirects = maxRedirects.toIntOrNull()?.coerceIn(0, 20) ?: 10,
            assertions = assertions.map { AssertionEntry(it.type, it.expression, it.expected, it.operator) }
        )
    )

    /** 把集合条目的快照写进指定标签；只动目标标签，不依赖当前选中项 */
    private fun restoreInto(tab: HttpTab, data: String) {
        val snap = try {
            snapshotJson.decodeFromString(HttpSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        tab.method = HttpMethod.parse(snap.method)
        tab.url = snap.url
        tab.params = snap.params.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        tab.headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        tab.bodyType = BodyType.entries.firstOrNull { it.name == snap.bodyType } ?: BodyType.NONE
        tab.bodyText = snap.bodyText
        tab.graphqlVariables = snap.graphqlVariables
        tab.formFields = snap.formFields
            .map { FormField(name = it.name, value = it.value, isFile = it.isFile, fileName = it.fileName) }
            .ifEmpty { listOf(FormField()) }
        tab.authType = AuthType.entries.firstOrNull { it.name == snap.authType } ?: AuthType.NONE
        tab.bearerToken = snap.bearerToken
        tab.basicUsername = snap.basicUsername
        tab.basicPassword = snap.basicPassword
        tab.apiKeyName = snap.apiKeyName
        tab.apiKeyValue = snap.apiKeyValue
        tab.preScript = snap.preScript
        tab.postScript = snap.postScript
        tab.timeoutSec = snap.timeoutSec.takeIf { it.isNotBlank() } ?: "30"
        tab.connectTimeoutSec = snap.connectTimeoutSec
        tab.readTimeoutSec = snap.readTimeoutSec
        tab.bodyFileUri = snap.bodyFileUri
        tab.bodyFileName = snap.bodyFileName
        tab.useProxy = snap.useProxy
        tab.throttlePreset = NetworkPreset.entries.firstOrNull { it.name == snap.throttlePreset } ?: NetworkPreset.NONE
        tab.forceTimeoutSec = snap.forceTimeoutSec
        tab.insecureTls = snap.insecureTls
        tab.customCaPem = snap.customCaPem
        tab.clientCertPem = snap.clientCertPem
        tab.clientKeyPem = snap.clientKeyPem
        tab.followRedirects = snap.followRedirects
        tab.maxRedirects = snap.maxRedirects.toString()
        tab.assertions = snap.assertions.map { ResponseAssertion(it.type, it.expression, it.expected, it.operator) }
    }

    /** 活动环境变量与文件夹变量合并；文件夹变量优先，便于按集合覆盖环境默认值 */
    fun activeEnvVars(): Map<String, String> = envVarsFor(selected.activeCollectionId)

    /** 指定文件夹的变量视图；请求在另一个标签里发送时仍用发起时的文件夹 */
    private fun envVarsFor(collectionId: Long?): Map<String, String> {
        val pid = currentProjectId.value
        val env = envStore.environments.value.firstOrNull { it.projectId == pid && it.isActive }
        val base = LinkedHashMap<String, String>()
        env?.variables?.forEach { (key, value) -> base[key] = value }
        base.putAll(collectionStore.resolvedVars(collectionId))
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

    /** 选择原始二进制请求体文件，补齐展示名并申请持久读权限，便于历史重发 */
    fun setBodyFile(uri: android.net.Uri) {
        val resolver = ServiceLocator.appContext.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }
        bodyFileName = displayName
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: uri.toString()
        bodyFileUri = uri.toString()
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearBodyFile() {
        bodyFileUri = null
        bodyFileName = null
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
    private fun rawConfig(tab: HttpTab): HttpCallConfig {
        val appliedAuth = authApplier.apply(
            AuthConfig(
                type = tab.authType,
                bearerToken = tab.bearerToken,
                basicUsername = tab.basicUsername,
                basicPassword = tab.basicPassword,
                apiKeyName = tab.apiKeyName,
                apiKeyValue = tab.apiKeyValue
            )
        )
        return HttpCallConfig(
            method = tab.method,
            url = tab.url,
            headers = tab.headers.filter { it.enabled && it.key.isNotBlank() } + appliedAuth,
            queryParams = tab.params.filter { it.enabled && it.key.isNotBlank() },
            bodyType = tab.bodyType,
            body = tab.bodyText,
            graphqlVariables = tab.graphqlVariables,
            formFields = tab.formFields.filter { it.name.isNotBlank() },
            timeoutMs = (tab.timeoutSec.toLongOrNull()?.coerceAtLeast(1) ?: 30L) * 1000L,
            connectTimeoutMs = (tab.connectTimeoutSec.toLongOrNull()?.takeIf { it > 0 } ?: 0L) * 1000L,
            readTimeoutMs = (tab.readTimeoutSec.toLongOrNull()?.takeIf { it > 0 } ?: 0L) * 1000L,
            fileUri = tab.bodyFileUri,
            fileName = tab.bodyFileName,
            proxy = if (tab.useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig(),
            throttle = NetworkThrottleConfig(
                preset = tab.throttlePreset,
                forceTimeoutSec = tab.forceTimeoutSec.toIntOrNull()?.coerceIn(0, 300) ?: 0
            ),
            insecureTls = tab.insecureTls,
            tls = tlsConfigOf(tab.insecureTls, tab.customCaPem, tab.clientCertPem, tab.clientKeyPem),
            followRedirects = tab.followRedirects,
            maxRedirects = tab.maxRedirects.toIntOrNull()?.coerceIn(0, 20) ?: 10
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
    private fun buildCall(tab: HttpTab, scope: VariableScope, runScripts: Boolean): BuiltCall {
        val raw = rawConfig(tab)
        val mutable = MutableRequest(
            method = raw.method.value,
            url = raw.url,
            headers = raw.headers.map { ScriptKv(it.key, it.value) }.toMutableList(),
            params = raw.queryParams.map { ScriptKv(it.key, it.value) }.toMutableList(),
            bodyType = raw.bodyType,
            bodyText = raw.body,
            formFields = raw.formFields.map { ScriptForm(it.name, it.value) }.toMutableList()
        )

        val preResult = if (runScripts && tab.preScript.isNotBlank()) {
            val sandbox = ScriptSession(scope, collectionScope(tab.activeCollectionId))
            executor.runPre(tab.preScript, mutable, sandbox)
        } else {
            ScriptRunResult.Skipped
        }

        val missing = mutableSetOf<String>()
        // 脚本视图只承载 method/url/headers/query/body，传输层配置需从原始配置补回，
        // 否则请求会退回 HttpCallConfig 的默认值，导致超时/TLS/代理/重定向设置失效
        val scriptEdited = mutable.toCallConfig(raw.proxy)
        // 脚本视图不承载文件字段的 URI，按索引把原始文件字段补回，multipart 上传才能带上文件
        val mergedFields = scriptEdited.formFields.mapIndexed { index, field ->
            val source = raw.formFields.getOrNull(index)
            if (source != null && source.isFile) source.copy(name = field.name, value = field.value) else field
        }
        val config = resolveConfig(
            scriptEdited.copy(
                formFields = mergedFields,
                graphqlVariables = raw.graphqlVariables,
                timeoutMs = raw.timeoutMs,
                connectTimeoutMs = raw.connectTimeoutMs,
                readTimeoutMs = raw.readTimeoutMs,
                fileUri = raw.fileUri,
                fileName = raw.fileName,
                fileContentType = raw.fileContentType,
                throttle = raw.throttle,
                insecureTls = raw.insecureTls,
                tls = raw.tls,
                followRedirects = raw.followRedirects,
                maxRedirects = raw.maxRedirects
            ),
            scope.all(),
            missing
        )
        return BuiltCall(config, missing.sorted(), preResult)
    }

    /** 文件夹变量作用域：只读写当前文件夹自身的变量 */
    private fun collectionScope(id: Long?): VariableScope? {
        id ?: return null
        return VariableScope(
            envLookup = { collectionStore.varsOf(id).filter { it.enabled && it.key.isNotBlank() }
                .associate { it.key to it.value } },
            envWriter = CollectionVarWriter(id)
        )
    }

    /** 导出当前请求为 [format] 格式文本；不跑脚本，先看原始内容 */
    fun exportText(format: HttpExportFormat): String =
        buildCall(selected, VariableScope(::activeEnvVars, ServiceLocator.scriptEnvWriter), runScripts = false)
            .config.exportTo(format)

    fun send() {
        // 捕获发起时的标签：发送过程中切到别的标签，结果仍写回本标签
        val tab = selected
        if (tab.sending) return
        tab.job = viewModelScope.launch {
            tab.sending = true
            tab.outcome = null
            missingVars = null
            tab.scriptConsole = emptyList()
            tab.scriptTests = emptyList()
            tab.assertionResults = emptyList()
            tab.scriptError = null
            try {
                // 前置与后置脚本共用同一个变量作用域，前置设的值后置能读到
                val vars = { envVarsFor(tab.activeCollectionId) }
                val scope = VariableScope(vars, ServiceLocator.scriptEnvWriter)
                val built = buildCall(tab, scope, runScripts = true)
                if (built.missingVars.isNotEmpty()) {
                    missingVars = built.missingVars
                    tab.scriptConsole = built.preResult.consoleLines()
                    tab.scriptTests = built.preResult.testRecords()
                    tab.scriptError = built.preResult.errorMessage()
                    return@launch
                }

                val result = engine.execute(withCookies(built.config))
                tab.outcome = result
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

                val postResult = if (tab.postScript.isNotBlank()) {
                    executor.runPost(
                        tab.postScript,
                        built.config,
                        result,
                        ScriptSession(scope, collectionScope(tab.activeCollectionId))
                    )
                } else {
                    ScriptRunResult.Skipped
                }
                publishScriptResult(tab, built.preResult, postResult)
                // 断言放在脚本之后求值：前置/后置脚本 set 的变量都能被 {{变量}} 引用；
                // 请求失败（超时/断网）没有响应体可校验，结果清空
                tab.assertionResults = if (result is HttpCallOutcome.Success) {
                    ResponseAssertions.evaluate(
                        AssertionTarget(
                            body = result.body,
                            statusCode = result.statusCode,
                            headers = result.headers.groupBy({ it.key }, { it.value })
                                .mapValues { (_, values) -> values.joinToString(", ") },
                            durationMs = result.durationMs
                        ),
                        tab.assertions,
                        scope.all()
                    )
                } else {
                    emptyList()
                }
                historyEntry?.let { entry ->
                    historyStore.add(
                        entry.copy(
                            assertions = tab.assertionResults.map { check ->
                                HistoryAssertion(
                                    type = check.assertion.type,
                                    expression = check.assertion.expression,
                                    expected = check.assertion.expected,
                                    operator = check.assertion.operator,
                                    passed = check.passed,
                                    detail = check.detail
                                )
                            }
                        )
                    )
                }
            } finally {
                tab.sending = false
            }
        }
    }

    /** 把前置与后置脚本的输出合并到标签界面上 */
    private fun publishScriptResult(tab: HttpTab, pre: ScriptRunResult, post: ScriptRunResult) {
        tab.scriptConsole = pre.consoleLines() + post.consoleLines()
        tab.scriptTests = pre.testRecords() + post.testRecords()
        tab.scriptError = pre.errorMessage() ?: post.errorMessage()
    }

    fun cancel() {
        selected.job?.cancel()
    }

    /** 历史里记录的请求体：表单类按 name=value 摊开，便于回看提交了什么 */
    private fun renderedBody(config: HttpCallConfig): String = when (config.bodyType) {
        BodyType.NONE -> ""
        BodyType.JSON, BodyType.RAW -> config.body
        BodyType.GRAPHQL -> buildGraphqlRequestBody(config.body, config.graphqlVariables)
        BodyType.FORM_URLENCODED, BodyType.FORM_DATA -> config.formFields
            .joinToString("&") { field -> "${field.name}=${field.value}" }
        BodyType.FILE -> config.fileName?.let { "[文件] $it" } ?: "[文件]"
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
