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

    suspend fun publish(topic: String, payload: String, qos: Int)

    suspend fun disconnect()
}
