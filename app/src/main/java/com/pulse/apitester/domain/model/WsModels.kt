package com.pulse.apitester.domain.model

data class WsConfig(
    val url: String,
    val headers: List<KeyValue> = emptyList(),
    val subprotocols: List<String> = emptyList(),
    val timeoutMs: Long = 15_000L
)

enum class WsConnectionState {
    IDLE, CONNECTING, CONNECTED, CLOSED, FAILED
}

sealed interface WsEvent {
    data class Connected(val timestamp: Long) : WsEvent
    data class TextReceived(val content: String, val timestamp: Long) : WsEvent
    data class Sent(val content: String, val timestamp: Long) : WsEvent
    data class Closed(val reason: String?, val timestamp: Long) : WsEvent
    data class Failed(val cause: String, val timestamp: Long) : WsEvent
}
