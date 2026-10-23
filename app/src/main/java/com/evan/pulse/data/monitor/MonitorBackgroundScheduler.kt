package com.evan.pulse.data.monitor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.evan.pulse.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * 后台拨测的调度入口。
 *
 * 前台循环精度够用，但进程被系统回收后就停了；这里注册一个周期任务，
 * 由 WorkManager 在进程不存在时拉起 Workers 补跑，重新打开应用时再由
 * 前台循环接管。
 */
interface MonitorBackgroundScheduler {
    /** 按启用目标数量与最短频率更新周期任务；没有启用目标时取消 */
    fun update(enabledCount: Int, intervalSec: Long)
}

/** WorkManager 最短周期是 15 分钟，更短的频率只能由前台循环保证 */
private const val MIN_PERIODIC_MINUTES = 15L

/**
 * 基于 WorkManager 的实现。
 *
 * 周期取「最短目标频率」与 15 分钟中的较大者：目标要求 1 小时检查一次，
 * 后台就每小时唤醒一次；要求 15 秒检查一次，后台也至少 15 分钟一次，
 * 由 [MonitorScheduler.runDueChecks] 的到期判断兜住多余唤醒。
 */
class WorkManagerMonitorScheduler(private val context: Context) : MonitorBackgroundScheduler {

    override fun update(enabledCount: Int, intervalSec: Long) {
        val manager = WorkManager.getInstance(context)
        if (enabledCount <= 0 || intervalSec <= 0L) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = maxOf(MIN_PERIODIC_MINUTES, (intervalSec / 60L).coerceAtLeast(1L))
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // UPDATE 让频率变化时就地替换，不会堆出多个同名任务
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "pulse_monitor_periodic"
    }
}

/**
 * 后台唤醒时执行的拨测任务。
 *
 * 依赖 [ServiceLocator] 的进程内状态：WorkManager 拉起进程时会先执行
 * Application.onCreate，因此 init/initMonitor 已经跑过。检查是否到期由
 * [MonitorScheduler.runDueChecks] 决定，这里只负责触发与返回结果。
 */
class MonitorWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        ServiceLocator.monitorScheduler.runDueChecks()
        Result.success()
    }.getOrElse { Result.retry() }
}
