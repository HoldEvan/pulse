package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.MqttConfig
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MqttEngine {

    val state: StateFlow<MqttConnectionState>
    val events: SharedFlow<MqttEvent>

    suspend fun connect(config: MqttConfig)

    suspend fun subscribe(topic: String, qos: Int)

    /** [retain] 为 true 时由 Broker 保留该消息，新订阅者连接后立即收到 */
    suspend fun publish(topic: String, payload: String, qos: Int, retain: Boolean = false)

    suspend fun disconnect()
}
