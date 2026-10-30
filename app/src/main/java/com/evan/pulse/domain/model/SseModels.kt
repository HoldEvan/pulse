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
    /** 首次重连的基础间隔（秒），开启指数退避后每次翻倍 */
    val reconnectDelaySec: Int = 3,
    /** 指数退避：每次重连间隔翻倍，受 [RetryPolicy.maxBackoffSec] 封顶 */
    val exponentialBackoff: Boolean = true,
    /** 重连间隔上限（秒） */
    val maxBackoffSec: Int = 30,
    /** 加入 ±25% 随机抖动，避免大量客户端同时重连造成惊群 */
    val jitter: Boolean = true,
    /** 最大连续重连次数，0 表示无限重试 */
    val maxRetries: Int = 0,
    /**
     * 空闲超时（秒）：超过该时长没有收到任何帧（含注释心跳）就主动断开并按重连策略重连。
     * 0 表示不检测。
     */
    val idleTimeoutSec: Int = 0,
    /** 手动指定的断点：首次连接时作为 Last-Event-ID 请求头发送，重连时被最后收到的 id 取代 */
    val lastEventId: String = "",
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试（等价于 tls.insecure，保留兼容） */
    val insecureTls: Boolean = false,
    /** 完整 TLS 策略：自定义 CA 与客户端证书 */
    val tls: TlsConfig = TlsConfig()
) {
    /** 合并出实际生效的 TLS 策略：旧布尔开关优先并入 tls */
    val effectiveTls: TlsConfig
        get() = if (insecureTls) tls.copy(insecure = true) else tls
}

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

    /**
     * 即将重连：delaySec 为本次等待时长，attempt 为第几次连续重连（从 1 开始），
     * maxRetries 为上限（0 表示无限），供界面提示「第 n 次重连 / 共 m 次」。
     */
    data class Reconnecting(
        val delaySec: Int,
        val attempt: Int = 1,
        val maxRetries: Int = 0,
        val timestamp: Long
    ) : SseEvent

    data class Closed(val reason: String?, val timestamp: Long) : SseEvent
    data class Failed(val cause: String, val timestamp: Long) : SseEvent

    /** 重连次数耗尽，连接已停止；此前应把状态置为 FAILED */
    data class RetryExhausted(val attempts: Int, val timestamp: Long) : SseEvent
}
