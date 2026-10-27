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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseApp : Application() {

    /** 全局唯一设置存储，供设置页和各协议页面共用 */
    lateinit var settingsStore: SettingsStore
        private set

    /** 后台初始化协程作用域：Room 全量读库不阻塞主线程，尽早让出首帧 */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(applicationContext)
        L10n.bind(applicationContext)
        ServiceLocator.bindSettingsStore(settingsStore)

        // 主线程瞬时注入 context/通知器/调度器：设置页等首帧会访问这些
        // checkNotNull getter，若放后台会因读库耗时被首帧抢先而崩溃
        ServiceLocator.initQuick(applicationContext)

        // Room 全量读库是冷启动慢的元凶（随拨测历史/cookie 数据量增长），
        // 移到后台让首帧尽快绘制；完成前各 Store 显示空数据，读完后自动刷新
        backgroundScope.launch { ServiceLocator.loadHeavy(applicationContext) }

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
