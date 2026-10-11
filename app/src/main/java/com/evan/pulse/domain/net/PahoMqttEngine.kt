package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.MqttConfig
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * 真实 MQTT 引擎：基于 Eclipse Paho v3 异步客户端。
 *
 * 直连时走 Paho 自带网络模块（tcp:// 与 ssl:// 都支持）；
 * 走 HTTP / SOCKS5 代理时，Paho 把建立底层连接的职责交给自定义 SocketFactory，
 * 由 [ProxySocketFactory] 先连到代理做 CONNECT / SOCKS5 握手，再复用同一连接与 Broker 通信。
 * ssl:// 走代理时 Paho 要求工厂必须是 SSLSocketFactory，本实现暂不支持，直接报错提示。
 */
class PahoMqttEngine : MqttEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val _state = MutableStateFlow(MqttConnectionState.IDLE)
    override val state: StateFlow<MqttConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    @Volatile private var client: MqttAsyncClient? = null

    override suspend fun connect(config: MqttConfig) {
        lock.withLock {
            closeClient()
            _state.value = MqttConnectionState.CONNECTING
            val start = System.currentTimeMillis()
            try {
                if (config.proxy.enabled && config.brokerUrl.startsWith("ssl://", ignoreCase = true)) {
                    throw IOException("ssl:// 地址暂不支持走代理，请直连或改用 tcp://")
                }
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    keepAliveInterval = config.keepAliveSec
                    connectionTimeout = config.connectTimeoutSec
                    config.username?.let { userName = it }
                    config.password?.let { setPassword(it.toCharArray()) }
                    config.proxy.takeIf { it.enabled }?.let {
                        socketFactory = ProxySocketFactory(it)
                    }
                }
                val asyncClient = MqttAsyncClient(
                    config.brokerUrl,
                    config.clientId.ifBlank { "pulse-android" },
                    MemoryPersistence()
                )
                client = asyncClient
                asyncClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        _state.value = MqttConnectionState.DISCONNECTED
                        _events.tryEmit(
                            MqttEvent.Error(
                                cause?.message ?: "连接已断开",
                                System.currentTimeMillis()
                            )
                        )
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val text = message?.payload?.let { String(it, StandardCharsets.UTF_8) }
                            ?: ""
                        _events.tryEmit(
                            MqttEvent.MessageReceived(
                                topic.orEmpty(),
                                text,
                                message?.qos ?: 0,
                                System.currentTimeMillis()
                            )
                        )
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
                })
                withContext(Dispatchers.IO) {
                    asyncClient.connect(options).waitForCompletion()
                }
                _state.value = MqttConnectionState.CONNECTED
                _events.tryEmit(
                    MqttEvent.Connected(
                        latencyMs = System.currentTimeMillis() - start,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                closeClient()
                _state.value = MqttConnectionState.FAILED
                _events.tryEmit(
                    MqttEvent.Error(
                        e.message?.trim().orEmpty().ifEmpty { e::class.simpleName ?: "连接失败" },
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun subscribe(topic: String, qos: Int) {
        val c = client ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                c.subscribe(topic, qos).waitForCompletion()
            }
        }.onFailure { e ->
            _events.tryEmit(MqttEvent.Error(e.message ?: "订阅失败", System.currentTimeMillis()))
        }
    }

    override suspend fun publish(topic: String, payload: String, qos: Int) {
        val c = client ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                c.publish(topic, payload.toByteArray(StandardCharsets.UTF_8), qos, false)
                    .waitForCompletion()
            }
        }.onFailure { e ->
            _events.tryEmit(MqttEvent.Error(e.message ?: "发布失败", System.currentTimeMillis()))
        }
    }

    override suspend fun disconnect() {
        lock.withLock {
            val c = client ?: run {
                _state.value = MqttConnectionState.DISCONNECTED
                return@withLock
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    c.disconnect(2_000).waitForCompletion()
                }
            }
            closeClient()
            _state.value = MqttConnectionState.DISCONNECTED
            _events.tryEmit(MqttEvent.Disconnected("用户断开", System.currentTimeMillis()))
        }
    }

    private fun closeClient() {
        val c = client ?: return
        client = null
        runCatching { c.close() }
    }
}

/**
 * 把 [ProxyConfig] 落地成 Paho 可用的 SocketFactory。
 *
 * Paho 的 TCP 网络模块会调用无参 createSocket() 拿到裸 Socket，再手动 connect 到 Broker，
 * 因此这里返回一个 [ProxySocket]：它的 connect() 先连到代理，完成 HTTP CONNECT 或
 * SOCKS5 握手后再复用同一 Socket 与目标通信。
 */
class ProxySocketFactory(
    private val proxy: ProxyConfig
) : SocketFactory() {

    override fun createSocket(host: String, port: Int): Socket = ProxySocket(host, port, proxy)

    override fun createSocket(
        host: String,
        port: Int,
        localHost: java.net.InetAddress,
        localPort: Int
    ): Socket = ProxySocket(host, port, proxy)

    override fun createSocket(): Socket = ProxySocket("", 0, proxy)

    override fun createSocket(
        address: java.net.InetAddress,
        port: Int
    ): Socket = ProxySocket(address.hostAddress ?: address.hostName ?: "", port, proxy)

    override fun createSocket(
        address: java.net.InetAddress,
        port: Int,
        localAddress: java.net.InetAddress,
        localPort: Int
    ): Socket = ProxySocket(address.hostAddress ?: address.hostName ?: "", port, proxy)
}

/**
 * 带代理隧道能力的 Socket：connect() 先连代理再握手。
 *
 * Paho 对 tcp:// 的调用路径是无参 createSocket() + socket.connect(目标地址, 超时)，
 * 因此目标 host/port 只有在 connect 阶段才拿得到，隧道握手只能在这里完成。
 */
private class ProxySocket(
    private val targetHost: String,
    private val targetPort: Int,
    private val proxy: ProxyConfig
) : Socket() {

    @Volatile private var tunneled = false

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        if (tunneled) return
        if (endpoint !is InetSocketAddress || endpoint.address == null) {
            throw IOException("代理隧道需要合法的目标地址")
        }
        val host = endpoint.hostName ?: endpoint.address.hostAddress
        val port = endpoint.port
        val proxyAddress = InetSocketAddress(proxy.host.trim(), proxy.port)
        super.connect(proxyAddress, timeout)
        try {
            handshake(host, port, timeout)
            tunneled = true
        } catch (e: Throwable) {
            runCatching { super.close() }
            throw e
        }
    }

    private fun handshake(host: String, port: Int, timeout: Int) {
        if (timeout > 0) soTimeout = timeout
        when (proxy.type) {
            ProxyType.SOCKS5 -> socks5Handshake(host, port)
            else -> httpConnect(host, port)
        }
    }

    /** HTTP CONNECT：请求代理建立到目标的隧道，可选 Proxy-Authorization 认证 */
    private fun httpConnect(host: String, port: Int) {
        val out = getOutputStream()
        val req = buildString {
            append("CONNECT $host:$port HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Proxy-Connection: Keep-Alive\r\n")
            proxy.username?.takeIf { it.isNotBlank() }?.let { user ->
                val pass = proxy.password.orEmpty()
                val token = java.util.Base64.getEncoder()
                    .encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
                append("Proxy-Authorization: Basic $token\r\n")
            }
            append("\r\n")
        }
        out.write(req.toByteArray(StandardCharsets.UTF_8))
        out.flush()
        val input = BufferedInputStream(getInputStream())
        val statusLine = readLine(input)
        val parts = statusLine.split(" ")
        if (parts.size < 2 || parts[1].toIntOrNull() != 200) {
            throw IOException("HTTP 代理 CONNECT 失败: ${statusLine.trim()}")
        }
        // 读完代理返回的剩余响应头，直到空行
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
        }
    }

    /** SOCKS5 握手：协商认证并建立到目标的连接 */
    private fun socks5Handshake(host: String, port: Int) {
        val out: OutputStream = getOutputStream()
        val input = BufferedInputStream(getInputStream())
        val authEnabled = !proxy.username.isNullOrBlank()
        // 协商：无认证(0x00) 或 用户名密码(0x02)
        out.write(byteArrayOf(0x05, if (authEnabled) 2 else 1, 0x00, 0x02))
        out.flush()
        val methodReply = readN(input, 2)
        if (methodReply[0] != 0x05.toByte()) throw IOException("SOCKS5 协议版本错误")
        when (methodReply[1]) {
            0x00.toByte() -> Unit
            0x02.toByte() -> {
                val user = proxy.username.orEmpty().toByteArray(StandardCharsets.UTF_8)
                val pass = proxy.password.orEmpty().toByteArray(StandardCharsets.UTF_8)
                out.write(byteArrayOf(0x01, user.size.toByte()))
                out.write(user)
                out.write(byteArrayOf(pass.size.toByte()))
                out.write(pass)
                out.flush()
                val auth = readN(input, 2)
                if (auth[1] != 0x00.toByte()) throw IOException("SOCKS5 代理认证失败")
            }
            else -> throw IOException("SOCKS5 代理不支持所选认证方式")
        }
        // CONNECT 请求：0x05 0x01 0x00 + 域名地址
        val hostBytes = host.toByteArray(StandardCharsets.UTF_8)
        out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        out.write(hostBytes)
        out.write(byteArrayOf(((port shr 8) and 0xFF).toByte(), (port and 0xFF).toByte()))
        out.flush()
        val connectReply = readN(input, 4)
        if (connectReply[0] != 0x05.toByte() || connectReply[1] != 0x00.toByte()) {
            throw IOException("SOCKS5 代理 CONNECT 失败")
        }
        // 跳过 BND.ADDR + BND.PORT
        when (connectReply[3]) {
            0x01.toByte() -> readN(input, 4)
            0x03.toByte() -> readN(input, readN(input, 1)[0].toInt() and 0xFF)
            0x04.toByte() -> readN(input, 16)
        }
        readN(input, 2)
    }

    private fun readLine(input: BufferedInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("代理响应读取失败")
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private fun readN(input: BufferedInputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read < 0) throw IOException("代理响应不完整")
            offset += read
        }
        return buf
    }

    override fun toString(): String =
        "ProxySocket{proxy=${proxy.summary.ifEmpty { "direct" }}, target=$targetHost:$targetPort, tunneled=$tunneled}"
}
