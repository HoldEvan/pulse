package com.evan.pulse.domain.model

data class WsConfig(
    val url: String,
    val headers: List<KeyValue> = emptyList(),
    val subprotocols: List<String> = emptyList(),
    val timeoutMs: Long = 15_000L,
    val proxy: ProxyConfig = ProxyConfig(),
    /** 心跳：间隔秒数，0 表示关闭；payload 为心跳帧内容 */
    val pingIntervalSec: Int = 0,
    val pingPayload: String = "ping",
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试 */
    val insecureTls: Boolean = false
)

enum class WsConnectionState {
    IDLE, CONNECTING, CONNECTED, CLOSED, FAILED
}

sealed interface WsEvent {
    data class Connected(val timestamp: Long) : WsEvent
    data class TextReceived(val content: String, val timestamp: Long) : WsEvent
    data class Sent(val content: String, val timestamp: Long) : WsEvent

    /** 收到二进制帧：[preview] 为按 hex/base64 编码的预览，[sizeBytes] 为完整字节数 */
    data class BinaryReceived(
        val preview: String,
        val sizeBytes: Int,
        val timestamp: Long
    ) : WsEvent

    /** 发出的二进制帧预览 */
    data class BinarySent(
        val preview: String,
        val sizeBytes: Int,
        val timestamp: Long
    ) : WsEvent

    data class Closed(val reason: String?, val timestamp: Long) : WsEvent
    data class Failed(val cause: String, val timestamp: Long) : WsEvent
}
