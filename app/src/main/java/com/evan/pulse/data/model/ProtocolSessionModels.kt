package com.evan.pulse.data.model

data class WsSessionEntry(
    val id: Long,
    val endpoint: String,
    val text: String,
    val isSystem: Boolean,
    val timestamp: Long
)

data class MqttSubscriptionEntry(
    val topic: String,
    val qos: Int
)

data class WsReconnectSettings(
    val enabled: Boolean = false,
    val baseDelaySec: Int = 3,
    val maxDelaySec: Int = 60
)

/**
 * WS 消息模板：保存常用消息文本，一键填充发送框。
 *
 * [binary] 标记模板是否用于二进制帧；[encoding] 存 `HEX`/`BASE64`，
 * 与 [com.evan.pulse.domain.net.BinaryEncoding] 名称对应。
 */
data class WsMessageTemplate(
    val id: Long,
    val name: String,
    val content: String,
    val binary: Boolean = false,
    val encoding: String = "HEX",
    val createdAt: Long = 0L
)

const val WS_SESSION_HISTORY_LIMIT = 2000

/** 单个协议页面最多保存的消息模板数 */
const val WS_TEMPLATE_LIMIT = 50
