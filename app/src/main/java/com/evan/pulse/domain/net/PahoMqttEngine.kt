package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.MqttConfig
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.domain.model.ProxyConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * 真实 MQTT 引擎：基于 Eclipse Paho v3 异步客户端。
 *
 * 直连时走 Paho 自带网络模块（tcp:// 与 ssl:// 都支持）；
 * 走 HTTP / SOCKS5 代理时，Paho 把建立底层连接的职责交给自定义 SocketFactory，
 * 由 [ProxySocketFactory] 先连到代理做 CONNECT / SOCKS5 握手，再复用同一连接与 Broker 通信。
 * ssl:// 走代理时 Paho 要求工厂必须是 SSLSocketFactory，改由 [ProxyTlsSocketFactory]
 * 在代理隧道之上叠加 TLS；跳过证书校验同时关闭 Paho 的主机名校验。
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
                val secure = MqttTls.isSecure(config.brokerUrl)
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    keepAliveInterval = config.keepAliveSec
                    connectionTimeout = config.connectTimeoutSec
                    config.username?.let { userName = it }
                    config.password?.let { setPassword(it.toCharArray()) }
                    if (secure) {
                        // ssl:// 只认 SSLSocketFactory：走代理时在隧道之上叠加 TLS，直连时按校验策略给工厂
                        val tlsConfig = config.effectiveTls
                        if (tlsConfig.insecure) {
                            // 跳过证书校验时同时关闭主机名校验，否则自签名证书仍会被拒
                            setHttpsHostnameVerificationEnabled(false)
                            setSSLHostnameVerifier(javax.net.ssl.HostnameVerifier { _, _ -> true })
                        }
                        val tlsFactory = TlsContexts.build(tlsConfig).socketFactory
                        socketFactory = if (config.proxy.enabled) {
                            val endpoint = MqttTls.endpoint(config.brokerUrl)
                                ?: throw IOException("无法解析 Broker 地址: ${config.brokerUrl}")
                            ProxyTlsSocketFactory(config.proxy, tlsFactory, endpoint.first, endpoint.second)
                        } else {
                            tlsFactory
                        }
                    } else {
                        config.proxy.takeIf { it.enabled }?.let {
                            socketFactory = ProxySocketFactory(it)
                        }
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
                                System.currentTimeMillis(),
                                message?.isRetained ?: false
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

    override suspend fun publish(topic: String, payload: String, qos: Int, retain: Boolean) {
        val c = client ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                c.publish(topic, payload.toByteArray(StandardCharsets.UTF_8), qos, retain)
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
 * 握手细节复用 [ProxyTunnel]，与 ssl:// 走代理的路径共用同一份实现。
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
        super.connect(InetSocketAddress(proxy.host.trim(), proxy.port), timeout)
        try {
            ProxyTunnel.handshake(this, proxy, host, port, timeout)
            tunneled = true
        } catch (e: Throwable) {
            runCatching { super.close() }
            throw e
        }
    }

    override fun toString(): String =
        "ProxySocket{proxy=${proxy.summary.ifEmpty { "direct" }}, target=$targetHost:$targetPort, tunneled=$tunneled}"
}
