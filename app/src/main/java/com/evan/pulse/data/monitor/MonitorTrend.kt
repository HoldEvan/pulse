package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorHistoryEntry

/** 单个趋势样本点，按时间升序 */
data class TrendPoint(
    val checkedAt: Long,
    val durationMs: Long,
    val ok: Boolean,
    val slow: Boolean
)

/** 一段窗口内的趋势统计结果 */
data class TrendStats(
    val sampleCount: Int,
    val successCount: Int,
    /** 可用率：成功样本占比，0..1 */
    val successRate: Float,
    /** 延迟（毫秒）平均/峰值/最低，无样本时为 0 */
    val avgLatencyMs: Float,
    val peakLatencyMs: Long,
    val minLatencyMs: Long,
    /** 时间升序的样本点，供折线绘制 */
    val points: List<TrendPoint>
)

/** 趋势计算与统计：纯函数，便于单元测试 */
object MonitorTrend {

    /**
     * 从倒序历史样本（最新在前）计算趋势统计。
     * [window] 表示取最近多少条样本参与统计与绘图；0 或负数表示不设上限。
     */
    fun compute(history: List<MonitorHistoryEntry>, window: Int = 0): TrendStats {
        if (history.isEmpty()) {
            return TrendStats(0, 0, 0f, 0f, 0L, 0L, emptyList())
        }
        val capped = if (window > 0 && history.size > window) history.take(window) else history
        val points = capped
            .asReversed()
            .map { TrendPoint(it.checkedAt, it.durationMs, it.ok, it.slow) }
            .sortedBy { it.checkedAt }
        val okCount = points.count { it.ok }
        val durations = points.map { it.durationMs }
        return TrendStats(
            sampleCount = points.size,
            successCount = okCount,
            successRate = if (points.isEmpty()) 0f else okCount.toFloat() / points.size,
            avgLatencyMs = if (durations.isEmpty()) 0f else durations.average().toFloat(),
            peakLatencyMs = durations.maxOrNull() ?: 0L,
            minLatencyMs = durations.minOrNull() ?: 0L,
            points = points
        )
    }
}