package com.evan.pulse.di

import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.WS_SESSION_HISTORY_LIMIT
import com.evan.pulse.data.model.WS_TEMPLATE_LIMIT
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
    fun `saving a template with an existing name overwrites content and keeps id`() {
        val store = store()
        val first = store.saveWsTemplate("ping", "{\"t\":1}", false, "HEX")
        val second = store.saveWsTemplate("ping", "{\"t\":2}", true, "BASE64")

        assertEquals(first.id, second.id)
        assertEquals(1, store.wsTemplates.value.size)
        val stored = store.wsTemplates.value.single()
        assertEquals("{\"t\":2}", stored.content)
        assertTrue(stored.binary)
        assertEquals("BASE64", stored.encoding)
    }

    @Test
    fun `templates are trimmed and blank names are kept out of the snapshot`() {
        val store = store()
        store.saveWsTemplate("  auth  ", "token", false, "HEX")

        assertEquals("auth", store.wsTemplates.value.single().name)
        val restored = store()
        restored.restore(store.snapshot())
        assertEquals("auth", restored.wsTemplates.value.single().name)
    }

    @Test
    fun `template list keeps only the newest entries when the limit is exceeded`() {
        val store = store()
        repeat(WS_TEMPLATE_LIMIT + 5) { index ->
            store.saveWsTemplate("t$index", "payload-$index", false, "HEX")
        }

        val templates = store.wsTemplates.value
        assertEquals(WS_TEMPLATE_LIMIT, templates.size)
        assertEquals("t5", templates.first().name)
        assertEquals("t${WS_TEMPLATE_LIMIT + 4}", templates.last().name)
    }

    @Test
    fun `deleting a template persists only when something changed`() {
        var saves = 0
        val persistence = PersistenceHandle().apply { action = { saves++ } }
        val store = ProtocolSessionStore(persistence)
        val template = store.saveWsTemplate("ping", "ping", false, "HEX")
        val before = saves

        store.deleteWsTemplate(template.id)
        store.deleteWsTemplate(template.id)

        assertTrue(store.wsTemplates.value.isEmpty())
        assertEquals(before + 1, saves)
    }

    @Test
    fun `snapshot restore preserves templates and the next template id`() {
        val original = store()
        val template = original.saveWsTemplate("hello", "world", false, "HEX")

        val restored = store()
        restored.restore(original.snapshot())
        val next = restored.saveWsTemplate("next", "again", false, "HEX")

        assertEquals(listOf("hello", "next"), restored.wsTemplates.value.map { it.name })
        assertTrue(next.id > template.id)
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
