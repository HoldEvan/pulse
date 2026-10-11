package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.model.SseEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
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
import kotlinx.coroutines.withContext

/**
 * 真实 SSE 引擎：基于 Ktor + OkHttp 发起长连接，按 text/event-stream 解析事件帧。
 *
 * 与 [OkHttpEngine] 一样按 ProxyConfig 缓存客户端。autoReconnect 打开时在流结束或
 * 失败后按 reconnectDelaySec 重新连接；关闭时发出 Reconnecting 事件供界面提示。
 */
class KtorSseEngine : SseEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val clients = ConcurrentHashMap<ProxyConfig, HttpClient>()

    private val _state = MutableStateFlow(SseConnectionState.IDLE)
    override val state: StateFlow<SseConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    @Volatile private var streamJob: Job? = null

    override suspend fun connect(config: SseConfig) {
        lock.withLock {
            streamJob?.cancel()
            streamJob = null
            _state.value = SseConnectionState.CONNECTING
            streamJob = scope.launch { runStream(config) }
        }
    }

    override suspend fun close() {
        lock.withLock {
            streamJob?.cancel()
            streamJob = null
            _state.value = SseConnectionState.CLOSED
            _events.tryEmit(SseEvent.Closed("用户断开", System.currentTimeMillis()))
        }
    }

    private suspend fun runStream(config: SseConfig) {
        while (true) {
            try {
                val response = clientFor(config.proxy).request(config.url) {
                    method = when (config.method) {
                        HttpMethod.POST -> KtorHttpMethod.Post
                        else -> KtorHttpMethod.Get
                    }
                    header("Accept", "text/event-stream")
                    config.queryParams
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { url.parameters.append(it.key, it.value) }
                    config.headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header(it.key, it.value) }
                    requestBody(config)?.let { setBody(it) }
                }
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP ${response.status.value} ${response.status.description}")
                }
                _state.value = SseConnectionState.CONNECTED
                _events.tryEmit(SseEvent.Connected(System.currentTimeMillis()))
                parseStream(response.bodyAsChannel(), config)
                _state.value = SseConnectionState.CLOSED
                _events.tryEmit(SseEvent.Closed("连接已关闭", System.currentTimeMillis()))
                if (!config.autoReconnect) return
                _events.tryEmit(
                    SseEvent.Reconnecting(config.reconnectDelaySec, System.currentTimeMillis())
                )
                delay(config.reconnectDelaySec * 1000L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = SseConnectionState.FAILED
                _events.tryEmit(SseEvent.Failed(describe(e), System.currentTimeMillis()))
                if (!config.autoReconnect) return
                _events.tryEmit(
                    SseEvent.Reconnecting(config.reconnectDelaySec, System.currentTimeMillis())
                )
                delay(config.reconnectDelaySec * 1000L)
            }
        }
    }

    /** 只有 POST 才携带请求体；JSON/Raw 都以文本发送，Content-Type 默认按类型推断 */
    private fun requestBody(config: SseConfig): TextContent? = when {
        config.method != HttpMethod.POST -> null
        config.body.isBlank() -> null
        config.bodyType == BodyType.JSON ->
            TextContent(config.body, ContentType.Application.Json)
        else -> TextContent(config.body, ContentType.Text.Plain)
    }

    /** 读取响应体直到 EOF，按 SSE 帧语法解析出事件与注释 */
    private suspend fun parseStream(channel: io.ktor.utils.io.ByteReadChannel, config: SseConfig) {
        var id: String? = null
        var event = ""
        val data = StringBuilder()

        fun flush() {
            if (data.isNotEmpty()) {
                val name = event.ifEmpty { "message" }
                if (config.eventFilter.isBlank() || name == config.eventFilter) {
                    _events.tryEmit(
                        SseEvent.FrameReceived(
                            id = id,
                            event = name,
                            data = data.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            id = null
            event = ""
            data.setLength(0)
        }

        withContext(Dispatchers.IO) {
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isBlank() -> flush()
                    line.startsWith(":") -> _events.tryEmit(
                        SseEvent.Comment(line.substring(1), System.currentTimeMillis())
                    )
                    line.startsWith("id:") -> id = line.removePrefix("id:").trim().ifBlank { null }
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
        }
        flush()
    }

    private fun clientFor(proxyConfig: ProxyConfig): HttpClient {
        clients[proxyConfig]?.let { return it }
        if (clients.size >= MAX_CACHED_CLIENTS) {
            clients.keys.firstOrNull()?.let { clients.remove(it) }
        }
        return clients.getOrPut(proxyConfig) { buildClient(proxyConfig) }
    }

    private fun buildClient(proxyConfig: ProxyConfig): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(0, TimeUnit.MILLISECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS)
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
