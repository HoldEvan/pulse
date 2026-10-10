package com.evan.pulse.domain

import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.MqttSnapshot
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.SseSnapshot
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.data.model.targetUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlPartsTest {

    @Test
    fun pathOnlyExtractsPathFromHttpUrl() {
        assertEquals(
            "/api/v3/wechat/getOpenid",
            "http://uat.app.lwjj888.com/api/v3/wechat/getOpenid".pathOnly()
        )
    }

    @Test
    fun pathOnlySupportsNonHttpSchemes() {
        assertEquals("/ws/chat", "wss://example.com/ws/chat".pathOnly())
        assertEquals("/topic", "tcp://broker.example.com:1883/topic".pathOnly())
    }

    @Test
    fun pathOnlyReturnsEmptyWhenThereIsNoPath() {
        assertEquals("", "http://host".pathOnly())
        assertEquals("", "http://host/".pathOnly())
        assertEquals("", "tcp://broker.example.com:1883".pathOnly())
    }

    @Test
    fun pathOnlySurvivesUnparseableAddresses() {
        assertEquals("", "tcp://{{mqttHost}}:1883".pathOnly())
        assertEquals("", "{{mqttHost}}/x".pathOnly())
        assertEquals("", "".pathOnly())
    }

    @Test
    fun savedRequestTargetUrlReadsTheProtocolField() {
        val now = 1L
        val http = SavedRequest(1, 1, RequestType.HTTP, "登录",
            snapshotJson.encodeToString(HttpSnapshot.serializer(), HttpSnapshot(url = "http://h/p")), now)
        val ws = SavedRequest(2, 1, RequestType.WS, "聊天",
            snapshotJson.encodeToString(WsSnapshot.serializer(), WsSnapshot(url = "wss://h/ws")), now)
        val sse = SavedRequest(3, 1, RequestType.SSE, "推送",
            snapshotJson.encodeToString(SseSnapshot.serializer(), SseSnapshot(url = "https://h/feed")), now)

        assertEquals("http://h/p", http.targetUrl())
        assertEquals("wss://h/ws", ws.targetUrl())
        assertEquals("https://h/feed", sse.targetUrl())
    }

    @Test
    fun savedRequestTargetUrlPrefersMqttTopicOverBroker() {
        val withTopic = SavedRequest(4, 1, RequestType.MQTT, "设备",
            snapshotJson.encodeToString(MqttSnapshot.serializer(),
                MqttSnapshot(brokerUrl = "tcp://broker:1883", subTopic = "demo/+/status", pubTopic = "")), 1L)
        val brokerOnly = SavedRequest(5, 1, RequestType.MQTT, "设备",
            snapshotJson.encodeToString(MqttSnapshot.serializer(),
                MqttSnapshot(brokerUrl = "tcp://broker:1883")), 1L)

        assertEquals("demo/+/status", withTopic.targetUrl())
        assertEquals("tcp://broker:1883", brokerOnly.targetUrl())
    }

    @Test
    fun savedRequestTargetUrlToleratesGarbagePayload() {
        val broken = SavedRequest(6, 1, RequestType.HTTP, "坏快照", "{not json", 1L)
        assertEquals("", broken.targetUrl())
    }
}
