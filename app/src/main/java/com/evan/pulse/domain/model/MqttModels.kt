package com.evan.pulse.domain.model

data class MqttConfig(
    val brokerUrl: String,
    val clientId: String,
    val username: String? = null,
    val password: String? = null,
    val keepAliveSec: Int = 60,
    val connectTimeoutSec: Int = 15,
    val proxy: ProxyConfig = ProxyConfig(),
    /** 跳过 TLS 证书校验：仅用于自签名/内网 Broker，只对 ssl:// 生效（等价于 tls.insecure，保留兼容） */
    val insecureTls: Boolean = false,
    /** 完整 TLS 策略：自定义 CA 与客户端证书，只对 ssl:// 生效 */
    val tls: TlsConfig = TlsConfig()
) {
    /** 合并出实际生效的 TLS 策略：旧布尔开关优先并入 tls */
    val effectiveTls: TlsConfig
        get() = if (insecureTls) tls.copy(insecure = true) else tls
}

enum class MqttConnectionState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, FAILED
}

sealed interface MqttEvent {
    data class Connected(val latencyMs: Long, val timestamp: Long) : MqttEvent
    data class MessageReceived(
        val topic: String,
        val payload: String,
        val qos: Int,
        val timestamp: Long,
        /** 是否为 Broker 保留的消息（retain 标志为 1） */
        val retained: Boolean = false
    ) : MqttEvent

    data class Disconnected(val reason: String, val timestamp: Long) : MqttEvent
    data class Error(val cause: String, val timestamp: Long) : MqttEvent
}
