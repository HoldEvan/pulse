package com.evan.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.data.backup.BackupService
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.ui.PulseApp
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

        // 设置页有内容时拦截系统返回事件走页面栈，只在栈底时才放行退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (settingsViewModel.nav.value.canGoBack) {
                    settingsViewModel.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            PulseTheme(settings) {
                PulseApp(settingsViewModel = settingsViewModel)
            }
        }
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
}
