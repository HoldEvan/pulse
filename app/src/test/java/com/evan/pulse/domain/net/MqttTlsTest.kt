package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttTlsTest {

    @Test
    fun `ssl scheme is recognised case insensitively`() {
        assertTrue(MqttTls.isSecure("ssl://broker.test:8883"))
        assertTrue(MqttTls.isSecure("SSL://broker.test:8883"))
    }

    @Test
    fun `tcp and ws schemes stay plaintext even on 8883`() {
        assertFalse(MqttTls.isSecure("tcp://broker.test:8883"))
        assertFalse(MqttTls.isSecure("ws://broker.test:8883"))
        assertFalse(MqttTls.isSecure("broker.test:8883"))
    }

    @Test
    fun `scheme extraction handles missing scheme`() {
        assertEquals("ssl", MqttTls.schemeOf("ssl://host"))
        assertEquals("", MqttTls.schemeOf("host:1883"))
    }

    @Test
    fun `endpoint parses host and explicit port`() {
        assertEquals("broker.test" to 8884, MqttTls.endpoint("ssl://broker.test:8884"))
    }

    @Test
    fun `endpoint falls back to the default secure port`() {
        assertEquals("broker.test" to 8883, MqttTls.endpoint("ssl://broker.test"))
    }

    @Test
    fun `endpoint strips user info and path`() {
        assertEquals("broker.test" to 8883, MqttTls.endpoint("ssl://user:pass@broker.test/mqtt"))
    }

    @Test
    fun `endpoint returns null for unusable input`() {
        assertNull(MqttTls.endpoint(""))
        assertNull(MqttTls.endpoint("   "))
    }

    @Test
    fun `insecure factory differs from the default one`() {
        val secure = MqttTls.socketFactory(false)
        val insecure = MqttTls.socketFactory(true)

        assertTrue(secure !== insecure)
        assertTrue(insecure === InsecureTls.insecureSocketFactory)
    }

    @Test
    fun `proxy tls factory reports the delegate cipher suites`() {
        val delegate = MqttTls.socketFactory(false)
        val factory = ProxyTlsSocketFactory(
            ProxyConfig(ProxyType.HTTP, "127.0.0.1", 8080),
            delegate,
            "broker.test",
            8883
        )

        assertEquals(delegate.defaultCipherSuites.toList(), factory.defaultCipherSuites.toList())
        assertEquals(delegate.supportedCipherSuites.toList(), factory.supportedCipherSuites.toList())
    }

    @Test
    fun `proxy tls socket is not connected before connect`() {
        val socket = ProxyTlsSocketFactory(
            ProxyConfig(ProxyType.SOCKS5, "127.0.0.1", 1080),
            MqttTls.socketFactory(true),
            "broker.test",
            8883
        ).createSocket()

        assertFalse(socket.isConnected)
        assertEquals(8883, socket.port)
        assertTrue(socket.toString().contains("broker.test:8883"))
    }
}
