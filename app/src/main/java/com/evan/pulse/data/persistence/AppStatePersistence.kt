package com.evan.pulse.data.persistence

import android.content.Context
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.di.CollectionStoreSnapshot
import com.evan.pulse.di.EnvStoreSnapshot
import com.evan.pulse.di.HistoryStoreSnapshot
import com.evan.pulse.di.MonitorStoreSnapshot
import com.evan.pulse.di.ProjectStoreSnapshot
import com.evan.pulse.di.ServiceLocator
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 应用数据持久化：项目、环境、集合、集合内请求快照、请求历史统一写入单个 JSON 文件。
 *
 * 策略为变更即写（同步、经临时文件原子替换），启动时读回注入各 Store，
 * 保证进程被系统杀死后数据不丢失。
 */
class AppStatePersistence(context: Context) {

    private val file = File(context.filesDir, "app_data.json")
    private val tmpFile = File(context.filesDir, "app_data.json.tmp")
    private val json = Json { prettyPrint = true }

    /** 从磁盘恢复；文件缺失或损坏时保留各 Store 的种子数据 */
    fun load() {
        if (!file.exists()) return
        val text = try {
            file.readText()
        } catch (_: Exception) {
            return
        }
        if (text.isBlank()) return
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return
        }

        ServiceLocator.projectStore.restore(
            ProjectStoreSnapshot(
                projects = root.arrayValue("projects")?.mapNotNull { item ->
                    val o = item.jsonObject
                    ProjectInfo(o.longValue("id", -1L), o.stringValue("name")).takeIf { it.id >= 0 }
                } ?: emptyList(),
                currentProjectId = root.longValue("currentProjectId", 1L),
                nextId = root.longValue("projectNextId", 2L)
            )
        )

        ServiceLocator.envStore.restore(
            EnvStoreSnapshot(
                environments = root.arrayValue("environments")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val variables = o.arrayValue("variables")?.mapNotNull { pair ->
                        val p = pair.jsonArray
                        val k = p.getOrNull(0)?.jsonPrimitive?.contentOrNull
                        val v = p.getOrNull(1)?.jsonPrimitive?.contentOrNull
                        if (k != null && v != null) k to v else null
                    } ?: emptyList()
                    EnvironmentInfo(
                        id = o.longValue("id", -1L),
                        projectId = o.longValue("projectId", 1L),
                        name = o.stringValue("name"),
                        isActive = o.booleanValue("isActive", false),
                        variables = variables
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                nextId = root.longValue("envNextId", 2L)
            )
        )

        ServiceLocator.historyStore.restore(
            HistoryStoreSnapshot(
                entries = root.arrayValue("history")?.map { item ->
                    parseHistoryEntry(item.jsonObject)
                }?.filterNotNull() ?: emptyList(),
                nextId = root.longValue("historyNextId", 1L)
            )
        )

        ServiceLocator.collectionStore.restore(
            CollectionStoreSnapshot(
                collections = root.arrayValue("collections")?.mapNotNull { item ->
                    val o = item.jsonObject
                    CollectionInfo(
                        id = o.longValue("id", -1L),
                        projectId = o.longValue("projectId", 1L),
                        parentId = o.longNullableValue("parentId"),
                        name = o.stringValue("name")
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                requests = root.arrayValue("requests")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val type = o.stringValue("type").let { name ->
                        runCatching { RequestType.valueOf(name) }.getOrNull()
                    } ?: return@mapNotNull null
                    SavedRequest(
                        id = o.longValue("id", -1L),
                        collectionId = o.longValue("collectionId", -1L),
                        type = type,
                        name = o.stringValue("name"),
                        data = o.stringValue("data"),
                        createdAt = o.longValue("createdAt", 0L)
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                nextCollectionId = root.longValue("collectionNextId", 2L),
                nextRequestId = root.longValue("requestNextId", 1L)
            )
        )

        ServiceLocator.monitorStore.restore(
            MonitorStoreSnapshot(
                targets = root.arrayValue("monitors")?.mapNotNull { item ->
                    parseMonitorTarget(item.jsonObject)
                } ?: emptyList(),
                records = root.arrayValue("monitorRecords")?.mapNotNull { item ->
                    parseMonitorRecord(item.jsonObject)
                } ?: emptyList(),
                nextTargetId = root.longValue("monitorNextId", 1L),
                nextRecordId = root.longValue("monitorRecordNextId", 1L)
            )
        )
    }

    /** 从外部 JSON 恢复各 Store（备份文件读回），与 load() 解析逻辑一致 */
    fun restoreFromJsonElement(root: JsonObject) {
        ServiceLocator.projectStore.restore(
            ProjectStoreSnapshot(
                projects = root.arrayValue("projects")?.mapNotNull { item ->
                    val o = item.jsonObject
                    ProjectInfo(o.longValue("id", -1L), o.stringValue("name")).takeIf { it.id >= 0 }
                } ?: emptyList(),
                currentProjectId = root.longValue("currentProjectId", 1L),
                nextId = root.longValue("projectNextId", 2L)
            )
        )

        ServiceLocator.envStore.restore(
            EnvStoreSnapshot(
                environments = root.arrayValue("environments")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val variables = o.arrayValue("variables")?.mapNotNull { pair ->
                        val p = pair.jsonArray
                        val k = p.getOrNull(0)?.jsonPrimitive?.contentOrNull
                        val v = p.getOrNull(1)?.jsonPrimitive?.contentOrNull
                        if (k != null && v != null) k to v else null
                    } ?: emptyList()
                    EnvironmentInfo(
                        id = o.longValue("id", -1L),
                        projectId = o.longValue("projectId", 1L),
                        name = o.stringValue("name"),
                        isActive = o.booleanValue("isActive", false),
                        variables = variables
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                nextId = root.longValue("envNextId", 2L)
            )
        )

        ServiceLocator.historyStore.restore(
            HistoryStoreSnapshot(
                entries = root.arrayValue("history")?.map { item ->
                    parseHistoryEntry(item.jsonObject)
                }?.filterNotNull() ?: emptyList(),
                nextId = root.longValue("historyNextId", 1L)
            )
        )

        ServiceLocator.collectionStore.restore(
            CollectionStoreSnapshot(
                collections = root.arrayValue("collections")?.mapNotNull { item ->
                    val o = item.jsonObject
                    CollectionInfo(
                        id = o.longValue("id", -1L),
                        projectId = o.longValue("projectId", 1L),
                        parentId = o.longNullableValue("parentId"),
                        name = o.stringValue("name")
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                requests = root.arrayValue("requests")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val type = o.stringValue("type").let { name ->
                        runCatching { RequestType.valueOf(name) }.getOrNull()
                    } ?: return@mapNotNull null
                    SavedRequest(
                        id = o.longValue("id", -1L),
                        collectionId = o.longValue("collectionId", -1L),
                        type = type,
                        name = o.stringValue("name"),
                        data = o.stringValue("data"),
                        createdAt = o.longValue("createdAt", 0L)
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                nextCollectionId = root.longValue("collectionNextId", 2L),
                nextRequestId = root.longValue("requestNextId", 1L)
            )
        )

        ServiceLocator.monitorStore.restore(
            MonitorStoreSnapshot(
                targets = root.arrayValue("monitors")?.mapNotNull { item ->
                    parseMonitorTarget(item.jsonObject)
                } ?: emptyList(),
                records = root.arrayValue("monitorRecords")?.mapNotNull { item ->
                    parseMonitorRecord(item.jsonObject)
                } ?: emptyList(),
                nextTargetId = root.longValue("monitorNextId", 1L),
                nextRecordId = root.longValue("monitorRecordNextId", 1L)
            )
        )
        save()
    }

    /** 把当前四个 Store 的状态写入磁盘 */
    fun save() {
        val project = ServiceLocator.projectStore.snapshot()
        val env = ServiceLocator.envStore.snapshot()
        val history = ServiceLocator.historyStore.snapshot()
        val collection = ServiceLocator.collectionStore.snapshot()
        val monitor = ServiceLocator.monitorStore.snapshot()

        val root = buildJsonObject {
            put("projects", buildJsonArray {
                project.projects.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                    })
                }
            })
            put("currentProjectId", project.currentProjectId)
            put("projectNextId", project.nextId)

            put("environments", buildJsonArray {
                env.environments.forEach { e ->
                    add(buildJsonObject {
                        put("id", e.id)
                        put("projectId", e.projectId)
                        put("name", e.name)
                        put("isActive", e.isActive)
                        put("variables", buildJsonArray {
                            e.variables.forEach { (k, v) ->
                                add(buildJsonArray {
                                    add(JsonPrimitive(k))
                                    add(JsonPrimitive(v))
                                })
                            }
                        })
                    })
                }
            })
            put("envNextId", env.nextId)

            put("history", buildJsonArray {
                history.entries.forEach { h ->
                    add(buildJsonObject {
                        put("id", h.id)
                        put("projectId", h.projectId)
                        put("method", h.method)
                        put("url", h.url)
                        put("statusCode", h.statusCode)
                        put("statusText", h.statusText)
                        put("durationMs", h.durationMs)
                        put("success", h.success)
                        put("createdAt", h.createdAt)
                        put("responseSizeBytes", h.responseSizeBytes)
                        put("requestHeaders", buildJsonArray {
                            h.requestHeaders.forEach { add(JsonPrimitive(it)) }
                        })
                        put("requestBody", h.requestBody)
                        put("requestBodyTruncated", h.requestBodyTruncated)
                        put("responseHeaders", buildJsonArray {
                            h.responseHeaders.forEach { add(JsonPrimitive(it)) }
                        })
                        put("responseBody", h.responseBody)
                        put("responseBodyTruncated", h.responseBodyTruncated)
                    })
                }
            })
            put("historyNextId", history.nextId)

            put("collections", buildJsonArray {
                collection.collections.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("projectId", c.projectId)
                        c.parentId?.let { put("parentId", it) }
                        put("name", c.name)
                    })
                }
            })
            put("collectionNextId", collection.nextCollectionId)

            put("requests", buildJsonArray {
                collection.requests.forEach { r ->
                    add(buildJsonObject {
                        put("id", r.id)
                        put("collectionId", r.collectionId)
                        put("type", r.type.name)
                        put("name", r.name)
                        put("data", r.data)
                        put("createdAt", r.createdAt)
                    })
                }
            })
            put("requestNextId", collection.nextRequestId)

            put("monitors", buildJsonArray {
                monitor.targets.forEach { m ->
                    add(buildJsonObject {
                        put("id", m.id)
                        put("projectId", m.projectId)
                        put("name", m.name)
                        put("type", m.type.name)
                        put("host", m.host)
                        put("port", m.port)
                        put("url", m.url)
                        put("timeoutMs", m.timeoutMs)
                        put("intervalSec", m.intervalSec)
                        put("statusMin", m.statusMin)
                        put("statusMax", m.statusMax)
                        put("enabled", m.enabled)
                        put("createdAt", m.createdAt)
                    })
                }
            })
            put("monitorNextId", monitor.nextTargetId)

            put("monitorRecords", buildJsonArray {
                monitor.records.forEach { r ->
                    add(buildJsonObject {
                        put("id", r.id)
                        put("projectId", r.projectId)
                        put("targetId", r.targetId)
                        put("targetName", r.targetName)
                        put("type", r.type.name)
                        put("message", r.message)
                        put("durationMs", r.durationMs)
                        put("checkedAt", r.checkedAt)
                    })
                }
            })
            put("monitorRecordNextId", monitor.nextRecordId)
        }

        try {
            tmpFile.writeText(json.encodeToString(JsonElement.serializer(), root))
            tmpFile.renameTo(file)
        } catch (_: Exception) {
            // 写入失败不影响运行期功能，下次变更会重试
        }
    }

    private fun JsonObject.longValue(key: String, default: Long): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    /** 可空长整型：字段缺失返回 null，用于旧数据缺少 parentId 时的迁移 */
    private fun JsonObject.longNullableValue(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.intValue(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: default

    private fun JsonObject.stringValue(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.booleanValue(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.arrayValue(key: String): JsonArray? =
        this[key]?.jsonArray

    private fun JsonObject.stringValueList(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    /**
     * 解析单条历史记录，id 缺失视为无效行跳过。
     *
     * 详情字段是后来加的，旧备份里没有这些键时会落到构造函数的默认值，
     * 详情页据此区分「早期记录没有详情」与「详情为空」。
     */
    private fun parseHistoryEntry(o: JsonObject): HistoryEntry? = HistoryEntry(
        id = o.longValue("id", -1L),
        projectId = o.longValue("projectId", 1L),
        method = o.stringValue("method"),
        url = o.stringValue("url"),
        statusCode = o.intValue("statusCode", 0),
        statusText = o.stringValue("statusText"),
        durationMs = o.longValue("durationMs", 0L),
        success = o.booleanValue("success", false),
        createdAt = o.longValue("createdAt", 0L),
        responseSizeBytes = o.longValue("responseSizeBytes", 0L),
        requestHeaders = o.stringValueList("requestHeaders"),
        requestBody = o.stringValue("requestBody"),
        requestBodyTruncated = o.booleanValue("requestBodyTruncated", false),
        responseHeaders = o.stringValueList("responseHeaders"),
        responseBody = o.stringValue("responseBody"),
        responseBodyTruncated = o.booleanValue("responseBodyTruncated", false)
    ).takeIf { it.id >= 0 }

    /** 解析单个拨测目标；类型不认识或 id 缺失时跳过整行 */
    private fun parseMonitorTarget(o: JsonObject): MonitorTarget? {
        val type = runCatching { MonitorType.valueOf(o.stringValue("type")) }.getOrNull()
            ?: return null
        return MonitorTarget(
            id = o.longValue("id", -1L),
            projectId = o.longValue("projectId", 1L),
            name = o.stringValue("name"),
            type = type,
            host = o.stringValue("host"),
            port = o.intValue("port", 0),
            url = o.stringValue("url"),
            timeoutMs = o.longValue("timeoutMs", 5000L),
            intervalSec = o.longValue("intervalSec", 60L),
            statusMin = o.intValue("statusMin", 200),
            statusMax = o.intValue("statusMax", 399),
            enabled = o.booleanValue("enabled", true),
            createdAt = o.longValue("createdAt", 0L)
        ).takeIf { it.id >= 0 }
    }

    /** 解析单条拨测记录 */
    private fun parseMonitorRecord(o: JsonObject): MonitorRecord? {
        val type = runCatching { MonitorType.valueOf(o.stringValue("type")) }.getOrNull()
            ?: return null
        return MonitorRecord(
            id = o.longValue("id", -1L),
            projectId = o.longValue("projectId", 1L),
            targetId = o.longValue("targetId", -1L),
            targetName = o.stringValue("targetName"),
            type = type,
            message = o.stringValue("message"),
            durationMs = o.longValue("durationMs", 0L),
            checkedAt = o.longValue("checkedAt", 0L)
        ).takeIf { it.id >= 0 }
    }
}
