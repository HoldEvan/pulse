package com.evan.pulse.ui.ws

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.snapshotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsSubprotocolsTest {

    @Test
    fun `blank input yields no subprotocol header`() {
        assertEquals(emptyList<String>(), parseSubprotocols(""))
        assertEquals(emptyList<String>(), parseSubprotocols("   "))
        assertEquals(emptyList<String>(), parseSubprotocols(" , , "))
    }

    @Test
    fun `entries are trimmed and empty segments dropped`() {
        assertEquals(
            listOf("graphql-ws", "json"),
            parseSubprotocols(" graphql-ws , json ,")
        )
    }

    @Test
    fun `duplicates keep the first occurrence to preserve negotiation order`() {
        assertEquals(
            listOf("a", "b"),
            parseSubprotocols("a, b, a")
        )
    }

    @Test
    fun `snapshot round-trips headers and subprotocols`() {
        val original = WsSnapshot(
            url = "wss://h/ws",
            headers = listOf(KeyValueEntry("Authorization", "Bearer t")),
            subprotocols = listOf("graphql-ws")
        )
        val decoded = snapshotJson.decodeFromString(
            WsSnapshot.serializer(),
            snapshotJson.encodeToString(WsSnapshot.serializer(), original)
        )
        assertEquals(listOf(KeyValueEntry("Authorization", "Bearer t")), decoded.headers)
        assertEquals(listOf("graphql-ws"), decoded.subprotocols)
    }

    @Test
    fun `snapshots saved before the header fields existed still decode`() {
        val legacy = """{"url":"wss://h/ws","pingIntervalSec":0,"pingPayload":"ping","useProxy":true}"""
        val decoded = snapshotJson.decodeFromString(WsSnapshot.serializer(), legacy)
        assertEquals("wss://h/ws", decoded.url)
        assertTrue(decoded.headers.isEmpty())
        assertTrue(decoded.subprotocols.isEmpty())
    }
}
