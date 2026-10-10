package com.evan.pulse.ui.sse

import androidx.compose.runtime.getValue
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
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseEvent
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch

/**
 * SSE 页面状态。SSE 为单向流，故只提供连接与断开，没有发送入口。
 */
class SseViewModel : ViewModel() {

    private val engine = ServiceLocator.sseEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val envStore = ServiceLocator.envStore
    private val settingsStore = ServiceLocator.settingsStore
    private val collectionStore = ServiceLocator.collectionStore

    var url by mutableStateOf("{{sseUrl}}/stream")
    var headers by mutableStateOf(listOf(KeyValue()))
    var eventFilter by mutableStateOf("")
    var autoReconnect by mutableStateOf(false)
    var reconnectDelaySec by mutableStateOf("3")

    val state = engine.state
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is SseEvent.Connected -> add(L10n.t(R.string.state_opened), true)
                    is SseEvent.FrameReceived -> add(frameText(event), false)
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
                    collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        SseSnapshot.serializer(),
        SseSnapshot(
            url = url,
            headers = headers.map { KeyValueEntry(it.key, it.value, it.enabled) },
            eventFilter = eventFilter,
            autoReconnect = autoReconnect,
            reconnectDelaySec = reconnectDelaySec.toIntOrNull() ?: 3
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(SseSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        url = snap.url
        headers = snap.headers.map { KeyValue(it.key, it.value, it.enabled) }.ifEmpty { listOf(KeyValue()) }
        eventFilter = snap.eventFilter
        autoReconnect = snap.autoReconnect
        reconnectDelaySec = snap.reconnectDelaySec.toString()
    }

    /** SSE 帧还原为协议原始文本：id / event / data 三行 */
    private fun frameText(event: SseEvent.FrameReceived): String = buildString {
        if (event.id != null && event.id.isNotBlank()) append("id: ").append(event.id).append('\n')
        if (event.event.isNotBlank()) append("event: ").append(event.event).append('\n')
        append("data: ").append(event.data)
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
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
            val proxy = settingsStore.settings.value.toProxyConfig()
            add(L10n.t(R.string.proxy_in_use, proxy.summary), true)
            val vars = envStore.activeVars()

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

            engine.connect(
                SseConfig(
                    url = res(url),
                    headers = headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .map { it.copy(value = res(it.value)) },
                    proxy = proxy,
                    eventFilter = eventFilter.trim(),
                    autoReconnect = autoReconnect,
                    reconnectDelaySec = reconnectDelaySec.toIntOrNull() ?: 3
                )
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { engine.close() }
    }

    fun clearLog() = lines.clear()

    fun dismissMissing() {
        missingVars = null
    }
}
