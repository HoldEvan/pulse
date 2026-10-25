package com.evan.pulse.ui.ws

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.WS_TEMPLATE_LIMIT
import com.evan.pulse.data.model.WsMessageTemplate
import com.evan.pulse.data.model.WsReconnectSettings
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.net.BinaryEncoding
import com.evan.pulse.domain.net.BinaryPayload
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import com.evan.pulse.domain.model.WsConfig
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class WsViewModel : ViewModel() {

    private val engine = ServiceLocator.wsEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val settingsStore = ServiceLocator.settingsStore
    private val sessions = ServiceLocator.protocolSessionStore
    private var activeConfig: WsConfig? = null
    private var activeEndpoint = ""
    private var allowReconnect = false
    private var connectJob: Job? = null
    private val retries = WsReconnectController(
        scope = viewModelScope,
        attempt = {
            val config = activeConfig ?: return@WsReconnectController false
            try {
                engine.connect(config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
                add(L10n.t(R.string.ws_log_failed, lastError.orEmpty()), true)
            }
            engine.state.value == WsConnectionState.CONNECTED
        },
        onScheduled = { seconds -> add(L10n.t(R.string.ws_log_reconnecting, seconds), true) }
    )

    var url by mutableStateOf("{{wsUrl}}/echo")
    var input by mutableStateOf("")
    var pingIntervalSec by mutableStateOf("0")
    var pingPayload by mutableStateOf("ping")

    /** 握手请求头；列表始终以一个空行结尾，由 KeyValueEditor 维护 */
    var headers by mutableStateOf(listOf(KeyValue()))

    /** Sec-WebSocket-Protocol 子协议，逗号分隔 */
    var subprotocols by mutableStateOf("")
    var autoReconnect by mutableStateOf(sessions.wsReconnectSettings.value.enabled)
        private set
    var reconnectBaseDelaySec by mutableStateOf(sessions.wsReconnectSettings.value.baseDelaySec.toString())
        private set
    var reconnectMaxDelaySec by mutableStateOf(sessions.wsReconnectSettings.value.maxDelaySec.toString())
        private set
    val reconnectDelaysValid: Boolean
        get() {
            val base = reconnectBaseDelaySec.toIntOrNull() ?: return false
            val max = reconnectMaxDelaySec.toIntOrNull() ?: return false
            return base in 1..3600 && max in base..3600
        }

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    /** 跳过 TLS 证书校验；仅用于调试自签名或内网 WSS 服务 */
    var insecureTls by mutableStateOf(false)

    /** 发送模式：false 为文本帧，true 为二进制帧 */
    var binaryMode by mutableStateOf(false)

    /** 二进制帧输入的编码方式 */
    var binaryEncoding by mutableStateOf(BinaryEncoding.HEX)

    /** 二进制输入非法时的提示；null 表示无错误 */
    var binaryError by mutableStateOf<String?>(null)

    val state = engine.state
    val events = engine.events
    val history = sessions.wsHistory

    /** 已保存的消息模板，按保存顺序排列 */
    val templates = sessions.wsTemplates

    /** 模板列表是否已达上限，用于禁用新建 */
    val templateLimitReached: Boolean
        get() = templates.value.size >= WS_TEMPLATE_LIMIT
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            state.collect { connection ->
                if (connection == WsConnectionState.CLOSED || connection == WsConnectionState.FAILED) {
                    scheduleRetry()
                }
            }
        }
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is WsEvent.Connected -> {
                        lastError = null
                        add(L10n.t(R.string.state_opened), true, event.timestamp)
                    }
                    is WsEvent.Sent -> add(event.content, true, event.timestamp)
                    is WsEvent.TextReceived -> add(event.content, false, event.timestamp)
                    is WsEvent.BinarySent -> add(
                        L10n.t(R.string.ws_log_binary_sent, event.sizeBytes, event.preview),
                        true,
                        event.timestamp
                    )

                    is WsEvent.BinaryReceived -> add(
                        L10n.t(R.string.ws_log_binary_received, event.sizeBytes, event.preview),
                        false,
                        event.timestamp
                    )
                    is WsEvent.Closed -> {
                        add(L10n.t(R.string.ws_log_closed, event.reason ?: L10n.t(R.string.ws_unknown_reason)), true, event.timestamp)
                        scheduleRetry()
                    }
                    is WsEvent.Failed -> {
                        lastError = event.cause
                        add(L10n.t(R.string.ws_log_failed, event.cause), true, event.timestamp)
                        if (engine.state.value != WsConnectionState.CONNECTED) scheduleRetry()
                    }
                }
            }
        }
        viewModelScope.launch {
            ServiceLocator.collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.WS) {
                    restore(req.data)
                    loadedRequest.track(req.id)
                    activeCollectionId = req.collectionId
                    ServiceLocator.collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 当前请求所属文件夹，用于合并文件夹变量 */
    private var activeCollectionId: Long? = null

    /** 与集合条目的绑定：从集合加载或保存过一次后，再保存即原地覆盖 */
    private val loadedRequest = LoadedRequestTracker(ServiceLocator.collectionStore)

    /** 已绑定集合条目时原地覆盖；返回 false 表示需要弹出保存对话框 */
    fun saveOverLoaded(): Boolean = loadedRequest.saveOver { snapshot() }

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        loadedRequest.track(id)
        activeCollectionId = ServiceLocator.collectionStore.requests.value
            .firstOrNull { it.id == id }?.collectionId
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        WsSnapshot.serializer(),
        WsSnapshot(
            url = url,
            pingIntervalSec = pingIntervalSec.toIntOrNull() ?: 0,
            pingPayload = pingPayload.ifBlank { "ping" },
            headers = headers.map { KeyValueEntry(it.key, it.value, it.enabled) },
            subprotocols = parseSubprotocols(subprotocols),
            useProxy = useProxy,
            autoReconnect = autoReconnect,
            reconnectBaseDelaySec = sessions.wsReconnectSettings.value.baseDelaySec,
            reconnectMaxDelaySec = sessions.wsReconnectSettings.value.maxDelaySec,
            insecureTls = insecureTls
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(WsSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        url = snap.url
        pingIntervalSec = snap.pingIntervalSec.toString()
        pingPayload = snap.pingPayload
        headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        subprotocols = snap.subprotocols.joinToString(", ")
        useProxy = snap.useProxy
        insecureTls = snap.insecureTls
        updateReconnectBase(snap.reconnectBaseDelaySec.toString())
        updateReconnectMax(snap.reconnectMaxDelaySec.toString())
        updateAutoReconnect(snap.autoReconnect)
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

    /** 当前页里出现的 {{变量}} 及取值，用于输入框下方提示 */
    fun placeholderHints(text: String): List<Pair<String, String?>> {
        if (text.isBlank()) return emptyList()
        val vars = ServiceLocator.varsForCollection(activeCollectionId)
        val seen = LinkedHashMap<String, String?>()
        for (m in PlaceholderResolver.PLACEHOLDER_REGEX.findAll(text)) {
            val key = m.groupValues[1]
            seen.putIfAbsent(key, vars[key])
        }
        return seen.map { (k, v) -> k to v }
    }

    private fun add(text: String, isSystem: Boolean, timestamp: Long = System.currentTimeMillis()) {
        sessions.appendWs(activeEndpoint, text, isSystem, timestamp)
    }

    private fun scheduleRetry() {
        if (!allowReconnect || !autoReconnect || activeConfig == null) return
        val settings = sessions.wsReconnectSettings.value
        retries.schedule(settings.baseDelaySec, settings.maxDelaySec)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        sessions.setWsReconnectSettings(sessions.wsReconnectSettings.value.copy(enabled = enabled))
        if (enabled && state.value in listOf(WsConnectionState.CLOSED, WsConnectionState.FAILED)) {
            scheduleRetry()
        } else if (!enabled) {
            retries.cancel()
        }
    }

    fun updateReconnectBase(value: String) {
        reconnectBaseDelaySec = value
        saveReconnectDelays()
    }

    fun updateReconnectMax(value: String) {
        reconnectMaxDelaySec = value
        saveReconnectDelays()
    }

    private fun saveReconnectDelays() {
        val base = reconnectBaseDelaySec.toIntOrNull() ?: return
        val max = reconnectMaxDelaySec.toIntOrNull() ?: return
        if (!reconnectDelaysValid) return
        sessions.setWsReconnectSettings(WsReconnectSettings(autoReconnect, base, max))
    }

    fun connect() {
        allowReconnect = false
        retries.cancel()
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            lastError = null
            val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
            val vars = ServiceLocator.varsForCollection(activeCollectionId)

            // 地址、请求头与子协议里的占位符共用一次解析：任一缺失就整体中止
            val missing = mutableSetOf<String>()
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
                .map { it.copy(key = res(it.key), value = res(it.value)) }
            val resolvedSubprotocols = parseSubprotocols(subprotocols).map { res(it) }

            if (missing.isNotEmpty()) {
                missingVars = missing.sorted()
                return@launch
            }

            activeEndpoint = resolvedUrl
            activeConfig = WsConfig(
                url = resolvedUrl,
                headers = resolvedHeaders,
                subprotocols = resolvedSubprotocols,
                proxy = proxy,
                pingIntervalSec = pingIntervalSec.toIntOrNull() ?: 0,
                pingPayload = pingPayload.ifBlank { "ping" },
                insecureTls = insecureTls
            )
            allowReconnect = true
            add(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }), true)
            engine.connect(activeConfig!!)
            if (state.value == WsConnectionState.FAILED) scheduleRetry()
        }
    }

    fun send() {
        val text = input
        if (text.isBlank()) return
        viewModelScope.launch {
            val vars = ServiceLocator.varsForCollection(activeCollectionId)
            when (val r = resolver.resolve(text, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> {
                    if (binaryMode) {
                        sendBinary(r.resolved)
                    } else {
                        engine.send(r.resolved)
                        input = ""
                    }
                }

                is PlaceholderResolver.ResolveResult.Missing -> missingVars = r.keys
            }
        }
    }

    /** 按当前编码解码后发送二进制帧；解码失败保留输入并提示 */
    private suspend fun sendBinary(resolved: String) {
        val decoded = BinaryPayload.decode(resolved, binaryEncoding)
        val bytes = decoded.getOrNull()
        if (bytes != null) {
            engine.sendBinary(bytes)
            binaryError = null
            input = ""
        } else {
            binaryError = L10n.t(R.string.ws_binary_invalid)
        }
    }

    fun dismissBinaryError() {
        binaryError = null
    }

    /**
     * 把当前输入保存为模板：名称重复时覆盖同名模板内容，编码与二进制开关一并记录。
     * 名称或内容为空时返回 false，由界面提示。
     */
    fun saveTemplate(name: String, content: String): Boolean {
        if (name.isBlank() || content.isBlank()) return false
        if (templates.value.none { it.name == name.trim() } && templateLimitReached) return false
        sessions.saveWsTemplate(
            name = name,
            content = content,
            binary = binaryMode,
            encoding = binaryEncoding.name
        )
        return true
    }

    fun deleteTemplate(id: Long) {
        sessions.deleteWsTemplate(id)
    }

    /** 一键填充：文本模板写入输入框，二进制模板同时切到对应模式与编码 */
    fun applyTemplate(template: WsMessageTemplate) {
        input = template.content
        binaryMode = template.binary
        if (template.binary) {
            binaryEncoding = BinaryEncoding.entries.firstOrNull { it.name == template.encoding }
                ?: BinaryEncoding.HEX
        }
        binaryError = null
    }

    fun disconnect() {
        allowReconnect = false
        retries.cancel()
        connectJob?.cancel()
        viewModelScope.launch { engine.close() }
    }

    fun clearLog() {
        // 清空日志只取消待执行的重试；连接保持，后续意外断线仍按开关自动重连（需求 2.1）
        retries.cancel()
        sessions.clearWsHistory()
    }

    override fun onCleared() {
        allowReconnect = false
        retries.cancel()
        super.onCleared()
    }

    fun dismissMissing() {
        missingVars = null
    }
}

/**
 * 把逗号分隔的子协议输入拆成握手要发送的顺序列表。
 *
 * 空串返回空列表（不发送该头），重复项只保留第一次出现，避免服务端
 * 按顺序匹配时因重复而拒绝握手。
 */
internal fun parseSubprotocols(raw: String): List<String> =
    raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
