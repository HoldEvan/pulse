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
    /** 定时发送：开启后每隔 [autoSendIntervalSec] 秒自动发送 [autoSendContent] */
    val autoSendEnabled: Boolean = false,
    val autoSendIntervalSec: Int = 10,
    val autoSendContent: String = "",
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试（等价于 tls.insecure，保留兼容） */
    val insecureTls: Boolean = false,
    /** 完整 TLS 策略：自定义 CA 与客户端证书 */
    val tls: TlsConfig = TlsConfig()
) {
    /** 合并出实际生效的 TLS 策略：旧布尔开关优先并入 tls */
    val effectiveTls: TlsConfig
        get() = if (insecureTls) tls.copy(insecure = true) else tls
}

enum class WsConnectionState {
    IDLE, CONNECTING, CONNECTED, CLOSED, FAILED
}

sealed interface WsEvent {
    /** 连接建立；[subprotocol] 为服务端最终协商出的子协议，未协商时为 null */
    data class Connected(val timestamp: Long, val subprotocol: String? = null) : WsEvent
    data class TextReceived(val content: String, val timestamp: Long) : WsEvent
    data class Sent(val content: String, val timestamp: Long) : WsEvent

    /** 发出 Ping 控制帧 */
    data class PingSent(val timestamp: Long) : WsEvent

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
