package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDueTest {

    private fun target(intervalSec: Long) = MonitorTarget(
        id = 1,
        projectId = 1,
        name = "接口",
        type = com.evan.pulse.data.model.MonitorType.HTTP,
        url = "https://example.com",
        intervalSec = intervalSec
    )

    @Test
    fun `interval is clamped to the supported range`() {
        assertEquals(15_000L, monitorDelayMs(target(1)))
        assertEquals(15_000L, monitorDelayMs(target(0)))
        assertEquals(60_000L, monitorDelayMs(target(60)))
        assertEquals(21_600_000L, monitorDelayMs(target(99_999)))
    }

    @Test
    fun `never checked targets are due immediately`() {
        assertTrue(isCheckDue(target(60), lastCheckedAt = null, now = 0L))
    }

    @Test
    fun `target becomes due after the interval with a small early allowance`() {
        val t = target(60)
        val last = 1_000_000L
        assertFalse(isCheckDue(t, last, last + 30_000L))
        // 提前量 20%：48 秒后即视为到期，避免周期任务与前台循环互相等待
        assertTrue(isCheckDue(t, last, last + 48_000L))
        assertTrue(isCheckDue(t, last, last + 60_000L))
    }

    @Test
    fun `short intervals clamp before applying the due check`() {
        val t = target(0)
        val last = 0L
        assertFalse(isCheckDue(t, last, 5_000L))
        assertTrue(isCheckDue(t, last, 12_000L))
    }
}
