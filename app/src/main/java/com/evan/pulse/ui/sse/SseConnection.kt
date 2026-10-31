package com.evan.pulse.ui.sse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.evan.pulse.R
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.di.SseHistoryStore
import com.evan.pulse.domain.JsonPath
import com.evan.pulse.domain.extractString
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.model.SseEvent
import com.evan.pulse.domain.net.SseEngine
import com.evan.pulse.domain.sse.SseStreamStats
import com.evan.pulse.domain.sse.SseTranscript
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * SSE 页面上的一个并行连接（标签页）。
 *
 * 每个连接持有独立的引擎实例、事件日志、统计与拼接预览，互不干扰。
 * 一次「会话」从点击连接开始、到最终断开为止（期间的自动重连属于同一会话），
 * 会话结束时把 text/event-stream 格式的转录写入 [SseHistoryStore]。
 */
class SseConnection(
    val id: Int,
    scope: CoroutineScope,
    private val history: SseHistoryStore,
    private val engine: SseEngine = ServiceLocator.newSseEngine()
) {

    val state: StateFlow<SseConnectionState> = engine.state
    val lines = mutableStateListOf<UiEventLine>()
    var stats by mutableStateOf(SseStreamStats())
        private set
    var previewText by mutableStateOf("")
    var lastError by mutableStateOf<String?>(null)
        private set

    /** 最近一次连接的目标，用于标签页标题与历史记录 */
    var targetUrl by mutableStateOf("")
        private set
    private var targetMethod = "GET"

    /** 拼接预览表达式由页面实时提供，改表达式无需重连即可生效 */
    private var previewPath: () -> String = { "" }

    private var autoReconnect = false
    private var userClosing = false
    private var sessionStartedAt = 0L
    private var transcript: SseTranscript? = null

    /** 最近结束的会话，供断开后仍能导出 */
    private var lastTranscript: Pair<Long, String>? = null

    private val collector: Job = scope.launch { engine.events.collect(::handle) }

    fun connect(config: SseConfig, method: String, previewPath: () -> String) {
        finishSession(L10n.t(R.string.sse_history_superseded))
        this.previewPath = previewPath
        targetUrl = config.url
        targetMethod = method
        autoReconnect = config.autoReconnect
        userClosing = false
        lastError = null
        previewText = ""
        stats = stats.reset()
        sessionStartedAt = System.currentTimeMillis()
        transcript = SseTranscript()
        detachedScope.launch { engine.connect(config) }
    }

    fun disconnect() {
        userClosing = true
        detachedScope.launch { engine.close() }
    }

    /** 写一条系统日志，同时进入当前会话的转录 */
    fun note(text: String) = add(text, isSystem = true, timestamp = System.currentTimeMillis())

    fun clearLog() {
        lines.clear()
        stats = stats.reset()
    }

    fun adoptableLastEventId(): String? = stats.lastEventId

    /** 可导出的转录：会话进行中导出当前内容，已结束则导出最近一次会话 */
    fun exportable(): Pair<Long, String>? {
        transcript?.takeIf { !it.isEmpty }?.let { return sessionStartedAt to it.render() }
        return lastTranscript
    }

    /** 关闭标签页或页面销毁：收尾会话、停止收集并断开连接 */
    fun dispose() {
        finishSession(L10n.t(R.string.sse_history_tab_closed))
        collector.cancel()
        detachedScope.launch { engine.close() }
    }

    /** 标签页标题：#序号 + 主机名；尚未连接过显示「新连接」 */
    fun label(): String {
        val host = targetUrl.substringAfter("://", targetUrl)
            .substringBefore('/').substringBefore('?').substringAfterLast('@')
        return "#$id " + host.ifBlank { L10n.t(R.string.sse_connection_new) }
    }

    private fun handle(event: SseEvent) {
        when (event) {
            is SseEvent.Connected -> add(L10n.t(R.string.state_opened), true, event.timestamp)
            is SseEvent.FrameReceived -> {
                stats = stats.record(event.event, event.id)
                appendPreview(event.data)
                lines.add(UiEventLine(frameText(event), false, event.timestamp))
                trimLines()
                transcript?.frame(event.id, event.event, event.data)
            }
            is SseEvent.Comment -> add(event.line, true, event.timestamp)
            is SseEvent.Reconnecting -> add(
                if (event.maxRetries > 0) {
                    L10n.t(R.string.sse_log_reconnecting_attempt, event.attempt, event.maxRetries, event.delaySec)
                } else {
                    L10n.t(R.string.sse_log_reconnecting, event.delaySec)
                },
                true,
                event.timestamp
            )
            is SseEvent.RetryExhausted -> {
                val message = L10n.t(R.string.sse_log_retry_exhausted, event.attempts)
                lastError = message
                add(message, true, event.timestamp)
                finishSession(message)
            }
            is SseEvent.Closed -> {
                val reason = event.reason ?: L10n.t(R.string.ws_unknown_reason)
                add(L10n.t(R.string.sse_log_closed, reason), true, event.timestamp)
                // 开启自动重连时，服务端正常结束流只是一次重连的开始，不算会话结束
                if (!autoReconnect || userClosing) finishSession(reason)
            }
            is SseEvent.Failed -> {
                lastError = event.cause
                add(L10n.t(R.string.sse_log_failed, event.cause), true, event.timestamp)
                if (!autoReconnect) finishSession(event.cause)
            }
        }
    }

    private fun finishSession(outcome: String) {
        val t = transcript ?: return
        transcript = null
        val text = t.render()
        lastTranscript = sessionStartedAt to text
        history.record(
            method = targetMethod,
            url = targetUrl,
            startedAt = sessionStartedAt,
            endedAt = System.currentTimeMillis(),
            frames = t.frames,
            outcome = outcome,
            transcript = text
        )
    }

    private fun add(text: String, isSystem: Boolean, timestamp: Long) {
        lines.add(UiEventLine(text, isSystem, timestamp))
        trimLines()
        transcript?.comment(text, timestamp)
    }

    private fun trimLines() {
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    /** SSE 帧还原为协议原始文本：id / event / data 三行 */
    private fun frameText(event: SseEvent.FrameReceived): String = buildString {
        if (event.id != null && event.id.isNotBlank()) append("id: ").append(event.id).append('\n')
        if (event.event.isNotBlank()) append("event: ").append(event.event).append('\n')
        append("data: ").append(event.data)
    }

    /** 帧的 data 若是 JSON，按预览表达式提取并拼接；非 JSON 或表达式为空时跳过 */
    private fun appendPreview(data: String) {
        val path = previewPath().trim()
        if (path.isEmpty()) return
        val hits = JsonPath.eval(data, path) ?: return
        if (hits.isEmpty()) return
        previewText += hits.joinToString("") { it.extractString() }
    }

    private companion object {
        const val MAX_LINES = 1000

        /**
         * 连接与断开放到独立作用域：页面销毁时 viewModelScope 已取消，
         * 仍需把关闭请求送达引擎，否则底层长连接会一直挂着。
         */
        val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
