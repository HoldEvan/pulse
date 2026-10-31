package com.evan.pulse.domain.net

import com.evan.pulse.domain.mqtt.MqttPayloadText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttEndpointTest {

    @Test
    fun tcpDefaultsTo1883() {
        assertEquals(MqttEndpoint(false, "broker.local", 1883), MqttEndpoint.parse("tcp://broker.local"))
    }

    @Test
    fun sslDefaultsTo8883() {
        assertEquals(MqttEndpoint(true, "broker.local", 8883), MqttEndpoint.parse("ssl://broker.local"))
        assertEquals(MqttEndpoint(true, "broker.local", 8883), MqttEndpoint.parse("mqtts://broker.local"))
    }

    @Test
    fun explicitPortAndCredentialsAndPath() {
        assertEquals(
            MqttEndpoint(false, "10.0.0.2", 11883),
            MqttEndpoint.parse("  mqtt://user:pw@10.0.0.2:11883/ignored?x=1 ")
        )
    }

    @Test
    fun noSchemeTreatedAsPlain() {
        assertEquals(MqttEndpoint(false, "host_with_underscore", 1884), MqttEndpoint.parse("host_with_underscore:1884"))
    }

    @Test
    fun ipv6Literal() {
        assertEquals(MqttEndpoint(true, "::1", 8884), MqttEndpoint.parse("ssl://[::1]:8884"))
        assertEquals(MqttEndpoint(false, "::1", 1883), MqttEndpoint.parse("tcp://[::1]"))
    }

    @Test
    fun rejectsWebSocketAndUnknownSchemes() {
        assertThrows(IllegalArgumentException::class.java) { MqttEndpoint.parse("ws://broker:8083/mqtt") }
        assertThrows(IllegalArgumentException::class.java) { MqttEndpoint.parse("http://broker") }
    }

    @Test
    fun rejectsBlankHostAndBadPort() {
        assertThrows(IllegalArgumentException::class.java) { MqttEndpoint.parse("tcp://:1883") }
        assertThrows(IllegalArgumentException::class.java) { MqttEndpoint.parse("tcp://broker:70000") }
    }

    @Test
    fun payloadTextDetection() {
        assertEquals("", MqttPayloadText.textOrNull(ByteArray(0)))
        assertEquals("温度=21\n", MqttPayloadText.textOrNull("温度=21\n".toByteArray()))
        // 非法 UTF-8
        assertNull(MqttPayloadText.textOrNull(byteArrayOf(0xC3.toByte(), 0x28)))
        // 合法 UTF-8 但含 NUL 等控制字符
        assertTrue(MqttPayloadText.isBinary(byteArrayOf(0x01, 0x02, 0x41)))
        assertFalse(MqttPayloadText.isBinary("tab\tok".toByteArray()))
        assertEquals("0102FF", MqttPayloadText.hexPreview(byteArrayOf(1, 2, -1)))
    }
}
