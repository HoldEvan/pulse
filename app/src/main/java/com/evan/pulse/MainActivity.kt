package com.evan.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.data.backup.BackupService
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.ui.PulseApp
import com.evan.pulse.ui.l10n.LocalizedRoot
import com.evan.pulse.ui.l10n.fromIndex
import com.evan.pulse.ui.settings.SettingsViewModel
import com.evan.pulse.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsViewModel: SettingsViewModel

    /** SAF 保存对话框：导出本地备份 */
    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) settingsViewModel.onBackupFilePicked(uri) else settingsViewModel.onBackupFileCancelled()
    }

    /** SAF 选择对话框：读取本地备份恢复 */
    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) settingsViewModel.onBackupFilePicked(uri) else settingsViewModel.onBackupFileCancelled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsViewModel = viewModelProvider((application as PulseApp).settingsStore)
        settingsViewModel.setBackupLauncherHandlers(
            onSave = { createBackupFile.launch("pulse_backup.json") },
            onImport = { openBackupFile.launch(arrayOf("application/json", "*/*")) },
        )
        handleShortcutIntent(intent)

        // 返回事件由 miuix-nav 的返回驱动接管：栈上有页面时预测性返回逐层弹出，
        // 栈底时无启用的回调，系统默认行为（退出应用）自然生效
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(intent?.action) {
                if (intent?.action == ACTION_OPEN_TOOLS) settingsViewModel.selectTab(0)
            }
            LocalizedRoot(language = fromIndex(settings.languageIndex)) {
                PulseTheme(settings) {
                    PulseApp(settingsViewModel = settingsViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: android.content.Intent?) {
        if (intent?.action != ACTION_OPEN_TOOLS) return
        settingsViewModel.selectTab(0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun viewModelProvider(store: SettingsStore): SettingsViewModel {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    store = store,
                    backupService = BackupService(applicationContext),
                    cacheCleaner = CacheCleaner(applicationContext),
                ) as T
        }
        return ViewModelProvider(this, factory)[SettingsViewModel::class.java]
    }

    companion object {
        const val ACTION_OPEN_TOOLS = "com.evan.pulse.action.OPEN_TOOLS"
    }
}
