package com.evan.pulse.ui.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorAssertion
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.monitor.MonitorChecker
import com.evan.pulse.data.monitor.MonitorScheduler
import com.evan.pulse.data.monitor.MonitorTrend
import com.evan.pulse.data.monitor.TrendStats
import com.evan.pulse.data.monitor.TargetValidation
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.di.MonitorStore
import com.evan.pulse.di.ProjectStore
import com.evan.pulse.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** 当前项目的全部拨测记录（通与不通都在内），供记录页筛选展示 */
    val records: StateFlow<List<MonitorRecord>> =
        combine(store.records, projectStore.currentProjectId) { all, id ->
            all.filter { it.projectId == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 首页只展示未通记录 */
    val failedRecords: StateFlow<List<MonitorRecord>> =
        records.map { list -> list.filter { !it.ok } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
    var latencyInput by mutableStateOf("0")
    var enabled by mutableStateOf(true)
    /** HTTP 拨测方法，默认 GET */
    var method by mutableStateOf("GET")
    /** HTTP 拨测请求头 */
    var headers by mutableStateOf(listOf<KeyValue>())
    /** HTTP 拨测请求体类型：NONE / JSON / RAW */
    var bodyType by mutableStateOf("NONE")
    /** HTTP 拨测请求体文本 */
    var bodyText by mutableStateOf("")
    /** HTTP 拨测鉴权：NONE / BEARER / BASIC / APIKEY */
    var authType by mutableStateOf("NONE")
    var bearerToken by mutableStateOf("")
    var basicUsername by mutableStateOf("")
    var basicPassword by mutableStateOf("")
    var apiKeyName by mutableStateOf("")
    var apiKeyValue by mutableStateOf("")
    /** HTTP 拨测响应断言 */
    var assertions by mutableStateOf(listOf<MonitorAssertion>())
    var deleteTarget by mutableStateOf<MonitorTarget?>(null)
    var clearRecordsAsked by mutableStateOf(false)

    /** 记录二级页的搜索关键词 */
    var recordQuery by mutableStateOf("")

    /** 记录二级页的通断筛选 */
    var recordStatusFilter by mutableStateOf(RecordStatusFilter.ALL)

    /** 记录二级页的类型筛选；null 表示不限类型 */
    var recordTypeFilter by mutableStateOf<MonitorType?>(null)

    /** 记录二级页的目标限定；null 表示看全部目标的记录 */
    var recordTargetId by mutableStateOf<Long?>(null)

    /** 从某个拨测项进入记录页：只看该目标的记录，类型固定因此清空类型筛选 */
    fun openRecordsForTarget(target: MonitorTarget) {
        recordTargetId = target.id
        recordTypeFilter = null
    }

    /** 从「更多」进入记录页：清除目标限定，恢复全量列表 */
    fun openAllRecords() {
        recordTargetId = null
    }

    /** 清除目标限定，保留其余筛选条件 */
    fun clearRecordTarget() {
        recordTargetId = null
    }

    /** 全部拨测目标按 id 索引，供未通记录页按目标地址检索 */
    val targetsById: StateFlow<Map<Long, MonitorTarget>> =
        store.targets
            .map { all -> all.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 趋势页当前选中的目标 id；null 表示趋势页未打开 */
    private val _trendTargetId = MutableStateFlow<Long?>(null)

    /** 趋势页数据：随历史样本与当前目标实时重算 */
    val trendStats: StateFlow<TrendStats?> =
        combine(store.history, _trendTargetId) { history, targetId ->
            if (targetId == null) return@combine null
            MonitorTrend.compute(history.filter { it.targetId == targetId })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openTrend(target: MonitorTarget) {
        _trendTargetId.value = target.id
    }

    fun closeTrend() {
        _trendTargetId.value = null
    }

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
        latencyInput = "0"
        enabled = true
        method = "GET"
        headers = emptyList()
        bodyType = "NONE"
        bodyText = ""
        authType = "NONE"
        bearerToken = ""
        basicUsername = ""
        basicPassword = ""
        apiKeyName = ""
        apiKeyValue = ""
        assertions = emptyList()
        showEditor = true
    }

    fun openCreateIp(host: String) {
        openCreate()
        type = MonitorType.IP
        hostInput = host
        nameInput = host
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
        latencyInput = target.latencyThresholdMs.toString()
        enabled = target.enabled
        method = target.method
        headers = target.headers.map { KeyValue(it.key, it.value, it.enabled) }
        bodyType = target.bodyType
        bodyText = target.bodyText
        authType = target.authType
        bearerToken = target.bearerToken
        basicUsername = target.basicUsername
        basicPassword = target.basicPassword
        apiKeyName = target.apiKeyName
        apiKeyValue = target.apiKeyValue
        assertions = target.assertions
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
            latencyThresholdMs = latencyInput.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            enabled = enabled,
            createdAt = existing?.createdAt ?: 0L,
            method = method.trim().uppercase(),
            headers = headers.filter { it.key.isNotBlank() }
                .map { KeyValueEntry(it.key, it.value, it.enabled) },
            bodyType = bodyType,
            bodyText = bodyText,
            authType = authType,
            bearerToken = bearerToken.trim(),
            basicUsername = basicUsername.trim(),
            basicPassword = basicPassword,
            apiKeyName = apiKeyName.trim(),
            apiKeyValue = apiKeyValue.trim(),
            assertions = assertions.filter { it.expression.isNotBlank() }
        )
    }
}

/** 记录页的通断筛选 */
enum class RecordStatusFilter { ALL, PASSED, FAILED }
