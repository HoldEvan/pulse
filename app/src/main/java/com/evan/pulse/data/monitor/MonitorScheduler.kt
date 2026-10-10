package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.di.EnvStore
import com.evan.pulse.di.MonitorStore
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.data.settings.SettingsStore
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 拨测调度器：每个启用的目标一条循环协程，按各自频率反复检查。
 *
 * 状态流转只有三态：从未检查、上一次通、上一次不通。只有「通→不通」发告警，
 * 「不通→通」发恢复通知，持续不通不重复打扰，也不重复记记录。
 *
 * 调度作用域与进程同生命周期：进程被系统回收后停止，重启时随 Application 重建。
 * 要长期保活需要前台服务，这里刻意不做，避免为一个调试工具申请常驻权限。
 */
class MonitorScheduler(
    private val store: MonitorStore = ServiceLocator.monitorStore,
    private val envStore: EnvStore = ServiceLocator.envStore,
    private val settingsStore: SettingsStore = ServiceLocator.settingsStore,
    private val checker: MonitorChecker = MonitorChecker(),
    private val notifier: MonitorNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 目标 id 到循环协程，synchronized 包装以便在 IO 线程池上并发增删 */
    private val jobs = Collections.synchronizedMap(LinkedHashMap<Long, Job>())

    private val _runs = MutableStateFlow<Map<Long, MonitorRun>>(emptyMap())

    /** 每个目标最近一次检查的结果，界面按它渲染状态 */
    val runs: StateFlow<Map<Long, MonitorRun>> = _runs.asStateFlow()

    /** 让正在跑的目标与 store 里启用的目标保持一致：新增的补上、关闭或删除的停掉 */
    fun sync() {
        val enabled = store.targets.value.filter { it.enabled }.map { it.id }
        synchronized(jobs) {
            jobs.entries.toList().filter { it.key !in enabled }.forEach { (id, job) ->
                job.cancel()
                jobs.remove(id)
            }
            enabled.forEach { id ->
                if (jobs[id]?.isActive != true) {
                    jobs[id] = startLoop(id)
                }
            }
        }
    }

    /** 立即检查一次指定目标，不受频率限制 */
    fun runOnce(targetId: Long) {
        scope.launch {
            val target = store.get(targetId) ?: return@launch
            runAndReport(target)
        }
    }

    fun stopAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
    }

    private fun startLoop(targetId: Long): Job = scope.launch {
        while (isActive) {
            val target = store.get(targetId) ?: break
            runAndReport(target)
            delay(nextDelayMs(target))
        }
    }

    private suspend fun runAndReport(target: MonitorTarget) {
        val previousOk = _runs.value[target.id]?.ok
        val run = runCheck(target)
        _runs.value = _runs.value + (target.id to run)
        report(target, run, previousOk)
    }

    private suspend fun runCheck(target: MonitorTarget) = try {
        checker.check(
            target = target,
            vars = envStore.varsFor(target.projectId),
            proxy = settingsStore.settings.value.toProxyConfig()
        )
    } catch (_: Exception) {
        MonitorRun(false, "", 0L, System.currentTimeMillis())
    }

    private fun report(target: MonitorTarget, run: MonitorRun, previousOk: Boolean?) {
        if (run.ok) {
            if (previousOk == false) notifyRecovered(target)
            return
        }
        // 只有「通 → 不通」这一跳算新事件：记一条未通记录，同时发一次告警
        if (previousOk != false) {
            store.recordFailure(target, run.detail, run.durationMs)
            notifyDown(target, run.detail)
        }
    }

    private fun notifyDown(target: MonitorTarget, detail: String) {
        if (settingsStore.settings.value.monitorNotify) notifier.postDown(target, detail)
    }

    private fun notifyRecovered(target: MonitorTarget) {
        if (settingsStore.settings.value.monitorNotify) notifier.postRecovered(target)
    }

    /** 频率换算成等待毫秒；过短容易打爆目标，过长失去意义，两侧都收一下 */
    private fun nextDelayMs(target: MonitorTarget): Long =
        MIN_INTERVAL_SEC.coerceAtMost(target.intervalSec.coerceAtMost(MAX_INTERVAL_SEC)) * MILLIS_PER_SECOND

    companion object {
        const val MIN_INTERVAL_SEC = 15L
        const val MAX_INTERVAL_SEC = 21600L
        private const val MILLIS_PER_SECOND = 1000L
    }
}
