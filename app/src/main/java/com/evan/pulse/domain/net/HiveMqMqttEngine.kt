package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.MqttConfig
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.domain.model.MqttWill
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttClientTransportConfig
import com.hivemq.client.mqtt.MqttProxyConfig
import com.hivemq.client.mqtt.MqttProxyProtocol
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 基于 HiveMQ MQTT Client 的引擎，同时支持 MQTT 5.0 与 3.1.1。
 *
 * - 代理：HiveMQ 传输层原生支持 HTTP CONNECT / SOCKS5，无需自定义 SocketFactory。
 * - TLS：复用 [TlsContexts] 的信任/密钥策略，跳过校验时同时放行主机名。
 * - 自动重连：交给 HiveMQ 的指数退避重连器；会话失效时由重连器自动重新订阅，
 *   订阅回调保持不变，因此重连后消息照常进入 [events]。
 * - 首次连接失败时停止重连，避免用户看到「连接失败」后后台仍在不停重试。
 */
class HiveMqMqttEngine : MqttEngine {

    private val lock = Mutex()

    private val _state = MutableStateFlow(MqttConnectionState.IDLE)
    override val state: StateFlow<MqttConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    @Volatile private var session: Session? = null

    /** 每次 connect 自增，旧客户端的生命周期回调据此识别并丢弃 */
    private val generation = AtomicInteger(0)

    override suspend fun connect(config: MqttConfig) {
        lock.withLock {
            closeSession()
            _state.value = MqttConnectionState.CONNECTING
            val gen = generation.incrementAndGet()
            val start = System.currentTimeMillis()
            var created: Session? = null
            try {
                val endpoint = MqttEndpoint.parse(config.brokerUrl)
                created = buildSession(config, endpoint, gen)
                session = created
                withTimeout((config.connectTimeoutSec.coerceAtLeast(1) + 5) * 1_000L) {
                    created.connect().await()
                }
                created.everConnected = true
                _state.value = MqttConnectionState.CONNECTED
                _events.tryEmit(
                    MqttEvent.Connected(
                        latencyMs = System.currentTimeMillis() - start,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Throwable) {
                // 失败的客户端可能已进入重连循环，必须主动断开
                created?.let { runCatching { it.disconnect() } }
                if (session === created) session = null
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                _state.value = MqttConnectionState.FAILED
                _events.tryEmit(MqttEvent.Error(describe(e, "连接失败"), System.currentTimeMillis()))
            }
        }
    }

    override suspend fun subscribe(topic: String, qos: Int) {
        val s = session ?: return
        runCatching {
            // HiveMQ 每次 subscribe 都挂一个新回调；同一 filter 重复订阅（如切换 QoS）
            // 会导致每条消息投递两次，因此先撤掉旧订阅再订阅
            if (!s.activeTopics.add(topic)) s.unsubscribe(topic).await()
            s.subscribe(topic, qos).await()
        }.onFailure { e ->
            s.activeTopics.remove(topic)
            _events.tryEmit(MqttEvent.Error(describe(e, "订阅失败"), System.currentTimeMillis()))
        }
    }

    override suspend fun unsubscribe(topic: String) {
        val s = session ?: return
        s.activeTopics.remove(topic)
        runCatching { s.unsubscribe(topic).await() }.onFailure { e ->
            _events.tryEmit(MqttEvent.Error(describe(e, "取消订阅失败"), System.currentTimeMillis()))
        }
    }

    override suspend fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {
        val s = session ?: return
        runCatching { s.publish(topic, payload, qos, retain).await() }.onFailure { e ->
            _events.tryEmit(MqttEvent.Error(describe(e, "发布失败"), System.currentTimeMillis()))
        }
    }

    override suspend fun disconnect() {
        lock.withLock {
            val had = session != null
            closeSession()
            _state.value = MqttConnectionState.DISCONNECTED
            if (had) _events.tryEmit(MqttEvent.Disconnected("用户断开", System.currentTimeMillis()))
        }
    }

    private suspend fun closeSession() {
        val s = session ?: return
        session = null
        // 先让旧代际失效，断开过程中的回调不再改写状态
        generation.incrementAndGet()
        runCatching { withTimeout(3_000) { s.disconnect().await() } }
    }

    private fun buildSession(config: MqttConfig, endpoint: MqttEndpoint, gen: Int): Session {
        val transport = MqttClientTransportConfig.builder()
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .socketConnectTimeout(config.connectTimeoutSec.toLong(), TimeUnit.SECONDS)
            .mqttConnectTimeout(config.connectTimeoutSec.toLong(), TimeUnit.SECONDS)
            .let { if (config.proxy.enabled) it.proxyConfig(proxyConfig(config.proxy)) else it }
            .let { if (endpoint.secure) it.sslConfig(sslConfig(config)) else it }
            .build()

        var builder = MqttClient.builder()
            .identifier(config.clientId.ifBlank { "pulse-android" })
            .transportConfig(transport)
            .addDisconnectedListener { ctx -> onDisconnected(ctx, gen) }
            .addConnectedListener { onReconnected(gen) }
        if (config.autoReconnect) {
            builder = builder.automaticReconnect(
                MqttClientAutoReconnect.builder()
                    .initialDelay(1, TimeUnit.SECONDS)
                    .maxDelay(30, TimeUnit.SECONDS)
                    .build()
            )
        }
        return if (config.mqttVersion == 3) {
            Mqtt3Session(builder.useMqttVersion3().buildAsync(), config, ::onMessage)
        } else {
            Mqtt5Session(builder.useMqttVersion5().buildAsync(), config, ::onMessage)
        }
    }

    private fun onMessage(topic: String, bytes: ByteArray, qos: Int, retained: Boolean) {
        _events.tryEmit(
            MqttEvent.MessageReceived(
                topic = topic,
                payload = String(bytes, Charsets.UTF_8),
                qos = qos,
                timestamp = System.currentTimeMillis(),
                retained = retained,
                payloadBytes = bytes
            )
        )
    }

    private fun onDisconnected(ctx: MqttClientDisconnectedContext, gen: Int) {
        if (gen != generation.get()) return
        val s = session ?: return
        if (ctx.source == MqttDisconnectSource.USER) return
        // 首次连接就失败：交由 connect() 报错，停止重连器
        if (!s.everConnected) {
            ctx.reconnector.reconnect(false)
            return
        }
        val reason = describe(ctx.cause, "连接已断开")
        if (ctx.reconnector.isReconnect) {
            _state.value = MqttConnectionState.CONNECTING
            _events.tryEmit(
                MqttEvent.Error("$reason，${ctx.reconnector.attempts + 1} 次重连中…", System.currentTimeMillis())
            )
        } else {
            _state.value = MqttConnectionState.DISCONNECTED
            _events.tryEmit(MqttEvent.Error(reason, System.currentTimeMillis()))
        }
    }

    /** 首次连接由 connect() 负责上报；这里只处理自动重连成功 */
    private fun onReconnected(gen: Int) {
        if (gen != generation.get()) return
        val s = session ?: return
        if (!s.everConnected) return
        _state.value = MqttConnectionState.CONNECTED
        _events.tryEmit(MqttEvent.Connected(latencyMs = 0, timestamp = System.currentTimeMillis()))
    }

    private fun proxyConfig(proxy: ProxyConfig): MqttProxyConfig =
        MqttProxyConfig.builder()
            .protocol(if (proxy.type == ProxyType.SOCKS5) MqttProxyProtocol.SOCKS_5 else MqttProxyProtocol.HTTP)
            .host(proxy.host.trim())
            .port(proxy.port)
            .let { b -> proxy.username?.takeIf { it.isNotBlank() }?.let { b.username(it) } ?: b }
            .let { b -> proxy.password?.takeIf { it.isNotEmpty() }?.let { b.password(it) } ?: b }
            .build()

    private fun sslConfig(config: MqttConfig): MqttClientSslConfig {
        val tls = config.effectiveTls
        return MqttClientSslConfig.builder()
            .trustManagerFactory(TlsContexts.trustManagerFactory(tls))
            .let { b -> TlsContexts.keyManagerFactory(tls)?.let { b.keyManagerFactory(it) } ?: b }
            .let { b -> if (tls.insecure) b.hostnameVerifier(HostnameVerifier { _, _ -> true }) else b }
            .build()
    }

    private fun describe(e: Throwable?, fallback: String): String {
        if (e is kotlinx.coroutines.TimeoutCancellationException) return "连接超时"
        var cur = e
        // HiveMQ 常把真正原因包在 CompletionException / ConnectionFailedException 里
        while (cur != null && cur.cause != null && cur.cause !== cur &&
            (cur is java.util.concurrent.CompletionException || cur.message.isNullOrBlank())
        ) cur = cur.cause
        return cur?.message?.trim().orEmpty().ifEmpty { cur?.let { it::class.simpleName } ?: fallback }
    }
}

/** Broker 地址解析结果：tcp/mqtt 默认 1883，ssl/mqtts/tls 默认 8883 */
internal data class MqttEndpoint(val secure: Boolean, val host: String, val port: Int) {
    companion object {
        private val SECURE = setOf("ssl", "mqtts", "tls")
        private val PLAIN = setOf("tcp", "mqtt", "")

        fun parse(brokerUrl: String): MqttEndpoint {
            val trimmed = brokerUrl.trim()
            val scheme = if (trimmed.contains("://")) trimmed.substringBefore("://").lowercase() else ""
            if (scheme == "ws" || scheme == "wss") {
                throw IllegalArgumentException("暂不支持 MQTT over WebSocket，请使用 tcp:// 或 ssl://")
            }
            require(scheme in SECURE || scheme in PLAIN) { "不支持的协议: $scheme://" }
            val secure = scheme in SECURE
            val authority = trimmed.substringAfter("://").substringBefore('/').substringBefore('?')
                .substringAfterLast('@')
            val (host, port) = if (authority.startsWith("[")) {
                // IPv6 字面量：[::1]:1883
                val h = authority.substringAfter('[').substringBefore(']')
                h to authority.substringAfter("]:", "").toIntOrNull()
            } else {
                authority.substringBeforeLast(':', authority) to
                    authority.substringAfterLast(':', "").takeIf { authority.contains(':') }?.toIntOrNull()
            }
            require(host.isNotBlank()) { "无法解析 Broker 地址: $brokerUrl" }
            val resolvedPort = port ?: if (secure) 8883 else 1883
            require(resolvedPort in 1..65535) { "端口无效: $resolvedPort" }
            return MqttEndpoint(secure, host.trim(), resolvedPort)
        }
    }
}

private typealias MessageSink = (topic: String, bytes: ByteArray, qos: Int, retained: Boolean) -> Unit

/** 抹平 MQTT 3 / 5 客户端差异的最小会话接口 */
private abstract class Session {
    @Volatile var everConnected = false

    /** 已挂回调的订阅 filter；自动重连沿用同一客户端，重连后仍然有效 */
    val activeTopics: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    abstract fun connect(): CompletableFuture<*>
    abstract fun subscribe(topic: String, qos: Int): CompletableFuture<*>
    abstract fun unsubscribe(topic: String): CompletableFuture<*>
    abstract fun publish(topic: String, bytes: ByteArray, qos: Int, retain: Boolean): CompletableFuture<*>
    abstract fun disconnect(): CompletableFuture<*>
}

private fun qosOf(value: Int): MqttQos = MqttQos.fromCode(value.coerceIn(0, 2)) ?: MqttQos.AT_MOST_ONCE

private class Mqtt5Session(
    private val client: Mqtt5AsyncClient,
    private val config: MqttConfig,
    private val sink: MessageSink
) : Session() {

    override fun connect(): CompletableFuture<*> {
        val builder = Mqtt5Connect.builder()
            .keepAlive(config.keepAliveSec.coerceIn(0, 65535))
            .cleanStart(config.cleanStart)
            .sessionExpiryInterval(config.sessionExpirySec.coerceIn(0, 0xFFFF_FFFFL))
        val auth = simpleAuth()
        val withAuth = if (auth != null) builder.simpleAuth(auth) else builder
        val will = config.will?.takeIf { it.topic.isNotBlank() }
        val withWill = if (will != null) withAuth.willPublish(willPublish(will)) else withAuth
        return client.connect(withWill.build())
    }

    private fun simpleAuth(): Mqtt5SimpleAuth? {
        val user = config.username?.takeIf { it.isNotEmpty() }
        val pass = config.password?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        return when {
            user != null && pass != null -> Mqtt5SimpleAuth.builder().username(user).password(pass).build()
            user != null -> Mqtt5SimpleAuth.builder().username(user).build()
            pass != null -> Mqtt5SimpleAuth.builder().password(pass).build()
            else -> null
        }
    }

    private fun willPublish(will: MqttWill): Mqtt5Publish =
        Mqtt5Publish.builder()
            .topic(will.topic.trim())
            .payload(will.payload.toByteArray(Charsets.UTF_8))
            .qos(qosOf(will.qos))
            .retain(will.retain)
            .build()

    override fun subscribe(topic: String, qos: Int): CompletableFuture<*> =
        client.subscribe(
            Mqtt5Subscribe.builder().topicFilter(topic).qos(qosOf(qos)).build()
        ) { p -> sink(p.topic.toString(), p.payloadAsBytes, p.qos.code, p.isRetain) }

    override fun unsubscribe(topic: String): CompletableFuture<*> =
        client.unsubscribe(Mqtt5Unsubscribe.builder().topicFilter(topic).build())

    override fun publish(topic: String, bytes: ByteArray, qos: Int, retain: Boolean): CompletableFuture<*> =
        client.publish(
            Mqtt5Publish.builder().topic(topic).payload(bytes).qos(qosOf(qos)).retain(retain).build()
        )

    override fun disconnect(): CompletableFuture<*> = client.disconnect()
}

private class Mqtt3Session(
    private val client: Mqtt3AsyncClient,
    private val config: MqttConfig,
    private val sink: MessageSink
) : Session() {

    override fun connect(): CompletableFuture<*> {
        val builder = Mqtt3Connect.builder()
            .keepAlive(config.keepAliveSec.coerceIn(0, 65535))
            .cleanSession(config.cleanStart)
        // MQTT 3.1.1 规定密码必须伴随用户名
        val user = config.username?.takeIf { it.isNotEmpty() }
        val withAuth = if (user != null) {
            val pass = config.password?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            val auth: Mqtt3SimpleAuth = Mqtt3SimpleAuth.builder().username(user)
                .let { if (pass != null) it.password(pass) else it }
                .build()
            builder.simpleAuth(auth)
        } else builder
        val will = config.will?.takeIf { it.topic.isNotBlank() }
        val withWill = if (will != null) {
            withAuth.willPublish(
                Mqtt3Publish.builder()
                    .topic(will.topic.trim())
                    .payload(will.payload.toByteArray(Charsets.UTF_8))
                    .qos(qosOf(will.qos))
                    .retain(will.retain)
                    .build()
            )
        } else withAuth
        return client.connect(withWill.build())
    }

    override fun subscribe(topic: String, qos: Int): CompletableFuture<*> =
        client.subscribe(
            Mqtt3Subscribe.builder().topicFilter(topic).qos(qosOf(qos)).build()
        ) { p -> sink(p.topic.toString(), p.payloadAsBytes, p.qos.code, p.isRetain) }

    override fun unsubscribe(topic: String): CompletableFuture<*> =
        client.unsubscribe(Mqtt3Unsubscribe.builder().topicFilter(topic).build())

    override fun publish(topic: String, bytes: ByteArray, qos: Int, retain: Boolean): CompletableFuture<*> =
        client.publish(
            Mqtt3Publish.builder().topic(topic).payload(bytes).qos(qosOf(qos)).retain(retain).build()
        )

    override fun disconnect(): CompletableFuture<*> = client.disconnect()
}
