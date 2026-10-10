package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.model.SseEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SSE 引擎。SSE 为单向流，因此只提供 connect / close，没有 send。
 */
interface SseEngine {

    val state: StateFlow<SseConnectionState>
    val events: SharedFlow<SseEvent>

    suspend fun connect(config: SseConfig)

    suspend fun close()
}
