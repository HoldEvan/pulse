package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.di.EnvStore
import com.evan.pulse.di.MonitorStore
import com.evan.pulse.di.ServiceLocator
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
 * 拨测调度器：前台按各自频率跑循环协程，后台交给 WorkManager 周期唤醒。
 *
 * 前台循环精度高（可到 15 秒），进程在后台被回收后由周期任务（最短 15 分钟）
 * 兜底，重新打开应用时对账一次。两条路径共用 [MonitorCheckRunner]，
 * 记录与告警语义完全一致。上次检查时间取自持久化历史，所以进程重启后
 * 不会因为内存状态丢失而重复告警。
 *
 * 状态流转只有三态：从未检查、上一次通、上一次不通。只有「通→不通」发告警，
 * 「不通→通」发恢复通知，持续不通不重复打扰，也不重复记记录。
 */
class MonitorScheduler(
    private val store: MonitorStore = ServiceLocator.monitorStore,
    private val envStore: EnvStore = ServiceLocator.envStore,
    private val settingsStore: SettingsStore = ServiceLocator.settingsStore,
    private val checker: MonitorChecker = MonitorChecker(),
    private val notifier: MonitorNotifier,
    private val background: MonitorBackgroundScheduler? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runner = MonitorCheckRunner(store, envStore, settingsStore, checker, notifier)

    /** 目标 id 到循环协程，synchronized 包装以便在 IO 线程池上并发增删 */
    private val jobs = Collections.synchronizedMap(LinkedHashMap<Long, Job>())

    /** 正在检查的目标：前台循环与后台唤醒同时命中时只放行一个 */
    private val inFlight = Collections.synchronizedSet(HashSet<Long>())

    private val _runs = MutableStateFlow<Map<Long, MonitorRun>>(emptyMap())

    /** 每个目标最近一次检查的结果，界面按它渲染状态 */
    val runs: StateFlow<Map<Long, MonitorRun>> = _runs.asStateFlow()

    init {
        // 启动即用已持久化的历史回填状态，界面不必等下一轮检查才有颜色
        _runs.value = store.targets.value.mapNotNull { target ->
            runner.previousRun(target.id)?.let { target.id to it }
        }.toMap()
    }

    /** 让正在跑的目标与 store 里启用的目标保持一致：新增的补上、关闭或删除的停掉 */
    fun sync() {
        val enabled = store.targets.value.filter { it.enabled }
        synchronized(jobs) {
            jobs.entries.toList().filter { (id, _) -> enabled.none { it.id == id } }
                .forEach { (id, job) ->
                    job.cancel()
                    jobs.remove(id)
                }
            enabled.forEach { target ->
                if (jobs[target.id]?.isActive != true) {
                    jobs[target.id] = startLoop(target.id)
                }
            }
        }
        background?.update(
            enabledCount = enabled.size,
            intervalSec = enabled.minOfOrNull { it.intervalSec } ?: 0L
        )
    }

    /** 立即检查一次指定目标，不受频率限制 */
    fun runOnce(targetId: Long, onComplete: (() -> Unit)? = null) {
        scope.launch {
            val target = store.get(targetId) ?: return@launch
            runAndReport(target)
            onComplete?.invoke()
        }
    }

    /**
     * 后台唤醒时调用：只检查已到期的启用目标，返回实际执行的次数。
     *
     * 到期判断基于持久化的上次检查时间，因此周期任务与前台循环同时存在时
     * 也不会把同一个目标连查两遍。
     */
    suspend fun runDueChecks(now: Long = System.currentTimeMillis()): Int {
        var checked = 0
        store.targets.value.filter { it.enabled }.forEach { target ->
            val last = store.historyFor(target.id).firstOrNull()?.checkedAt
            if (isCheckDue(target, last, now) && runAndReport(target)) {
                checked++
            }
        }
        return checked
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
            val last = store.historyFor(target.id).firstOrNull()?.checkedAt
            // 后台任务可能刚查过，前台循环不重复打目标
            if (isCheckDue(target, last, System.currentTimeMillis())) {
                runAndReport(target)
            }
            delay(monitorDelayMs(target))
        }
    }

    private suspend fun runAndReport(target: MonitorTarget): Boolean {
        // 前台循环与后台唤醒可能同时命中同一目标，第一个取到锁的才执行
        if (!inFlight.add(target.id)) return false
        try {
            val run = runner.perform(target)
            _runs.value = _runs.value + (target.id to run)
            return true
        } finally {
            inFlight.remove(target.id)
        }
    }

    companion object {
        const val MIN_INTERVAL_SEC = 15L
        const val MAX_INTERVAL_SEC = 21600L
    }
}
