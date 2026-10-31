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

/** MQTT 事件日志的一行：与 WS 会话日志同构，按 Broker 地址区分来源 */
data class MqttSessionEntry(
    val id: Long,
    val broker: String,
    val text: String,
    val isSystem: Boolean,
    val timestamp: Long
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

/** MQTT 事件日志最多保留的条数，超出按时间淘汰最旧的 */
const val MQTT_SESSION_HISTORY_LIMIT = 2000

/**
 * 一次 SSE 会话的历史记录：从点击连接到最终断开（含期间的自动重连）。
 * [transcript] 为 text/event-stream 格式的转录，可直接导出重放。
 */
data class SseHistoryEntry(
    val id: Long,
    val method: String,
    val url: String,
    val startedAt: Long,
    val endedAt: Long,
    val frames: Int,
    /** 结束原因：用户断开、连接失败原因等 */
    val outcome: String,
    val transcript: String
)

/** SSE 历史最多保留的会话数 */
const val SSE_HISTORY_LIMIT = 50

/** 单个协议页面最多保存的消息模板数 */
const val WS_TEMPLATE_LIMIT = 50
