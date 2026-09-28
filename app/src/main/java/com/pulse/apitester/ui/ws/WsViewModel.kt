package com.pulse.apitester.ui.ws

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.apitester.di.ServiceLocator
import com.pulse.apitester.domain.PlaceholderResolver
import com.pulse.apitester.domain.model.WsConnectionState
import com.pulse.apitester.domain.model.WsEvent
import com.pulse.apitester.ui.history.UiEventLine
import kotlinx.coroutines.launch

class WsViewModel : ViewModel() {

    private val engine = ServiceLocator.wsEngine
    private val resolver = ServiceLocator.placeholderResolver

    var url by mutableStateOf("{{wsUrl}}/echo")
    var input by mutableStateOf("")

    val state = engine.state
    val events = engine.events
    val lines = mutableStateListOf<UiEventLine>()
    var missingVars by mutableStateOf<List<String>?>(null)
    var lastError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            events.collect { event ->
                when (event) {
                    is WsEvent.Connected -> add("已连接", true)
                    is WsEvent.Sent -> add(event.content, true)
                    is WsEvent.TextReceived -> add(event.content, false)
                    is WsEvent.Closed -> add("连接已断开：${event.reason ?: "未知原因"}", true)
                    is WsEvent.Failed -> {
                        lastError = event.cause
                        add("连接失败：${event.cause}", true)
                    }
                }
            }
        }
    }

    private fun add(text: String, isSystem: Boolean) {
        lines.add(UiEventLine(text, isSystem, System.currentTimeMillis()))
        if (lines.size > 1000) lines.removeAt(0)
    }

    fun connect() {
        viewModelScope.launch {
            lastError = null
            val vars = ServiceLocator.envStore.activeVars()
            when (val r = resolver.resolve(url, vars)) {
                is PlaceholderResolver.ResolveResult.Ok -> engine.connect(
                    com.pulse.apitester.domain.model.WsConfig(url = r.resolved)
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
