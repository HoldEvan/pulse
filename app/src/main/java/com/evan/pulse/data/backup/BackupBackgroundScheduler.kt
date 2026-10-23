package com.evan.pulse.data.backup

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

/** 自动备份的后台调度入口；没有启用时应当取消已有任务 */
interface BackupBackgroundScheduler {
    fun update(enabled: Boolean, intervalHours: Int)
}

/** WorkManager 周期下限 */
private const val MIN_PERIODIC_MINUTES = 15L

/**
 * 基于 WorkManager 的自动备份调度。
 *
 * 周期取设置值与 15 分钟中的较大者，`UPDATE` 策略让周期调整就地生效。
 * 关闭时取消唯一任务，不会留下后台唤醒。
 */
class WorkManagerBackupScheduler(private val context: Context) : BackupBackgroundScheduler {

    override fun update(enabled: Boolean, intervalHours: Int) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = maxOf(MIN_PERIODIC_MINUTES, intervalHours.coerceAtLeast(1) * 60L)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "pulse_backup_periodic"
    }
}

/**
 * 自动备份任务。
 *
 * 配置在 Worker 内重新读取：设置可能在任务排队后被改动，
 * 每次都以执行时刻的最新配置为准。未配置 WebDAV 时直接成功返回。
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val settings = ServiceLocator.settingsStore.settings.value
        val config = AutoBackupRunner.webDavConfigOf(settings)
            ?: return@runCatching Result.success()
        val service = BackupService(applicationContext)
        val outcome = AutoBackupRunner.run(service, config, settings.autoBackupKeepCount)
        if (outcome.ok) Result.success() else Result.retry()
    }.getOrElse { Result.retry() }
}
