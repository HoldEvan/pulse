package com.evan.pulse.domain.net

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkThrottleTest {

    private fun config(
        preset: NetworkPreset = NetworkPreset.NONE,
        forceTimeoutSec: Int = 0
    ) = NetworkThrottleConfig(preset, forceTimeoutSec)

    @Test
    fun `off preset and empty force timeout are inactive`() {
        assertFalse(config(preset = NetworkPreset.NONE).isActive)
        assertFalse(config(preset = NetworkPreset.FORCE_TIMEOUT, forceTimeoutSec = 0).isActive)
    }

    @Test
    fun `network preset or non-zero force timeout activates`() {
        assertTrue(config(preset = NetworkPreset.THREE_G).isActive)
        assertTrue(config(preset = NetworkPreset.FORCE_TIMEOUT, forceTimeoutSec = 5).isActive)
    }

    @Test
    fun `upload delay contains rtt within preset range`() {
        val random = Random(42)
        repeat(200) {
            // 0 字节上传时只剩 RTT：5G 应在 20..40ms
            val delay = NetworkThrottle.uploadDelayMs(0L, config(preset = NetworkPreset.FIVE_G), random)
            assertTrue("5G rtt should be 20..40ms, got $delay", delay in 20..40)
        }
    }

    @Test
    fun `upload uses uplink rate`() {
        // 2G 上行 50Kbps：1000 bytes * 8 / 50 = 160ms，再加 RTT 600..1200
        val delay = NetworkThrottle.uploadDelayMs(1000L, config(preset = NetworkPreset.TWO_G), Random(42))
        assertTrue("2G upload should be >= 660ms, got $delay", delay in 660..1360)
    }

    @Test
    fun `download uses downlink rate without rtt`() {
        // 2G 下行 100Kbps：1000 bytes * 8 / 100 = 80ms，无 RTT
        assertEquals(80L, NetworkThrottle.downloadDelayMs(1000L, config(preset = NetworkPreset.TWO_G)))
        assertEquals(0L, NetworkThrottle.downloadDelayMs(0L, config(preset = NetworkPreset.TWO_G)))
    }

    @Test
    fun `force timeout preset yields no transfer delay`() {
        val cfg = config(preset = NetworkPreset.FORCE_TIMEOUT, forceTimeoutSec = 5)
        assertEquals(0L, NetworkThrottle.uploadDelayMs(5000L, cfg))
        assertEquals(0L, NetworkThrottle.downloadDelayMs(5000L, cfg))
    }

    @Test
    fun `transfer delay follows bytes and rate`() {
        assertEquals(80L, NetworkThrottle.transferDelayMs(1000, 100))
        assertEquals(0L, NetworkThrottle.transferDelayMs(0, 100))
        assertEquals(0L, NetworkThrottle.transferDelayMs(1000, 0))
    }

    @Test
    fun `each phase capped at 15s`() {
        // 10MB @ 100Kbps 远超上限
        assertEquals(
            NetworkThrottle.MAX_TRANSFER_DELAY_MS,
            NetworkThrottle.downloadDelayMs(10_000_000L, config(preset = NetworkPreset.TWO_G))
        )
        assertEquals(
            NetworkThrottle.MAX_TRANSFER_DELAY_MS,
            NetworkThrottle.uploadDelayMs(10_000_000L, config(preset = NetworkPreset.TWO_G), Random(42))
        )
    }
}
