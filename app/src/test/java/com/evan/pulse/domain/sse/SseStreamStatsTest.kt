package com.evan.pulse.domain.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseStreamStatsTest {

    @Test
    fun `records total and per-event counts`() {
        val stats = SseStreamStats()
            .record("message", "1")
            .record("message", "2")
            .record("done", "3")

        assertEquals(3, stats.total)
        assertEquals(mapOf("message" to 2, "done" to 1), stats.byEvent)
    }

    @Test
    fun `blank event name falls back to message`() {
        val stats = SseStreamStats().record("", null)

        assertEquals(mapOf("message" to 1), stats.byEvent)
    }

    @Test
    fun `breakdown sorts by count desc then name asc`() {
        val stats = SseStreamStats()
            .record("b", null)
            .record("a", null)
            .record("c", null)
            .record("c", null)

        assertEquals(listOf("c" to 2, "a" to 1, "b" to 1), stats.breakdown())
    }

    @Test
    fun `last event id keeps the newest non blank id`() {
        val stats = SseStreamStats()
            .record("message", "evt-1")
            .record("message", "  ")
            .record("message", "evt-2")

        assertEquals("evt-2", stats.lastEventId)
    }

    @Test
    fun `frames without id keep the previous resume point`() {
        val stats = SseStreamStats().record("message", "evt-7").record("message", null)

        assertEquals("evt-7", stats.lastEventId)
    }

    @Test
    fun `reset clears counters and resume point`() {
        val stats = SseStreamStats().record("message", "evt-1").reset()

        assertEquals(0, stats.total)
        assertEquals(emptyMap<String, Int>(), stats.byEvent)
        assertNull(stats.lastEventId)
    }

    @Test
    fun `resume id prefers the last seen id over the manual value`() {
        assertEquals("seen", SseResume.lastEventId("manual", "seen"))
    }

    @Test
    fun `resume id falls back to manual value before any id is seen`() {
        assertEquals("manual", SseResume.lastEventId("manual", null))
        assertEquals("manual", SseResume.lastEventId("  manual  ", "  "))
    }

    @Test
    fun `resume id is null when neither source is set`() {
        assertNull(SseResume.lastEventId("", null))
        assertNull(SseResume.lastEventId("   ", null))
    }
}
