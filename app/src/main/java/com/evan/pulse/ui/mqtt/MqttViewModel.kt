package com.evan.pulse.ui.mqtt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.ui.common.tlsConfigOf
import com.evan.pulse.data.model.AssertionEntry
import com.evan.pulse.data.model.MqttSnapshot
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.assertion.AssertionCheck
import com.evan.pulse.domain.assertion.AssertionTarget
import com.evan.pulse.domain.assertion.ResponseAssertion
import com.evan.pulse.domain.assertion.ResponseAssertions
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.domain.mqtt.MqttSubscriptionInput
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MqttViewModel : ViewModel() {

    private val engine = ServiceLocator.mqttEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val settingsStore = ServiceLocator.settingsStore
    private val sessions = ServiceLocator.protocolSessionStore

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

    /** 发布时是否让 Broker 保留消息 */
    var pubRetain by mutableStateOf(false)

    /** 跳过 TLS 证书校验；仅对 ssl:// 生效，用于调试自签名或内网 Broker */
    var insecureTls by mutableStateOf(false)
    var customCaPem by mutableStateOf("")
    var clientCertPem by mutableStateOf("")
    var clientKeyPem by mutableStateOf("")

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    /** 当前请求所属文件夹，用于合并文件夹变量 */
    private var activeCollectionId: Long? = null

    /** 持久化订阅集合，来自 ProtocolSessionStore，启动时随本地数据库恢复 */
    val subscriptions: StateFlow<List<MqttSubscriptionEntry>> = sessions.mqttSubscriptions

    /** 最近一次订阅输入中无法解析的条目原文，用于行内提示 */
    var invalidTopics by mutableStateOf<List<String>>(emptyList())
        private set
    val state = engine.state
    val events = engine.events
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    /** 收到消息时执行的断言：全部通过才认为该条消息符合预期 */
    var assertions by mutableStateOf<List<ResponseAssertion>>(emptyList())

    /** 最近一条消息的断言结果 */
    var assertionResults by mutableStateOf<List<AssertionCheck>>(emptyList())

    /** 自连接以来累计校验的消息条数与失败条数 */
    var assertionChecked by mutableStateOf(0)
    var assertionFailed by mutableStateOf(0)

    init {
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is MqttEvent.Connected -> add(L10n.t(R.string.state_connected, event.latencyMs), true)
                    is MqttEvent.MessageReceived -> {
                        add(
                            if (event.retained) {
                                L10n.t(R.string.mqtt_message_retained, event.topic, event.qos, event.payload)
                            } else {
                                L10n.t(R.string.message_line, event.topic, event.qos, event.payload)
                            },
                            false
                        )
                        evaluateAssertions(event.payload)
                    }
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
                    loadedRequest.track(req.id)
                    activeCollectionId = req.collectionId
                    ServiceLocator.collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 与集合条目的绑定：从集合加载或保存过一次后，再保存即原地覆盖 */
    private val loadedRequest = LoadedRequestTracker(ServiceLocator.collectionStore)

    /** 已绑定集合条目时原地覆盖；返回 false 表示需要弹出保存对话框 */
    fun saveOverLoaded(): Boolean = loadedRequest.saveOver { snapshot() }

    /** 保存对话框新建成功后绑定新条目，避免下次保存再建一条 */
    fun markSaved(id: Long) {
        loadedRequest.track(id)
        activeCollectionId = ServiceLocator.collectionStore.requests.value
            .firstOrNull { it.id == id }?.collectionId
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
            pubQos = pubQos,
            pubRetain = pubRetain,
            useProxy = useProxy,
            insecureTls = insecureTls,
            customCaPem = customCaPem,
            clientCertPem = clientCertPem,
            clientKeyPem = clientKeyPem,
            assertions = assertions.map { AssertionEntry(it.type, it.expression, it.expected, it.operator) }
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
        pubRetain = snap.pubRetain
        useProxy = snap.useProxy
        insecureTls = snap.insecureTls
        customCaPem = snap.customCaPem
        clientCertPem = snap.clientCertPem
        clientKeyPem = snap.clientKeyPem
        assertions = snap.assertions.map { ResponseAssertion(it.type, it.expression, it.expected, it.operator) }
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
    }

    /** 对一条收到的消息负载求值断言；没有配置断言时不做任何事 */
    private fun evaluateAssertions(payload: String) {
        if (assertions.isEmpty()) return
        val vars = ServiceLocator.varsForCollection(activeCollectionId)
        val results = ResponseAssertions.evaluate(AssertionTarget(body = payload), assertions, vars)
        if (results.isEmpty()) return
        assertionResults = results
        assertionChecked += 1
        if (results.any { !it.passed }) assertionFailed += 1
    }

    private fun resolve(text: String, onOk: (String) -> Unit) {
        val vars = ServiceLocator.varsForCollection(activeCollectionId)
        when (val r = resolver.resolve(text, vars)) {
            is PlaceholderResolver.ResolveResult.Ok -> onOk(r.resolved)
            is PlaceholderResolver.ResolveResult.Missing -> missingVars = r.keys
        }
    }

    fun connect() {
        viewModelScope.launch {
            lastError = null
            assertionResults = emptyList()
            assertionChecked = 0
            assertionFailed = 0
            val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
            resolve(brokerUrl) { resolvedUrl ->
                viewModelScope.launch {
                    add(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }), true)
                    engine.connect(
                        com.evan.pulse.domain.model.MqttConfig(
                            brokerUrl = resolvedUrl,
                            clientId = clientId.ifBlank { "pulse-android" },
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            keepAliveSec = keepAliveSec.toIntOrNull() ?: 60,
                            proxy = proxy,
                            insecureTls = insecureTls,
                            tls = tlsConfigOf(insecureTls, customCaPem, clientCertPem, clientKeyPem)
                        )
                    )
                    // 连接建立或恢复后，对保存的订阅逐一按存储的 QoS 重新订阅（需求 3.3）
                    if (engine.state.value == MqttConnectionState.CONNECTED) {
                        sessions.mqttSubscriptions.value.forEach { engine.subscribe(it.topic, it.qos) }
                    }
                }
            }
        }
    }

    /**
     * 批量订阅：输入框支持换行/逗号/分号分隔多个 topic，每条可用 `@0`/`@1`/`@2` 指定独立 QoS。
     * 非法条目只提示不写入，合法条目照常保存并在已连接时立即订阅（需求 3.5、3.6）。
     */
    fun addSubscription() {
        val parsed = MqttSubscriptionInput.parse(subTopic, subQos)
        invalidTopics = parsed.invalid
        if (parsed.topics.isEmpty()) return
        val merged = sessions.mqttSubscriptions.value
            .filter { saved -> parsed.topics.none { it.topic == saved.topic } } +
            parsed.topics.map { MqttSubscriptionEntry(it.topic, it.qos) }
        sessions.setMqttSubscriptions(merged)
        if (state.value == MqttConnectionState.CONNECTED) {
            viewModelScope.launch {
                parsed.topics.forEach { engine.subscribe(it.topic, it.qos) }
            }
        }
    }

    /** 点击订阅行循环切换 QoS（0→1→2→0），已连接时按新 QoS 重新订阅 */
    fun cycleSubscriptionQos(topic: String) {
        val current = sessions.mqttSubscriptions.value.firstOrNull { it.topic == topic } ?: return
        val next = (current.qos + 1) % 3
        sessions.setMqttSubscriptions(
            sessions.mqttSubscriptions.value.map {
                if (it.topic == topic) it.copy(qos = next) else it
            }
        )
        if (state.value == MqttConnectionState.CONNECTED) {
            viewModelScope.launch { engine.subscribe(topic, next) }
        }
    }

    fun removeSubscription(topic: String) {
        sessions.setMqttSubscriptions(sessions.mqttSubscriptions.value.filter { it.topic != topic })
    }

    fun clearFilterError() {
        invalidTopics = emptyList()
    }

    fun publish() {
        val topic = pubTopic.trim()
        if (topic.isBlank() || state.value != MqttConnectionState.CONNECTED) return
        viewModelScope.launch {
            resolve(pubPayload) { resolvedPayload ->
                viewModelScope.launch { engine.publish(topic, resolvedPayload, pubQos, pubRetain) }
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
