package com.evan.pulse.domain.sse

/**
 * SSE 断点续连的 Last-Event-ID 取值规则。
 *
 * 首次连接使用用户在页面上填写的断点；一旦本次会话收到过带 id 的帧，
 * 重连时优先使用最后收到的 id，符合 SSE 规范对断点续传的预期。
 */
object SseResume {

    /** 请求头名，SSE 规范固定写法 */
    const val HEADER = "Last-Event-ID"

    fun lastEventId(manual: String, seen: String?): String? =
        seen?.trim()?.takeIf { it.isNotEmpty() }
            ?: manual.trim().takeIf { it.isNotEmpty() }
}

/**
 * SSE 事件流统计：总帧数、按事件名计数、最后收到的事件 id。
 *
 * 纯不可变数据，便于在 ViewModel 里用一次赋值触发重组，也便于单测。
 */
data class SseStreamStats(
    val total: Int = 0,
    val byEvent: Map<String, Int> = emptyMap(),
    val lastEventId: String? = null
) {

    /** 记录一帧；事件名为空时按 SSE 默认类型 message 统计，空 id 不覆盖已知断点 */
    fun record(event: String, id: String?): SseStreamStats {
        val name = event.trim().ifEmpty { DEFAULT_EVENT }
        val nextId = id?.trim()?.takeIf { it.isNotEmpty() } ?: lastEventId
        return copy(
            total = total + 1,
            byEvent = byEvent + (name to (byEvent[name] ?: 0) + 1),
            lastEventId = nextId
        )
    }

    fun reset(): SseStreamStats = SseStreamStats()

    /** 按次数倒序，次数相同按事件名升序，保证展示顺序稳定 */
    fun breakdown(): List<Pair<String, Int>> = byEvent.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

    private companion object {
        const val DEFAULT_EVENT = "message"
    }
}
