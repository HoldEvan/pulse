package com.pulse.apitester.di

import com.pulse.apitester.data.model.EnvironmentInfo
import com.pulse.apitester.data.model.HistoryEntry
import com.pulse.apitester.data.model.ProjectInfo
import com.pulse.apitester.domain.AuthApplier
import com.pulse.apitester.domain.PlaceholderResolver
import com.pulse.apitester.domain.model.AuthConfig
import com.pulse.apitester.domain.model.HttpCallConfig
import com.pulse.apitester.domain.model.HttpCallOutcome
import com.pulse.apitester.domain.model.KeyValue
import com.pulse.apitester.domain.model.MqttConfig
import com.pulse.apitester.domain.model.MqttConnectionState
import com.pulse.apitester.domain.model.MqttEvent
import com.pulse.apitester.domain.model.WsConfig
import com.pulse.apitester.domain.model.WsConnectionState
import com.pulse.apitester.domain.model.WsEvent
import com.pulse.apitester.domain.net.HttpEngine
import com.pulse.apitester.domain.net.MqttEngine
import com.pulse.apitester.domain.net.WsEngine
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimplePlaceholderResolver : PlaceholderResolver {
    override fun resolve(text: String, vars: Map<String, String>): PlaceholderResolver.ResolveResult {
        val missing = PlaceholderResolver.PLACEHOLDER_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in vars }
            .distinct()
            .toList()
        if (missing.isNotEmpty()) return PlaceholderResolver.ResolveResult.Missing(missing)
        val resolved = PlaceholderResolver.PLACEHOLDER_REGEX.replace(text) { m ->
            vars[m.groupValues[1]] ?: m.value
        }
        return PlaceholderResolver.ResolveResult.Ok(resolved)
    }
}

class SimpleAuthApplier : AuthApplier {
    override fun apply(config: AuthConfig): List<KeyValue> = when (config.type) {
        com.pulse.apitester.domain.model.AuthType.NONE -> emptyList()
        com.pulse.apitester.domain.model.AuthType.BEARER -> listOf(
            KeyValue("Authorization", "Bearer ${config.bearerToken}")
        )

        com.pulse.apitester.domain.model.AuthType.BASIC -> {
            val raw = "${config.basicUsername}:${config.basicPassword}"
            listOf(KeyValue("Authorization", "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)))
        }
    }
}

class StubHttpEngine : HttpEngine {
    override suspend fun execute(config: HttpCallConfig): HttpCallOutcome {
        delay(600)
        val body = """
            {
              "stub": true,
              "method": "${config.method.name}",
              "url": "${config.url}",
              "message": "网络层尚未接入，这是本地模拟响应"
            }
        """.trimIndent()
        return HttpCallOutcome.Success(
            statusCode = 200,
            statusText = "OK (stub)",
            headers = listOf(KeyValue("Content-Type", "application/json")),
            body = body,
            durationMs = 612,
            responseSizeBytes = body.toByteArray().size.toLong()
        )
    }
}

class StubWsEngine : WsEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(WsConnectionState.IDLE)
    override val state: StateFlow<WsConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<WsEvent> = _events.asSharedFlow()
    private var closed = CompletableDeferred<Unit>()

    override suspend fun connect(config: WsConfig) {
        _state.value = WsConnectionState.CONNECTING
        delay(700)
        closed = CompletableDeferred()
        _state.value = WsConnectionState.CONNECTED
        _events.emit(WsEvent.Connected(System.currentTimeMillis()))
        _events.emit(
            WsEvent.TextReceived(
                "{\"stub\":true,\"message\":\"WebSocket 引擎尚未接入，这是本地模拟连接\"}",
                System.currentTimeMillis()
            )
        )
    }

    override suspend fun send(text: String) {
        if (_state.value != WsConnectionState.CONNECTED) return
        _events.emit(WsEvent.Sent(text, System.currentTimeMillis()))
        scope.launch {
            delay(300)
            _events.emit(
                WsEvent.TextReceived(
                    text.reversed().ifEmpty { text },
                    System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun close() {
        _state.value = WsConnectionState.CLOSED
        _events.emit(WsEvent.Closed("用户断开 (stub)", System.currentTimeMillis()))
        closed.complete(Unit)
    }
}

class StubMqttEngine : MqttEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MqttConnectionState.IDLE)
    override val state: StateFlow<MqttConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<MqttEvent> = _events.asSharedFlow()
    private val subscribed = Mutex()

    override suspend fun connect(config: MqttConfig) {
        _state.value = MqttConnectionState.CONNECTING
        delay(800)
        _state.value = MqttConnectionState.CONNECTED
        _events.emit(MqttEvent.Connected(801, System.currentTimeMillis()))
    }

    override suspend fun subscribe(topic: String, qos: Int) {
        subscribed.withLock { delay(200) }
        _events.emit(
            MqttEvent.MessageReceived(
                topic,
                "{\"stub\":true,\"message\":\"MQTT 引擎尚未接入，这是本地模拟订阅确认\"}",
                qos,
                System.currentTimeMillis()
            )
        )
    }

    override suspend fun publish(topic: String, payload: String, qos: Int) {
        scope.launch {
            delay(400)
            _events.emit(MqttEvent.MessageReceived(topic, payload, qos, System.currentTimeMillis()))
        }
    }

    override suspend fun disconnect() {
        _state.value = MqttConnectionState.DISCONNECTED
        _events.emit(MqttEvent.Disconnected("用户断开 (stub)", System.currentTimeMillis()))
    }
}

class ProjectStore {
    private val _projects = MutableStateFlow(
        listOf(ProjectInfo(1, "默认项目"))
    )
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()
    private val _currentProjectId = MutableStateFlow(1L)
    val currentProjectId: StateFlow<Long> = _currentProjectId.asStateFlow()
    private var nextId = 2L

    fun create(name: String) {
        _projects.value = _projects.value + ProjectInfo(nextId++, name)
    }

    fun rename(id: Long, name: String) {
        _projects.value = _projects.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun delete(id: Long) {
        if (_projects.value.size <= 1) return
        _projects.value = _projects.value.filter { it.id != id }
        if (_currentProjectId.value == id) _currentProjectId.value = _projects.value.first().id
    }

    fun switch(id: Long) {
        _currentProjectId.value = id
    }
}

class HistoryStore {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()
    private var nextId = 1L

    fun add(entry: HistoryEntry) {
        _entries.value = (listOf(entry.copy(id = nextId++)) + _entries.value).take(500)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

class EnvStore {
    private val _environments = MutableStateFlow(
        listOf(EnvironmentInfo(1, 1, "default", true, listOf("baseUrl" to "https://api.example.com")))
    )
    val environments: StateFlow<List<EnvironmentInfo>> = _environments.asStateFlow()
    private var nextId = 2L

    fun activeVars(): Map<String, String> {
        val pid = ServiceLocator.projectStore.currentProjectId.value
        return _environments.value
            .firstOrNull { it.projectId == pid && it.isActive }
            ?.variables?.toMap() ?: emptyMap()
    }

    fun environmentsFor(projectId: Long): StateFlow<List<EnvironmentInfo>> = _environments

    fun create(projectId: Long, name: String) {
        _environments.value = _environments.value + EnvironmentInfo(nextId++, projectId, name, false, emptyList())
    }

    fun rename(id: Long, name: String) {
        _environments.value = _environments.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun delete(id: Long) {
        _environments.value = _environments.value.filter { it.id != id }
    }

    fun activate(projectId: Long, envId: Long) {
        _environments.value = _environments.value.map {
            when {
                it.projectId == projectId && it.id == envId -> it.copy(isActive = true)
                it.projectId == projectId -> it.copy(isActive = false)
                else -> it
            }
        }
    }

    fun setVariables(id: Long, variables: List<Pair<String, String>>) {
        _environments.value = _environments.value.map { if (it.id == id) it.copy(variables = variables) else it }
    }
}

object ServiceLocator {
    val projectStore = ProjectStore()
    val historyStore = HistoryStore()
    val envStore = EnvStore()
    val placeholderResolver: PlaceholderResolver = SimplePlaceholderResolver()
    val authApplier: AuthApplier = SimpleAuthApplier()
    val httpEngine: HttpEngine = StubHttpEngine()
    val wsEngine: WsEngine = StubWsEngine()
    val mqttEngine: MqttEngine = StubMqttEngine()
}
