package com.evan.pulse.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.tools.DefaultHostProbe
import com.evan.pulse.domain.tools.HostProbe
import com.evan.pulse.domain.tools.LanHost
import com.evan.pulse.domain.tools.LanNetwork
import com.evan.pulse.domain.tools.LanNetworkInfo
import com.evan.pulse.domain.tools.ipv4ToLong
import com.evan.pulse.domain.tools.lanScan
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.evan.pulse.R
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.l10n.L10n
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType

/** 局域网扫描：检测网段、并发探测在线主机，结果按数值 IP 升序。扫描为会话内临时结果，不持久化。 */
class LanScanViewModel : ViewModel() {

    private val probe: HostProbe = DefaultHostProbe()

    var networkInfo by mutableStateOf<LanNetworkInfo?>(LanNetwork.detect(ServiceLocator.appContext))
    var hosts by mutableStateOf<List<LanHost>>(emptyList())
    var scanning by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var total by mutableStateOf(0)
        private set

    private var scanJob: Job? = null

    fun addToMonitor(host: LanHost) {
        val store = ServiceLocator.monitorStore
        val projectId = ServiceLocator.projectStore.currentProjectId.value
        if (store.targets.value.any { it.projectId == projectId && it.type == MonitorType.IP && it.host == host.ip }) {
            AppToast.show(L10n.t(R.string.lan_scan_monitor_exists))
            return
        }
        store.create(MonitorTarget(id = 0L, projectId = projectId, name = host.ip, type = MonitorType.IP, host = host.ip))
        ServiceLocator.monitorScheduler.sync()
        AppToast.show(L10n.t(R.string.lan_scan_monitor_added))
    }

    fun start() {
        if (scanning) return
        networkInfo = LanNetwork.detect(ServiceLocator.appContext)
        val info = networkInfo ?: run {
            AppToast.show(L10n.t(R.string.lan_scan_no_lan))
            return
        }
        hosts = emptyList()
        progress = 0
        total = info.hosts.size
        scanning = true
        scanJob = viewModelScope.launch {
            try {
                lanScan(
                    hosts = info.hosts,
                    probe = probe,
                    context = Dispatchers.Main.immediate,
                    onHost = { host ->
                        hosts = (hosts + host).sortedBy { ipv4ToLong(it.ip) }
                    },
                    onProgress = { scanned, totalHosts ->
                        progress = scanned
                        total = totalHosts
                    }
                )
                AppToast.show(L10n.t(R.string.lan_scan_complete, hosts.size))
                if (ServiceLocator.settingsStore.settings.value.monitorNotify) {
                    ServiceLocator.monitorNotifier.postScanCompleted(hosts.size)
                }
            } finally {
                scanning = false
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanning = false
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
