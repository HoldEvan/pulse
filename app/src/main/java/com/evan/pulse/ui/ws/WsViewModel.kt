package com.evan.pulse.ui.ws

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.R
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.launch

class WsViewModel : ViewModel() {

    private val engine = ServiceLocator.wsEngine
    private val resolver = ServiceLocator.placeholderResolver
    private val settingsStore = ServiceLocator.settingsStore

    var url by mutableStateOf("{{wsUrl}}/echo")
    var input by mutableStateOf("")
    var pingIntervalSec by mutableStateOf("0")
    var pingPayload by mutableStateOf("ping")

    /** 是否套用设置页里的全局代理；关闭则本次连接强制直连 */
    var useProxy by mutableStateOf(true)

    val state = engine.state
    val events = engine.events
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is WsEvent.Connected -> add(L10n.t(R.string.state_opened), true)
                    is WsEvent.Sent -> add(event.content, true)
                    is WsEvent.TextReceived -> add(event.content, false)
                    is WsEvent.Closed -> add(
                        L10n.t(R.string.ws_log_closed, event.reason ?: L10n.t(R.string.ws_unknown_reason)),
                        true
                    )
                    is WsEvent.Failed -> {
                        lastError = event.cause
                        add(L10n.t(R.string.ws_log_failed, event.cause), true)
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
            useProxy = useProxy
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
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
    }

    fun connect() {
        viewModelScope.launch {
            lastError = null
            val proxy = if (useProxy) settingsStore.settings.value.toProxyConfig() else ProxyConfig()
            add(L10n.t(R.string.proxy_in_use, proxy.summary.ifEmpty { L10n.t(R.string.proxy_summary_direct) }), true)
            val vars = ServiceLocator.envStore.activeVars()
            when (val r = resolver.resolve(url, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> engine.connect(
                    com.evan.pulse.domain.model.WsConfig(
                        url = r.resolved,
                        proxy = proxy,
                        pingIntervalSec = pingIntervalSec.toIntOrNull() ?: 0,
                        pingPayload = pingPayload.ifBlank { "ping" }
                    )
                )

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
        viewModelScope.launch { engine.close() }
    }

    fun clearLog() = lines.clear()

    fun dismissMissing() {
        missingVars = null
    }
}
