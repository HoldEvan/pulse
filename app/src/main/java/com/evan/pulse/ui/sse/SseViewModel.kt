package com.evan.pulse.ui.sse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.R
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SseSnapshot
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.JsonPath
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.extractString
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseEvent
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch

/**
 * SSE 页面状态。SSE 为单向流，故只提供连接与断开，没有发送入口。
 * 支持与 HTTP 一致的请求方法/参数/请求体配置，并在接收帧时按
 * JSONPath 表达式提取字段做拼接预览（如 OpenAI 的 delta.content）。
 */
class SseViewModel : ViewModel() {

    private val engine = ServiceLocator.sseEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val envStore = ServiceLocator.envStore
    private val settingsStore = ServiceLocator.settingsStore
    private val collectionStore = ServiceLocator.collectionStore

    var method by mutableStateOf(HttpMethod.POST)
    var url by mutableStateOf("{{sseUrl}}/v1/chat/completions")
    var params by mutableStateOf(listOf(KeyValue()))
    var headers by mutableStateOf(listOf(KeyValue()))
    var bodyType by mutableStateOf(BodyType.JSON)
    var bodyText by mutableStateOf("")
    var eventFilter by mutableStateOf("")
    var autoReconnect by mutableStateOf(false)
    var reconnectDelaySec by mutableStateOf("3")

    /** 连接建立超时（秒）：只约束握手与响应头到达，流建立后不受影响 */
    var timeoutSec by mutableStateOf("15")

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    /** 跳过 TLS 证书校验；仅用于调试自签名或内网 HTTPS 服务 */
    var insecureTls by mutableStateOf(false)

    var activeTab by mutableIntStateOf(0)

    /** 当前请求所属文件夹，用于合并文件夹变量 */
    private var activeCollectionId: Long? = null

    /** 拼接预览：表达式 + 累计文本；previewTab 0=原始 1=Markdown */
    var previewPath by mutableStateOf("$.choices[0].delta.content")
    var previewText by mutableStateOf("")
    var previewTab by mutableIntStateOf(0)

    val state = engine.state
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is SseEvent.Connected -> add(L10n.t(R.string.state_opened), true)
                    is SseEvent.FrameReceived -> {
                        appendPreview(event.data)
                        add(frameText(event), false)
                    }

                    is SseEvent.Comment -> add(event.line, true)
                    is SseEvent.Reconnecting -> add(
                        L10n.t(R.string.sse_log_reconnecting, event.delaySec),
                        true
                    )
                    is SseEvent.Closed -> add(
                        L10n.t(R.string.sse_log_closed, event.reason ?: L10n.t(R.string.ws_unknown_reason)),
                        true
                    )
                    is SseEvent.Failed -> {
                        lastError = event.cause
                        add(L10n.t(R.string.sse_log_failed, event.cause), true)
                    }
                }
            }
        }
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
            previewPath = previewPath,
            timeoutSec = timeoutSec.toIntOrNull() ?: 15,
            useProxy = useProxy,
            insecureTls = insecureTls
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
        timeoutSec = snap.timeoutSec.toString()
        useProxy = snap.useProxy
        insecureTls = snap.insecureTls
        previewPath = snap.previewPath
    }

    /** SSE 帧还原为协议原始文本：id / event / data 三行 */
    private fun frameText(event: SseEvent.FrameReceived): String = buildString {
        if (event.id != null && event.id.isNotBlank()) append("id: ").append(event.id).append('\n')
        if (event.event.isNotBlank()) append("event: ").append(event.event).append('\n')
        append("data: ").append(event.data)
    }

    /** 帧的 data 若是 JSON，按 previewPath 提取并拼接；非 JSON 或表达式为空时跳过 */
    private fun appendPreview(data: String) {
        val path = previewPath.trim()
        if (path.isEmpty()) return
        val hits = JsonPath.eval(data, path) ?: return
        if (hits.isEmpty()) return
        previewText += hits.joinToString("") { it.extractString() }
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
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

    fun connect() {
        if (url.isBlank()) return
        viewModelScope.launch {
            lastError = null
            val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
            add(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }), true)
            val vars = ServiceLocator.varsForCollection(activeCollectionId)

            val missing = mutableSetOf<String>()
            fun res(text: String): String = when (val r = resolver.resolve(text, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> r.resolved
                is PlaceholderResolver.ResolveResult.Missing -> {
                    missing += r.keys
                    text
                }
            }

            if (missing.isNotEmpty()) {
                missingVars = missing.sorted()
                return@launch
            }

            previewText = ""
            engine.connect(
                SseConfig(
                    url = res(url),
                    method = method,
                    headers = headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .map { it.copy(value = res(it.value)) },
                    queryParams = params
                        .filter { it.enabled && it.key.isNotBlank() }
                        .map { it.copy(value = res(it.value)) },
                    bodyType = bodyType,
                    body = res(bodyText),
                    timeoutMs = (timeoutSec.toIntOrNull()?.coerceAtLeast(1) ?: 15) * 1000L,
                    proxy = proxy,
                    eventFilter = eventFilter.trim(),
                    autoReconnect = autoReconnect,
                    reconnectDelaySec = reconnectDelaySec.toIntOrNull() ?: 3,
                    insecureTls = insecureTls
                )
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { engine.close() }
    }

    fun clearLog() {
        lines.clear()
    }

    /** 只清拼接预览，不清事件流 */
    fun clearPreview() {
        previewText = ""
    }

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
}
