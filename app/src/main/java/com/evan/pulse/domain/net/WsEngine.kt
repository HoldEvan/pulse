package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.WsConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WsEngine {

    val state: StateFlow<WsConnectionState>
    val events: SharedFlow<WsEvent>

    suspend fun connect(config: WsConfig)

    suspend fun send(text: String)

    /** 发送二进制帧；[bytes] 为空时按空帧发送 */
    suspend fun sendBinary(bytes: ByteArray)

    suspend fun close()
}
