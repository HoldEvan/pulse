package com.evan.pulse.di

import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.ImportResult
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MONITOR_RECORD_LIMIT
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.monitor.MonitorChecker
import com.evan.pulse.data.monitor.MonitorNotifier
import com.evan.pulse.data.monitor.MonitorScheduler
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.data.persistence.AppStatePersistence
import com.evan.pulse.domain.AuthApplier
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.MqttConfig
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.domain.model.MqttEvent
import com.evan.pulse.domain.model.SseConfig
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.model.SseEvent
import com.evan.pulse.domain.model.WsConfig
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.model.WsEvent
import com.evan.pulse.domain.net.HttpEngine
import com.evan.pulse.domain.net.MqttEngine
import com.evan.pulse.domain.net.OkHttpEngine
import com.evan.pulse.domain.net.SseEngine
import com.evan.pulse.domain.net.WsEngine
import com.evan.pulse.domain.script.RhinoScriptExecutor
import com.evan.pulse.domain.script.ScriptEnvWriter
import android.content.Context
import android.util.Base64
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimplePlaceholderResolver : PlaceholderResolver {
    override fun resolve(text: String, vars: Map<String, String>): PlaceholderResolver.ResolveResult {
        val missing = PlaceholderResolver.PLACEHOLDER_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in vars }
            .distinct()
            .toList()
        if (missing.isNotEmpty()) return PlaceholderResolver.ResolveResult.Missing(missing)
        val resolved = PlaceholderResolver.PLACEHOLDER_REGEX.replace(text) { m ->
            vars[m.groupValues[1]] ?: m.value
        }
        return PlaceholderResolver.ResolveResult.Ok(resolved)
    }
}

class SimpleAuthApplier : AuthApplier {
    override fun apply(config: AuthConfig): List<KeyValue> = when (config.type) {
        com.evan.pulse.domain.model.AuthType.NONE -> emptyList()
        com.evan.pulse.domain.model.AuthType.BEARER -> listOf(
            KeyValue("Authorization", "Bearer ${config.bearerToken}")
        )

        com.evan.pulse.domain.model.AuthType.BASIC -> {
            val raw = "${config.basicUsername}:${config.basicPassword}"
            listOf(KeyValue("Authorization", "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)))
        }

        com.evan.pulse.domain.model.AuthType.APIKEY -> {
            if (config.apiKeyValue.isBlank()) {
                emptyList()
            } else {
                listOf(KeyValue(config.apiKeyName.ifBlank { "X-API-Key" }, config.apiKeyValue))
            }
        }
    }
}

/**
 * 脚本写变量的落地实现：只写到当前项目已激活的那个环境。
 *
 * 没有激活环境时写入不落盘，脚本本次请求内仍然能读到，
 * 但不会偷偷帮用户创建或激活一个环境。
 */
class ActiveEnvWriter : ScriptEnvWriter {
    override fun replaceActiveVars(variables: List<Pair<String, String>>) {
        val projectId = ServiceLocator.projectStore.currentProjectId.value
        val envId = ServiceLocator.envStore.activeEnvId(projectId) ?: return
        ServiceLocator.envStore.setVariables(envId, variables)
    }

    override fun removeActiveVar(name: String) {
        val projectId = ServiceLocator.projectStore.currentProjectId.value
        val envId = ServiceLocator.envStore.activeEnvId(projectId) ?: return
        val env = ServiceLocator.envStore.environments.value.firstOrNull { it.id == envId } ?: return
        ServiceLocator.envStore.setVariables(envId, env.variables.filter { it.first != name })
    }
}

class StubWsEngine : WsEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(WsConnectionState.IDLE)
    override val state: StateFlow<WsConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<WsEvent> = _events.asSharedFlow()
    private var closed = CompletableDeferred<Unit>()

    override suspend fun connect(config: WsConfig) {
        _state.value = WsConnectionState.CONNECTING
        delay(700)
        closed = CompletableDeferred()
        _state.value = WsConnectionState.CONNECTED
        _events.emit(WsEvent.Connected(System.currentTimeMillis()))
        _events.emit(
            WsEvent.TextReceived(
                "{\"stub\":true,\"message\":\"WebSocket 引擎尚未接入，这是本地模拟连接\"}",
                System.currentTimeMillis()
            )
        )
    }

    override suspend fun send(text: String) {
        if (_state.value != WsConnectionState.CONNECTED) return
        _events.emit(WsEvent.Sent(text, System.currentTimeMillis()))
        scope.launch {
            delay(300)
            _events.emit(
                WsEvent.TextReceived(
                    text.reversed().ifEmpty { text },
                    System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun close() {
        _state.value = WsConnectionState.CLOSED
        _events.emit(WsEvent.Closed("用户断开 (stub)", System.currentTimeMillis()))
        closed.complete(Unit)
    }
}

/** 本地模拟 SSE 引擎：连接后按固定间隔推送事件帧，用于在网络层接入前验证 UI 与数据流 */
class StubSseEngine : SseEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SseConnectionState.IDLE)
    override val state: StateFlow<SseConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    @Volatile private var streamJob: Job? = null
    @Volatile private var eventFilter = ""

    override suspend fun connect(config: SseConfig) {
        _state.value = SseConnectionState.CONNECTING
        eventFilter = config.eventFilter.trim()
        delay(700)
        _state.value = SseConnectionState.CONNECTED
        _events.emit(SseEvent.Connected(System.currentTimeMillis()))
        _events.emit(SseEvent.Comment("SSE 引擎尚未接入，以下为本地模拟事件流", System.currentTimeMillis()))

        streamJob?.cancel()
        streamJob = scope.launch {
            var index = 0
            while (true) {
                index++
                val name = eventFilter.ifEmpty { "message" }
                _events.emit(
                    SseEvent.FrameReceived(
                        id = index.toString(),
                        event = name,
                        data = """{"stub":true,"index":$index,"url":"${config.url}","timestamp":${System.currentTimeMillis()}}""",
                        timestamp = System.currentTimeMillis()
                    )
                )
                delay(2_000)
            }
        }
    }

    override suspend fun close() {
        streamJob?.cancel()
        streamJob = null
        _state.value = SseConnectionState.CLOSED
        _events.emit(SseEvent.Closed("用户断开 (stub)", System.currentTimeMillis()))
    }
}

class StubMqttEngine : MqttEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MqttConnectionState.IDLE)
    override val state: StateFlow<MqttConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<MqttEvent> = _events.asSharedFlow()
    private val subscribed = Mutex()

    override suspend fun connect(config: MqttConfig) {
        _state.value = MqttConnectionState.CONNECTING
        delay(800)
        _state.value = MqttConnectionState.CONNECTED
        _events.emit(MqttEvent.Connected(801, System.currentTimeMillis()))
    }

    override suspend fun subscribe(topic: String, qos: Int) {
        subscribed.withLock { delay(200) }
        _events.emit(
            MqttEvent.MessageReceived(
                topic,
                "{\"stub\":true,\"message\":\"MQTT 引擎尚未接入，这是本地模拟订阅确认\"}",
                qos,
                System.currentTimeMillis()
            )
        )
    }

    override suspend fun publish(topic: String, payload: String, qos: Int) {
        scope.launch {
            delay(400)
            _events.emit(MqttEvent.MessageReceived(topic, payload, qos, System.currentTimeMillis()))
        }
    }

    override suspend fun disconnect() {
        _state.value = MqttConnectionState.DISCONNECTED
        _events.emit(MqttEvent.Disconnected("用户断开 (stub)", System.currentTimeMillis()))
    }
}

class ProjectStore(private val persist: PersistenceHandle) {
    private val _projects = MutableStateFlow(
        listOf(ProjectInfo(1, "默认项目"))
    )
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()
    private val _currentProjectId = MutableStateFlow(1L)
    val currentProjectId: StateFlow<Long> = _currentProjectId.asStateFlow()
    private var nextId = 2L

    fun create(name: String) {
        _projects.value = _projects.value + ProjectInfo(nextId++, name)
        persist.save()
    }

    fun rename(id: Long, name: String) {
        _projects.value = _projects.value.map { if (it.id == id) it.copy(name = name) else it }
        persist.save()
    }

    fun delete(id: Long) {
        if (_projects.value.size <= 1) return
        _projects.value = _projects.value.filter { it.id != id }
        if (_currentProjectId.value == id) _currentProjectId.value = _projects.value.first().id
        persist.save()
    }

    fun switch(id: Long) {
        _currentProjectId.value = id
        persist.save()
    }

    fun snapshot() = ProjectStoreSnapshot(_projects.value, _currentProjectId.value, nextId)

    fun restore(state: ProjectStoreSnapshot) {
        val projects = state.projects.ifEmpty { _projects.value }
        val current = state.currentProjectId.takeIf { id -> projects.any { it.id == id } }
            ?: projects.first().id
        _projects.value = projects
        _currentProjectId.value = current
        nextId = state.nextId.coerceAtLeast((projects.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}

class ProjectStoreSnapshot(
    val projects: List<ProjectInfo>,
    val currentProjectId: Long,
    val nextId: Long
)

class HistoryStore(private val persist: PersistenceHandle) {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()
    private var nextId = 1L

    fun add(entry: HistoryEntry) {
        _entries.value = (listOf(entry.copy(id = nextId++)) + _entries.value).take(500)
        persist.save()
    }

    fun clear() {
        _entries.value = emptyList()
        persist.save()
    }

    /** 删除单条历史；返回是否真的删到了 */
    fun delete(id: Long): Boolean {
        val before = _entries.value
        val after = before.filter { it.id != id }
        if (after.size == before.size) return false
        _entries.value = after
        persist.save()
        return true
    }

    fun snapshot() = HistoryStoreSnapshot(_entries.value, nextId)

    fun restore(state: HistoryStoreSnapshot) {
        _entries.value = state.entries
        nextId = state.nextId.coerceAtLeast((state.entries.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}

class HistoryStoreSnapshot(
    val entries: List<HistoryEntry>,
    val nextId: Long
)

class EnvStore(private val persist: PersistenceHandle) {
    private val _environments = MutableStateFlow(
        listOf(EnvironmentInfo(1, 1, "default", true, listOf("baseUrl" to "https://api.example.com")))
    )
    val environments: StateFlow<List<EnvironmentInfo>> = _environments.asStateFlow()
    private var nextId = 2L

    fun activeVars(): Map<String, String> {
        val pid = ServiceLocator.projectStore.currentProjectId.value
        return _environments.value
            .firstOrNull { it.projectId == pid && it.isActive }
            ?.variables?.toMap() ?: emptyMap()
    }

    /** 指定项目已激活环境的变量；没有激活环境时返回空表 */
    fun varsFor(projectId: Long): Map<String, String> =
        _environments.value
            .firstOrNull { it.projectId == projectId && it.isActive }
            ?.variables?.toMap() ?: emptyMap()

    /** 当前项目已激活环境的 id；没有激活环境时返回 null */
    fun activeEnvId(projectId: Long): Long? =
        _environments.value.firstOrNull { it.projectId == projectId && it.isActive }?.id

    fun setActive(projectId: Long, envId: Long, active: Boolean) {
        _environments.value = _environments.value.map {
            if (it.projectId == projectId && it.id == envId) it.copy(isActive = active) else it
        }
        persist.save()
    }

    fun deactivateAll(projectId: Long) {
        _environments.value = _environments.value.map {
            if (it.projectId == projectId) it.copy(isActive = false) else it
        }
        persist.save()
    }

    fun activate(projectId: Long, envId: Long) {
        _environments.value = _environments.value.map {
            when {
                it.projectId == projectId && it.id == envId -> it.copy(isActive = true)
                it.projectId == projectId -> it.copy(isActive = false)
                else -> it
            }
        }
        persist.save()
    }

    fun environmentsFor(projectId: Long): StateFlow<List<EnvironmentInfo>> = _environments

    fun create(projectId: Long, name: String, variables: List<Pair<String, String>> = emptyList()) {
        _environments.value = _environments.value +
            EnvironmentInfo(nextId++, projectId, name, false, variables)
        persist.save()
    }

    fun rename(id: Long, name: String) {
        _environments.value = _environments.value.map { if (it.id == id) it.copy(name = name) else it }
        persist.save()
    }

    fun delete(id: Long) {
        _environments.value = _environments.value.filter { it.id != id }
        persist.save()
    }

    fun setVariables(id: Long, variables: List<Pair<String, String>>) {
        _environments.value = _environments.value.map { if (it.id == id) it.copy(variables = variables) else it }
        persist.save()
    }

    fun snapshot() = EnvStoreSnapshot(_environments.value, nextId)

    fun restore(state: EnvStoreSnapshot) {
        val environments = state.environments.ifEmpty { _environments.value }
        _environments.value = environments
        nextId = state.nextId.coerceAtLeast((environments.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}

class EnvStoreSnapshot(
    val environments: List<EnvironmentInfo>,
    val nextId: Long
)

/** 集合存储：内存态，保存接口快照并提供跨页面加载通道 */
class CollectionStore(private val persist: PersistenceHandle) {
    private val _collections = MutableStateFlow(
        listOf(CollectionInfo(1, 1, null, "默认集合"))
    )
    val collections: StateFlow<List<CollectionInfo>> = _collections.asStateFlow()

    private val _requests = MutableStateFlow(emptyList<SavedRequest>())
    val requests: StateFlow<List<SavedRequest>> = _requests.asStateFlow()

    private var nextCollectionId = 2L
    private var nextRequestId = 1L

    /** 待加载请求：集合页点击后写入，由对应协议页的 ViewModel 消费 */
    val pendingLoad = MutableStateFlow<SavedRequest?>(null)

    /** 新建文件夹；parentId 为空表示在当前项目下建根文件夹 */
    fun create(projectId: Long, name: String, parentId: Long? = null) {
        if (name.isBlank()) return
        _collections.value = _collections.value +
            CollectionInfo(nextCollectionId++, projectId, parentId, name.trim())
        persist.save()
    }

    /** 重命名文件夹；空白名称视为无效，保留原名称 */
    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        _collections.value = _collections.value.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        persist.save()
    }

    /** 递归删除文件夹、其全部后代文件夹及全部后代下的接口条目 */
    fun deleteRecursive(id: Long) {
        val doomed = CollectionTree.descendantsOf(_collections.value, id) + id
        _collections.value = _collections.value.filter { it.id !in doomed }
        _requests.value = _requests.value.filter { it.collectionId !in doomed }
        persist.save()
    }

    /**
     * 把文件夹移到 newParentId 之下，newParentId 为空表示移回根。
     * 目标是自身或自身后代时拒绝并返回 false；跨项目移动时同步整棵子树的所属项目。
     */
    fun move(id: Long, newParentId: Long?): Boolean {
        val list = _collections.value
        val folder = list.firstOrNull { it.id == id } ?: return false
        if (newParentId != null && CollectionTree.isAncestorOrSelf(list, id, newParentId)) {
            return false
        }
        val targetProject = newParentId?.let { targetId ->
            list.firstOrNull { it.id == targetId }?.projectId ?: folder.projectId
        } ?: folder.projectId
        val subtree = CollectionTree.descendantsOf(list, id)
        val remaining = list.filter { it.id !in subtree && it.id != id }
        val relocated = list.filter { it.id == id || it.id in subtree }.map { item ->
            item.copy(
                projectId = targetProject,
                parentId = if (item.id == id) newParentId else item.parentId
            )
        }
        _collections.value = remaining + relocated
        persist.save()
        return true
    }

    fun deleteRequest(id: Long) {
        _requests.value = _requests.value.filter { it.id != id }
        persist.save()
    }

    /** 重命名接口；空白名称视为无效，保留原名称 */
    fun renameRequest(id: Long, name: String) {
        if (name.isBlank()) return
        _requests.value = _requests.value.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        persist.save()
    }

    /** 移动接口到目标集合；目标集合不存在时不动 */
    fun moveRequest(id: Long, collectionId: Long) {
        if (_collections.value.none { it.id == collectionId }) return
        _requests.value = _requests.value.map {
            if (it.id == id) it.copy(collectionId = collectionId) else it
        }
        persist.save()
    }

    /** 保存请求；目标集合缺失时在当前项目下自动建默认集合 */
    fun saveRequest(
        projectId: Long,
        collectionId: Long,
        type: RequestType,
        name: String,
        data: String,
        fallbackCollectionName: String
    ) {
        if (name.isBlank()) return
        val collection = _collections.value.firstOrNull { it.id == collectionId }
            ?: _collections.value.firstOrNull { it.projectId == projectId }
            ?: CollectionInfo(nextCollectionId++, projectId, null, fallbackCollectionName).also {
                _collections.value = _collections.value + it
            }
        _requests.value = listOf(
            SavedRequest(nextRequestId++, collection.id, type, name, data, System.currentTimeMillis())
        ) + _requests.value
        persist.save()
    }

    /**
     * 批量导入：在当前项目根下新建一个集合，分组超过一个时按标签各建一个子集合。
     *
     * 集合与接口一次性写入后只触发一次持久化；空标签视为无分组直接放主集合。
     */
    fun importBatch(projectId: Long, collectionName: String, groups: List<ImportGroup>): ImportResult {
        val name = collectionName.trim()
        if (name.isEmpty()) return ImportResult(name, 0)

        val root = CollectionInfo(nextCollectionId++, projectId, null, name)
        val folders = mutableListOf(root)
        val created = mutableListOf<SavedRequest>()
        val now = System.currentTimeMillis()

        for (group in groups) {
            val target = if (groups.size == 1 || group.tag.isBlank()) {
                root
            } else {
                CollectionInfo(nextCollectionId++, projectId, root.id, group.tag.trim()).also {
                    folders += it
                }
            }
            group.items.forEach { item ->
                created += SavedRequest(
                    id = nextRequestId++,
                    collectionId = target.id,
                    type = RequestType.HTTP,
                    name = item.name,
                    data = item.data,
                    createdAt = now
                )
            }
        }

        _collections.value = _collections.value + folders
        _requests.value = created + _requests.value
        persist.save()
        return ImportResult(name, created.size)
    }

    /** 协议页消费掉待加载请求，避免重复恢复 */
    fun consumePending(request: SavedRequest) {
        if (pendingLoad.value?.id == request.id) pendingLoad.value = null
    }

    fun snapshot() = CollectionStoreSnapshot(
        _collections.value,
        _requests.value,
        nextCollectionId,
        nextRequestId
    )

    fun restore(state: CollectionStoreSnapshot) {
        val collections = state.collections.ifEmpty { _collections.value }
        val requests = state.requests.filter { request ->
            collections.any { it.id == request.collectionId }
        }
        _collections.value = collections
        _requests.value = requests
        nextCollectionId = state.nextCollectionId.coerceAtLeast(
            (collections.maxOfOrNull { it.id } ?: 0L) + 1
        )
        nextRequestId = state.nextRequestId.coerceAtLeast(
            (requests.maxOfOrNull { it.id } ?: 0L) + 1
        )
    }
}

class CollectionStoreSnapshot(
    val collections: List<CollectionInfo>,
    val requests: List<SavedRequest>,
    val nextCollectionId: Long,
    val nextRequestId: Long
)

class MonitorStore(private val persist: PersistenceHandle) {
    private val _targets = MutableStateFlow(emptyList<MonitorTarget>())
    val targets: StateFlow<List<MonitorTarget>> = _targets.asStateFlow()

    private val _records = MutableStateFlow(emptyList<MonitorRecord>())
    val records: StateFlow<List<MonitorRecord>> = _records.asStateFlow()

    private var nextTargetId = 1L
    private var nextRecordId = 1L

    fun targetsFor(projectId: Long): List<MonitorTarget> =
        _targets.value.filter { it.projectId == projectId }

    fun recordsFor(projectId: Long): List<MonitorRecord> =
        _records.value.filter { it.projectId == projectId }

    fun get(id: Long): MonitorTarget? = _targets.value.firstOrNull { it.id == id }

    fun create(target: MonitorTarget): MonitorTarget {
        val created = target.copy(
            id = nextTargetId++,
            createdAt = if (target.createdAt > 0L) target.createdAt else System.currentTimeMillis()
        )
        _targets.value = _targets.value + created
        persist.save()
        return created
    }

    fun update(target: MonitorTarget) {
        _targets.value = _targets.value.map { if (it.id == target.id) target else it }
        persist.save()
    }

    /** 启用与停用只改一个字段，其余配置保持不变 */
    fun setEnabled(id: Long, enabled: Boolean) {
        val before = _targets.value
        val after = before.map { if (it.id == id) it.copy(enabled = enabled) else it }
        if (after == before) return
        _targets.value = after
        persist.save()
    }

    fun delete(id: Long) {
        _targets.value = _targets.value.filter { it.id != id }
        val before = _records.value
        _records.value = before.filter { it.targetId != id }
        if (_records.value == before) return
        persist.save()
    }

    /** 记录一次未通的拨测，最新在前，超出上限丢弃最旧的 */
    fun recordFailure(target: MonitorTarget, message: String, durationMs: Long) {
        val record = MonitorRecord(
            id = nextRecordId++,
            projectId = target.projectId,
            targetId = target.id,
            targetName = target.name,
            type = target.type,
            message = message,
            durationMs = durationMs,
            checkedAt = System.currentTimeMillis()
        )
        _records.value = (listOf(record) + _records.value).take(MONITOR_RECORD_LIMIT)
        persist.save()
    }

    fun clearRecords(projectId: Long) {
        val before = _records.value
        _records.value = before.filter { it.projectId != projectId }
        if (_records.value == before) return
        persist.save()
    }

    fun snapshot() = MonitorStoreSnapshot(_targets.value, _records.value, nextTargetId, nextRecordId)

    fun restore(state: MonitorStoreSnapshot) {
        _targets.value = state.targets
        _records.value = state.records.filter { record ->
            state.targets.any { it.id == record.targetId }
        }
        nextTargetId = state.nextTargetId.coerceAtLeast((state.targets.maxOfOrNull { it.id } ?: 0L) + 1)
        nextRecordId = state.nextRecordId.coerceAtLeast((state.records.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}

class MonitorStoreSnapshot(
    val targets: List<MonitorTarget>,
    val records: List<MonitorRecord>,
    val nextTargetId: Long,
    val nextRecordId: Long
)

/** Store 的持久化回调句柄：init 前为空操作，避免启动早期写入 */
class PersistenceHandle {
    var action: () -> Unit = {}

    fun save() = action()
}

object ServiceLocator {

    private var _settingsStore: SettingsStore? = null
    private var _persistence: AppStatePersistence? = null

    private val persistHandle = PersistenceHandle()

    val projectStore = ProjectStore(persistHandle)
    val historyStore = HistoryStore(persistHandle)
    val envStore = EnvStore(persistHandle)
    val collectionStore = CollectionStore(persistHandle)
    val placeholderResolver: PlaceholderResolver = SimplePlaceholderResolver()
    val authApplier: AuthApplier = SimpleAuthApplier()
    val scriptExecutor: com.evan.pulse.domain.script.ScriptExecutor = RhinoScriptExecutor()
    val scriptEnvWriter: ScriptEnvWriter = ActiveEnvWriter()
    val httpEngine: HttpEngine = OkHttpEngine()
    val wsEngine: WsEngine = StubWsEngine()
    val mqttEngine: MqttEngine = StubMqttEngine()
    val sseEngine: SseEngine = StubSseEngine()

    /** 拨测目标与未通记录。必须在 httpEngine 之后声明，MonitorChecker 依赖它 */
    val monitorStore = MonitorStore(persistHandle)
    val monitorChecker: MonitorChecker = MonitorChecker()

    private var _monitorNotifier: MonitorNotifier? = null
    private var _monitorScheduler: MonitorScheduler? = null

    val monitorNotifier: MonitorNotifier
        get() = checkNotNull(_monitorNotifier) { "MonitorNotifier 尚未初始化" }

    val monitorScheduler: MonitorScheduler
        get() = checkNotNull(_monitorScheduler) { "MonitorScheduler 尚未初始化" }

    /** 由 Application 注入，供各协议页面读取代理等全局设置 */
    fun bindSettingsStore(store: SettingsStore) {
        _settingsStore = store
    }

    /** 初始化数据持久化：注册写盘回调并读回已有数据。必须在 Application.onCreate 中调用 */
    fun init(context: Context) {
        val persistence = AppStatePersistence(context)
        _persistence = persistence
        persistHandle.action = { persistence.save() }
        persistence.load()
    }

    /** 初始化拨测调度：建通知通道并按已启用目标启动定时检查。必须在 init 之后调用 */
    fun initMonitor(context: Context) {
        val notifier = MonitorNotifier(context.applicationContext).also { _monitorNotifier = it }
        notifier.ensureChannel()
        _monitorScheduler = MonitorScheduler(notifier = notifier).also { it.sync() }
    }

    /** 立即把当前状态写盘，作为进程退出前的兜底 */
    fun flush() {
        _persistence?.save()
    }

    /** 从 JSON 备份字符串恢复各 Store（用于本地 / WebDAV 恢复） */
    fun restoreFromJson(json: String) {
        val root = runCatching {
            Json.parseToJsonElement(json).jsonObject
        }.getOrNull() ?: throw java.io.IOException("备份文件不是有效的 JSON")
        _persistence?.restoreFromJsonElement(root)
    }

    val settingsStore: SettingsStore
        get() = checkNotNull(_settingsStore) {
            "SettingsStore 尚未注入，请先在 Application 中调用 bindSettingsStore"
        }
}
