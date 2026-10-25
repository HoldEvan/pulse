package com.evan.pulse.data.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import com.evan.pulse.BuildConfig
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.diagnostics.DiagnosticHistorySample
import com.evan.pulse.domain.diagnostics.DiagnosticInput
import com.evan.pulse.domain.diagnostics.DiagnosticMonitorSample
import com.evan.pulse.domain.diagnostics.DiagnosticReport
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 采集运行时诊断信息并写入用户选择的位置。
 *
 * 只读取各 Store 的公开快照与计数，不触碰请求正文；所有文本最终都会经过
 * [DiagnosticReport] 的脱敏流程，因此这里可以按需放入原始值。
 */
class DiagnosticCollector(private val context: Context) {

    /** 生成脱敏后的诊断 JSON 文本 */
    fun collect(settings: AppSettings, now: Long = System.currentTimeMillis()): String =
        DiagnosticReport.build(buildInput(settings, now))

    /** 写入 SAF 选定的 Uri；失败时抛出异常交由上层提示 */
    suspend fun exportTo(uri: Uri, settings: AppSettings) = withContext(Dispatchers.IO) {
        val text = collect(settings)
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("无法打开目标文件")
        stream.bufferedWriter().use { it.write(text) }
    }

    private fun buildInput(settings: AppSettings, now: Long): DiagnosticInput {
        val history = ServiceLocator.historyStore.entries.value
        val monitors = ServiceLocator.monitorStore
        val monitorRecords = monitors.records.value

        return DiagnosticInput(
            generatedAt = now,
            meta = metaInfo(now),
            settings = settingsInfo(settings),
            counts = mapOf(
                "projects" to ServiceLocator.projectStore.projects.value.size,
                "environments" to ServiceLocator.envStore.environments.value.size,
                "collections" to ServiceLocator.collectionStore.collections.value.size,
                "savedRequests" to ServiceLocator.collectionStore.requests.value.size,
                "history" to history.size,
                "monitorTargets" to monitors.targets.value.size,
                "monitorRecords" to monitorRecords.size,
                "monitorHistory" to monitors.history.value.size,
                "cookies" to ServiceLocator.cookieStore.cookies.value.values
                    .sumOf { buckets -> buckets.values.sumOf { it.size } }
            ),
            historyFailures = history.filter { !it.success }
                .take(DiagnosticReport.MAX_SAMPLES)
                .map { entry ->
                    DiagnosticHistorySample(
                        method = entry.method,
                        url = entry.url,
                        statusCode = entry.statusCode,
                        durationMs = entry.durationMs,
                        createdAt = entry.createdAt
                    )
                },
            monitorFailures = monitorRecords.filter { !it.ok }
                .take(DiagnosticReport.MAX_SAMPLES)
                .map { record ->
                    DiagnosticMonitorSample(
                        type = record.type.name,
                        targetName = record.targetName,
                        message = record.message,
                        durationMs = record.durationMs,
                        checkedAt = record.checkedAt
                    )
                }
        )
    }

    private fun metaInfo(now: Long): Map<String, String> = linkedMapOf(
        "appVersionName" to BuildConfig.VERSION_NAME,
        "appVersionCode" to BuildConfig.VERSION_CODE.toString(),
        "buildTimeMs" to BuildConfig.BUILD_TIME_MS.toString(),
        "debugBuild" to BuildConfig.DEBUG.toString(),
        "packageName" to context.packageName,
        "androidRelease" to Build.VERSION.RELEASE.orEmpty(),
        "androidSdk" to Build.VERSION.SDK_INT.toString(),
        "manufacturer" to Build.MANUFACTURER.orEmpty(),
        "model" to Build.MODEL.orEmpty(),
        "abilist" to Build.SUPPORTED_ABIS.joinToString(","),
        "locale" to Locale.getDefault().toLanguageTag(),
        "timeZone" to java.util.TimeZone.getDefault().id,
        "generatedAt" to now.toString(),
        "cpuCores" to Runtime.getRuntime().availableProcessors().toString(),
        "heapMaxMb" to (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toString(),
        "dataFileMb" to dataFileSizeMb()
    )

    /** 诊断需要知道设置的整体形态，但密码类字段一律只报「是否设置」 */
    private fun settingsInfo(settings: AppSettings): Map<String, String> = linkedMapOf(
        "uiStyleIndex" to settings.uiStyleIndex.toString(),
        "themeModeIndex" to settings.themeModeIndex.toString(),
        "monetEnabled" to settings.monetEnabled.toString(),
        "languageIndex" to settings.languageIndex.toString(),
        "uiScalePercent" to settings.uiScalePercent.toString(),
        "pureBlackInDark" to settings.pureBlackInDark.toString(),
        "proxyTypeIndex" to settings.proxyTypeIndex.toString(),
        "proxyHost" to settings.proxyHost,
        "proxyPort" to settings.proxyPort,
        "proxyAuthSet" to (settings.proxyUsername.isNotBlank() || settings.proxyPassword.isNotBlank()).toString(),
        "webdavUrl" to settings.webdavUrl,
        "webdavAuthSet" to (settings.webdavUsername.isNotBlank() || settings.webdavPassword.isNotBlank()).toString(),
        "autoBackupEnabled" to settings.autoBackupEnabled.toString(),
        "autoBackupIntervalIndex" to settings.autoBackupIntervalIndex.toString(),
        "webhookEnabled" to settings.webhookEnabled.toString(),
        "webhookUrl" to settings.webhookUrl,
        "webhookFormatIndex" to settings.webhookFormatIndex.toString(),
        "monitorNotify" to settings.monitorNotify.toString(),
        "floatingBottomBar" to settings.floatingBottomBar.toString(),
        "blurEnabled" to settings.blurEnabled.toString(),
        "bottomBarLabelVisible" to settings.bottomBarLabelVisible.toString()
    )

    /** 主数据库体积（含 WAL），作为数据量的排查基线 */
    private fun dataFileSizeMb(): String {
        val dbFile = context.getDatabasePath("pulse.db")
        val bytes = dbFile.length() + File(dbFile.path + "-wal").length() + File(dbFile.path + "-shm").length()
        return "%.2f".format(bytes / 1024.0 / 1024.0)
    }
}
