package com.pulse.apitester.ui.mqtt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.apitester.di.ServiceLocator
import com.pulse.apitester.domain.PlaceholderResolver
import com.pulse.apitester.domain.model.MqttConnectionState
import com.pulse.apitester.domain.model.MqttEvent
import com.pulse.apitester.ui.history.UiEventLine
import kotlinx.coroutines.launch

data class MqttSubscription(
    val topic: String,
    val qos: Int
)

class MqttViewModel : ViewModel() {

    private val engine = ServiceLocator.mqttEngine
    private val resolver = ServiceLocator.placeholderResolver

    var brokerUrl by mutableStateOf("tcp://{{mqttHost}}:1883")
    var clientId by mutableStateOf("pulse-android")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var keepAliveSec by mutableStateOf("60")

    var subTopic by mutableStateOf("demo/+/status")
    var subQos by mutableStateOf(0)
    var pubTopic by mutableStateOf("demo/device")
    var pubPayload by mutableStateOf("")
    var pubQos by mutableStateOf(0)

    val subscriptions = mutableStateListOf<MqttSubscription>()
    val state = engine.state
    val events = engine.events
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is MqttEvent.Connected -> add("已连接，耗时 ${event.latencyMs}ms", true)
                    is MqttEvent.MessageReceived -> add("[${event.topic}] QoS${event.qos} ${event.payload}", false)
                    is MqttEvent.Disconnected -> add("已断开：${event.reason}", true)
                    is MqttEvent.Error -> {
                        lastError = event.cause
                        add("错误：${event.cause}", true)
                    }
                }
            }
        }
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
    }

    private fun resolve(text: String, onOk: (String) -> Unit) {
        val vars = ServiceLocator.envStore.activeVars()
        when (val r = resolver.resolve(text, vars)) {
            is PlaceholderResolver.ResolveResult.Ok -> onOk(r.resolved)
            is PlaceholderResolver.ResolveResult.Missing -> missingVars = r.keys
        }
    }

    fun connect() {
        viewModelScope.launch {
            lastError = null
            resolve(brokerUrl) { resolvedUrl ->
                viewModelScope.launch {
                    engine.connect(
                        com.pulse.apitester.domain.model.MqttConfig(
                            brokerUrl = resolvedUrl,
                            clientId = clientId.ifBlank { "pulse-android" },
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            keepAliveSec = keepAliveSec.toIntOrNull() ?: 60
                        )
                    )
                    subscriptions.forEach { engine.subscribe(it.topic, it.qos) }
                }
            }
        }
    }

    fun addSubscription() {
        val topic = subTopic.trim()
        if (topic.isBlank()) return
        subscriptions.add(MqttSubscription(topic, subQos))
        if (state.value == MqttConnectionState.CONNECTED) {
            viewModelScope.launch { engine.subscribe(topic, subQos) }
        }
    }

    fun removeSubscription(index: Int) {
        subscriptions.removeAt(index)
    }

    fun publish() {
        val topic = pubTopic.trim()
        if (topic.isBlank() || state.value != MqttConnectionState.CONNECTED) return
        viewModelScope.launch {
            resolve(pubPayload) { resolvedPayload ->
                viewModelScope.launch { engine.publish(topic, resolvedPayload, pubQos) }
            }
            pubPayload = ""
        }
    }

    fun disconnect() {
        viewModelScope.launch { engine.disconnect() }
    }

    fun clearLog() = lines.clear()

    fun dismissMissing() {
        missingVars = null
    }
}
