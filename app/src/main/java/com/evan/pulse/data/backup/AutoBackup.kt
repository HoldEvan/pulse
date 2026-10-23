package com.evan.pulse.data.backup

import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.data.settings.AUTO_BACKUP_INTERVAL_HOURS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 一次自动备份的结果，供界面提示与日志 */
data class AutoBackupOutcome(
    val ok: Boolean,
    val fileUrl: String? = null,
    val deleted: Int = 0,
    val message: String? = null
)

/**
 * 定时自动备份到 WebDAV。
 *
 * 由 WorkManager 周期唤醒（最短 15 分钟，实际周期由设置决定），
 * 每次执行「导出 → 按保留份数清理旧文件」。只清理自动备份产生的文件，
 * 手动导出的名字里带时间戳但前缀相同，一并按份数管理，避免目录无限增长。
 */
object AutoBackupRunner {

    /** 单次执行：导出当前数据并裁剪旧备份；配置不完整时直接返回失败原因 */
    suspend fun run(
        service: BackupService,
        config: WebDavConfig?,
        keepCount: Int
    ): AutoBackupOutcome {
        if (config == null) {
            return AutoBackupOutcome(false, message = "WebDAV 未配置")
        }
        val fileUrl = runCatching { service.exportToWebDav(config) }
            .getOrElse { return AutoBackupOutcome(false, message = it.message ?: "导出失败") }
        val deleted = runCatching { prune(service, config, keepCount) }.getOrDefault(0)
        return AutoBackupOutcome(true, fileUrl = fileUrl, deleted = deleted)
    }

    /** 保留最近 [keepCount] 份备份，返回删除数量；列表失败时不动远端 */
    suspend fun prune(service: BackupService, config: WebDavConfig, keepCount: Int): Int {
        val keep = keepCount.coerceAtLeast(1)
        // 至少多取一份，才能判断是否还有更旧的备份需要清理
        val entries = service.listRemote(config, limit = maxOf(keep + 1, 10))
        val doomed = entries.drop(keep)
        doomed.forEach { service.deleteWebDavUrl(config, it.url) }
        return doomed.size
    }

    /**
     * 把设置里的自动备份配置转成 WebDAV 配置；未启用或地址为空时返回 null。
     *
     * 周期下标越界时回落到每天一次，避免历史数据里出现不可用的值。
     */
    fun webDavConfigOf(settings: com.evan.pulse.data.settings.AppSettings): WebDavConfig? {
        if (!settings.autoBackupEnabled) return null
        if (settings.webdavUrl.isBlank()) return null
        return WebDavConfig(
            url = settings.webdavUrl.trim(),
            path = settings.webdavPath.trim(),
            username = settings.webdavUsername.ifBlank { null },
            password = settings.webdavPassword.ifBlank { null }
        )
    }

    /** 周期小时数：下标越界时回落到每天一次 */
    fun intervalHours(index: Int): Int =
        AUTO_BACKUP_INTERVAL_HOURS.getOrElse(index) { AUTO_BACKUP_INTERVAL_HOURS[2] }
}

/**
 * 观察设置变化并同步周期任务。
 *
 * 设置页改动开关/周期后不需要重启：这里订阅 SettingsStore，把最新配置
 * 推给 [BackupBackgroundScheduler]。
 */
class AutoBackupScheduler(
    private val store: SettingsStore,
    private val background: BackupBackgroundScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    fun start() {
        scope.launch {
            store.settings
                .map { settings ->
                    settings.autoBackupEnabled to settings.autoBackupIntervalIndex
                }
                .distinctUntilChanged()
                .collect { (enabled, intervalIndex) ->
                    background.update(
                        enabled = enabled,
                        intervalHours = AutoBackupRunner.intervalHours(intervalIndex)
                    )
                }
        }
    }
}
