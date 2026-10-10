package com.evan.pulse.ui.mqtt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.MqttSnapshot
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch

data class MqttSubscription(
    val topic: String,
    val qos: Int
)

class MqttViewModel : ViewModel() {

    private val engine = ServiceLocator.mqttEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val settingsStore = ServiceLocator.settingsStore

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
                    is MqttEvent.Connected -> add(L10n.t(R.string.state_connected, event.latencyMs), true)
                    is MqttEvent.MessageReceived -> add(L10n.t(R.string.message_line, event.topic, event.qos, event.payload), false)
                    is MqttEvent.Disconnected -> add(L10n.t(R.string.state_closed_message, event.reason), true)
                    is MqttEvent.Error -> {
                        lastError = event.cause
                        add(L10n.t(R.string.mqtt_state_error, event.cause), true)
                    }
                }
            }
        }
        viewModelScope.launch {
            ServiceLocator.collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.MQTT) {
                    restore(req.data)
                    ServiceLocator.collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        MqttSnapshot.serializer(),
        MqttSnapshot(
            brokerUrl = brokerUrl,
            clientId = clientId,
            username = username,
            password = password,
            keepAliveSec = keepAliveSec,
            subTopic = subTopic,
            subQos = subQos,
            pubTopic = pubTopic,
            pubQos = pubQos
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(MqttSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        brokerUrl = snap.brokerUrl
        clientId = snap.clientId
        username = snap.username
        password = snap.password
        keepAliveSec = snap.keepAliveSec
        subTopic = snap.subTopic
        subQos = snap.subQos
        pubTopic = snap.pubTopic
        pubQos = snap.pubQos
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
            val proxy = settingsStore.settings.value.toProxyConfig()
            resolve(brokerUrl) { resolvedUrl ->
                viewModelScope.launch {
                    add(L10n.t(R.string.proxy_in_use, proxy.summary), true)
                    engine.connect(
                        com.evan.pulse.domain.model.MqttConfig(
                            brokerUrl = resolvedUrl,
                            clientId = clientId.ifBlank { "pulse-android" },
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            keepAliveSec = keepAliveSec.toIntOrNull() ?: 60,
                            proxy = proxy
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
