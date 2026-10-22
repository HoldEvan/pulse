package com.evan.pulse.ui.ws

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.WsReconnectSettings
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import com.evan.pulse.domain.model.WsConfig
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class WsViewModel : ViewModel() {

    private val engine = ServiceLocator.wsEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val settingsStore = ServiceLocator.settingsStore
    private val sessions = ServiceLocator.protocolSessionStore
    private var activeConfig: WsConfig? = null
    private var activeEndpoint = ""
    private var allowReconnect = false
    private var connectJob: Job? = null
    private val retries = WsReconnectController(
        scope = viewModelScope,
        attempt = {
            val config = activeConfig ?: return@WsReconnectController false
            try {
                engine.connect(config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
                add(L10n.t(R.string.ws_log_failed, lastError.orEmpty()), true)
            }
            engine.state.value == WsConnectionState.CONNECTED
        },
        onScheduled = { seconds -> add(L10n.t(R.string.ws_log_reconnecting, seconds), true) }
    )

    var url by mutableStateOf("{{wsUrl}}/echo")
    var input by mutableStateOf("")
    var pingIntervalSec by mutableStateOf("0")
    var pingPayload by mutableStateOf("ping")
    var autoReconnect by mutableStateOf(sessions.wsReconnectSettings.value.enabled)
        private set
    var reconnectBaseDelaySec by mutableStateOf(sessions.wsReconnectSettings.value.baseDelaySec.toString())
        private set
    var reconnectMaxDelaySec by mutableStateOf(sessions.wsReconnectSettings.value.maxDelaySec.toString())
        private set
    val reconnectDelaysValid: Boolean
        get() {
            val base = reconnectBaseDelaySec.toIntOrNull() ?: return false
            val max = reconnectMaxDelaySec.toIntOrNull() ?: return false
            return base in 1..3600 && max in base..3600
        }

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    val state = engine.state
    val events = engine.events
    val history = sessions.wsHistory
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            state.collect { connection ->
                if (connection == WsConnectionState.CLOSED || connection == WsConnectionState.FAILED) {
                    scheduleRetry()
                }
            }
        }
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is WsEvent.Connected -> {
                        lastError = null
                        add(L10n.t(R.string.state_opened), true, event.timestamp)
                    }
                    is WsEvent.Sent -> add(event.content, true, event.timestamp)
                    is WsEvent.TextReceived -> add(event.content, false, event.timestamp)
                    is WsEvent.Closed -> {
                        add(L10n.t(R.string.ws_log_closed, event.reason ?: L10n.t(R.string.ws_unknown_reason)), true, event.timestamp)
                        scheduleRetry()
                    }
                    is WsEvent.Failed -> {
                        lastError = event.cause
                        add(L10n.t(R.string.ws_log_failed, event.cause), true, event.timestamp)
                        if (engine.state.value != WsConnectionState.CONNECTED) scheduleRetry()
                    }
                }
            }
        }
        viewModelScope.launch {
            ServiceLocator.collectionStore.pendingLoad.collect { req ->
                if (req?.type == RequestType.WS) {
                    restore(req.data)
                    ServiceLocator.collectionStore.consumePending(req)
                }
            }
        }
    }

    /** 导出当前配置为可恢复快照 */
    fun snapshot(): String = snapshotJson.encodeToString(
        WsSnapshot.serializer(),
        WsSnapshot(
            url = url,
            pingIntervalSec = pingIntervalSec.toIntOrNull() ?: 0,
            pingPayload = pingPayload.ifBlank { "ping" },
            useProxy = useProxy,
            autoReconnect = autoReconnect,
            reconnectBaseDelaySec = sessions.wsReconnectSettings.value.baseDelaySec,
            reconnectMaxDelaySec = sessions.wsReconnectSettings.value.maxDelaySec
        )
    )

    private fun restore(data: String) {
        val snap = try {
            snapshotJson.decodeFromString(WsSnapshot.serializer(), data)
        } catch (_: Exception) {
            return
        }
        url = snap.url
        pingIntervalSec = snap.pingIntervalSec.toString()
        pingPayload = snap.pingPayload
        useProxy = snap.useProxy
        updateReconnectBase(snap.reconnectBaseDelaySec.toString())
        updateReconnectMax(snap.reconnectMaxDelaySec.toString())
        updateAutoReconnect(snap.autoReconnect)
    }

    private fun add(text: String, isSystem: Boolean, timestamp: Long = System.currentTimeMillis()) {
        sessions.appendWs(activeEndpoint, text, isSystem, timestamp)
    }

    private fun scheduleRetry() {
        if (!allowReconnect || !autoReconnect || activeConfig == null) return
        val settings = sessions.wsReconnectSettings.value
        retries.schedule(settings.baseDelaySec, settings.maxDelaySec)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        sessions.setWsReconnectSettings(sessions.wsReconnectSettings.value.copy(enabled = enabled))
        if (enabled && state.value in listOf(WsConnectionState.CLOSED, WsConnectionState.FAILED)) {
            scheduleRetry()
        } else if (!enabled) {
            retries.cancel()
        }
    }

    fun updateReconnectBase(value: String) {
        reconnectBaseDelaySec = value
        saveReconnectDelays()
    }

    fun updateReconnectMax(value: String) {
        reconnectMaxDelaySec = value
        saveReconnectDelays()
    }

    private fun saveReconnectDelays() {
        val base = reconnectBaseDelaySec.toIntOrNull() ?: return
        val max = reconnectMaxDelaySec.toIntOrNull() ?: return
        if (!reconnectDelaysValid) return
        sessions.setWsReconnectSettings(WsReconnectSettings(autoReconnect, base, max))
    }

    fun connect() {
        allowReconnect = false
        retries.cancel()
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            lastError = null
            val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
            val vars = ServiceLocator.envStore.activeVars()
            when (val r = resolver.resolve(url, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> {
                    activeEndpoint = r.resolved
                    activeConfig = WsConfig(
                        url = r.resolved,
                        proxy = proxy,
                        pingIntervalSec = pingIntervalSec.toIntOrNull() ?: 0,
                        pingPayload = pingPayload.ifBlank { "ping" }
                    )
                    allowReconnect = true
                    add(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }), true)
                    engine.connect(activeConfig!!)
                    if (state.value == WsConnectionState.FAILED) scheduleRetry()
                }

                is PlaceholderResolver.ResolveResult.Missing -> missingVars = r.keys
            }
        }
    }

    fun send() {
        val text = input
        if (text.isBlank()) return
        viewModelScope.launch {
            val vars = ServiceLocator.envStore.activeVars()
            when (val r = resolver.resolve(text, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> {
                    engine.send(r.resolved)
                    input = ""
                }

                is PlaceholderResolver.ResolveResult.Missing -> missingVars = r.keys
            }
        }
    }

    fun disconnect() {
        allowReconnect = false
        retries.cancel()
        connectJob?.cancel()
        viewModelScope.launch { engine.close() }
    }

    fun clearLog() {
        // 清空日志只取消待执行的重试；连接保持，后续意外断线仍按开关自动重连（需求 2.1）
        retries.cancel()
        sessions.clearWsHistory()
    }

    override fun onCleared() {
        allowReconnect = false
        retries.cancel()
        super.onCleared()
    }

    fun dismissMissing() {
        missingVars = null
    }
}
