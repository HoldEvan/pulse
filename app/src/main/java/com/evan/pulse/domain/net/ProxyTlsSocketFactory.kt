package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.ProxyConfig
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 让 `ssl://` 也能走代理的套接字工厂。
 *
 * Paho 对 TLS 地址要求工厂必须是 [SSLSocketFactory]，且会先调用无参 `createSocket()`
 * 再 `connect(目标地址)`，最后把返回的 Socket 强转为 [SSLSocket] 做握手。
 * 因此这里返回一个延迟建立的 [ProxyTlsSocket]：`connect()` 时先经代理打通隧道，
 * 再在隧道之上叠加真正的 TLS 套接字。
 */
class ProxyTlsSocketFactory(
    private val proxy: ProxyConfig,
    private val delegate: SSLSocketFactory,
    private val targetHost: String,
    private val targetPort: Int
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(): Socket = ProxyTlsSocket(proxy, delegate, targetHost, targetPort)

    override fun createSocket(host: String, port: Int): Socket =
        openTunnel(host, port).let { delegate.createSocket(it, host, port, true) }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket = createSocket(host, port)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket(host.hostAddress ?: host.hostName ?: targetHost, port)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = createSocket(address, port)

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        delegate.createSocket(socket, host, port, autoClose)

    private fun openTunnel(host: String, port: Int): Socket =
        ProxyTunnel.open(proxy, host, port, DEFAULT_TUNNEL_TIMEOUT_MS)

    private companion object {
        const val DEFAULT_TUNNEL_TIMEOUT_MS = 15_000
    }
}

/**
 * 延迟建立的 TLS 套接字：`connect()` 先经代理打通隧道再叠加 TLS，
 * 其余方法全部转发给内层套接字。Paho 只在 connect 之后才调用这些方法。
 */
internal class ProxyTlsSocket(
    private val proxy: ProxyConfig,
    private val delegate: SSLSocketFactory,
    private val targetHost: String,
    private val targetPort: Int
) : SSLSocket() {

    @Volatile private var inner: SSLSocket? = null

    private fun requireInner(): SSLSocket =
        inner ?: throw IOException("TLS 套接字尚未连接")

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        if (inner != null) return
        val address = endpoint as? InetSocketAddress
            ?: throw IOException("TLS 连接需要合法的目标地址")
        val host = address.hostString?.takeIf { it.isNotBlank() } ?: targetHost
        val port = if (address.port > 0) address.port else targetPort
        val tunnel = ProxyTunnel.open(proxy, host, port, timeout)
        val ssl = delegate.createSocket(tunnel, host, port, true)
        inner = ssl as? SSLSocket ?: throw IOException("TLS 套接字工厂返回了非 TLS 套接字")
    }

    override fun connect(endpoint: SocketAddress) = connect(endpoint, 0)

    override fun getInputStream(): InputStream = requireInner().inputStream
    override fun getOutputStream(): OutputStream = requireInner().outputStream
    override fun close() {
        runCatching { inner?.close() }
    }

    override fun setSoTimeout(timeout: Int) {
        inner?.soTimeout = timeout
    }

    override fun getSoTimeout(): Int = inner?.soTimeout ?: 0
    override fun isConnected(): Boolean = inner?.isConnected ?: false
    override fun isClosed(): Boolean = inner?.isClosed ?: false
    override fun getInetAddress(): InetAddress? = inner?.inetAddress
    override fun getPort(): Int = inner?.port ?: targetPort
    override fun getLocalPort(): Int = inner?.localPort ?: -1
    override fun getRemoteSocketAddress(): SocketAddress? = inner?.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress? = inner?.localSocketAddress
    override fun shutdownInput() = requireInner().shutdownInput()
    override fun shutdownOutput() = requireInner().shutdownOutput()
    override fun isInputShutdown(): Boolean = inner?.isInputShutdown ?: false
    override fun isOutputShutdown(): Boolean = inner?.isOutputShutdown ?: false
    override fun setTcpNoDelay(on: Boolean) {
        inner?.tcpNoDelay = on
    }

    override fun getTcpNoDelay(): Boolean = inner?.tcpNoDelay ?: false
    override fun setKeepAlive(on: Boolean) {
        inner?.keepAlive = on
    }

    override fun getKeepAlive(): Boolean = inner?.keepAlive ?: false
    override fun setSoLinger(on: Boolean, linger: Int) {
        inner?.setSoLinger(on, linger)
    }

    override fun getSoLinger(): Int = inner?.soLinger ?: -1
    override fun setReceiveBufferSize(size: Int) {
        inner?.receiveBufferSize = size
    }

    override fun getReceiveBufferSize(): Int = inner?.receiveBufferSize ?: 0
    override fun setSendBufferSize(size: Int) {
        inner?.sendBufferSize = size
    }

    override fun getSendBufferSize(): Int = inner?.sendBufferSize ?: 0
    override fun setTrafficClass(tc: Int) {
        inner?.trafficClass = tc
    }

    override fun getTrafficClass(): Int = inner?.trafficClass ?: 0
    override fun setReuseAddress(on: Boolean) {
        inner?.reuseAddress = on
    }

    override fun getReuseAddress(): Boolean = inner?.reuseAddress ?: false

    override fun toString(): String =
        "ProxyTlsSocket{proxy=${proxy.summary.ifEmpty { "direct" }}, target=$targetHost:$targetPort, connected=${inner != null}}"

    override fun getSupportedCipherSuites(): Array<String> = requireInner().supportedCipherSuites
    override fun getEnabledCipherSuites(): Array<String> = requireInner().enabledCipherSuites

    override fun setEnabledCipherSuites(suites: Array<out String>?) {
        requireInner().enabledCipherSuites = suites ?: emptyArray()
    }

    override fun getSupportedProtocols(): Array<String> = requireInner().supportedProtocols
    override fun getEnabledProtocols(): Array<String> = requireInner().enabledProtocols

    override fun setEnabledProtocols(protocols: Array<out String>?) {
        requireInner().enabledProtocols = protocols ?: emptyArray()
    }

    override fun getSession(): SSLSession = requireInner().session

    override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) {
        listener?.let { requireInner().addHandshakeCompletedListener(it) }
    }

    override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) {
        listener?.let { requireInner().removeHandshakeCompletedListener(it) }
    }

    override fun startHandshake() = requireInner().startHandshake()

    override fun setUseClientMode(mode: Boolean) {
        requireInner().useClientMode = mode
    }

    override fun getUseClientMode(): Boolean = requireInner().useClientMode

    override fun setNeedClientAuth(need: Boolean) {
        requireInner().needClientAuth = need
    }

    override fun getNeedClientAuth(): Boolean = requireInner().needClientAuth

    override fun setWantClientAuth(want: Boolean) {
        requireInner().wantClientAuth = want
    }

    override fun getWantClientAuth(): Boolean = requireInner().wantClientAuth

    override fun setEnableSessionCreation(flag: Boolean) {
        requireInner().enableSessionCreation = flag
    }

    override fun getEnableSessionCreation(): Boolean = requireInner().enableSessionCreation

    override fun getSSLParameters(): SSLParameters = requireInner().sslParameters

    override fun setSSLParameters(parameters: SSLParameters) {
        requireInner().sslParameters = parameters
    }
}
