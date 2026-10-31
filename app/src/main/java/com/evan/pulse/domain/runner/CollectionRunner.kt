package com.evan.pulse.domain.runner

import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.di.StoredCookie
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.net.NetworkThrottleConfig
import com.evan.pulse.domain.net.NetworkPreset
import com.evan.pulse.domain.script.MutableRequest
import com.evan.pulse.domain.script.ScriptForm
import com.evan.pulse.domain.script.ScriptKv
import com.evan.pulse.domain.script.ScriptRunResult
import com.evan.pulse.domain.script.CollectionVarWriter
import com.evan.pulse.domain.script.ScriptSession
import com.evan.pulse.domain.script.TestRecord
import com.evan.pulse.domain.script.VariableScope
import com.evan.pulse.domain.script.consoleLines
import com.evan.pulse.domain.script.errorMessage
import com.evan.pulse.domain.script.testRecords
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.evan.pulse.data.model.HistoryEntry

/**
 * 集合运行器：按顺序执行一组 HTTP 请求。
 *
 * 所有请求共享同一个 [VariableScope]，前置脚本 pm.variables.set / pm.environment.set
 * 写出的值经活动环境持久化后，后序请求的 {{var}} 占位符能直接引用，形成完整测试链。
 * 每个请求按「前置脚本 → 解析占位符 → 发送 → 收集 Cookie → 后置脚本」执行，
 * 结果逐条记录；非 HTTP 类型的请求被跳过并标记。
 */
object CollectionRunner {

    data class Step(
        val requestId: Long,
        val name: String,
        val type: RequestType,
        val method: String,
        val url: String,
        val statusCode: Int?,
        val durationMs: Long?,
        val success: Boolean,
        val bodyPreview: String,
        val error: String?,
        val console: List<String>,
        val tests: List<TestRecord>,
        /** 本步实际尝试次数（含首次），大于 1 表示发生过重试 */
        val attempts: Int = 1,
        /** 数据驱动运行时的迭代序号（从 1 开始），非数据集运行为 1 */
        val iteration: Int = 1
    )

    data class RunResult(val steps: List<Step>) {
        val passed: Int get() = steps.count { it.success }
        val failed: Int get() = steps.size - passed
        val hasFailure: Boolean get() = failed > 0
    }

    private const val BODY_PREVIEW_CAP = 300

    /**
     * 运行器执行选项。
     *
     * [retries] 为单步最终失败前的额外重试次数（0 表示不重试）；
     * [stopOnFailure] 为 true 时某步重试耗尽仍失败即终止整条链；
     * [stepDelayMs] 为相邻两步之间的等待时长。
     */
    data class RunnerOptions(
        val retries: Int = 0,
        val stopOnFailure: Boolean = false,
        val stepDelayMs: Long = 0
    )

    /**
     * 运行一组请求。[projectId] 用于取活动环境、Cookie 与写历史；
     * [withHistory] 为 false 时不落历史（预览式运行）。
     * [options] 控制单步重试、失败即停与步间延时。
     * [dataset] 不为空时按行迭代整条链路，每行注入一组数据变量。
     */
    suspend fun run(
        requests: List<SavedRequest>,
        projectId: Long,
        withHistory: Boolean = true,
        options: RunnerOptions = RunnerOptions(),
        dataset: RunDataset? = null,
        onStep: ((Step) -> Unit)? = null
    ): RunResult {
        // 整链共享一个作用域：前置/后置脚本设的变量在后续请求里都可见
        // 当前请求所属文件夹：变量查找要带上它，链路内每条请求可能来自不同文件夹
        var activeCollectionId: Long? = null
        val scope = VariableScope(
            envLookup = {
                LinkedHashMap(activeEnvVars()).apply {
                    putAll(ServiceLocator.collectionStore.resolvedVars(activeCollectionId))
                }
            },
            envWriter = ServiceLocator.scriptEnvWriter
        )
        val maxRetries = options.retries.coerceAtLeast(0)
        val iterations = dataset?.rows?.takeIf { it.isNotEmpty() } ?: listOf(emptyMap())
        val steps = ArrayList<Step>(requests.size * iterations.size)
        for ((iterationIndex, row) in iterations.withIndex()) {
            // 每轮注入本行数据，脚本/占位符据此解析；下一轮覆盖为下一行
            scope.dataVars = row
            for ((index, request) in requests.withIndex()) {
                activeCollectionId = request.collectionId
                var step = runOne(request, projectId, scope, withHistory)
                // 失败重试：只对最终失败前重跑，成功即停；失败尝试不落历史
                var attempt = 0
                while (!step.success && attempt < maxRetries) {
                    attempt++
                    step = runOne(request, projectId, scope, withHistory)
                }
                var finalStep = if (attempt > 0) step.copy(attempts = attempt + 1) else step
                if (iterations.size > 1) finalStep = finalStep.copy(iteration = iterationIndex + 1)
                steps += finalStep
                // 每步完成就回调，界面可以边跑边显示，不必等整条链结束
                onStep?.invoke(finalStep)
                if (!finalStep.success && options.stopOnFailure) return RunResult(steps)
                if (options.stepDelayMs > 0 && index < requests.lastIndex) delay(options.stepDelayMs)
            }
        }
        return RunResult(steps)
    }

    private suspend fun runOne(
        request: SavedRequest,
        projectId: Long,
        scope: VariableScope,
        withHistory: Boolean
    ): Step {
        if (request.type != RequestType.HTTP) {
            return Step(
                requestId = request.id,
                name = request.name,
                type = request.type,
                method = "",
                url = "",
                statusCode = null,
                durationMs = null,
                success = false,
                bodyPreview = "",
                error = "仅支持 HTTP 请求",
                console = emptyList(),
                tests = emptyList()
            )
        }

        val snapshot = runCatching {
            snapshotJson.decodeFromString(HttpSnapshot.serializer(), request.data)
        }.getOrNull()
        if (snapshot == null) {
            return failed(request, "请求配置已损坏，无法解析")
        }

        val config = buildConfig(snapshot, projectId, scope, request.collectionId)
            ?: return failed(request, "存在未解析的变量（{{var}}），已跳过")
        val result = try {
            ServiceLocator.httpEngine.execute(withCookies(config, projectId))
        } catch (e: Exception) {
            HttpCallOutcome.Failure(com.evan.pulse.domain.model.HttpErrorType.UNKNOWN, e.message ?: "发送失败")
        }
        if (result is HttpCallOutcome.Success) {
            storeResponseCookies(config.url, result.headers, projectId)
            if (withHistory) {
                recordHistory(request, snapshot, config, result, projectId)
            }
        }

        val collectionScope = collectionScopeOf(request.collectionId)
        val post = if (snapshot.postScript.isNotBlank()) {
            ServiceLocator.scriptExecutor.runPost(
                snapshot.postScript,
                config,
                result,
                ScriptSession(scope, collectionScope)
            )
        } else {
            ScriptRunResult.Skipped
        }

        return when (result) {
            is HttpCallOutcome.Success -> Step(
                requestId = request.id,
                name = request.name,
                type = request.type,
                method = config.method.value,
                url = urlWithQuery(config),
                statusCode = result.statusCode,
                durationMs = result.durationMs,
                success = result.statusCode in 200..399,
                bodyPreview = preview(result.body),
                error = null,
                console = post.consoleLines(),
                tests = post.testRecords()
            )
            is HttpCallOutcome.Failure -> Step(
                requestId = request.id,
                name = request.name,
                type = request.type,
                method = config.method.value,
                url = urlWithQuery(config),
                statusCode = null,
                durationMs = null,
                success = false,
                bodyPreview = "",
                error = result.message,
                console = post.consoleLines(),
                tests = post.testRecords()
            )
        }
    }

    /** 从快照重建可发送配置：鉴权头合并 + 前置脚本 + 占位符解析 */
    private fun buildConfig(
        snapshot: HttpSnapshot,
        projectId: Long,
        scope: VariableScope,
        collectionId: Long
    ): HttpCallConfig? {
        val method = HttpMethod.parse(snapshot.method)
        val appliedAuth = ServiceLocator.authApplier.apply(
            AuthConfig(
                type = com.evan.pulse.domain.model.AuthType.entries
                    .firstOrNull { it.name == snapshot.authType } ?: com.evan.pulse.domain.model.AuthType.NONE,
                bearerToken = snapshot.bearerToken,
                basicUsername = snapshot.basicUsername,
                basicPassword = snapshot.basicPassword,
                apiKeyName = snapshot.apiKeyName,
                apiKeyValue = snapshot.apiKeyValue
            )
        )
        val base = HttpCallConfig(
            method = method,
            url = snapshot.url,
            headers = snapshot.headers.filter { it.enabled && it.key.isNotBlank() }
                .map { KeyValue(it.key, it.value) } + appliedAuth,
            queryParams = snapshot.params.filter { it.enabled && it.key.isNotBlank() }
                .map { KeyValue(it.key, it.value) },
            bodyType = BodyType.entries.firstOrNull { it.name == snapshot.bodyType } ?: BodyType.NONE,
            body = snapshot.bodyText,
            graphqlVariables = snapshot.graphqlVariables,
            formFields = snapshot.formFields.filter { it.name.isNotBlank() && !it.isFile }
                .map { FormField(name = it.name, value = it.value) },
            timeoutMs = (snapshot.timeoutSec.toLongOrNull()?.coerceAtLeast(1) ?: 30L) * 1000L,
            proxy = if (snapshot.useProxy) ServiceLocator.settingsStore.settings.value.toProxyConfig()
                else ProxyConfig(),
            throttle = NetworkThrottleConfig(
                preset = NetworkPreset.entries.firstOrNull { it.name == snapshot.throttlePreset }
                    ?: NetworkPreset.NONE,
                forceTimeoutSec = snapshot.forceTimeoutSec.toIntOrNull()?.coerceIn(0, 300) ?: 0
            ),
            insecureTls = snapshot.insecureTls,
            followRedirects = snapshot.followRedirects,
            maxRedirects = snapshot.maxRedirects
        )

        val mutable = MutableRequest(
            method = base.method.value,
            url = base.url,
            headers = base.headers.map { ScriptKv(it.key, it.value) }.toMutableList(),
            params = base.queryParams.map { ScriptKv(it.key, it.value) }.toMutableList(),
            bodyType = base.bodyType,
            bodyText = base.body,
            formFields = base.formFields.map { ScriptForm(it.name, it.value) }.toMutableList()
        )
        if (snapshot.preScript.isNotBlank()) {
            ServiceLocator.scriptExecutor.runPre(
                snapshot.preScript,
                mutable,
                ScriptSession(scope, collectionScopeOf(collectionId))
            )
        }

        val scriptEdited = mutable.toCallConfig(base.proxy)
        // 脚本视图不承载文件字段的 URI，按索引把原始文件字段补回，multipart 上传才能带上文件
        val mergedFields = scriptEdited.formFields.mapIndexed { index, field ->
            val source = base.formFields.getOrNull(index)
            if (source != null && source.isFile) source.copy(name = field.name, value = field.value) else field
        }
        val config = scriptEdited.copy(
            formFields = mergedFields,
            timeoutMs = base.timeoutMs,
            connectTimeoutMs = base.connectTimeoutMs,
            readTimeoutMs = base.readTimeoutMs,
            fileUri = base.fileUri,
            fileName = base.fileName,
            fileContentType = base.fileContentType,
            throttle = base.throttle,
            insecureTls = base.insecureTls,
            tls = base.tls,
            followRedirects = base.followRedirects,
            maxRedirects = base.maxRedirects
        )
        val missing = mutableSetOf<String>()
        val resolved = resolveConfig(config, scope.all(), missing)
        return if (missing.isNotEmpty()) null else resolved
    }

    private fun resolveConfig(
        config: HttpCallConfig,
        vars: Map<String, String>,
        missing: MutableSet<String>
    ): HttpCallConfig {
        fun res(text: String): String = when (val r = ServiceLocator.placeholderResolver.resolve(text, vars)) {
            is com.evan.pulse.domain.PlaceholderResolver.ResolveResult.Ok -> r.resolved
            is com.evan.pulse.domain.PlaceholderResolver.ResolveResult.Missing -> {
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
            graphqlVariables = if (config.bodyType == BodyType.GRAPHQL) res(config.graphqlVariables)
                else config.graphqlVariables,
            formFields = config.formFields.map { it.copy(value = res(it.value)) }
        )
    }

    /** 自动附带 Cookie 库中命中 host 的 Cookie；用户手动设置的 Cookie 头优先 */
    private fun withCookies(config: HttpCallConfig, projectId: Long): HttpCallConfig {
        if (config.headers.any { it.key.equals("Cookie", ignoreCase = true) }) return config
        val host = config.url.toHttpUrlOrNull()?.host ?: return config
        val jar = ServiceLocator.cookieStore.forHost(projectId, host)
        if (jar.isEmpty()) return config
        val value = jar.joinToString("; ") { "${it.name}=${it.value}" }
        return config.copy(headers = config.headers + KeyValue("Cookie", value))
    }

    /** 收集响应 Set-Cookie 存入项目 Cookie 库 */
    private fun storeResponseCookies(url: String, headers: List<KeyValue>, projectId: Long) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        headers.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.forEach { kv ->
            Cookie.parse(httpUrl, kv.value)?.let { cookie ->
                if (cookie.name.isNotBlank()) {
                    ServiceLocator.cookieStore.upsert(
                        projectId,
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

    /** 落历史：与 HTTP 页发送行为一致，正文按同一上限裁剪 */
    private fun recordHistory(
        request: SavedRequest,
        snapshot: HttpSnapshot,
        config: HttpCallConfig,
        result: HttpCallOutcome.Success,
        projectId: Long
    ) {
        val requestBody = clip(renderedBody(snapshot, config))
        val responseBody = clip(result.body)
        ServiceLocator.historyStore.add(
            HistoryEntry(
                id = 0,
                projectId = projectId,
                method = config.method.value,
                url = urlWithQuery(config),
                statusCode = result.statusCode,
                statusText = result.statusText,
                durationMs = result.durationMs,
                success = result.statusCode in 200..399,
                createdAt = System.currentTimeMillis(),
                responseSizeBytes = result.responseSizeBytes,
                requestHeaders = config.headers.map { "${it.key}: ${it.value}" },
                requestBody = requestBody.text,
                requestBodyTruncated = requestBody.truncated,
                responseHeaders = result.headers.map { "${it.key}: ${it.value}" },
                responseBody = responseBody.text,
                responseBodyTruncated = responseBody.truncated
            )
        )
    }

    private fun renderedBody(snapshot: HttpSnapshot, config: HttpCallConfig): String = when (config.bodyType) {
        BodyType.NONE -> ""
        BodyType.JSON, BodyType.RAW -> config.body
        BodyType.GRAPHQL -> buildGraphqlBody(snapshot)
        BodyType.FORM_URLENCODED, BodyType.FORM_DATA -> config.formFields
            .joinToString("&") { "${it.name}=${it.value}" }
        BodyType.FILE -> config.fileName?.let { "[文件] $it" } ?: "[文件]"
    }

    private fun buildGraphqlBody(snapshot: HttpSnapshot): String {
        val query = snapshot.bodyText.trim()
        if (query.isEmpty()) return ""
        val variables = snapshot.graphqlVariables.trim()
        return if (variables.isEmpty()) {
            """{"query":${query.let { q -> if (q.startsWith('{')) q else "\"$q\"" }}}"""
        } else {
            """{"query":${query.let { q -> if (q.startsWith('{')) q else "\"$q\"" }},"variables":$variables}"""
        }
    }

    private fun urlWithQuery(config: HttpCallConfig): String {
        val qs = config.queryParams.filter { it.enabled && it.key.isNotBlank() }
        if (qs.isEmpty()) return config.url
        val separator = if ('?' in config.url) '&' else '?'
        return config.url + separator + qs.joinToString("&") { "${it.key}=${it.value}" }
    }

    /** 文件夹变量作用域：只读写该文件夹自身的变量 */
    private fun collectionScopeOf(collectionId: Long): VariableScope = VariableScope(
        envLookup = {
            ServiceLocator.collectionStore.varsOf(collectionId)
                .filter { it.enabled && it.key.isNotBlank() }
                .associate { it.key to it.value }
        },
        envWriter = CollectionVarWriter(collectionId)
    )

    private fun activeEnvVars(): Map<String, String> {
        val pid = ServiceLocator.projectStore.currentProjectId.value
        val env = ServiceLocator.envStore.environments.value.firstOrNull { it.projectId == pid && it.isActive }
        return env?.variables?.toMap() ?: emptyMap()
    }

    private fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= BODY_PREVIEW_CAP) trimmed else trimmed.substring(0, BODY_PREVIEW_CAP) + "…"
    }

    private fun clip(text: String): ClippedBody =
        if (text.length <= HISTORY_BODY_CHAR_CAP) ClippedBody(text, false)
        else ClippedBody(text.substring(0, HISTORY_BODY_CHAR_CAP), true)

    private fun failed(request: SavedRequest, reason: String): Step = Step(
        requestId = request.id,
        name = request.name,
        type = request.type,
        method = "",
        url = "",
        statusCode = null,
        durationMs = null,
        success = false,
        bodyPreview = "",
        error = reason,
        console = emptyList(),
        tests = emptyList()
    )

    private const val HISTORY_BODY_CHAR_CAP = 16 * 1024

    private data class ClippedBody(val text: String, val truncated: Boolean)
}