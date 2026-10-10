package com.evan.pulse.domain.model

data class MqttConfig(
    val brokerUrl: String,
    val clientId: String,
    val username: String? = null,
    val password: String? = null,
    val keepAliveSec: Int = 60,
    val connectTimeoutSec: Int = 15,
    val proxy: ProxyConfig = ProxyConfig()
)

enum class MqttConnectionState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, FAILED
}

sealed interface MqttEvent {
    data class Connected(val latencyMs: Long, val timestamp: Long) : MqttEvent
    data class MessageReceived(
        val topic: String,
        val payload: String,
        val qos: Int,
        val timestamp: Long
    ) : MqttEvent

    data class Disconnected(val reason: String, val timestamp: Long) : MqttEvent
    data class Error(val cause: String, val timestamp: Long) : MqttEvent
}
