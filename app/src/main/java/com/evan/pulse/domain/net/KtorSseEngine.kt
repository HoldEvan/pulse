package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import com.evan.pulse.domain.model.TlsConfig
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.evan.pulse.domain.net.InsecureTls.applyTls
import com.evan.pulse.domain.sse.SseResume
import com.evan.pulse.domain.sse.SseRetryPolicy
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
    /** 代理与 TLS 策略都是引擎级配置，按两者组合缓存客户端 */
    private data class ClientKey(val proxy: ProxyConfig, val tls: TlsConfig)

    private val clients = ConcurrentHashMap<ClientKey, HttpClient>()

    private val _state = MutableStateFlow(SseConnectionState.IDLE)
    override val state: StateFlow<SseConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    @Volatile private var streamJob: Job? = null

    override suspend fun connect(config: SseConfig) {
        lock.withLock {
            streamJob?.cancel()
            streamJob = null
            serverRetryMs = null
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
        // 跨重连保留最后收到的事件 id，作为下一次请求的断点
        var resumeId: String? = null
        // 连续重连计数：任意一次连接成功建立并收到数据后清零
        var attempt = 0
        while (true) {
            var gotFrame = false
            try {
                val resumeHeader = SseResume.lastEventId(config.lastEventId, resumeId)
                val explicitResume = config.headers.any {
                    it.enabled && it.key.trim().equals(SseResume.HEADER, ignoreCase = true)
                }
                // 超时只约束到响应头到达为止；流建立后 parseStream 不再受影响
                val response = withTimeout(config.timeoutMs) {
                    clientFor(config.proxy, config.effectiveTls).request(config.url) {
                        method = ktorMethod(config.method)
                        header("Accept", "text/event-stream")
                        config.queryParams
                            .filter { it.enabled && it.key.isNotBlank() }
                            .forEach { url.parameters.append(it.key, it.value) }
                        config.headers
                            .filter { it.enabled && it.key.isNotBlank() }
                            .forEach { header(it.key, it.value) }
                        // 用户显式配置了同名头时以其为准，避免重复发送
                        if (resumeHeader != null && !explicitResume) {
                            header(SseResume.HEADER, resumeHeader)
                        }
                        requestBody(config)?.let { setBody(it) }
                    }
                }
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP ${response.status.value} ${response.status.description}")
                }
                _state.value = SseConnectionState.CONNECTED
                _events.tryEmit(SseEvent.Connected(System.currentTimeMillis()))
                parseStream(response.bodyAsChannel(), config) {
                    resumeId = it
                    gotFrame = true
                }
                _state.value = SseConnectionState.CLOSED
                _events.tryEmit(SseEvent.Closed("连接已关闭", System.currentTimeMillis()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = SseConnectionState.FAILED
                _events.tryEmit(SseEvent.Failed(describe(e), System.currentTimeMillis()))
            }
            if (!config.autoReconnect) return
            // 收到过数据说明这条流曾经可用，重新从第一次退避开始计
            if (gotFrame) attempt = 0
            // 解析服务端下发的 retry 字段作为基础间隔（若本次流设置过）
            val baseSec = serverRetryMs?.let { (it / 1000).toInt().coerceAtLeast(1) }
                ?: config.reconnectDelaySec
            attempt++
            if (config.maxRetries > 0 && attempt > config.maxRetries) {
                _state.value = SseConnectionState.FAILED
                _events.tryEmit(
                    SseEvent.RetryExhausted(config.maxRetries, System.currentTimeMillis())
                )
                return
            }
            val delaySec = SseRetryPolicy.delaySeconds(
                baseSec = baseSec,
                attempt = attempt,
                exponential = config.exponentialBackoff,
                maxSec = config.maxBackoffSec,
                jitter = config.jitter
            )
            _events.tryEmit(
                SseEvent.Reconnecting(
                    delaySec = delaySec,
                    attempt = attempt,
                    maxRetries = config.maxRetries,
                    timestamp = System.currentTimeMillis()
                )
            )
            delay(delaySec * 1000L)
        }
    }

    /**
     * 解析流时记录服务端下发的 `retry:` 值（毫秒），供下一次重连用它作为基础间隔。
     * 服务端未下发时保持 null，沿用用户配置的重连延迟。
     */
    @Volatile private var serverRetryMs: Long? = null

    /** 与 HTTP 引擎一致：只要有 body 内容就随请求发送，Content-Type 按类型推断 */
    private fun requestBody(config: SseConfig): TextContent? = when {
        config.body.isBlank() -> null
        config.bodyType == BodyType.JSON ->
            TextContent(config.body, ContentType.Application.Json)
        else -> TextContent(config.body, ContentType.Text.Plain)
    }

    /** 读取响应体直到 EOF，按 SSE 帧语法解析出事件与注释；每收到一个带 id 的帧回调一次 */
    private suspend fun parseStream(
        channel: io.ktor.utils.io.ByteReadChannel,
        config: SseConfig,
        onEventId: (String) -> Unit
    ) {
        var id: String? = null
        var event = ""
        val data = StringBuilder()

        fun flush() {
            id?.takeIf { it.isNotBlank() }?.let(onEventId)
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

        val idleMs = config.idleTimeoutSec * 1000L
        withContext(Dispatchers.IO) {
            while (true) {
                // 空闲超时：长时间没有任何帧（含注释心跳）就判定连接已僵死，主动断开交给外层重连
                val line = if (idleMs > 0) {
                    withTimeoutOrNull(idleMs) { channel.readUTF8Line() } ?: throw IdleTimeoutException(
                        "空闲超过 ${config.idleTimeoutSec}s 未收到数据"
                    )
                } else {
                    channel.readUTF8Line()
                } ?: break
                when {
                    line.isBlank() -> flush()
                    line.startsWith(":") -> _events.tryEmit(
                        SseEvent.Comment(line.substring(1), System.currentTimeMillis())
                    )
                    line.startsWith("id:") -> id = line.removePrefix("id:").trim().ifBlank { null }
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("retry:") -> {
                        // 服务端建议的重连间隔（毫秒），记录后由外层在下次重连时采用
                        line.removePrefix("retry:").trim().toLongOrNull()
                            ?.takeIf { it >= 0 }
                            ?.let { serverRetryMs = it }
                    }
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
        }
        flush()
    }

    /** 空闲超时触发的主动断开，用于和真正的网络异常区分提示 */
    private class IdleTimeoutException(message: String) : IOException(message)

    private fun clientFor(proxyConfig: ProxyConfig, tls: TlsConfig): HttpClient {
        val key = ClientKey(proxyConfig, tls)
        clients[key]?.let { return it }
        if (clients.size >= MAX_CACHED_CLIENTS) {
            clients.keys.firstOrNull()?.let { clients.remove(it) }
        }
        return clients.getOrPut(key) { buildClient(proxyConfig, tls) }
    }

    private fun buildClient(proxyConfig: ProxyConfig, tls: TlsConfig): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(0, TimeUnit.MILLISECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS)
                applyTls(tls)
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

/** Ktor 的 HttpMethod 本身接受任意字符串，自定义方法直接透传 */
private fun ktorMethod(method: HttpMethod): KtorHttpMethod = KtorHttpMethod(method.value)
