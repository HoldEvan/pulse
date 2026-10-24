package com.evan.pulse.di

import com.evan.pulse.R
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.ImportResult
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.MONITOR_HISTORY_LIMIT
import com.evan.pulse.data.model.MonitorHistoryEntry
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MONITOR_RECORD_LIMIT
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.WsSessionEntry
import com.evan.pulse.data.model.WsReconnectSettings
import com.evan.pulse.data.model.WS_SESSION_HISTORY_LIMIT
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.monitor.MonitorChecker
import com.evan.pulse.data.monitor.MonitorNotifier
import com.evan.pulse.data.monitor.MonitorScheduler
import com.evan.pulse.data.monitor.WorkManagerMonitorScheduler
import com.evan.pulse.data.backup.AutoBackupScheduler
import com.evan.pulse.data.backup.WorkManagerBackupScheduler
import com.evan.pulse.data.settings.SettingsStore
import com.evan.pulse.data.persistence.AppStatePersistence
import com.evan.pulse.domain.AuthApplier
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.net.HttpEngine
import com.evan.pulse.domain.net.KtorSseEngine
import com.evan.pulse.domain.net.KtorWsEngine
import com.evan.pulse.domain.net.MqttEngine
import com.evan.pulse.domain.net.OkHttpEngine
import com.evan.pulse.domain.net.PahoMqttEngine
import com.evan.pulse.domain.net.SseEngine
import com.evan.pulse.ui.l10n.L10n
import com.evan.pulse.domain.net.WsEngine
import com.evan.pulse.domain.script.RhinoScriptExecutor
import com.evan.pulse.domain.script.ScriptEnvWriter
import android.content.Context
import android.util.Base64
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

class ProjectStore(private val persist: PersistenceHandle) {
    private val _projects = MutableStateFlow(
        listOf(ProjectInfo(1, L10n.t(R.string.project_default_name)))
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

    fun toggleFavorite(id: Long): Boolean {
        val before = _entries.value
        if (before.none { it.id == id }) return false
        _entries.value = before.map { entry ->
            if (entry.id == id) entry.copy(favorite = !entry.favorite) else entry
        }
        persist.save()
        return true
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

/** 单个 Cookie：okhttp3.Cookie 解析后的精简形态，按项目 + 域名分桶存储 */
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAtMs: Long = Long.MAX_VALUE
)

class CookieStore(private val persist: PersistenceHandle) {
    private val _cookies = MutableStateFlow<Map<Long, Map<String, List<StoredCookie>>>>(emptyMap())
    val cookies: StateFlow<Map<Long, Map<String, List<StoredCookie>>>> = _cookies.asStateFlow()

    private val nowMs: Long get() = System.currentTimeMillis()

    /** 当前项目下各域名的未过期 Cookie */
    fun active(projectId: Long): Map<String, List<StoredCookie>> =
        _cookies.value[projectId]
            ?.mapValues { (_, list) -> list.filter { it.expiresAtMs > nowMs } }
            ?.filterValues { it.isNotEmpty() }
            ?: emptyMap()

    /**
     * 命中请求 host 的未过期 Cookie：精确域名优先，其余按父域匹配
     * （Domain=example.com 写入的 Cookie 对 api.example.com 同样生效）。
     */
    fun forHost(projectId: Long, host: String): List<StoredCookie> {
        val lower = host.lowercase()
        val all = active(projectId)
        val exact = all[lower].orEmpty()
        val wider = all.filterKeys { key -> lower != key && lower.endsWith(".$key") }
            .values.flatten()
        return exact + wider
    }

    /** 同名同路径覆盖，实现 Set-Cookie 的更新语义 */
    fun upsert(projectId: Long, cookie: StoredCookie) {
        if (cookie.name.isBlank() || cookie.domain.isBlank()) return
        val hosts = _cookies.value[projectId].orEmpty().toMutableMap()
        val kept = hosts[cookie.domain].orEmpty()
            .filter { it.name != cookie.name || it.path != cookie.path }
        hosts[cookie.domain] = (kept + cookie).sortedBy { it.name }
        _cookies.value = _cookies.value + (projectId to hosts)
        persist.save()
    }

    fun remove(projectId: Long, domain: String, name: String) {
        val hosts = _cookies.value[projectId] ?: return
        val kept = hosts[domain]?.filter { it.name != name } ?: return
        val updated = hosts.toMutableMap()
        if (kept.isEmpty()) updated.remove(domain) else updated[domain] = kept
        _cookies.value = _cookies.value + (projectId to updated)
        persist.save()
    }

    fun clearHost(projectId: Long, domain: String) {
        val hosts = _cookies.value[projectId] ?: return
        _cookies.value = _cookies.value + (projectId to (hosts - domain))
        persist.save()
    }

    fun clearProject(projectId: Long) {
        if (!_cookies.value.containsKey(projectId)) return
        _cookies.value = _cookies.value - projectId
        persist.save()
    }

    fun snapshot() = CookieStoreSnapshot(_cookies.value)

    fun restore(state: CookieStoreSnapshot) {
        _cookies.value = state.projects
    }
}

class CookieStoreSnapshot(
    val projects: Map<Long, Map<String, List<StoredCookie>>>
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
        listOf(CollectionInfo(1, 1, null, L10n.t(R.string.collection_default_name)))
    )
    val collections: StateFlow<List<CollectionInfo>> = _collections.asStateFlow()

    private val _requests = MutableStateFlow(emptyList<SavedRequest>())
    val requests: StateFlow<List<SavedRequest>> = _requests.asStateFlow()

    /** 文件夹变量：按文件夹 id 保存，解析时按祖先链合并 */
    private val _variables = MutableStateFlow<Map<Long, List<KeyValueEntry>>>(emptyMap())
    val variables: StateFlow<Map<Long, List<KeyValueEntry>>> = _variables.asStateFlow()

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
        _variables.value = _variables.value.filterKeys { it !in doomed }
        persist.save()
    }

    /** 某个文件夹自身的变量（不含继承） */
    fun varsOf(collectionId: Long): List<KeyValueEntry> =
        _variables.value[collectionId].orEmpty()

    /**
     * 合并自身与祖先的变量：根在前、自身在后，同名时子级覆盖父级。
     *
     * 只取启用且键非空的条目；[collectionId] 为空时返回空表。
     */
    fun resolvedVars(collectionId: Long?): Map<String, String> {
        if (collectionId == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (id in CollectionTree.idPathOf(_collections.value, collectionId)) {
            _variables.value[id].orEmpty()
                .filter { it.enabled && it.key.isNotBlank() }
                .forEach { result[it.key] = it.value }
        }
        return result
    }

    /** 覆盖某个文件夹自身的变量；空列表表示清空该文件夹的变量 */
    fun setVars(collectionId: Long, entries: List<KeyValueEntry>) {
        val cleaned = entries.filter { it.key.isNotBlank() }
        _variables.value = if (cleaned.isEmpty()) {
            _variables.value - collectionId
        } else {
            _variables.value + (collectionId to cleaned)
        }
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

    /**
     * 复制接口到同文件夹，紧跟在原条目之后。
     *
     * 只复制快照与协议类型，名称由调用方按界面语言生成；条目不存在或名称空白时返回 null。
     */
    fun duplicateRequest(id: Long, name: String): Long? {
        if (name.isBlank()) return null
        val index = _requests.value.indexOfFirst { it.id == id }
        if (index < 0) return null
        val original = _requests.value[index]
        val copy = original.copy(
            id = nextRequestId++,
            name = name.trim(),
            createdAt = System.currentTimeMillis()
        )
        _requests.value = _requests.value.toMutableList().apply { add(index + 1, copy) }
        persist.save()
        return copy.id
    }

    /** 重命名接口；空白名称视为无效，保留原名称 */
    fun renameRequest(id: Long, name: String) {
        if (name.isBlank()) return
        _requests.value = _requests.value.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        persist.save()
    }

    /**
     * 在同一文件夹内上移或下移接口，[offset] 为 -1 或 1。
     *
     * 同级顺序就是请求在扁平列表里的相对顺序，因此与相邻的同文件夹条目交换位置即可；
     * 已在边界或找不到条目时返回 false，不触发落盘。
     */
    fun moveRequest(id: Long, offset: Int): Boolean {
        if (offset == 0) return false
        val list = _requests.value
        val currentIndex = list.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false
        val collectionId = list[currentIndex].collectionId
        val siblings = list.withIndex().filter { it.value.collectionId == collectionId }
        val position = siblings.indexOfFirst { it.index == currentIndex }
        val targetPosition = position + if (offset < 0) -1 else 1
        if (position < 0 || targetPosition !in siblings.indices) return false
        val targetIndex = siblings[targetPosition].index
        _requests.value = list.toMutableList().apply {
            val moved = this[currentIndex]
            this[currentIndex] = this[targetIndex]
            this[targetIndex] = moved
        }
        persist.save()
        return true
    }

    /** 把接口置顶到所在文件夹的最前面；已在首位时返回 false */
    fun pinRequest(id: Long): Boolean {
        val list = _requests.value
        val currentIndex = list.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false
        val collectionId = list[currentIndex].collectionId
        val firstIndex = list.indexOfFirst { it.collectionId == collectionId }
        if (firstIndex < 0 || firstIndex == currentIndex) return false
        _requests.value = list.toMutableList().apply {
            val moved = removeAt(currentIndex)
            add(firstIndex, moved)
        }
        persist.save()
        return true
    }

    /** 移动接口到目标集合；目标集合不存在时不动 */
    fun moveRequest(id: Long, collectionId: Long) {
        if (_collections.value.none { it.id == collectionId }) return
        _requests.value = _requests.value.map {
            if (it.id == id) it.copy(collectionId = collectionId) else it
        }
        persist.save()
    }

    /**
     * 保存请求；目标集合缺失时在当前项目下自动建默认集合。
     *
     * 返回新建条目的 id（名称为空时返回 -1），调用方据此把后续保存切换为原地覆盖。
     */
    fun saveRequest(
        projectId: Long,
        collectionId: Long,
        type: RequestType,
        name: String,
        data: String,
        fallbackCollectionName: String
    ): Long {
        if (name.isBlank()) return -1L
        val collection = _collections.value.firstOrNull { it.id == collectionId }
            ?: _collections.value.firstOrNull { it.projectId == projectId }
            ?: CollectionInfo(nextCollectionId++, projectId, null, fallbackCollectionName).also {
                _collections.value = _collections.value + it
            }
        val created = SavedRequest(
            nextRequestId++,
            collection.id,
            type,
            name,
            data,
            System.currentTimeMillis()
        )
        _requests.value = listOf(created) + _requests.value
        persist.save()
        return created.id
    }

    /** 原地覆盖已保存接口的快照，保持条目位置与名称不变；条目已不存在时返回 false */
    fun updateRequest(id: Long, data: String): Boolean {
        var found = false
        _requests.value = _requests.value.map { request ->
            if (request.id == id) {
                found = true
                request.copy(data = data)
            } else {
                request
            }
        }
        if (!found) return false
        persist.save()
        return true
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
        nextRequestId,
        _variables.value
    )

    fun restore(state: CollectionStoreSnapshot) {
        val collections = state.collections.ifEmpty { _collections.value }
        val requests = state.requests.filter { request ->
            collections.any { it.id == request.collectionId }
        }
        _collections.value = collections
        _requests.value = requests
        // 只保留仍存在的文件夹的变量，避免恢复出指向已删文件夹的配置
        _variables.value = state.variables.filterKeys { id -> collections.any { it.id == id } }
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
    val nextRequestId: Long,
    val variables: Map<Long, List<KeyValueEntry>> = emptyMap()
)

class MonitorStore(private val persist: PersistenceHandle) {
    private val _targets = MutableStateFlow(emptyList<MonitorTarget>())
    val targets: StateFlow<List<MonitorTarget>> = _targets.asStateFlow()

    private val _records = MutableStateFlow(emptyList<MonitorRecord>())
    val records: StateFlow<List<MonitorRecord>> = _records.asStateFlow()

    private val _history = MutableStateFlow(emptyList<MonitorHistoryEntry>())
    val history: StateFlow<List<MonitorHistoryEntry>> = _history.asStateFlow()

    private var nextTargetId = 1L
    private var nextRecordId = 1L
    private var nextHistoryId = 1L

    fun targetsFor(projectId: Long): List<MonitorTarget> =
        _targets.value.filter { it.projectId == projectId }

    fun recordsFor(projectId: Long): List<MonitorRecord> =
        _records.value.filter { it.projectId == projectId }

    /** 某目标的趋势历史，倒序（最新在前） */
    fun historyFor(targetId: Long): List<MonitorHistoryEntry> =
        _history.value.filter { it.targetId == targetId }

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

    /**
     * 按分组批量设置开关：group 为 null 表示全部目标，空串表示未分组。
     * 返回实际发生变更的条数，无变更时不落盘。
     */
    fun setEnabledForGroup(group: String?, enabled: Boolean): Int {
        val before = _targets.value
        val after = com.evan.pulse.data.monitor.applyEnabledByGroup(before, group, enabled)
        val changed = before.indices.count { before[it] != after[it] }
        if (changed == 0) return 0
        _targets.value = after
        persist.save()
        return changed
    }

    fun delete(id: Long) {
        _targets.value = _targets.value.filter { it.id != id }
        val recordsBefore = _records.value
        _records.value = recordsBefore.filter { it.targetId != id }
        val historyBefore = _history.value
        _history.value = historyBefore.filter { it.targetId != id }
        if (_records.value == recordsBefore && _history.value == historyBefore) return
        persist.save()
    }

    /** 记录一次拨测结果（通与不通都记录），最新在前，超出上限丢弃最旧的 */
    fun recordResult(
        target: MonitorTarget,
        ok: Boolean,
        message: String,
        durationMs: Long,
        checkedAt: Long = System.currentTimeMillis()
    ) {
        val record = MonitorRecord(
            id = nextRecordId++,
            projectId = target.projectId,
            targetId = target.id,
            targetName = target.name,
            type = target.type,
            message = message,
            durationMs = durationMs,
            checkedAt = checkedAt,
            ok = ok
        )
        _records.value = (listOf(record) + _records.value).take(MONITOR_RECORD_LIMIT)
        persist.save()
    }

    /** 记录一次拨测历史样本（无论成败），每个目标倒序保留最近若干条 */
    fun recordHistory(target: MonitorTarget, ok: Boolean, slow: Boolean, durationMs: Long, checkedAt: Long) {
        val entry = MonitorHistoryEntry(
            id = nextHistoryId++,
            targetId = target.id,
            ok = ok,
            slow = slow,
            durationMs = durationMs,
            checkedAt = checkedAt
        )
        _history.value = (listOf(entry) + _history.value).take(MONITOR_HISTORY_LIMIT)
        persist.save()
    }

    fun clearRecords(projectId: Long) {
        val recordsBefore = _records.value
        _records.value = recordsBefore.filter { it.projectId != projectId }
        val historyBefore = _history.value
        _history.value = historyBefore.filter { entry ->
            targetsFor(projectId).none { it.id == entry.targetId }
        }
        if (_records.value == recordsBefore && _history.value == historyBefore) return
        persist.save()
    }

    fun snapshot() = MonitorStoreSnapshot(
        _targets.value,
        _records.value,
        _history.value,
        nextTargetId,
        nextRecordId,
        nextHistoryId
    )

    fun restore(state: MonitorStoreSnapshot) {
        _targets.value = state.targets
        _records.value = state.records.filter { record ->
            state.targets.any { it.id == record.targetId }
        }
        _history.value = state.history.filter { entry ->
            state.targets.any { it.id == entry.targetId }
        }
        nextTargetId = state.nextTargetId.coerceAtLeast((state.targets.maxOfOrNull { it.id } ?: 0L) + 1)
        nextRecordId = state.nextRecordId.coerceAtLeast((state.records.maxOfOrNull { it.id } ?: 0L) + 1)
        nextHistoryId = state.nextHistoryId.coerceAtLeast((state.history.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}

class ProtocolSessionStore(private val persist: PersistenceHandle) {
    private val _wsHistory = MutableStateFlow<List<WsSessionEntry>>(emptyList())
    val wsHistory: StateFlow<List<WsSessionEntry>> = _wsHistory.asStateFlow()

    private val _mqttSubscriptions = MutableStateFlow<List<MqttSubscriptionEntry>>(emptyList())
    val mqttSubscriptions: StateFlow<List<MqttSubscriptionEntry>> = _mqttSubscriptions.asStateFlow()

    private val _wsReconnectSettings = MutableStateFlow(WsReconnectSettings())
    val wsReconnectSettings: StateFlow<WsReconnectSettings> = _wsReconnectSettings.asStateFlow()

    private var nextWsId = 1L

    fun appendWs(endpoint: String, text: String, isSystem: Boolean, timestamp: Long): WsSessionEntry {
        val entry = WsSessionEntry(nextWsId++, endpoint, text, isSystem, timestamp)
        _wsHistory.value = (_wsHistory.value + entry).takeLast(WS_SESSION_HISTORY_LIMIT)
        persist.save()
        return entry
    }

    fun clearWsHistory() {
        if (_wsHistory.value.isEmpty()) return
        _wsHistory.value = emptyList()
        persist.save()
    }

    fun setMqttSubscriptions(subscriptions: List<MqttSubscriptionEntry>) {
        if (_mqttSubscriptions.value == subscriptions) return
        _mqttSubscriptions.value = subscriptions.distinctBy { it.topic }
        persist.save()
    }

    fun setWsReconnectSettings(settings: WsReconnectSettings) {
        val normalized = settings.copy(
            baseDelaySec = settings.baseDelaySec.coerceIn(1, 3600),
            maxDelaySec = settings.maxDelaySec.coerceIn(settings.baseDelaySec.coerceIn(1, 3600), 3600)
        )
        if (_wsReconnectSettings.value == normalized) return
        _wsReconnectSettings.value = normalized
        persist.save()
    }

    fun snapshot() = ProtocolSessionStoreSnapshot(
        _wsHistory.value,
        _mqttSubscriptions.value,
        nextWsId,
        _wsReconnectSettings.value
    )

    fun restore(snapshot: ProtocolSessionStoreSnapshot) {
        _wsHistory.value = snapshot.wsHistory.takeLast(WS_SESSION_HISTORY_LIMIT)
        _mqttSubscriptions.value = snapshot.mqttSubscriptions.distinctBy { it.topic }
        val settings = snapshot.wsReconnectSettings
        _wsReconnectSettings.value = settings.copy(
            baseDelaySec = settings.baseDelaySec.coerceIn(1, 3600),
            maxDelaySec = settings.maxDelaySec.coerceIn(settings.baseDelaySec.coerceIn(1, 3600), 3600)
        )
        nextWsId = maxOf(snapshot.nextWsId, (_wsHistory.value.maxOfOrNull { it.id } ?: 0L) + 1L)
    }
}

class ProtocolSessionStoreSnapshot(
    val wsHistory: List<WsSessionEntry>,
    val mqttSubscriptions: List<MqttSubscriptionEntry>,
    val nextWsId: Long,
    val wsReconnectSettings: WsReconnectSettings = WsReconnectSettings()
)

class MonitorStoreSnapshot(
    val targets: List<MonitorTarget>,
    val records: List<MonitorRecord>,
    val history: List<MonitorHistoryEntry>,
    val nextTargetId: Long,
    val nextRecordId: Long,
    val nextHistoryId: Long
)

/** Store 的持久化回调句柄：init 前为空操作，避免启动早期写入 */
class PersistenceHandle {
    var action: () -> Unit = {}

    fun save() = action()
}

object ServiceLocator {

    private var _settingsStore: SettingsStore? = null
    private var _persistence: AppStatePersistence? = null

    /** 由 [init] 注入的应用级 Context，供引擎读取 content:// 文件等系统资源 */
    private var _appContext: Context? = null
    val appContext: Context
        get() = checkNotNull(_appContext) { "ServiceLocator 尚未初始化" }

    /** 直读可空字段，供引擎在对象初始化期安全取得 context（不触发 checkNotNull） */
    private fun appContextOrNull(): Context? = _appContext

    private val persistHandle = PersistenceHandle()

    val projectStore = ProjectStore(persistHandle)
    val historyStore = HistoryStore(persistHandle)
    val envStore = EnvStore(persistHandle)
    val collectionStore = CollectionStore(persistHandle)
    val placeholderResolver: PlaceholderResolver = SimplePlaceholderResolver()
    val authApplier: AuthApplier = SimpleAuthApplier()
    val scriptExecutor: com.evan.pulse.domain.script.ScriptExecutor = RhinoScriptExecutor()
    val scriptEnvWriter: ScriptEnvWriter = ActiveEnvWriter()

    // 拨测目标与未通记录。必须在 httpEngine 之后声明，MonitorChecker 依赖它。
    // httpEngine 走 lazy 且 monitorChecker 同样 lazy：两处都不允许在 <clinit>（ServiceLocator
    // 类加载）期间触发 appContext 的 checkNotNull getter——否则 init 注入前首个访问即崩。
    // provider 走私有函数直读可空字段，避免 R8/编译器把 lambda 内私有字段 accessor 合并成公共
    // checkNotNull getter。
    val httpEngine: HttpEngine by lazy { OkHttpEngine(::appContextOrNull) }
    val wsEngine: WsEngine = KtorWsEngine()
    val mqttEngine: MqttEngine = PahoMqttEngine()
    val sseEngine: SseEngine = KtorSseEngine()

    val monitorStore = MonitorStore(persistHandle)
    val cookieStore = CookieStore(persistHandle)
    val protocolSessionStore = ProtocolSessionStore(persistHandle)
    val monitorChecker: MonitorChecker by lazy { MonitorChecker() }

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

    /** 环境变量与文件夹变量合并；同名时文件夹变量优先 */
    fun varsForCollection(collectionId: Long?): Map<String, String> {
        val merged = LinkedHashMap<String, String>(envStore.activeVars())
        merged.putAll(collectionStore.resolvedVars(collectionId))
        return merged
    }

    /** 初始化数据持久化：注册写盘回调并读回已有数据。必须在 Application.onCreate 中调用 */
    fun init(context: Context) {
        _appContext = context.applicationContext
        val persistence = AppStatePersistence(context)
        _persistence = persistence
        persistHandle.action = { persistence.save() }
        persistence.load()
    }

    /** 初始化拨测调度：建通知通道并按已启用目标启动定时检查。必须在 init 之后调用 */
    fun initMonitor(context: Context) {
        val appContext = context.applicationContext
        val notifier = MonitorNotifier(appContext).also { _monitorNotifier = it }
        notifier.ensureChannel()
        _monitorScheduler = MonitorScheduler(
            notifier = notifier,
            background = WorkManagerMonitorScheduler(appContext)
        ).also { it.sync() }
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
