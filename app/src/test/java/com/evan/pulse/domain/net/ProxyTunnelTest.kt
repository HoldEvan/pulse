package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 用本机临时 ServerSocket 冒充 HTTP 代理，验证 CONNECT 请求与失败分支，
 * 不访问外网，也不依赖真实 Broker。
 */
class ProxyTunnelTest {

    private fun fakeProxy(
        statusLine: String,
        requestLine: AtomicReference<String>,
        authHeader: AtomicReference<String>
    ): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    requestLine.set(reader.readLine())
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Proxy-Authorization:")) authHeader.set(line)
                    }
                    client.getOutputStream().write("$statusLine\r\n\r\n".toByteArray())
                    client.getOutputStream().flush()
                    Thread.sleep(200)
                }
            }
        }
        return server
    }

    private fun proxyConfig(server: ServerSocket) = ProxyConfig(
        type = ProxyType.HTTP,
        host = "127.0.0.1",
        port = server.localPort,
        username = "user",
        password = "pass"
    )

    @Test
    fun `http connect tunnel sends target and basic auth then returns a connected socket`() {
        val requestLine = AtomicReference("")
        val authHeader = AtomicReference("")
        val server = fakeProxy("HTTP/1.1 200 Connection Established", requestLine, authHeader)
        val socket = ProxyTunnel.open(proxyConfig(server), "broker.test", 8883, 3000)
        try {
            assertTrue(socket.isConnected)
            assertEquals("CONNECT broker.test:8883 HTTP/1.1", requestLine.get())
            assertTrue(authHeader.get().startsWith("Proxy-Authorization: Basic "))
        } finally {
            socket.close()
            server.close()
        }
    }

    @Test
    fun `proxy rejection closes the tunnel and reports the status line`() {
        val requestLine = AtomicReference("")
        val authHeader = AtomicReference("")
        val server = fakeProxy("HTTP/1.1 407 Proxy Authentication Required", requestLine, authHeader)
        try {
            ProxyTunnel.open(proxyConfig(server), "broker.test", 8883, 3000)
            fail("代理拒绝时应当抛出 IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("CONNECT 失败"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `tls socket layers over the tunnel when connect is called`() {
        val requestLine = AtomicReference("")
        val authHeader = AtomicReference("")
        val server = fakeProxy("HTTP/1.1 200 Connection Established", requestLine, authHeader)
        val socket = ProxyTlsSocketFactory(
            proxyConfig(server),
            MqttTls.socketFactory(true),
            "broker.test",
            8883
        ).createSocket()
        try {
            socket.connect(InetSocketAddress.createUnresolved("broker.test", 8883), 3000)
            assertTrue(socket.isConnected)
            assertEquals("CONNECT broker.test:8883 HTTP/1.1", requestLine.get())
        } finally {
            socket.close()
            server.close()
        }
    }
}
