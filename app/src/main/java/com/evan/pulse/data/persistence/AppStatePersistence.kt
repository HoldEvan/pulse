package com.evan.pulse.data.persistence

import android.content.Context
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.MonitorAssertion
import com.evan.pulse.data.model.MonitorHistoryEntry
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.WsSessionEntry
import com.evan.pulse.data.model.WsMessageTemplate
import com.evan.pulse.data.model.WsReconnectSettings
import com.evan.pulse.data.crypto.KeystoreSecretBox
import com.evan.pulse.domain.crypto.SecretBox
import com.evan.pulse.domain.crypto.SecretField
import com.evan.pulse.di.CollectionStoreSnapshot
import com.evan.pulse.di.CookieStoreSnapshot
import com.evan.pulse.di.EnvStoreSnapshot
import com.evan.pulse.di.HistoryStoreSnapshot
import com.evan.pulse.di.MonitorStoreSnapshot
import com.evan.pulse.di.ProjectStoreSnapshot
import com.evan.pulse.di.ProtocolSessionStoreSnapshot
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.di.StoredCookie
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
class AppStatePersistence(
    context: Context,
    private val secrets: SecretBox? = runCatching { KeystoreSecretBox() }.getOrNull(),
) {

    private val file = File(context.filesDir, "app_data.json")
    private val tmpFile = File(context.filesDir, "app_data.json.tmp")
    private val json = Json { prettyPrint = true }

    /**
     * 写入敏感字段：没有可用密钥、或 Keystore 在运行期失效时退回明文。
     * 加密失败不该让整次写盘失败，否则用户会丢掉这一次的全部改动。
     */
    private fun seal(plain: String): String {
        val box = secrets ?: return plain
        return runCatching { SecretField.seal(plain, box) }.getOrDefault(plain)
    }

    /** 读回敏感字段：明文（旧数据/旧备份）原样返回，密文解不开时落到空串 */
    private fun open(value: String): String = SecretField.open(value, secrets)

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
                projects = parseProjects(root),
                currentProjectId = root.longValue("currentProjectId", 1L),
                nextId = root.longValue("projectNextId", 2L)
            )
        )

        ServiceLocator.envStore.restore(
            EnvStoreSnapshot(
                environments = parseEnvironments(root),
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
                requests = parseSavedRequests(root),
                nextCollectionId = root.longValue("collectionNextId", 2L),
                nextRequestId = root.longValue("requestNextId", 1L),
                variables = parseCollectionVars(root)
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
                history = root.arrayValue("monitorHistory")?.mapNotNull { item ->
                    parseMonitorHistory(item.jsonObject)
                } ?: emptyList(),
                nextTargetId = root.longValue("monitorNextId", 1L),
                nextRecordId = root.longValue("monitorRecordNextId", 1L),
                nextHistoryId = root.longValue("monitorHistoryNextId", 1L)
            )
        )

        restoreProtocolSessions(root)

        restoreCookies(root)
    }

    /** 从外部 JSON 恢复各 Store（备份文件读回），与 load() 解析逻辑一致 */
    fun restoreFromJsonElement(root: JsonObject) {
        ServiceLocator.projectStore.restore(
            ProjectStoreSnapshot(
                projects = parseProjects(root),
                currentProjectId = root.longValue("currentProjectId", 1L),
                nextId = root.longValue("projectNextId", 2L)
            )
        )

        ServiceLocator.envStore.restore(
            EnvStoreSnapshot(
                environments = parseEnvironments(root),
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
                requests = parseSavedRequests(root),
                nextCollectionId = root.longValue("collectionNextId", 2L),
                nextRequestId = root.longValue("requestNextId", 1L),
                variables = parseCollectionVars(root)
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
                history = root.arrayValue("monitorHistory")?.mapNotNull { item ->
                    parseMonitorHistory(item.jsonObject)
                } ?: emptyList(),
                nextTargetId = root.longValue("monitorNextId", 1L),
                nextRecordId = root.longValue("monitorRecordNextId", 1L),
                nextHistoryId = root.longValue("monitorHistoryNextId", 1L)
            )
        )

        restoreProtocolSessions(root)

        restoreCookies(root)
        save()
    }

    /** 项目列表：id 缺失视为无效行 */
    private fun parseProjects(root: JsonObject): List<ProjectInfo> =
        root.arrayValue("projects")?.mapNotNull { item ->
            val o = item.jsonObject
            ProjectInfo(o.longValue("id", -1L), o.stringValue("name")).takeIf { it.id >= 0 }
        } ?: emptyList()

    /** 环境列表：变量值可能是密文，读回时统一解封 */
    private fun parseEnvironments(root: JsonObject): List<EnvironmentInfo> =
        root.arrayValue("environments")?.mapNotNull { item ->
            val o = item.jsonObject
            val variables = o.arrayValue("variables")?.mapNotNull { pair ->
                val p = pair.jsonArray
                val k = p.getOrNull(0)?.jsonPrimitive?.contentOrNull
                val v = p.getOrNull(1)?.jsonPrimitive?.contentOrNull
                if (k != null && v != null) k to open(v) else null
            } ?: emptyList()
            EnvironmentInfo(
                id = o.longValue("id", -1L),
                projectId = o.longValue("projectId", 1L),
                name = o.stringValue("name"),
                isActive = o.booleanValue("isActive", false),
                variables = variables
            ).takeIf { it.id >= 0 }
        } ?: emptyList()

    /** 集合内请求快照：data 是整段 JSON 文本，整体加密后逐条解封 */
    private fun parseSavedRequests(root: JsonObject): List<SavedRequest> =
        root.arrayValue("requests")?.mapNotNull { item ->
            val o = item.jsonObject
            val type = o.stringValue("type").let { name ->
                runCatching { RequestType.valueOf(name) }.getOrNull()
            } ?: return@mapNotNull null
            SavedRequest(
                id = o.longValue("id", -1L),
                collectionId = o.longValue("collectionId", -1L),
                type = type,
                name = o.stringValue("name"),
                data = open(o.stringValue("data")),
                createdAt = o.longValue("createdAt", 0L)
            ).takeIf { it.id >= 0 }
        } ?: emptyList()

    /** 文件夹变量：按文件夹 id 读回键值列表，旧备份没有该段时返回空表 */
    private fun parseCollectionVars(root: JsonObject): Map<Long, List<KeyValueEntry>> {
        val result = LinkedHashMap<Long, List<KeyValueEntry>>()
        root.arrayValue("collectionVars")?.forEach { item ->
            val o = item.jsonObject
            val id = o.longValue("collectionId", -1L)
            if (id < 0) return@forEach
            val entries = o.arrayValue("vars")?.mapNotNull { varItem ->
                val kv = varItem.jsonObject
                val key = kv.stringValue("key")
                if (key.isBlank()) null
                else KeyValueEntry(key, open(kv.stringValue("value")), kv.booleanValue("enabled", true))
            } ?: emptyList()
            if (entries.isNotEmpty()) result[id] = entries
        }
        return result
    }

    /** Cookie 库恢复：按项目 + 域名分桶结构原样读回 */
    private fun restoreCookies(root: JsonObject) {
        ServiceLocator.cookieStore.restore(
            CookieStoreSnapshot(
                projects = root.arrayValue("cookies")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val projectId = o.longValue("projectId", -1L)
                    if (projectId < 0) return@mapNotNull null
                    val hosts = o.arrayValue("hosts")?.mapNotNull { hostItem ->
                        val host = hostItem.jsonObject
                        val domain = host.stringValue("host")
                        if (domain.isBlank()) return@mapNotNull null
                        domain to (host.arrayValue("cookies")?.mapNotNull { cookieItem ->
                            val c = cookieItem.jsonObject
                            val name = c.stringValue("name")
                            if (name.isBlank()) null else StoredCookie(
                                name = name,
                                value = open(c.stringValue("value")),
                                domain = domain,
                                path = c.stringValue("path").ifBlank { "/" },
                                expiresAtMs = c.longValue("expiresAtMs", Long.MAX_VALUE)
                            )
                        } ?: emptyList())
                    }?.toMap() ?: emptyMap()
                    projectId to hosts
                }?.toMap() ?: emptyMap()
            )
        )
    }

    private fun restoreProtocolSessions(root: JsonObject) {
        ServiceLocator.protocolSessionStore.restore(
            ProtocolSessionStoreSnapshot(
                wsHistory = root.arrayValue("wsSessionHistory")?.mapNotNull { item ->
                    val o = item.jsonObject
                    WsSessionEntry(
                        id = o.longValue("id", -1L),
                        endpoint = o.stringValue("endpoint"),
                        text = o.stringValue("text"),
                        isSystem = o.booleanValue("isSystem", false),
                        timestamp = o.longValue("timestamp", 0L)
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                mqttSubscriptions = root.arrayValue("mqttSubscriptions")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val topic = o.stringValue("topic")
                    val qos = o.intValue("qos", -1)
                    MqttSubscriptionEntry(topic, qos).takeIf { topic.isNotBlank() && qos in 0..2 }
                } ?: emptyList(),
                nextWsId = root.longValue("wsSessionNextId", 1L),
                wsReconnectSettings = WsReconnectSettings(
                    enabled = root.booleanValue("wsAutoReconnect", false),
                    baseDelaySec = root.intValue("wsReconnectBaseDelaySec", 3),
                    maxDelaySec = root.intValue("wsReconnectMaxDelaySec", 60)
                ),
                wsTemplates = root.arrayValue("wsTemplates")?.mapNotNull { item ->
                    val o = item.jsonObject
                    val name = o.stringValue("name")
                    WsMessageTemplate(
                        id = o.longValue("id", -1L),
                        name = name,
                        content = o.stringValue("content"),
                        binary = o.booleanValue("binary", false),
                        encoding = o.stringValue("encoding", "HEX"),
                        createdAt = o.longValue("createdAt", 0L)
                    ).takeIf { it.id >= 0 && name.isNotBlank() }
                } ?: emptyList(),
                nextTemplateId = root.longValue("wsTemplateNextId", 1L)
            )
        )
    }

    /** 把当前四个 Store 的状态写入磁盘 */
    fun save() {
        val project = ServiceLocator.projectStore.snapshot()
        val env = ServiceLocator.envStore.snapshot()
        val history = ServiceLocator.historyStore.snapshot()
        val collection = ServiceLocator.collectionStore.snapshot()
        val monitor = ServiceLocator.monitorStore.snapshot()
        val protocolSessions = ServiceLocator.protocolSessionStore.snapshot()

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
                                    add(JsonPrimitive(seal(v)))
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
                            h.requestHeaders.forEach { add(JsonPrimitive(seal(it))) }
                        })
                        put("requestBody", seal(h.requestBody))
                        put("requestBodyTruncated", h.requestBodyTruncated)
                        put("responseHeaders", buildJsonArray {
                            h.responseHeaders.forEach { add(JsonPrimitive(seal(it))) }
                        })
                        put("responseBody", seal(h.responseBody))
                        put("responseBodyTruncated", h.responseBodyTruncated)
                        put("favorite", h.favorite)
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
                        put("data", seal(r.data))
                        put("createdAt", r.createdAt)
                    })
                }
            })
            put("requestNextId", collection.nextRequestId)

            put("collectionVars", buildJsonArray {
                collection.variables.forEach { (collectionId, entries) ->
                    add(buildJsonObject {
                        put("collectionId", collectionId)
                        put("vars", buildJsonArray {
                            entries.forEach { kv ->
                                add(buildJsonObject {
                                    put("key", kv.key)
                                    put("value", seal(kv.value))
                                    put("enabled", kv.enabled)
                                })
                            }
                        })
                    })
                }
            })

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
                        put("latencyThresholdMs", m.latencyThresholdMs)
                        put("enabled", m.enabled)
                        put("group", m.group)
                        put("createdAt", m.createdAt)
                        put("method", m.method)
                        put("headers", buildJsonArray {
                            m.headers.forEach { h ->
                                add(buildJsonObject {
                                    put("key", h.key)
                                    put("value", seal(h.value))
                                    put("enabled", h.enabled)
                                })
                            }
                        })
                        put("bodyType", m.bodyType)
                        put("bodyText", m.bodyText)
                        put("authType", m.authType)
                        put("bearerToken", seal(m.bearerToken))
                        put("basicUsername", m.basicUsername)
                        put("basicPassword", seal(m.basicPassword))
                        put("apiKeyName", m.apiKeyName)
                        put("apiKeyValue", seal(m.apiKeyValue))
                        put("assertions", buildJsonArray {
                            m.assertions.forEach { a ->
                                add(buildJsonObject {
                                    put("type", a.type)
                                    put("expression", a.expression)
                                    put("expected", a.expected)
                                })
                            }
                        })
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
                        put("ok", r.ok)
                    })
                }
            })
            put("monitorRecordNextId", monitor.nextRecordId)

            put("monitorHistory", buildJsonArray {
                monitor.history.forEach { h ->
                    add(buildJsonObject {
                        put("id", h.id)
                        put("targetId", h.targetId)
                        put("ok", h.ok)
                        put("slow", h.slow)
                        put("durationMs", h.durationMs)
                        put("checkedAt", h.checkedAt)
                    })
                }
            })
            put("monitorHistoryNextId", monitor.nextHistoryId)

            put("wsSessionHistory", buildJsonArray {
                protocolSessions.wsHistory.forEach { entry ->
                    add(buildJsonObject {
                        put("id", entry.id)
                        put("endpoint", entry.endpoint)
                        put("text", entry.text)
                        put("isSystem", entry.isSystem)
                        put("timestamp", entry.timestamp)
                    })
                }
            })
            put("wsSessionNextId", protocolSessions.nextWsId)
            put("wsAutoReconnect", protocolSessions.wsReconnectSettings.enabled)
            put("wsReconnectBaseDelaySec", protocolSessions.wsReconnectSettings.baseDelaySec)
            put("wsReconnectMaxDelaySec", protocolSessions.wsReconnectSettings.maxDelaySec)
            put("wsTemplates", buildJsonArray {
                protocolSessions.wsTemplates.forEach { template ->
                    add(buildJsonObject {
                        put("id", template.id)
                        put("name", template.name)
                        put("content", template.content)
                        put("binary", template.binary)
                        put("encoding", template.encoding)
                        put("createdAt", template.createdAt)
                    })
                }
            })
            put("wsTemplateNextId", protocolSessions.nextTemplateId)
            put("mqttSubscriptions", buildJsonArray {
                protocolSessions.mqttSubscriptions.forEach { subscription ->
                    add(buildJsonObject {
                        put("topic", subscription.topic)
                        put("qos", subscription.qos)
                    })
                }
            })

            put("cookies", buildJsonArray {
                val cookie = ServiceLocator.cookieStore.snapshot()
                cookie.projects.forEach { (projectId, hosts) ->
                    add(buildJsonObject {
                        put("projectId", projectId)
                        put("hosts", buildJsonArray {
                            hosts.forEach { (domain, list) ->
                                add(buildJsonObject {
                                    put("host", domain)
                                    put("cookies", buildJsonArray {
                                        list.forEach { c ->
                                            add(buildJsonObject {
                                                put("name", c.name)
                                                put("value", seal(c.value))
                                                put("path", c.path)
                                                put("expiresAtMs", c.expiresAtMs)
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            })
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
        requestHeaders = o.stringValueList("requestHeaders").map(::open),
        requestBody = open(o.stringValue("requestBody")),
        requestBodyTruncated = o.booleanValue("requestBodyTruncated", false),
        responseHeaders = o.stringValueList("responseHeaders").map(::open),
        responseBody = open(o.stringValue("responseBody")),
        responseBodyTruncated = o.booleanValue("responseBodyTruncated", false),
        favorite = o.booleanValue("favorite", false)
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
            latencyThresholdMs = o.longValue("latencyThresholdMs", 0L),
            enabled = o.booleanValue("enabled", true),
            group = o.stringValue("group"),
            createdAt = o.longValue("createdAt", 0L),
            method = o.stringValue("method", "GET"),
            headers = o.arrayValue("headers")?.mapNotNull { h ->
                val ho = h.jsonObject
                KeyValueEntry(
                    key = ho.stringValue("key"),
                    value = open(ho.stringValue("value")),
                    enabled = ho.booleanValue("enabled", true)
                )
            } ?: emptyList(),
            bodyType = o.stringValue("bodyType", "NONE"),
            bodyText = o.stringValue("bodyText"),
            authType = o.stringValue("authType", "NONE"),
            bearerToken = open(o.stringValue("bearerToken")),
            basicUsername = o.stringValue("basicUsername"),
            basicPassword = open(o.stringValue("basicPassword")),
            apiKeyName = o.stringValue("apiKeyName"),
            apiKeyValue = open(o.stringValue("apiKeyValue")),
            assertions = o.arrayValue("assertions")?.mapNotNull { a ->
                val ao = a.jsonObject
                MonitorAssertion(
                    type = ao.stringValue("type", "CONTAINS"),
                    expression = ao.stringValue("expression"),
                    expected = ao.stringValue("expected")
                )
            } ?: emptyList()
        ).takeIf { it.id >= 0 }
    }

    /** 解析单条拨测历史样本 */
    private fun parseMonitorHistory(o: JsonObject): MonitorHistoryEntry? =
        MonitorHistoryEntry(
            id = o.longValue("id", -1L),
            targetId = o.longValue("targetId", -1L),
            ok = o.booleanValue("ok", false),
            slow = o.booleanValue("slow", false),
            durationMs = o.longValue("durationMs", 0L),
            checkedAt = o.longValue("checkedAt", 0L)
        ).takeIf { it.id >= 0 && it.targetId >= 0 }

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
            checkedAt = o.longValue("checkedAt", 0L),
            ok = o.booleanValue("ok", false)
        ).takeIf { it.id >= 0 }
    }
}
