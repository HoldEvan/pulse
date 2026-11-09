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

    /** 编辑区的子 Tab（原始/预览），与连接标签无关，全局一份 */
    var activeTab by mutableIntStateOf(0)
    var previewTab by mutableIntStateOf(0)

    // ---- 并行连接：每个标签自带连接配置与运行状态 ----

    private var nextConnectionId = 1

    /** 所有并行连接（标签页），至少保留一个 */
    val connections = mutableStateListOf(newConnection())

    var selectedIndex by mutableIntStateOf(0)
        private set

    val selected: SseConnection get() = connections[selectedIndex.coerceIn(0, connections.lastIndex)]

    val canAddConnection: Boolean get() = connections.size < MAX_CONNECTIONS

    // 以下成员都指向所选标签页，页面其余部分无需感知多连接
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

    var eventFilter: String
        get() = selected.eventFilter
        set(value) { selected.eventFilter = value }

    var autoReconnect: Boolean
        get() = selected.autoReconnect
        set(value) { selected.autoReconnect = value }

    var reconnectDelaySec: String
        get() = selected.reconnectDelaySec
        set(value) { selected.reconnectDelaySec = value }

    var exponentialBackoff: Boolean
        get() = selected.exponentialBackoff
        set(value) { selected.exponentialBackoff = value }

    var maxBackoffSec: String
        get() = selected.maxBackoffSec
        set(value) { selected.maxBackoffSec = value }

    var jitter: Boolean
        get() = selected.jitter
        set(value) { selected.jitter = value }

    var maxRetries: String
        get() = selected.maxRetries
        set(value) { selected.maxRetries = value }

    var idleTimeoutSec: String
        get() = selected.idleTimeoutSec
        set(value) { selected.idleTimeoutSec = value }

    var auth: AuthConfig
        get() = selected.auth
        set(value) { selected.auth = value }

    var lastEventIdInput: String
        get() = selected.lastEventIdInput
        set(value) { selected.lastEventIdInput = value }

    var timeoutSec: String
        get() = selected.timeoutSec
        set(value) { selected.timeoutSec = value }

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

    var previewPath: String
        get() = selected.previewPath
        set(value) { selected.previewPath = value }

    var missingVars: List<String>?
        get() = selected.missingVars
        set(value) { selected.missingVars = value }

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
                    // 当前标签还是空白就直接填进去，否则开一个新标签，避免覆盖手上的连接
                    val target = if (selected.isIdle() || !canAddConnection) {
                        selected
                    } else {
                        newConnection().also { connections.add(it); selectedIndex = connections.lastIndex }
                    }
                    restoreInto(target, req.data)
                    target.loadedRequest.track(req.id)
                    target.activeCollectionId = req.collectionId
                    collectionStore.consumePending(req)
                }
            }
        }
    }

    private fun newConnection() = SseConnection(
        id = nextConnectionId++,
        scope = viewModelScope,
        history = ServiceLocator.sseHistoryStore,
        loadedRequest = LoadedRequestTracker(ServiceLocator.collectionStore)
    )

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

    /** 已绑定集合条目时原地覆盖；返回 false 表示需要弹出保存对话框 */
    fun saveOverLoaded(): Boolean = selected.loadedRequest.saveOver { snapshot() }

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        selected.loadedRequest.track(id)
        selected.activeCollectionId = collectionStore.requests.value
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

    /** 把集合条目的快照写进指定标签；只动目标标签，不依赖当前选中项 */
    private fun restoreInto(tab: SseConnection, data: String) {
        val snap = try {
            snapshotJson.decodeFromString(SseSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        tab.url = snap.url
        tab.method = HttpMethod.parse(snap.method)
        tab.params = snap.params.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        tab.headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        tab.bodyType = BodyType.entries.firstOrNull { it.name == snap.bodyType } ?: BodyType.JSON
        tab.bodyText = snap.bodyText
        tab.eventFilter = snap.eventFilter
        tab.autoReconnect = snap.autoReconnect
        tab.reconnectDelaySec = snap.reconnectDelaySec.toString()
        tab.exponentialBackoff = snap.exponentialBackoff
        tab.maxBackoffSec = snap.maxBackoffSec.toString()
        tab.jitter = snap.jitter
        tab.maxRetries = snap.maxRetries.toString()
        tab.idleTimeoutSec = snap.idleTimeoutSec.toString()
        tab.lastEventIdInput = snap.lastEventId
        tab.timeoutSec = snap.timeoutSec.toString()
        tab.useProxy = snap.useProxy
        tab.insecureTls = snap.insecureTls
        tab.customCaPem = snap.customCaPem
        tab.clientCertPem = snap.clientCertPem
        tab.clientKeyPem = snap.clientKeyPem
        tab.previewPath = snap.previewPath
        tab.auth = AuthConfig(
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
        val vars = ServiceLocator.varsForCollection(selected.activeCollectionId)

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
            )
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
