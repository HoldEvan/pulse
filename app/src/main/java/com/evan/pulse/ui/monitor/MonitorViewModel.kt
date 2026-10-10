package com.evan.pulse.ui.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.monitor.MonitorChecker
import com.evan.pulse.data.monitor.MonitorScheduler
import com.evan.pulse.data.monitor.TargetValidation
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.di.MonitorStore
import com.evan.pulse.di.ProjectStore
import com.evan.pulse.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel : ViewModel() {

    private val store: MonitorStore = ServiceLocator.monitorStore
    private val projectStore: ProjectStore = ServiceLocator.projectStore
    private val settingsStore: SettingsStore = ServiceLocator.settingsStore
    private val scheduler: MonitorScheduler = ServiceLocator.monitorScheduler

    val currentProjectId: StateFlow<Long> = projectStore.currentProjectId

    val targets: StateFlow<List<MonitorTarget>> =
        combine(store.targets, projectStore.currentProjectId) { all, id ->
            all.filter { it.projectId == id }.sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val records: StateFlow<List<MonitorRecord>> =
        combine(store.records, projectStore.currentProjectId) { all, id ->
            all.filter { it.projectId == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val runs: StateFlow<Map<Long, MonitorRun>> = scheduler.runs

    val notifyEnabled: StateFlow<Boolean> = settingsStore.settings
        .map { it.monitorNotify }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    var showEditor by mutableStateOf(false)
    var creating by mutableStateOf(false)
    var editing by mutableStateOf<MonitorTarget?>(null)
    var nameInput by mutableStateOf("")
    var groupInput by mutableStateOf("")
    var type by mutableStateOf(MonitorType.HTTP)
    var hostInput by mutableStateOf("")
    var portInput by mutableStateOf("443")
    var urlInput by mutableStateOf("")
    var timeoutMs by mutableStateOf(5000L)
    var intervalSec by mutableStateOf(60L)
    var statusMinInput by mutableStateOf("200")
    var statusMaxInput by mutableStateOf("399")
    var enabled by mutableStateOf(true)
    var deleteTarget by mutableStateOf<MonitorTarget?>(null)
    var clearRecordsAsked by mutableStateOf(false)

    /** 未通记录二级页的搜索关键词 */
    var recordQuery by mutableStateOf("")

    /** 全部拨测目标按 id 索引，供未通记录页按目标地址检索 */
    val targetsById: StateFlow<Map<Long, MonitorTarget>> =
        store.targets
            .map { all -> all.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 编辑器当前草稿的配置是否可保存 */
    val editorValidation: TargetValidation
        get() = MonitorChecker.validation(draft())

    fun openCreate() {
        creating = true
        editing = null
        nameInput = ""
        groupInput = ""
        type = MonitorType.HTTP
        hostInput = ""
        portInput = "443"
        urlInput = ""
        timeoutMs = 5000L
        intervalSec = 60L
        statusMinInput = "200"
        statusMaxInput = "399"
        enabled = true
        showEditor = true
    }

    fun openEdit(target: MonitorTarget) {
        creating = false
        editing = target
        nameInput = target.name
        groupInput = target.group
        type = target.type
        hostInput = target.host
        portInput = target.port.toString()
        urlInput = target.url
        timeoutMs = target.timeoutMs
        intervalSec = target.intervalSec
        statusMinInput = target.statusMin.toString()
        statusMaxInput = target.statusMax.toString()
        enabled = target.enabled
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        editing = null
        creating = false
    }

    fun save() {
        if (editorValidation is TargetValidation.Invalid) return
        val draft = draft()
        if (creating) {
            store.create(draft)
        } else {
            editing?.let { store.update(draft) }
        }
        closeEditor()
        scheduler.sync()
    }


    fun toggleEnabled(target: MonitorTarget) {
        store.setEnabled(target.id, !target.enabled)
        scheduler.sync()
    }

    fun requestDelete(target: MonitorTarget) {
        deleteTarget = target
    }

    fun confirmDelete() {
        deleteTarget?.let { target ->
            store.delete(target.id)
            scheduler.sync()
        }
        deleteTarget = null
    }

    fun cancelDelete() {
        deleteTarget = null
    }

    fun requestClearRecords() {
        clearRecordsAsked = true
    }

    fun confirmClearRecords() {
        store.clearRecords(currentProjectId.value)
        clearRecordsAsked = false
    }

    fun cancelClearRecords() {
        clearRecordsAsked = false
    }

    fun setNotifyEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it.copy(monitorNotify = value) }
        }
    }

    private fun draft(): MonitorTarget {
        val existing = editing
        return MonitorTarget(
            id = existing?.id ?: 0L,
            projectId = existing?.projectId ?: currentProjectId.value,
            name = nameInput.trim(),
            group = groupInput.trim(),
            type = type,
            host = hostInput.trim(),
            port = portInput.trim().toIntOrNull() ?: 0,
            url = urlInput.trim(),
            timeoutMs = timeoutMs,
            intervalSec = intervalSec,
            statusMin = statusMinInput.trim().toIntOrNull() ?: 0,
            statusMax = statusMaxInput.trim().toIntOrNull() ?: 0,
            enabled = enabled,
            createdAt = existing?.createdAt ?: 0L
        )
    }
}
