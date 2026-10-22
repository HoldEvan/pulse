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

const val WS_SESSION_HISTORY_LIMIT = 2000
