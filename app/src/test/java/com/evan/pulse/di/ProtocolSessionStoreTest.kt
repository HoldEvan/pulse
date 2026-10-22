package com.evan.pulse.di

import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.WS_SESSION_HISTORY_LIMIT
import com.evan.pulse.data.model.WsReconnectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSessionStoreTest {
    private fun store() = ProtocolSessionStore(PersistenceHandle())

    @Test
    fun `websocket history retains newest entries in insertion order`() {
        val store = store()
        repeat(WS_SESSION_HISTORY_LIMIT + 3) { index ->
            store.appendWs("wss://example.test", "event-$index", false, index.toLong())
        }

        val history = store.wsHistory.value
        assertEquals(WS_SESSION_HISTORY_LIMIT, history.size)
        assertEquals("event-3", history.first().text)
        assertEquals("event-${WS_SESSION_HISTORY_LIMIT + 2}", history.last().text)
    }

    @Test
    fun `snapshot restore preserves sessions subscriptions and next id`() {
        val original = store()
        original.appendWs("wss://example.test", "hello", false, 42L)
        original.setMqttSubscriptions(listOf(MqttSubscriptionEntry("device/+/state", 1)))
        original.setWsReconnectSettings(WsReconnectSettings(true, 2, 8))

        val restored = store()
        restored.restore(original.snapshot())
        val next = restored.appendWs("wss://example.test", "next", true, 43L)

        assertEquals(listOf("hello", "next"), restored.wsHistory.value.map { it.text })
        assertEquals(2L, next.id)
        assertEquals(listOf(MqttSubscriptionEntry("device/+/state", 1)), restored.mqttSubscriptions.value)
        assertEquals(WsReconnectSettings(true, 2, 8), restored.wsReconnectSettings.value)
    }

    @Test
    fun `clearing websocket history persists empty state and preserves subscription settings`() {
        var saves = 0
        val persistence = PersistenceHandle().apply { action = { saves++ } }
        val store = ProtocolSessionStore(persistence)
        store.appendWs("wss://example.test", "hello", false, 42L)
        store.setWsReconnectSettings(WsReconnectSettings(true, 3, 12))
        val before = saves

        store.clearWsHistory()

        assertTrue(store.wsHistory.value.isEmpty())
        assertTrue(store.snapshot().wsHistory.isEmpty())
        assertEquals(WsReconnectSettings(true, 3, 12), store.wsReconnectSettings.value)
        assertEquals(before + 1, saves)
    }
}
