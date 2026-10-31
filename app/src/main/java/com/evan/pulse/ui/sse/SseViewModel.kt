package com.evan.pulse.ui.sse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.ui.common.tlsConfigOf
import com.evan.pulse.R
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SseHistoryEntry
import com.evan.pulse.data.model.SseSnapshot
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.sse.SseStreamStats
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * SSE 页面状态。SSE 为单向流，故只提供连接与断开，没有发送入口。
 * 支持与 HTTP 一致的请求方法/参数/请求体/鉴权配置，并在接收帧时按
 * JSONPath 表达式提取字段做拼接预览（如 OpenAI 的 delta.content）。
 *
 * 编辑表单只有一份；连接以标签页形式并行存在，「连接」按当前表单配置
 * 打开所选标签页，因此可以对同一或不同端点同时保持多条流。
 */
class SseViewModel : ViewModel() {

    private val resolver = ServiceLocator.placeholderResolver
    private val envStore = ServiceLocator.envStore
    private val settingsStore = ServiceLocator.settingsStore
    private val collectionStore = ServiceLocator.collectionStore
    private val historyStore = ServiceLocator.sseHistoryStore

    var method by mutableStateOf(HttpMethod.POST)
    var url by mutableStateOf("{{sseUrl}}/v1/chat/completions")
    var params by mutableStateOf(listOf(KeyValue()))
    var headers by mutableStateOf(listOf(KeyValue()))
    var bodyType by mutableStateOf(BodyType.JSON)
    var bodyText by mutableStateOf("")
    var eventFilter by mutableStateOf("")
    var autoReconnect by mutableStateOf(false)
    var reconnectDelaySec by mutableStateOf("3")
    var exponentialBackoff by mutableStateOf(true)
    var maxBackoffSec by mutableStateOf("30")
    var jitter by mutableStateOf(true)
    /** 0 表示无限重连 */
    var maxRetries by mutableStateOf("0")
    /** 空闲超时（秒），0 表示不检测 */
    var idleTimeoutSec by mutableStateOf("0")

    /** 鉴权：与 HTTP 页相同的 Bearer / Basic / API Key，连接时合并到请求头 */
    var auth by mutableStateOf(AuthConfig())

    /** 手动断点：首次连接时作为 Last-Event-ID 发送 */
    var lastEventIdInput by mutableStateOf("")

    /** 连接建立超时（秒）：只约束握手与响应头到达，流建立后不受影响 */
    var timeoutSec by mutableStateOf("15")

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    /** 跳过 TLS 证书校验；仅用于调试自签名或内网 HTTPS 服务 */
    var insecureTls by mutableStateOf(false)
    var customCaPem by mutableStateOf("")
    var clientCertPem by mutableStateOf("")
    var clientKeyPem by mutableStateOf("")

    var activeTab by mutableIntStateOf(0)

    /** 当前请求所属文件夹，用于合并文件夹变量 */
    private var activeCollectionId: Long? = null

    /** 拼接预览：表达式 + 累计文本；previewTab 0=原始 1=Markdown */
    var previewPath by mutableStateOf("$.choices[0].delta.content")
    var previewTab by mutableIntStateOf(0)

    var missingVars by mutableStateOf<List<String>?>(null)

    // ---- 并行连接 ----

    private var nextConnectionId = 1

    /** 所有并行连接（标签页），至少保留一个 */
    val connections = mutableStateListOf(newConnection())

    var selectedIndex by mutableIntStateOf(0)
        private set

    val selected: SseConnection get() = connections[selectedIndex.coerceIn(0, connections.lastIndex)]

    val canAddConnection: Boolean get() = connections.size < MAX_CONNECTIONS

    // 以下成员都指向所选标签页，页面其余部分无需感知多连接
    val state: StateFlow<SseConnectionState> get() = selected.state
    val lines: List<UiEventLine> get() = selected.lines
    val stats: SseStreamStats get() = selected.stats
    val previewText: String get() = selected.previewText
    val lastError: String? get() = selected.lastError

    /** 已结束会话的历史，最新的在前 */
    val history: StateFlow<List<SseHistoryEntry>> = historyStore.entries

    init {
        viewModelScope.launch {
            collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.SSE) {
                    restore(req.data)
                    loadedRequest.track(req.id)
                    activeCollectionId = req.collectionId
                    collectionStore.consumePending(req)
                }
            }
        }
    }

    private fun newConnection() = SseConnection(nextConnectionId++, viewModelScope, ServiceLocator.sseHistoryStore)

    fun selectConnection(index: Int) {
        if (index in connections.indices) selectedIndex = index
    }

    fun addConnection() {
        if (!canAddConnection) return
        connections.add(newConnection())
        selectedIndex = connections.lastIndex
    }

    /** 关闭标签页会断开该连接；关掉最后一个时换成一个空白连接 */
    fun closeConnection(index: Int) {
        if (index !in connections.indices) return
        connections[index].dispose()
        connections.removeAt(index)
        if (connections.isEmpty()) connections.add(newConnection())
        selectedIndex = when {
            selectedIndex > index -> selectedIndex - 1
            else -> selectedIndex.coerceAtMost(connections.lastIndex)
        }
    }

    /** 与集合条目的绑定：从集合加载或保存过一次后，再保存即原地覆盖 */
    private val loadedRequest = LoadedRequestTracker(ServiceLocator.collectionStore)

    /** 已绑定集合条目时原地覆盖；返回 false 表示需要弹出保存对话框 */
    fun saveOverLoaded(): Boolean = loadedRequest.saveOver { snapshot() }

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        loadedRequest.track(id)
        activeCollectionId = collectionStore.requests.value
            .firstOrNull { it.id == id }?.collectionId
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        SseSnapshot.serializer(),
        SseSnapshot(
            url = url,
            method = method.value,
            params = params.map { KeyValueEntry(it.key, it.value, it.enabled) },
            headers = headers.map { KeyValueEntry(it.key, it.value, it.enabled) },
            bodyType = bodyType.name,
            bodyText = bodyText,
            eventFilter = eventFilter,
            autoReconnect = autoReconnect,
            reconnectDelaySec = reconnectDelaySec.toIntOrNull() ?: 3,
            exponentialBackoff = exponentialBackoff,
            maxBackoffSec = maxBackoffSec.toIntOrNull() ?: 30,
            jitter = jitter,
            maxRetries = maxRetries.toIntOrNull() ?: 0,
            idleTimeoutSec = idleTimeoutSec.toIntOrNull() ?: 0,
            lastEventId = lastEventIdInput.trim(),
            previewPath = previewPath,
            timeoutSec = timeoutSec.toIntOrNull() ?: 15,
            useProxy = useProxy,
            insecureTls = insecureTls,
            customCaPem = customCaPem,
            clientCertPem = clientCertPem,
            clientKeyPem = clientKeyPem,
            authType = auth.type.name,
            bearerToken = auth.bearerToken,
            basicUsername = auth.basicUsername,
            basicPassword = auth.basicPassword,
            apiKeyName = auth.apiKeyName,
            apiKeyValue = auth.apiKeyValue
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(SseSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        url = snap.url
        method = HttpMethod.parse(snap.method)
        params = snap.params.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        bodyType = BodyType.entries.firstOrNull { it.name == snap.bodyType } ?: BodyType.JSON
        bodyText = snap.bodyText
        eventFilter = snap.eventFilter
        autoReconnect = snap.autoReconnect
        reconnectDelaySec = snap.reconnectDelaySec.toString()
        exponentialBackoff = snap.exponentialBackoff
        maxBackoffSec = snap.maxBackoffSec.toString()
        jitter = snap.jitter
        maxRetries = snap.maxRetries.toString()
        idleTimeoutSec = snap.idleTimeoutSec.toString()
        lastEventIdInput = snap.lastEventId
        timeoutSec = snap.timeoutSec.toString()
        useProxy = snap.useProxy
        insecureTls = snap.insecureTls
        customCaPem = snap.customCaPem
        clientCertPem = snap.clientCertPem
        clientKeyPem = snap.clientKeyPem
        previewPath = snap.previewPath
        auth = AuthConfig(
            type = AuthType.entries.firstOrNull { it.name == snap.authType } ?: AuthType.NONE,
            bearerToken = snap.bearerToken,
            basicUsername = snap.basicUsername,
            basicPassword = snap.basicPassword,
            apiKeyName = snap.apiKeyName,
            apiKeyValue = snap.apiKeyValue
        )
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

    /** 按当前表单配置打开所选标签页的连接 */
    fun connect() {
        if (url.isBlank()) return
        val connection = selected
        val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
        val vars = ServiceLocator.varsForCollection(activeCollectionId)

        // 先解析完所有字段再检查缺失变量：缺任何一个都不发起连接
        val missing = sortedSetOf<String>()
        fun res(text: String): String = when (val r = resolver.resolve(text, vars)) {
            is PlaceholderResolver.ResolveResult.Ok -> r.resolved
            is PlaceholderResolver.ResolveResult.Missing -> {
                missing += r.keys
                text
            }
        }

        val resolvedUrl = res(url)
        val resolvedHeaders = headers
            .filter { it.enabled && it.key.isNotBlank() }
            .map { it.copy(value = res(it.value)) }
        val resolvedParams = params
            .filter { it.enabled && it.key.isNotBlank() }
            .map { it.copy(value = res(it.value)) }
        val resolvedBody = res(bodyText)
        val resolvedLastEventId = res(lastEventIdInput.trim())
        // 鉴权字段先解析变量再生成请求头，Basic 的 Base64 才会编码真实凭据
        val authHeaders = ServiceLocator.authApplier.apply(
            auth.copy(
                bearerToken = res(auth.bearerToken),
                basicUsername = res(auth.basicUsername),
                basicPassword = res(auth.basicPassword),
                apiKeyName = res(auth.apiKeyName),
                apiKeyValue = res(auth.apiKeyValue)
            )
        )

        if (missing.isNotEmpty()) {
            missingVars = missing.toList()
            return
        }

        connection.note(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }))
        connection.connect(
            SseConfig(
                url = resolvedUrl,
                method = method,
                // 与 HTTP 页一致：鉴权头追加在用户请求头之后
                headers = resolvedHeaders + authHeaders,
                queryParams = resolvedParams,
                bodyType = bodyType,
                body = resolvedBody,
                timeoutMs = (timeoutSec.toIntOrNull()?.coerceAtLeast(1) ?: 15) * 1000L,
                proxy = proxy,
                eventFilter = eventFilter.trim(),
                lastEventId = resolvedLastEventId,
                autoReconnect = autoReconnect,
                reconnectDelaySec = reconnectDelaySec.toIntOrNull() ?: 3,
                exponentialBackoff = exponentialBackoff,
                maxBackoffSec = maxBackoffSec.toIntOrNull() ?: 30,
                jitter = jitter,
                maxRetries = maxRetries.toIntOrNull() ?: 0,
                idleTimeoutSec = idleTimeoutSec.toIntOrNull() ?: 0,
                insecureTls = insecureTls,
                tls = tlsConfigOf(insecureTls, customCaPem, clientCertPem, clientKeyPem)
            ),
            method = method.value,
            previewPath = { previewPath }
        )
    }

    fun disconnect() = selected.disconnect()

    fun clearLog() = selected.clearLog()

    /** 把当前断点写回输入框，便于下次连接从同一位置续传 */
    fun adoptLastEventId() {
        selected.adoptableLastEventId()?.let { lastEventIdInput = it }
    }

    /** 只清拼接预览，不清事件流 */
    fun clearPreview() {
        selected.previewText = ""
    }

    fun deleteHistory(id: Long) = historyStore.delete(id)

    fun clearHistory() = historyStore.clear()

    fun dismissMissing() {
        missingVars = null
    }

    /** 供输入框下方提示：把文本里的 {{var}} 逐项解析成“名字 → 解析值”，缺失的 value 为 null */
    fun placeholderHints(text: String): List<Pair<String, String?>> {
        if (text.isBlank()) return emptyList()
        val pid = ServiceLocator.projectStore.currentProjectId.value
        val vars = envStore.environments.value
            .firstOrNull { it.projectId == pid && it.isActive }
            ?.variables?.toMap()
            ?: emptyMap()
        val seen = LinkedHashMap<String, String?>()
        for (m in PlaceholderResolver.PLACEHOLDER_REGEX.findAll(text)) {
            val key = m.groupValues[1]
            seen.putIfAbsent(key, vars[key])
        }
        return seen.map { (k, v) -> k to v }
    }

    override fun onCleared() {
        connections.forEach { it.dispose() }
    }

    private companion object {
        /** 同时保持的 SSE 连接上限：每条连接占一个长连接与收集协程 */
        const val MAX_CONNECTIONS = 5
    }
}
