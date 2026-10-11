package com.evan.pulse.domain.model

/**
 * SSE（Server-Sent Events）连接配置。
 *
 * SSE 是服务器到客户端的单向长连接，因此没有"发送消息"入口；
 * 客户端只负责建立连接、接收事件流，并可通过 [eventFilter] 过滤事件名。
 *
 * 与 HTTP 一样支持 query 参数与请求体：多数 AI 流式接口需要 POST + JSON body
 * （如 OpenAI 兼容接口把 {"stream":true,...} 放进 body），因此这里复刻 HTTP 的
 * 参数/请求体形态，由 [method] 决定是否携带 body。
 */
data class SseConfig(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: List<KeyValue> = emptyList(),
    val queryParams: List<KeyValue> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val body: String = "",
    val timeoutMs: Long = 15_000L,
    val proxy: ProxyConfig = ProxyConfig(),
    /** 事件名过滤：非空时只保留该 event 类型的帧，空串表示接收全部 */
    val eventFilter: String = "",
    val autoReconnect: Boolean = false,
    val reconnectDelaySec: Int = 3
)

enum class SseConnectionState {
    IDLE, CONNECTING, CONNECTED, CLOSED, FAILED
}

sealed interface SseEvent {
    data class Connected(val timestamp: Long) : SseEvent

    /** 一个完整的 SSE 帧：可含 id 与 event 字段，data 为负载 */
    data class FrameReceived(
        val id: String?,
        val event: String,
        val data: String,
        val timestamp: Long
    ) : SseEvent

    /** 服务器发送的注释行（以 : 开头），常用于心跳保活 */
    data class Comment(val line: String, val timestamp: Long) : SseEvent

    data class Reconnecting(val delaySec: Int, val timestamp: Long) : SseEvent
    data class Closed(val reason: String?, val timestamp: Long) : SseEvent
    data class Failed(val cause: String, val timestamp: Long) : SseEvent
}
