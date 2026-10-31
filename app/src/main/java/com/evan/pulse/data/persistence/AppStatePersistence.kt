package com.evan.pulse.data.persistence

import android.content.Context
import com.evan.pulse.data.db.CollectionVarEntity
import com.evan.pulse.data.db.EnvironmentVarEntity
import com.evan.pulse.data.db.PulseDatabase
import com.evan.pulse.data.db.StoreMetaEntity
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryAssertion
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.MonitorAssertion
import com.evan.pulse.data.model.MonitorHistoryEntry
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.WsMessageTemplate
import com.evan.pulse.data.model.WsReconnectSettings
import com.evan.pulse.data.model.MqttSessionEntry
import com.evan.pulse.data.model.SseHistoryEntry
import com.evan.pulse.data.model.WsSessionEntry
import com.evan.pulse.data.crypto.KeystoreSecretBox
import com.evan.pulse.di.CollectionStoreSnapshot
import com.evan.pulse.di.CookieStoreSnapshot
import com.evan.pulse.di.StoredCookie
import com.evan.pulse.di.EnvStoreSnapshot
import com.evan.pulse.di.HistoryStoreSnapshot
import com.evan.pulse.di.MonitorStoreSnapshot
import com.evan.pulse.di.ProjectStoreSnapshot
import com.evan.pulse.di.ProtocolSessionStoreSnapshot
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.crypto.SecretBox
import com.evan.pulse.domain.crypto.SecretField
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 应用状态的持久化门面。
 *
 * 运行时数据落在 Room；`app_data.json` 只作为「备份交换格式」保留：
 * - 首次升级安装：DB 为空且旧 JSON 存在时导入一次，旧文件保留不删，便于回滚。
 * - 导出备份：由 [exportJson] 从当前内存快照生成，结构与旧版本一致，
 *   因此旧版 App 仍可读回。
 * - 恢复备份：解析 JSON 后写回各 Store，再整体落库。
 *
 * 每个 Store 有独立的保存入口（[saveProjects] 等），写操作只重写自己那组表，
 * 不再像旧实现那样每次改动都序列化全部模块。
 */
class AppStatePersistence(
    context: Context,
    private val secrets: SecretBox? = runCatching { KeystoreSecretBox() }.getOrNull(),
    private val db: PulseDatabase = PulseDatabase.build(context),
) {

    private val legacyFile = File(context.filesDir, "app_data.json")
    private val json = Json { prettyPrint = true }

    private val cipher = object : FieldCipher {
        override fun seal(plain: String): String {
            val box = secrets ?: return plain
            return runCatching { SecretField.seal(plain, box) }.getOrDefault(plain)
        }

        override fun open(value: String): String = SecretField.open(value, secrets)
    }

    private val mapper = StateMapper(cipher)

    private fun seal(plain: String): String = cipher.seal(plain)

    private fun open(value: String): String = cipher.open(value)

    private val projectDao get() = db.projectDao()
    private val envDao get() = db.environmentDao()
    private val collectionDao get() = db.collectionDao()
    private val historyDao get() = db.historyDao()
    private val cookieDao get() = db.cookieDao()
    private val monitorDao get() = db.monitorDao()
    private val protocolDao get() = db.protocolDao()
    private val metaDao get() = db.metaDao()

    // ---- 加载 ----

    /**
     * 启动时恢复。
     *
     * 用 `initialized` 标记而非「某张表是否为空」判断是否首次启动：
     * 否则用户只改环境、项目表仍为空时，下次启动会误判为首次而跳过恢复。
     */
    fun load() {
        val initialized = runCatching { metaLong(KEY_INITIALIZED, 0L) == 1L }.getOrDefault(false)
        if (initialized) {
            runCatching { restoreFromDatabase() }
            return
        }
        runCatching { importLegacyIfPresent() }
        // 首次启动也要落库种子数据，避免后续只写部分表时出现「半初始化」
        runCatching {
            saveAll()
            metaDao.upsert(meta(KEY_INITIALIZED, 1))
        }
    }

    private fun importLegacyIfPresent() {
        if (!legacyFile.exists()) return
        val root = runCatching {
            json.parseToJsonElement(legacyFile.readText()).jsonObject
        }.getOrNull() ?: return
        // restoreFromJsonElement 内部会写库；失败时保留旧文件，下次启动重试
        runCatching { restoreFromJsonElement(root) }
    }

    private fun restoreFromDatabase() {
        ServiceLocator.projectStore.restore(
            ProjectStoreSnapshot(
                projects = projectDao.allNow().map { mapper.projectFromEntity(it) },
                currentProjectId = metaLong(KEY_CURRENT_PROJECT, 1L),
                nextId = metaLong(KEY_PROJECT_NEXT, 2L)
            )
        )

        val envEntities = envDao.allNow()
        val envVars = envDao.allVarsNow()
        ServiceLocator.envStore.restore(
            EnvStoreSnapshot(
                environments = envEntities.map { mapper.environmentFromEntity(it, envVars) },
                nextId = metaLong(KEY_ENV_NEXT, 2L)
            )
        )

        ServiceLocator.historyStore.restore(
            HistoryStoreSnapshot(
                entries = historyDao.allNow().map { mapper.historyFromEntity(it) },
                nextId = metaLong(KEY_HISTORY_NEXT, 1L)
            )
        )

        val collections = collectionDao.allNow()
        ServiceLocator.collectionStore.restore(
            CollectionStoreSnapshot(
                collections = collections.map { mapper.collectionFromEntity(it) },
                requests = collectionDao.allRequestsNow().map { mapper.requestFromEntity(it) },
                nextCollectionId = metaLong(KEY_COLLECTION_NEXT, 2L),
                nextRequestId = metaLong(KEY_REQUEST_NEXT, 1L),
                variables = mapper.collectionVarsFromEntity(collectionDao.allVarsNow())
            )
        )

        ServiceLocator.monitorStore.restore(
            MonitorStoreSnapshot(
                targets = monitorDao.allTargetsNow().map { mapper.monitorTargetFromEntity(it) },
                records = monitorDao.allRecordsNow().map { mapper.monitorRecordFromEntity(it) },
                history = monitorDao.allHistoryNow().map { mapper.monitorHistoryFromEntity(it) },
                nextTargetId = metaLong(KEY_MONITOR_TARGET_NEXT, 1L),
                nextRecordId = metaLong(KEY_MONITOR_RECORD_NEXT, 1L),
                nextHistoryId = metaLong(KEY_MONITOR_HISTORY_NEXT, 1L)
            )
        )

        ServiceLocator.protocolSessionStore.restore(
            ProtocolSessionStoreSnapshot(
                wsHistory = protocolDao.allWsNow().map { mapper.wsSessionFromEntity(it) },
                mqttSubscriptions = protocolDao.allMqttNow().map {
                    mapper.mqttSubscriptionFromEntity(it)
                },
                nextWsId = metaLong(KEY_WS_NEXT, 1L),
                wsReconnectSettings = WsReconnectSettings(
                    enabled = metaLong(KEY_WS_RECONNECT_ENABLED, 0L) == 1L,
                    baseDelaySec = metaLong(KEY_WS_RECONNECT_BASE, 3L).toInt(),
                    maxDelaySec = metaLong(KEY_WS_RECONNECT_MAX, 60L).toInt()
                ),
                wsTemplates = protocolDao.allTemplatesNow().map { mapper.wsTemplateFromEntity(it) },
                nextTemplateId = metaLong(KEY_WS_TEMPLATE_NEXT, 1L),
                mqttHistory = protocolDao.allMqttSessionsNow().map { mapper.mqttSessionFromEntity(it) },
                nextMqttId = metaLong(KEY_MQTT_SESSION_NEXT, 1L)
            )
        )
        ServiceLocator.sseHistoryStore.restore(
            protocolDao.allSseHistoryNow().map { mapper.sseHistoryFromEntity(it) },
            metaLong(KEY_SSE_HISTORY_NEXT, 1L)
        )

        val cookies = cookieDao.allNow().groupBy { it.projectId }
            .mapValues { (_, list) ->
                list.groupBy { it.domain }
                    .mapValues { (_, domainCookies) ->
                        domainCookies.map { mapper.cookieFromEntity(it) }
                    }
            }
        ServiceLocator.cookieStore.restore(CookieStoreSnapshot(cookies))
    }

    private fun metaLong(key: String, default: Long): Long =
        metaDao.valueNow(key)?.toLongOrNull() ?: default

    // ---- 备份交换 ----

    /** 从外部 JSON 恢复各 Store（本地 / WebDAV 备份读回） */
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
        saveAll()
    }

    /** 生成与旧版本一致的备份 JSON，供本地备份与 WebDAV 上传 */
    fun exportJson(): String = json.encodeToString(JsonElement.serializer(), snapshotJson())

    // ---- 保存 ----

    fun saveAll() {
        saveProjects()
        saveEnvironments()
        saveHistory()
        saveCollections()
        saveCookies()
        saveMonitors()
        saveProtocols()
        saveSseHistory()
    }

    /** 兼容旧调用点：全量落库 */
    fun save() = saveAll()

    fun saveProjects() {
        val snapshot = ServiceLocator.projectStore.snapshot()
        db.runInTransaction {
            projectDao.clear()
            projectDao.insertAll(snapshot.projects.map { mapper.projectToEntity(it) })
            metaDao.upsertAll(
                listOf(
                    meta(KEY_CURRENT_PROJECT, snapshot.currentProjectId),
                    meta(KEY_PROJECT_NEXT, snapshot.nextId)
                )
            )
        }
    }

    fun saveEnvironments() {
        val snapshot = ServiceLocator.envStore.snapshot()
        db.runInTransaction {
            envDao.clear()
            envDao.clearVars()
            envDao.insertAll(snapshot.environments.map { mapper.environmentToEntity(it) })
            envDao.insertAllVars(snapshot.environments.flatMap { mapper.environmentVarsToEntity(it) })
            metaDao.upsert(meta(KEY_ENV_NEXT, snapshot.nextId))
        }
    }

    fun saveHistory() {
        val snapshot = ServiceLocator.historyStore.snapshot()
        db.runInTransaction {
            historyDao.clear()
            historyDao.insertAll(snapshot.entries.map { mapper.historyToEntity(it) })
            metaDao.upsert(meta(KEY_HISTORY_NEXT, snapshot.nextId))
        }
    }

    fun saveCollections() {
        val snapshot = ServiceLocator.collectionStore.snapshot()
        db.runInTransaction {
            collectionDao.clear()
            collectionDao.clearRequests()
            collectionDao.clearVars()
            collectionDao.insertAll(
                snapshot.collections.mapIndexed { index, item ->
                    mapper.collectionToEntity(item, index)
                }
            )
            collectionDao.insertAllRequests(
                snapshot.requests.mapIndexed { index, item ->
                    mapper.requestToEntity(item, index)
                }
            )
            collectionDao.insertAllVars(
                snapshot.variables.flatMap { (collectionId, entries) ->
                    mapper.collectionVarsToEntity(collectionId, entries)
                }
            )
            metaDao.upsertAll(
                listOf(
                    meta(KEY_COLLECTION_NEXT, snapshot.nextCollectionId),
                    meta(KEY_REQUEST_NEXT, snapshot.nextRequestId)
                )
            )
        }
    }

    fun saveCookies() {
        val snapshot = ServiceLocator.cookieStore.snapshot()
        db.runInTransaction {
            cookieDao.clear()
            cookieDao.insertAll(
                snapshot.projects.flatMap { (projectId, hosts) ->
                    hosts.values.flatten().map { mapper.cookieToEntity(projectId, it) }
                }
            )
        }
    }

    fun saveMonitors() {
        val snapshot = ServiceLocator.monitorStore.snapshot()
        db.runInTransaction {
            monitorDao.clearTargets()
            monitorDao.clearRecords()
            monitorDao.clearHistory()
            monitorDao.insertAllTargets(snapshot.targets.map { mapper.monitorTargetToEntity(it) })
            monitorDao.insertAllRecords(snapshot.records.map { mapper.monitorRecordToEntity(it) })
            monitorDao.insertAllHistory(snapshot.history.map { mapper.monitorHistoryToEntity(it) })
            metaDao.upsertAll(
                listOf(
                    meta(KEY_MONITOR_TARGET_NEXT, snapshot.nextTargetId),
                    meta(KEY_MONITOR_RECORD_NEXT, snapshot.nextRecordId),
                    meta(KEY_MONITOR_HISTORY_NEXT, snapshot.nextHistoryId)
                )
            )
        }
    }

    fun saveProtocols() {
        val snapshot = ServiceLocator.protocolSessionStore.snapshot()
        db.runInTransaction {
            protocolDao.clearWs()
            protocolDao.clearMqtt()
            protocolDao.clearTemplates()
            protocolDao.clearMqttSessions()
            protocolDao.insertAllWs(snapshot.wsHistory.map { mapper.wsSessionToEntity(it) })
            protocolDao.insertAllMqttSessions(snapshot.mqttHistory.map { mapper.mqttSessionToEntity(it) })
            protocolDao.insertAllMqtt(
                snapshot.mqttSubscriptions.mapIndexed { index, item ->
                    mapper.mqttSubscriptionToEntity(item, index)
                }
            )
            protocolDao.insertAllTemplates(
                snapshot.wsTemplates.mapIndexed { index, item ->
                    mapper.wsTemplateToEntity(item, index)
                }
            )
            metaDao.upsertAll(
                listOf(
                    meta(KEY_WS_NEXT, snapshot.nextWsId),
                    meta(KEY_MQTT_SESSION_NEXT, snapshot.nextMqttId),
                    meta(KEY_WS_TEMPLATE_NEXT, snapshot.nextTemplateId),
                    meta(KEY_WS_RECONNECT_ENABLED, if (snapshot.wsReconnectSettings.enabled) 1 else 0),
                    meta(KEY_WS_RECONNECT_BASE, snapshot.wsReconnectSettings.baseDelaySec.toLong()),
                    meta(KEY_WS_RECONNECT_MAX, snapshot.wsReconnectSettings.maxDelaySec.toLong())
                )
            )
        }
    }

    fun saveSseHistory() {
        val store = ServiceLocator.sseHistoryStore
        val entries = store.entries.value
        val nextId = store.nextId
        db.runInTransaction {
            protocolDao.clearSseHistory()
            protocolDao.insertAllSseHistory(entries.map { mapper.sseHistoryToEntity(it) })
            metaDao.upsertAll(listOf(meta(KEY_SSE_HISTORY_NEXT, nextId)))
        }
    }

    private fun meta(key: String, value: Long) = StoreMetaEntity(key, value.toString())

    // ---- JSON 编码（备份格式） ----

    private fun snapshotJson(): JsonObject {
        val project = ServiceLocator.projectStore.snapshot()
        val env = ServiceLocator.envStore.snapshot()
        val history = ServiceLocator.historyStore.snapshot()
        val collection = ServiceLocator.collectionStore.snapshot()
        val monitor = ServiceLocator.monitorStore.snapshot()
        val protocolSessions = ServiceLocator.protocolSessionStore.snapshot()

        return buildJsonObject {
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
                        put(
                            "assertions",
                            seal(
                                json.encodeToString(
                                    ListSerializer(HistoryAssertion.serializer()),
                                    h.assertions
                                )
                            )
                        )
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
            put("mqttSessionHistory", buildJsonArray {
                protocolSessions.mqttHistory.forEach { entry ->
                    add(buildJsonObject {
                        put("id", entry.id)
                        put("broker", entry.broker)
                        put("text", entry.text)
                        put("isSystem", entry.isSystem)
                        put("timestamp", entry.timestamp)
                    })
                }
            })
            put("mqttSessionNextId", protocolSessions.nextMqttId)
            put("sseHistory", buildJsonArray {
                ServiceLocator.sseHistoryStore.entries.value.forEach { entry ->
                    add(buildJsonObject {
                        put("id", entry.id)
                        put("method", entry.method)
                        put("url", entry.url)
                        put("startedAt", entry.startedAt)
                        put("endedAt", entry.endedAt)
                        put("frames", entry.frames)
                        put("outcome", entry.outcome)
                        put("transcript", entry.transcript)
                    })
                }
            })
            put("sseHistoryNextId", ServiceLocator.sseHistoryStore.nextId)
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
                ServiceLocator.cookieStore.snapshot().projects.forEach { (projectId, hosts) ->
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
    }

    // ---- 旧 JSON 解析 ----

    private fun parseProjects(root: JsonObject): List<ProjectInfo> =
        root.arrayValue("projects")?.mapNotNull { item ->
            val o = item.jsonObject
            ProjectInfo(o.longValue("id", -1L), o.stringValue("name")).takeIf { it.id >= 0 }
        } ?: emptyList()

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
                nextTemplateId = root.longValue("wsTemplateNextId", 1L),
                mqttHistory = root.arrayValue("mqttSessionHistory")?.mapNotNull { item ->
                    val o = item.jsonObject
                    MqttSessionEntry(
                        id = o.longValue("id", -1L),
                        broker = o.stringValue("broker"),
                        text = o.stringValue("text"),
                        isSystem = o.booleanValue("isSystem", false),
                        timestamp = o.longValue("timestamp", 0L)
                    ).takeIf { it.id >= 0 }
                } ?: emptyList(),
                nextMqttId = root.longValue("mqttSessionNextId", 1L)
            )
        )
        ServiceLocator.sseHistoryStore.restore(
            root.arrayValue("sseHistory")?.mapNotNull { item ->
                val o = item.jsonObject
                SseHistoryEntry(
                    id = o.longValue("id", -1L),
                    method = o.stringValue("method", "GET"),
                    url = o.stringValue("url"),
                    startedAt = o.longValue("startedAt", 0L),
                    endedAt = o.longValue("endedAt", 0L),
                    frames = o.intValue("frames", 0),
                    outcome = o.stringValue("outcome"),
                    transcript = o.stringValue("transcript")
                ).takeIf { it.id >= 0 }
            } ?: emptyList(),
            root.longValue("sseHistoryNextId", 1L)
        )
    }

    private fun JsonObject.longValue(key: String, default: Long): Long =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: default

    private fun JsonObject.longNullableValue(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.intValue(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default

    private fun JsonObject.stringValue(key: String, default: String = ""): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: default

    private fun JsonObject.booleanValue(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.arrayValue(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.stringValueList(key: String): List<String> =
        arrayValue(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun parseHistoryEntry(o: JsonObject): HistoryEntry? = HistoryEntry(
        id = o.longValue("id", -1L),
        projectId = o.longValue("projectId", 1L),
        method = o.stringValue("method", "GET"),
        url = o.stringValue("url"),
        statusCode = o.intValue("statusCode", 0),
        statusText = o.stringValue("statusText"),
        durationMs = o.longValue("durationMs", 0L),
        success = o.booleanValue("success", false),
        createdAt = o.longValue("createdAt", 0L),
        responseSizeBytes = o.longValue("responseSizeBytes", 0L),
        requestHeaders = o.stringValueList("requestHeaders").map { open(it) },
        requestBody = open(o.stringValue("requestBody")),
        requestBodyTruncated = o.booleanValue("requestBodyTruncated", false),
        responseHeaders = o.stringValueList("responseHeaders").map { open(it) },
        responseBody = open(o.stringValue("responseBody")),
        responseBodyTruncated = o.booleanValue("responseBodyTruncated", false),
        favorite = o.booleanValue("favorite", false),
        assertions = runCatching {
            json.decodeFromString(
                ListSerializer(HistoryAssertion.serializer()),
                open(o.stringValue("assertions"))
            )
        }.getOrDefault(emptyList())
    ).takeIf { it.id >= 0 }

    private fun parseMonitorTarget(o: JsonObject): MonitorTarget? {
        val id = o.longValue("id", -1L)
        if (id < 0) return null
        return MonitorTarget(
            id = id,
            projectId = o.longValue("projectId", 1L),
            name = o.stringValue("name"),
            type = runCatching { MonitorType.valueOf(o.stringValue("type", "HTTP")) }
                .getOrDefault(MonitorType.HTTP),
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
            headers = o.arrayValue("headers")?.mapNotNull { item ->
                val kv = item.jsonObject
                val key = kv.stringValue("key")
                if (key.isBlank()) null else KeyValueEntry(
                    key = key,
                    value = open(kv.stringValue("value")),
                    enabled = kv.booleanValue("enabled", true)
                )
            } ?: emptyList(),
            bodyType = o.stringValue("bodyType", "NONE"),
            bodyText = open(o.stringValue("bodyText")),
            authType = o.stringValue("authType", "NONE"),
            bearerToken = open(o.stringValue("bearerToken")),
            basicUsername = o.stringValue("basicUsername"),
            basicPassword = open(o.stringValue("basicPassword")),
            apiKeyName = o.stringValue("apiKeyName"),
            apiKeyValue = open(o.stringValue("apiKeyValue")),
            assertions = o.arrayValue("assertions")?.map { item ->
                val a = item.jsonObject
                MonitorAssertion(
                    type = a.stringValue("type", "CONTAINS"),
                    expression = a.stringValue("expression"),
                    expected = a.stringValue("expected")
                )
            } ?: emptyList()
        )
    }

    private fun parseMonitorHistory(o: JsonObject): MonitorHistoryEntry? {
        val id = o.longValue("id", -1L)
        if (id < 0) return null
        return MonitorHistoryEntry(
            id = id,
            targetId = o.longValue("targetId", -1L),
            ok = o.booleanValue("ok", false),
            slow = o.booleanValue("slow", false),
            durationMs = o.longValue("durationMs", 0L),
            checkedAt = o.longValue("checkedAt", 0L)
        )
    }

    private fun parseMonitorRecord(o: JsonObject): MonitorRecord? {
        val id = o.longValue("id", -1L)
        if (id < 0) return null
        return MonitorRecord(
            id = id,
            projectId = o.longValue("projectId", 1L),
            targetId = o.longValue("targetId", -1L),
            targetName = o.stringValue("targetName"),
            type = runCatching { MonitorType.valueOf(o.stringValue("type", "HTTP")) }
                .getOrDefault(MonitorType.HTTP),
            message = o.stringValue("message"),
            durationMs = o.longValue("durationMs", 0L),
            checkedAt = o.longValue("checkedAt", 0L),
            ok = o.booleanValue("ok", false)
        )
    }

    private companion object {
        const val KEY_CURRENT_PROJECT = "currentProjectId"
        const val KEY_PROJECT_NEXT = "projectNextId"
        const val KEY_ENV_NEXT = "envNextId"
        const val KEY_HISTORY_NEXT = "historyNextId"
        const val KEY_COLLECTION_NEXT = "collectionNextId"
        const val KEY_REQUEST_NEXT = "requestNextId"
        const val KEY_MONITOR_TARGET_NEXT = "monitorTargetNextId"
        const val KEY_MONITOR_RECORD_NEXT = "monitorRecordNextId"
        const val KEY_MONITOR_HISTORY_NEXT = "monitorHistoryNextId"
        const val KEY_WS_NEXT = "wsSessionNextId"
        const val KEY_MQTT_SESSION_NEXT = "mqttSessionNextId"
        const val KEY_SSE_HISTORY_NEXT = "sseHistoryNextId"
        const val KEY_WS_TEMPLATE_NEXT = "wsTemplateNextId"
        const val KEY_WS_RECONNECT_ENABLED = "wsReconnectEnabled"
        const val KEY_WS_RECONNECT_BASE = "wsReconnectBaseSec"
        const val KEY_WS_RECONNECT_MAX = "wsReconnectMaxSec"
        const val KEY_INITIALIZED = "dbInitialized"
    }
}
