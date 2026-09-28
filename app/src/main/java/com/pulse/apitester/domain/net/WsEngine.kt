package com.pulse.apitester.domain.net

import com.pulse.apitester.domain.model.WsConfig
import com.pulse.apitester.domain.model.WsConnectionState
import com.pulse.apitester.domain.model.WsEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WsEngine {

    val state: StateFlow<WsConnectionState>
    val events: SharedFlow<WsEvent>

    suspend fun connect(config: WsConfig)

    suspend fun send(text: String)

    suspend fun close()
}
