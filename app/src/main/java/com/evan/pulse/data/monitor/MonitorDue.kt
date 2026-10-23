package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorTarget

/** 频率换算成等待毫秒；过短容易打爆目标，过长失去意义，两侧都收一下 */
internal fun monitorDelayMs(target: MonitorTarget): Long =
    maxOf(
        MonitorScheduler.MIN_INTERVAL_SEC,
        minOf(MonitorScheduler.MAX_INTERVAL_SEC, target.intervalSec)
    ) * 1000L

/**
 * 判断某个目标此刻是否该检查。
 *
 * [lastCheckedAt] 为空表示从未检查过，重启或新装后应当立刻补一次；
 * 留 20% 的提前量，避免周期任务与进程内循环因毫秒级抖动互相等待。
 */
internal fun isCheckDue(target: MonitorTarget, lastCheckedAt: Long?, now: Long): Boolean {
    if (lastCheckedAt == null) return true
    val interval = monitorDelayMs(target)
    return now - lastCheckedAt >= interval - interval / 5
}
