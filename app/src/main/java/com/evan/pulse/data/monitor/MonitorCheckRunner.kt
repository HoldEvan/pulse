package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.di.EnvStore
import com.evan.pulse.di.MonitorStore

/**
 * 单个目标的「检查 → 落库 → 告警」流程。
 *
 * 前台循环与后台唤醒共用这一份实现，避免两条路径给出不同的记录与通知行为。
 * 上一次结果从持久化的历史样本里取，因此进程被回收后重新检查时不会重复告警。
 */
class MonitorCheckRunner(
    private val store: MonitorStore,
    private val envStore: EnvStore,
    private val settingsStore: SettingsStore,
    private val checker: MonitorChecker,
    private val notifier: MonitorNotifier,
) {

    /** 最近一次检查的结果；没有历史样本时返回 null（视为从未检查） */
    fun previousRun(targetId: Long): MonitorRun? =
        store.historyFor(targetId).firstOrNull()?.let { entry ->
            MonitorRun(entry.ok, "", entry.durationMs, entry.checkedAt, entry.slow)
        }

    suspend fun perform(target: MonitorTarget): MonitorRun {
        val previous = previousRun(target.id)
        val run = runCheck(target)
        store.recordResult(target, run.ok, run.detail, run.durationMs, run.checkedAt)
        store.recordHistory(target, run.ok, run.slow, run.durationMs, run.checkedAt)
        report(target, run, previous)
        return run
    }

    private suspend fun runCheck(target: MonitorTarget): MonitorRun = try {
        checker.check(
            target = target,
            vars = envStore.varsFor(target.projectId),
            proxy = settingsStore.settings.value.toProxyConfig()
        )
    } catch (_: Exception) {
        MonitorRun(false, "", 0L, System.currentTimeMillis())
    }

    /**
     * 告警状态机：通 / 慢 / 断三态加"从未检查"。记录已由 [perform] 全量落库，
     * 这里只负责在状态变化时发通知，持续异常不重复打扰：
     * - 断：从未检查或上一轮通/慢时发告警；
     * - 慢：连通但延迟超阈值，上一轮慢则静默，其余发"响应过慢"；
     * - 通：从断或慢恢复时发恢复通知。
     */
    private fun report(target: MonitorTarget, run: MonitorRun, previous: MonitorRun?) {
        val previousDown = previous != null && !previous.ok
        val previousSlow = previous?.slow == true
        when {
            run.slow -> if (!previousSlow) notifySlow(target, run.detail)
            run.ok -> if (previousDown || previousSlow) notifyRecovered(target)
            else -> if (!previousDown) notifyDown(target, run.detail)
        }
    }

    private fun notifyDown(target: MonitorTarget, detail: String) {
        if (settingsStore.settings.value.monitorNotify) notifier.postDown(target, detail)
    }

    private fun notifySlow(target: MonitorTarget, detail: String) {
        if (settingsStore.settings.value.monitorNotify) notifier.postSlow(target, detail)
    }

    private fun notifyRecovered(target: MonitorTarget) {
        if (settingsStore.settings.value.monitorNotify) notifier.postRecovered(target)
    }
}
