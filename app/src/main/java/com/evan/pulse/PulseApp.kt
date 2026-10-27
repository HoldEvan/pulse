package com.evan.pulse

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.evan.pulse.data.backup.AutoBackupScheduler
import com.evan.pulse.data.backup.WorkManagerBackupScheduler
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.ui.l10n.L10n

class PulseApp : Application() {

    /** 全局唯一设置存储，供设置页和各协议页面共用 */
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(applicationContext)
        L10n.bind(applicationContext)
        ServiceLocator.bindSettingsStore(settingsStore)
        ServiceLocator.init(applicationContext)
        ServiceLocator.initMonitor(applicationContext)

        // 自动备份：订阅设置变化，把最新开关与周期推给周期任务
        AutoBackupScheduler(
            store = settingsStore,
            background = WorkManagerBackupScheduler(applicationContext)
        ).start()

        // App 切到后台时兜底落盘，避免进程被回收时丢失最后一次变更
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                ServiceLocator.flush()
            }
        })
    }
}
