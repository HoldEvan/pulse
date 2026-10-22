package com.evan.pulse.domain.net

import org.junit.Assert.assertEquals
import org.junit.Test

class MutableTimelineTest {

    @Test
    fun `full https timeline decomposes all phases`() {
        val tl = MutableTimeline()
        var t = 1000L
        tl.markCallStart(t); t += 30
        tl.markDnsStart(t); t += 12
        tl.markDnsEnd(t); t += 3
        tl.markConnectStart(t); t += 8
        tl.markSecureStart(t); t += 25
        tl.markSecureEnd(t); t += 4
        tl.markConnectEnd(t); t += 20
        tl.markHeadersStart(t); t += 6
        val snap = tl.snapshot(totalMs = 78)
        assertEquals(12, snap.dnsMs)
        assertEquals(8, snap.tcpMs)
        assertEquals(25, snap.tlsMs)
        // callStart(1000) -> headersStart(1000+30+12+3+8+25+4+20=1102) = 102
        assertEquals(102, snap.ttfbMs)
        assertEquals(78, snap.totalMs)
    }

    @Test
    fun `http without tls omits tls and uses connectEnd for tcp`() {
        val tl = MutableTimeline()
        var t = 0L
        tl.markCallStart(t); t += 10
        tl.markConnectStart(t); t += 15
        tl.markConnectEnd(t); t += 5
        tl.markHeadersStart(t); t += 3
        val snap = tl.snapshot(totalMs = 33)
        assertEquals(15, snap.tcpMs)
        assertEquals(0, snap.tlsMs)
        // callStart(0) -> headersStart(10+15+5=30)
        assertEquals(30, snap.ttfbMs)
        assertEquals(33, snap.totalMs)
    }

    @Test
    fun `unfilled stages default to zero`() {
        val tl = MutableTimeline()
        tl.markCallStart(5)
        val snap = tl.snapshot(totalMs = 5)
        assertEquals(0, snap.dnsMs)
        assertEquals(0, snap.tcpMs)
        assertEquals(0, snap.tlsMs)
        assertEquals(0, snap.ttfbMs)
        assertEquals(5, snap.totalMs)
    }

    @Test
    fun `marks are idempotent - later events do not overwrite earlier timestamps`() {
        val tl = MutableTimeline()
        tl.markDnsStart(10)
        tl.markDnsStart(999)
        tl.markDnsEnd(20)
        tl.markDnsEnd(888)
        val snap = tl.snapshot(totalMs = 0)
        assertEquals(10, snap.dnsMs)
    }
}