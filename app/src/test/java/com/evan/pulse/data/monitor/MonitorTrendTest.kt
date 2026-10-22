package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorTrendTest {

    private fun entry(id: Long, ok: Boolean, durationMs: Long, checkedAt: Long) =
        MonitorHistoryEntry(
            id = id,
            targetId = 1L,
            ok = ok,
            slow = !ok,
            durationMs = durationMs,
            checkedAt = checkedAt
        )

    @Test
    fun `compute returns empty stats for no samples`() {
        val stats = MonitorTrend.compute(emptyList())
        assertEquals(0, stats.sampleCount)
        assertEquals(0, stats.successCount)
        assertEquals(0f, stats.successRate)
        assertEquals(0L, stats.peakLatencyMs)
        assertEquals(0L, stats.minLatencyMs)
        assert(stats.points.isEmpty())
    }

    @Test
    fun `compute calculates success rate and latency stats`() {
        val history = listOf(
            entry(1, ok = true, durationMs = 100, checkedAt = 1000),
            entry(2, ok = true, durationMs = 300, checkedAt = 2000),
            entry(3, ok = false, durationMs = 0, checkedAt = 3000),
            entry(4, ok = true, durationMs = 200, checkedAt = 4000)
        )
        // history 是倒序（最新在前），compute 内部会纠正为升序
        val stats = MonitorTrend.compute(history.asReversed(), window = 0)
        assertEquals(4, stats.sampleCount)
        assertEquals(3, stats.successCount)
        assertEquals(0.75f, stats.successRate, 0.0001f)
        assertEquals(150f, stats.avgLatencyMs, 0.0001f)
        assertEquals(300L, stats.peakLatencyMs)
        assertEquals(0L, stats.minLatencyMs)
        // 升序输出
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L), stats.points.map { it.checkedAt })
    }

    @Test
    fun `compute caps to window count keeping latest`() {
        val history = (1..10).map { i ->
            entry(i.toLong(), ok = true, durationMs = i.toLong() * 10, checkedAt = i.toLong() * 1000)
        }
        // 倒序传入（最新在前），window=5 应保留最近 5 条：id 6..10
        val stats = MonitorTrend.compute(history.asReversed(), window = 5)
        assertEquals(5, stats.sampleCount)
        assertEquals(listOf(6000L, 7000L, 8000L, 9000L, 10000L), stats.points.map { it.checkedAt })
        // id6=60ms 为窗口内最小，id10=100ms 为最大
        assertEquals(60L, stats.minLatencyMs)
        assertEquals(100L, stats.peakLatencyMs)
    }

    @Test
    fun `zero window means no cap`() {
        val history = (1..20).map { i ->
            entry(i.toLong(), ok = true, durationMs = 10L, checkedAt = i.toLong())
        }
        val stats = MonitorTrend.compute(history.asReversed(), window = 0)
        assertEquals(20, stats.sampleCount)
    }

    @Test
    fun `single sample yields flat line point`() {
        val stats = MonitorTrend.compute(listOf(entry(1, ok = true, durationMs = 250, checkedAt = 500)))
        assertEquals(1, stats.sampleCount)
        assertEquals(1.0f, stats.successRate, 0.0001f)
        assertEquals(250L, stats.peakLatencyMs)
        assertEquals(250L, stats.minLatencyMs)
    }
}