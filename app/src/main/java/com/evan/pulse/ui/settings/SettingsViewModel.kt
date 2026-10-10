package com.evan.pulse.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.backup.BackupService
import com.evan.pulse.data.backup.RemoteBackupEntry
import com.evan.pulse.data.backup.WebDavConfig
import com.evan.pulse.data.backup.WebDavStage
import com.evan.pulse.data.backup.WebDavTestResult
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.data.cache.CacheStats
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.R
import com.evan.pulse.ui.LEVEL_HISTORY_DETAIL
import com.evan.pulse.ui.LEVEL_SCRIPT_DOCS
import com.evan.pulse.ui.LEVEL_SCRIPT_EDITOR
import com.evan.pulse.ui.NavState
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 备份状态：空闲 / 进行中 / 成功 / 失败 */
data class BackupStatus(
    val busy: Boolean = false,
    /** 最近一次备份或恢复的结果文案，null 表示尚未执行 */
    val message: String? = null,
    val success: Boolean = true,
)

/** WebDAV 远程备份列表状态 */
data class RemoteBackupState(
    val loading: Boolean = false,
    val entries: List<RemoteBackupEntry> = emptyList(),
    /** 读取列表失败时的错误文案 */
    val error: String? = null,
)

/** WebDAV 连接测试状态：busy 为探测进行中，result 为最近一次结果 */
data class WebDavTestState(
    val busy: Boolean = false,
    val result: WebDavTestResult? = null,
)

class SettingsViewModel(
    private val store: SettingsStore,
    private val backupService: BackupService,
    private val cacheCleaner: CacheCleaner,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings

    private val _nav = MutableStateFlow(NavState())

    /** 页面栈，由界面层驱动，不持久化 */
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    private val _backupStatus = MutableStateFlow(BackupStatus())

    /** 备份/恢复的进度与结果，供界面显示 */
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val _remoteBackups = MutableStateFlow(RemoteBackupState())

    /** WebDAV 远程备份列表 */
    val remoteBackups: StateFlow<RemoteBackupState> = _remoteBackups.asStateFlow()

    private val _webDavTest = MutableStateFlow(WebDavTestState())

    /** WebDAV 连接测试的进度与结果 */
    val webDavTest: StateFlow<WebDavTestState> = _webDavTest.asStateFlow()

    private val _cacheStats = MutableStateFlow(cacheCleaner.stats())

    /** 当前本地缓存占用 */
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private val _cacheBusy = MutableStateFlow(false)

    /** 缓存清理是否进行中 */
    val cacheBusy: StateFlow<Boolean> = _cacheBusy.asStateFlow()

    /** 标记下一步 SAF 选择是导出保存，还是恢复读取 */
    private var pendingExport = false

    /** SAF 选择回调，由持有 Activity 注册器的调用方提供 */
    private var onSaveRequested: (() -> Unit)? = null

    private var onImportRequested: (() -> Unit)? = null

    fun setBackupLauncherHandlers(onSave: () -> Unit, onImport: () -> Unit) {
        onSaveRequested = onSave
        onImportRequested = onImport
    }

    fun push(level: Int) {
        _nav.value = _nav.value.copy(stack = _nav.value.stack + level, historyEntryId = null, scriptTarget = null)
    }

    /** 打开某条历史记录的详情页，记录 id 随栈帧一起保存 */
    fun pushHistoryDetail(entryId: Long) {
        _nav.value = _nav.value.copy(
            stack = _nav.value.stack + LEVEL_HISTORY_DETAIL,
            historyEntryId = entryId,
            scriptTarget = null
        )
    }

    /** 打开脚本代码编辑器，target 0 为前置脚本、1 为后置脚本 */
    fun pushScriptEditor(target: Int) {
        _nav.value = _nav.value.copy(
            stack = _nav.value.stack + LEVEL_SCRIPT_EDITOR,
            historyEntryId = null,
            scriptTarget = target
        )
    }

    /** 打开脚本内置 API 文档页，与编辑器共用 target 语义 */
    fun pushScriptDocs(target: Int) {
        _nav.value = _nav.value.copy(
            stack = _nav.value.stack + LEVEL_SCRIPT_DOCS,
            historyEntryId = null,
            scriptTarget = target
        )
    }

    fun goBack() {
        if (!_nav.value.canGoBack) return
        _nav.value = _nav.value.copy(
            stack = _nav.value.stack.dropLast(1),
            historyEntryId = null,
            scriptTarget = null
        )
    }

    fun selectTab(index: Int) {
        _nav.value = _nav.value.copy(selectedTab = index)
    }

    /** 首页协议子页切换，跟随主 tab 一起保存 */
    fun selectHomeProtocol(index: Int) {
        _nav.value = _nav.value.copy(homeProtocolTab = index)
    }

    /** 从集合跳回首页并切到对应协议页，返回时回到主 tab 层级 */
    fun openHomeProtocol(index: Int) {
        _nav.value = _nav.value.copy(selectedTab = 0, homeProtocolTab = index)
    }

    fun setThemeMode(index: Int) {
        viewModelScope.launch { store.update { it.copy(themeModeIndex = index) } }
    }

    fun setKeyColor(index: Int) {
        viewModelScope.launch { store.update { it.copy(keyColorIndex = index) } }
    }

    fun setPaletteStyle(index: Int) {
        viewModelScope.launch { store.update { it.copy(paletteStyleIndex = index) } }
    }

    fun setColorSpec(index: Int) {
        viewModelScope.launch { store.update { it.copy(colorSpecIndex = index) } }
    }

    fun setBackGestureMode(index: Int) {
        viewModelScope.launch { store.update { it.copy(backGestureModeIndex = index) } }
    }

    fun setMonitorNotify(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(monitorNotify = enabled) } }
    }

    fun setFloatingBottomBar(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(floatingBottomBar = enabled) } }
    }

    fun setLiquidGlassBottomBar(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(liquidGlassBottomBar = enabled) } }
    }

    fun setLanguage(index: Int) {
        viewModelScope.launch { store.update { it.copy(languageIndex = index) } }
    }

    fun setProxyType(index: Int) {
        viewModelScope.launch { store.update { it.copy(proxyTypeIndex = index) } }
    }

    fun setProxyHost(value: String) {
        viewModelScope.launch { store.update { it.copy(proxyHost = value) } }
    }

    fun setProxyPort(value: String) {
        viewModelScope.launch { store.update { it.copy(proxyPort = value) } }
    }

    fun setProxyUsername(value: String) {
        viewModelScope.launch { store.update { it.copy(proxyUsername = value) } }
    }

    fun setProxyPassword(value: String) {
        viewModelScope.launch { store.update { it.copy(proxyPassword = value) } }
    }

    fun setWebdavUrl(value: String) {
        viewModelScope.launch { store.update { it.copy(webdavUrl = value) } }
    }

    fun setWebdavPath(value: String) {
        viewModelScope.launch { store.update { it.copy(webdavPath = value) } }
    }

    fun setWebdavUsername(value: String) {
        viewModelScope.launch { store.update { it.copy(webdavUsername = value) } }
    }

    fun setWebdavPassword(value: String) {
        viewModelScope.launch { store.update { it.copy(webdavPassword = value) } }
    }

    /** WebDAV 地址是否已填写 */
    fun isWebdavConfigured(settings: AppSettings): Boolean =
        settings.webdavUrl.isNotBlank()

    /** 请求导出到本地：拉起 SAF 保存对话框，回传后写入当前数据 */
    fun exportLocal() {
        markBusy()
        pendingExport = true
        onSaveRequested?.invoke()
    }

    /** 请求从本地恢复：拉起 SAF 文件选择对话框 */
    fun importLocal() {
        markBusy()
        pendingExport = false
        onImportRequested?.invoke()
    }

    /** SAF 对话框被取消：仅复位状态，不做任何写入或恢复 */
    fun onBackupFileCancelled() {
        pendingExport = false
        markOk(null)
    }

    /** SAF 回传后：导出模式复制当前数据，恢复模式从 Uri 读回数据 */
    fun onBackupFilePicked(uri: Uri) {
        viewModelScope.launch {
            val exporting = pendingExport
            pendingExport = false
            try {
                if (exporting) {
                    backupService.copyToUri(uri)
                    markOk(null)
                } else {
                    backupService.importLocal(uri)
                    markOk(null)
                }
            } catch (e: Exception) {
                markFail(e)
            }
        }
    }

    /** 测试 WebDAV 连接：探测连通性、认证，通过后验证目录可写性 */
    fun testWebDav(settings: AppSettings) {
        if (!isWebdavConfigured(settings)) return
        _webDavTest.value = WebDavTestState(busy = true)
        viewModelScope.launch {
            val result = runCatching { backupService.testWebDav(webDavConfigOf(settings)) }
                .getOrElse {
                    WebDavTestResult(
                        ok = false,
                        probe = WebDavStage(false, null, it.message ?: "连接失败", 0L),
                        write = null,
                        elapsedMs = 0L
                    )
                }
            _webDavTest.value = WebDavTestState(result = result)
        }
    }

    /** 请求导出到 WebDAV */
    fun exportToWebDav(settings: AppSettings) {
        viewModelScope.launch {
            markBusy()
            try {
                val fileUrl = backupService.exportToWebDav(webDavConfigOf(settings))
                val fileName = fileUrl.substringAfterLast('/')
                markOk(L10n.t(R.string.backup_done_export_webdav, fileName))
            } catch (e: Exception) {
                markFail(e)
            }
        }
    }

    /** 刷新 WebDAV 远程备份列表 */
    fun refreshRemoteBackups(settings: AppSettings) {
        if (!isWebdavConfigured(settings)) return
        _remoteBackups.value = RemoteBackupState(loading = true)
        viewModelScope.launch {
            try {
                val entries = backupService.listRemote(webDavConfigOf(settings))
                _remoteBackups.value = RemoteBackupState(entries = entries)
            } catch (e: Exception) {
                _remoteBackups.value = RemoteBackupState(
                    error = e.message?.ifBlank { null },
                )
            }
        }
    }

    /** 从 WebDAV 指定历史备份恢复，成功后刷新列表 */
    fun restoreFromWebDavUrl(settings: AppSettings, fileUrl: String) {
        viewModelScope.launch {
            markBusy()
            try {
                backupService.restoreFromWebDavUrl(webDavConfigOf(settings), fileUrl)
                markOk(L10n.t(R.string.backup_done_import_webdav, fileUrl.substringAfterLast('/')))
                refreshRemoteBackups(settings)
            } catch (e: Exception) {
                markFail(e)
            }
        }
    }

    /** 删除 WebDAV 远程备份，成功后刷新列表 */
    fun deleteRemoteBackup(settings: AppSettings, fileUrl: String) {
        viewModelScope.launch {
            markBusy()
            try {
                backupService.deleteWebDavUrl(webDavConfigOf(settings), fileUrl)
                markOk(null)
                refreshRemoteBackups(settings)
            } catch (e: Exception) {
                markFail(e)
            }
        }
    }

    /** 清除本地缓存：只删系统缓存目录与残留临时文件，不动用户数据 */
    fun clearCache() {
        if (_cacheBusy.value) return
        _cacheBusy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { cacheCleaner.clear() }
            _cacheStats.value = result.remaining
            _cacheBusy.value = false
            if (result.freed.bytes > 0L) {
                AppToast.show(L10n.t(R.string.cache_cleared, CacheCleaner.formatBytes(result.freed.bytes)))
            }
        }
    }

    private fun webDavConfigOf(settings: AppSettings) = WebDavConfig(
        url = settings.webdavUrl.trim(),
        path = settings.webdavPath.trim(),
        username = settings.webdavUsername.ifBlank { null },
        password = settings.webdavPassword.ifBlank { null },
    )

    private fun markBusy() {
        _backupStatus.value = BackupStatus(busy = true, message = null, success = true)
    }

    private fun markOk(message: String?) {
        _backupStatus.value = BackupStatus(busy = false, message = message, success = true)
    }

    private fun markFail(error: Exception) {
        _backupStatus.value = BackupStatus(
            busy = false,
            message = error.message?.ifBlank { null },
            success = false,
        )
    }
}
