package com.evan.pulse.domain.model

/**
 * 遗嘱消息（Last Will and Testament）：客户端异常掉线时由 Broker 代发。
 *
 * 只有 [topic] 非空时才会随 CONNECT 下发；[payload] 为空表示发送空负载，
 * 用于主动清除 retained 消息（配合 [retain] = true）。
 */
data class MqttWill(
    val topic: String,
    val payload: String = "",
    val qos: Int = 0,
    val retain: Boolean = false
)

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
    val tls: TlsConfig = TlsConfig(),
    /** MQTT 协议版本：5 走 MQTT 5.0，3 走 3.1.1，兼容旧 Broker */
    val mqttVersion: Int = 5,
    /** clean start / clean session：true 时每次连接丢弃旧会话 */
    val cleanStart: Boolean = true,
    /** 会话过期时间（秒），MQTT 5 有效；配合 cleanStart=false 让 Broker 保留订阅与离线消息 */
    val sessionExpirySec: Long = 0,
    /** 断线后自动重连（指数退避），重连后自动恢复订阅 */
    val autoReconnect: Boolean = true,
    /** 遗嘱消息，null 表示不下发 */
    val will: MqttWill? = null
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
        val retained: Boolean = false,
        /** 原始负载字节：UI 用它判断是否为可打印文本，二进制时展示十六进制 */
        val payloadBytes: ByteArray = ByteArray(0)
    ) : MqttEvent

    data class Disconnected(val reason: String, val timestamp: Long) : MqttEvent
    data class Error(val cause: String, val timestamp: Long) : MqttEvent
}
