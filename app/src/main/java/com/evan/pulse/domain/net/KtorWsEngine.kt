package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import com.evan.pulse.domain.model.WsConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import com.evan.pulse.domain.net.InsecureTls.applyInsecureTls
import kotlinx.coroutines.withContext

/**
 * 真实 WebSocket 引擎：基于 Ktor + OkHttp 的 WebSockets 插件。
 *
 * 与 [OkHttpEngine] 一样按 ProxyConfig 缓存客户端；直连时由 Ktor 自带网络层处理。
 * 收到服务器关闭帧或流结束时自动回到 CLOSED；用户调用 close() 时主动关闭会话。
 */
class KtorWsEngine : WsEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    /** 代理与 TLS 策略都是引擎级配置，按两者组合缓存客户端 */
    private data class ClientKey(val proxy: ProxyConfig, val insecureTls: Boolean)

    private val clients = ConcurrentHashMap<ClientKey, HttpClient>()

    private val _state = MutableStateFlow(WsConnectionState.IDLE)
    override val state: StateFlow<WsConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var receiveJob: Job? = null
    @Volatile private var pingJob: Job? = null

    override suspend fun connect(config: WsConfig) {
        lock.withLock {
            closeSession()
            _state.value = WsConnectionState.CONNECTING
            try {
                val wsSession = clientFor(config.proxy, config.insecureTls).webSocketSession(
                    urlString = config.url
                ) {
                    config.subprotocols.takeIf { it.isNotEmpty() }
                        ?.let { header("Sec-WebSocket-Protocol", it.joinToString(", ")) }
                    config.headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header(it.key, it.value) }
                }
                session = wsSession
                _state.value = WsConnectionState.CONNECTED
                _events.tryEmit(WsEvent.Connected(System.currentTimeMillis()))
                receiveJob = scope.launch { receiveLoop(wsSession) }
                if (config.pingIntervalSec > 0) {
                    pingJob = scope.launch { pingLoop(wsSession, config.pingIntervalSec, config.pingPayload) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                closeSession()
                _state.value = WsConnectionState.FAILED
                _events.tryEmit(WsEvent.Failed(describe(e), System.currentTimeMillis()))
            }
        }
    }

    override suspend fun send(text: String) {
        val s = session ?: return
        runCatching {
            withContext(Dispatchers.IO) { s.send(Frame.Text(text)) }
            _events.tryEmit(WsEvent.Sent(text, System.currentTimeMillis()))
        }.onFailure { e ->
            _events.tryEmit(WsEvent.Failed(e.message ?: "发送失败", System.currentTimeMillis()))
        }
    }

    override suspend fun sendBinary(bytes: ByteArray) {
        val s = session ?: return
        runCatching {
            withContext(Dispatchers.IO) { s.send(Frame.Binary(true, bytes)) }
            _events.tryEmit(
                WsEvent.BinarySent(
                    preview = BinaryPayload.preview(bytes, BinaryEncoding.HEX),
                    sizeBytes = bytes.size,
                    timestamp = System.currentTimeMillis()
                )
            )
        }.onFailure { e ->
            _events.tryEmit(WsEvent.Failed(e.message ?: "发送失败", System.currentTimeMillis()))
        }
    }

    override suspend fun close() {
        lock.withLock {
            closeSession()
            _state.value = WsConnectionState.CLOSED
            _events.tryEmit(WsEvent.Closed("用户断开", System.currentTimeMillis()))
        }
    }

    private suspend fun receiveLoop(s: DefaultClientWebSocketSession) {
        try {
            for (frame in s.incoming) {
                when (frame) {
                    is Frame.Text -> _events.tryEmit(
                        WsEvent.TextReceived(frame.readText(), System.currentTimeMillis())
                    )

                    is Frame.Binary -> {
                        val bytes = frame.readBytes()
                        _events.tryEmit(
                            WsEvent.BinaryReceived(
                                preview = BinaryPayload.preview(bytes, BinaryEncoding.HEX),
                                sizeBytes = bytes.size,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }

                    is Frame.Close -> {
                        val reason = frame.readReason()
                            ?.let { "code=${it.code}" }
                            ?: "服务器关闭连接"
                        _events.tryEmit(WsEvent.Closed(reason, System.currentTimeMillis()))
                        break
                    }

                    else -> Unit
                }
            }
            _state.value = WsConnectionState.CLOSED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = WsConnectionState.FAILED
            _events.tryEmit(WsEvent.Failed(describe(e), System.currentTimeMillis()))
        }
    }

    private suspend fun pingLoop(s: DefaultClientWebSocketSession, intervalSec: Int, payload: String) {
        val content = payload.ifBlank { "ping" }
        while (true) {
            delay(intervalSec * 1000L)
            runCatching {
                withContext(Dispatchers.IO) { s.send(Frame.Text(content)) }
            }.onSuccess {
                _events.tryEmit(WsEvent.Sent(content, System.currentTimeMillis()))
            }
        }
    }

    private suspend fun closeSession() {
        pingJob?.cancel()
        pingJob = null
        receiveJob?.cancel()
        receiveJob = null
        val s = session
        session = null
        runCatching { s?.close() }
    }

    private fun clientFor(proxyConfig: ProxyConfig, insecureTls: Boolean): HttpClient {
        val key = ClientKey(proxyConfig, insecureTls)
        clients[key]?.let { return it }
        if (clients.size >= MAX_CACHED_CLIENTS) {
            clients.keys.firstOrNull()?.let { clients.remove(it) }
        }
        return clients.getOrPut(key) { buildClient(proxyConfig, insecureTls) }
    }

    private fun buildClient(proxyConfig: ProxyConfig, insecureTls: Boolean): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
        engine {
            config {
                connectTimeout(0, TimeUnit.MILLISECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS)
                applyInsecureTls(insecureTls)
                if (proxyConfig.enabled) {
                    proxy(
                        Proxy(
                            if (proxyConfig.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                            InetSocketAddress(proxyConfig.host.trim(), proxyConfig.port)
                        )
                    )
                }
            }
        }
    }

    private fun describe(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val type = error::class.simpleName.orEmpty()
        return when {
            message.isEmpty() -> type.ifEmpty { "连接失败" }
            type.isEmpty() -> message
            else -> "$type: $message"
        }
    }

    private companion object {
        const val MAX_CACHED_CLIENTS = 8
    }
}
