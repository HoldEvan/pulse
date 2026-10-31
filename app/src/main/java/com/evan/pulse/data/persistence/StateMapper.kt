package com.evan.pulse.data.persistence

import com.evan.pulse.data.db.CollectionEntity
import com.evan.pulse.data.db.CollectionVarEntity
import com.evan.pulse.data.db.CookieEntity
import com.evan.pulse.data.db.EnvironmentEntity
import com.evan.pulse.data.db.EnvironmentVarEntity
import com.evan.pulse.data.db.HistoryEntity
import com.evan.pulse.data.db.MonitorHistoryEntity
import com.evan.pulse.data.db.MonitorRecordEntity
import com.evan.pulse.data.db.MonitorTargetEntity
import com.evan.pulse.data.db.MqttSubscriptionEntity
import com.evan.pulse.data.db.ProjectEntity
import com.evan.pulse.data.db.SavedRequestEntity
import com.evan.pulse.data.db.MqttSessionEntity
import com.evan.pulse.data.db.SseHistoryEntity
import com.evan.pulse.data.db.WsSessionEntity
import com.evan.pulse.data.db.WsTemplateEntity
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
import com.evan.pulse.data.model.MqttSessionEntry
import com.evan.pulse.data.model.SseHistoryEntry
import com.evan.pulse.data.model.WsSessionEntry
import com.evan.pulse.di.StoredCookie
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 领域模型与 Room 实体之间的映射。
 *
 * 加密收口在 [FieldCipher] 上：写入实体前 [FieldCipher.seal]，读回领域对象前
 * [FieldCipher.open]。因此表里永远只有密文，而 Store 与界面只接触明文。
 */
class StateMapper(private val cipher: FieldCipher) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- 项目 ----

    fun projectToEntity(item: ProjectInfo) = ProjectEntity(item.id, item.name)

    fun projectFromEntity(item: ProjectEntity) = ProjectInfo(item.id, item.name)

    // ---- 环境 ----

    fun environmentToEntity(item: EnvironmentInfo) = EnvironmentEntity(
        id = item.id,
        projectId = item.projectId,
        name = item.name,
        isActive = item.isActive
    )

    fun environmentVarsToEntity(item: EnvironmentInfo): List<EnvironmentVarEntity> =
        item.variables.mapIndexed { index, (key, value) ->
            EnvironmentVarEntity(item.id, index, cipher.seal(key), cipher.seal(value))
        }

    fun environmentFromEntity(
        entity: EnvironmentEntity,
        vars: List<EnvironmentVarEntity>
    ) = EnvironmentInfo(
        id = entity.id,
        projectId = entity.projectId,
        name = entity.name,
        isActive = entity.isActive,
        variables = vars.filter { it.envId == entity.id }
            .sortedBy { it.position }
            .map { cipher.open(it.name) to cipher.open(it.value) }
    )

    // ---- 集合与接口 ----

    fun collectionToEntity(item: CollectionInfo, position: Int) = CollectionEntity(
        id = item.id,
        projectId = item.projectId,
        parentId = item.parentId,
        name = item.name,
        position = position
    )

    fun collectionFromEntity(item: CollectionEntity) = CollectionInfo(
        id = item.id,
        projectId = item.projectId,
        parentId = item.parentId,
        name = item.name
    )

    fun collectionVarsToEntity(
        collectionId: Long,
        entries: List<KeyValueEntry>
    ): List<CollectionVarEntity> = entries.mapIndexed { index, entry ->
        CollectionVarEntity(
            collectionId = collectionId,
            position = index,
            key = cipher.seal(entry.key),
            value = cipher.seal(entry.value),
            enabled = entry.enabled
        )
    }

    fun collectionVarsFromEntity(entries: List<CollectionVarEntity>): Map<Long, List<KeyValueEntry>> =
        entries.groupBy { it.collectionId }.mapValues { (_, list) ->
            list.sortedBy { it.position }.map {
                KeyValueEntry(
                    key = cipher.open(it.key),
                    value = cipher.open(it.value),
                    enabled = it.enabled
                )
            }
        }

    fun requestToEntity(item: SavedRequest, position: Int) = SavedRequestEntity(
        id = item.id,
        collectionId = item.collectionId,
        type = item.type.name,
        name = item.name,
        data = cipher.seal(item.data),
        createdAt = item.createdAt,
        position = position
    )

    fun requestFromEntity(item: SavedRequestEntity) = SavedRequest(
        id = item.id,
        collectionId = item.collectionId,
        type = runCatching { RequestType.valueOf(item.type) }.getOrDefault(RequestType.HTTP),
        name = item.name,
        data = cipher.open(item.data),
        createdAt = item.createdAt
    )

    // ---- 历史 ----

    fun historyToEntity(item: HistoryEntry) = HistoryEntity(
        id = item.id,
        projectId = item.projectId,
        method = item.method,
        url = cipher.seal(item.url),
        statusCode = item.statusCode,
        statusText = item.statusText,
        durationMs = item.durationMs,
        success = item.success,
        createdAt = item.createdAt,
        responseSizeBytes = item.responseSizeBytes,
        requestHeaders = cipher.sealAll(item.requestHeaders),
        requestBody = cipher.seal(item.requestBody),
        requestBodyTruncated = item.requestBodyTruncated,
        responseHeaders = cipher.sealAll(item.responseHeaders),
        responseBody = cipher.seal(item.responseBody),
        responseBodyTruncated = item.responseBodyTruncated,
        favorite = item.favorite,
        // 断言快照整块存成加密后的 JSON，便于后续加字段而不用再动表结构
        assertions = cipher.seal(
            json.encodeToString(ListSerializer(HistoryAssertion.serializer()), item.assertions)
        )
    )

    fun historyFromEntity(item: HistoryEntity) = HistoryEntry(
        id = item.id,
        projectId = item.projectId,
        method = item.method,
        url = cipher.open(item.url),
        statusCode = item.statusCode,
        statusText = item.statusText,
        durationMs = item.durationMs,
        success = item.success,
        createdAt = item.createdAt,
        responseSizeBytes = item.responseSizeBytes,
        requestHeaders = cipher.openAll(item.requestHeaders),
        requestBody = cipher.open(item.requestBody),
        requestBodyTruncated = item.requestBodyTruncated,
        responseHeaders = cipher.openAll(item.responseHeaders),
        responseBody = cipher.open(item.responseBody),
        responseBodyTruncated = item.responseBodyTruncated,
        favorite = item.favorite,
        assertions = decodeList(cipher.open(item.assertions)) { HistoryAssertion.serializer() }
    )

    // ---- Cookie ----

    fun cookieToEntity(projectId: Long, item: StoredCookie) = CookieEntity(
        projectId = projectId,
        domain = item.domain,
        name = item.name,
        path = item.path,
        value = cipher.seal(item.value),
        expiresAtMs = item.expiresAtMs
    )

    fun cookieFromEntity(item: CookieEntity) = StoredCookie(
        name = item.name,
        value = cipher.open(item.value),
        domain = item.domain,
        path = item.path,
        expiresAtMs = item.expiresAtMs
    )

    // ---- 拨测 ----

    fun monitorTargetToEntity(item: MonitorTarget) = MonitorTargetEntity(
        id = item.id,
        projectId = item.projectId,
        name = item.name,
        type = item.type.name,
        host = item.host,
        port = item.port,
        url = cipher.seal(item.url),
        timeoutMs = item.timeoutMs,
        intervalSec = item.intervalSec,
        statusMin = item.statusMin,
        statusMax = item.statusMax,
        latencyThresholdMs = item.latencyThresholdMs,
        enabled = item.enabled,
        group = item.group,
        createdAt = item.createdAt,
        method = item.method,
        headers = json.encodeToString(
            ListSerializer(KeyValueEntry.serializer()),
            item.headers.map { it.copy(value = cipher.seal(it.value)) }
        ),
        bodyType = item.bodyType,
        bodyText = cipher.seal(item.bodyText),
        authType = item.authType,
        bearerToken = cipher.seal(item.bearerToken),
        basicUsername = item.basicUsername,
        basicPassword = cipher.seal(item.basicPassword),
        apiKeyName = item.apiKeyName,
        apiKeyValue = cipher.seal(item.apiKeyValue),
        assertions = json.encodeToString(ListSerializer(MonitorAssertion.serializer()), item.assertions)
    )

    fun monitorTargetFromEntity(item: MonitorTargetEntity) = MonitorTarget(
        id = item.id,
        projectId = item.projectId,
        name = item.name,
        type = runCatching { MonitorType.valueOf(item.type) }.getOrDefault(MonitorType.HTTP),
        host = item.host,
        port = item.port,
        url = cipher.open(item.url),
        timeoutMs = item.timeoutMs,
        intervalSec = item.intervalSec,
        statusMin = item.statusMin,
        statusMax = item.statusMax,
        latencyThresholdMs = item.latencyThresholdMs,
        enabled = item.enabled,
        group = item.group,
        createdAt = item.createdAt,
        method = item.method,
        headers = decodeList(item.headers) { KeyValueEntry.serializer() }
            .map { it.copy(value = cipher.open(it.value)) },
        bodyType = item.bodyType,
        bodyText = cipher.open(item.bodyText),
        authType = item.authType,
        bearerToken = cipher.open(item.bearerToken),
        basicUsername = item.basicUsername,
        basicPassword = cipher.open(item.basicPassword),
        apiKeyName = item.apiKeyName,
        apiKeyValue = cipher.open(item.apiKeyValue),
        assertions = decodeList(item.assertions) { MonitorAssertion.serializer() }
    )

    fun monitorRecordToEntity(item: MonitorRecord) = MonitorRecordEntity(
        id = item.id,
        projectId = item.projectId,
        targetId = item.targetId,
        targetName = item.targetName,
        type = item.type.name,
        message = cipher.seal(item.message),
        durationMs = item.durationMs,
        checkedAt = item.checkedAt,
        ok = item.ok
    )

    fun monitorRecordFromEntity(item: MonitorRecordEntity) = MonitorRecord(
        id = item.id,
        projectId = item.projectId,
        targetId = item.targetId,
        targetName = item.targetName,
        type = runCatching { MonitorType.valueOf(item.type) }.getOrDefault(MonitorType.HTTP),
        message = cipher.open(item.message),
        durationMs = item.durationMs,
        checkedAt = item.checkedAt,
        ok = item.ok
    )

    fun monitorHistoryToEntity(item: MonitorHistoryEntry) = MonitorHistoryEntity(
        id = item.id,
        targetId = item.targetId,
        ok = item.ok,
        slow = item.slow,
        durationMs = item.durationMs,
        checkedAt = item.checkedAt
    )

    fun monitorHistoryFromEntity(item: MonitorHistoryEntity) = MonitorHistoryEntry(
        id = item.id,
        targetId = item.targetId,
        ok = item.ok,
        slow = item.slow,
        durationMs = item.durationMs,
        checkedAt = item.checkedAt
    )

    // ---- 协议会话 ----

    fun wsSessionToEntity(item: WsSessionEntry) = WsSessionEntity(
        id = item.id,
        endpoint = cipher.seal(item.endpoint),
        text = cipher.seal(item.text),
        isSystem = item.isSystem,
        timestamp = item.timestamp
    )

    fun wsSessionFromEntity(item: WsSessionEntity) = WsSessionEntry(
        id = item.id,
        endpoint = cipher.open(item.endpoint),
        text = cipher.open(item.text),
        isSystem = item.isSystem,
        timestamp = item.timestamp
    )

    fun mqttSessionToEntity(item: MqttSessionEntry) = MqttSessionEntity(
        id = item.id,
        broker = cipher.seal(item.broker),
        text = cipher.seal(item.text),
        isSystem = item.isSystem,
        timestamp = item.timestamp
    )

    fun mqttSessionFromEntity(item: MqttSessionEntity) = MqttSessionEntry(
        id = item.id,
        broker = cipher.open(item.broker),
        text = cipher.open(item.text),
        isSystem = item.isSystem,
        timestamp = item.timestamp
    )

    fun sseHistoryToEntity(item: SseHistoryEntry) = SseHistoryEntity(
        id = item.id,
        method = item.method,
        url = cipher.seal(item.url),
        startedAt = item.startedAt,
        endedAt = item.endedAt,
        frames = item.frames,
        outcome = item.outcome,
        transcript = cipher.seal(item.transcript)
    )

    fun sseHistoryFromEntity(item: SseHistoryEntity) = SseHistoryEntry(
        id = item.id,
        method = item.method,
        url = cipher.open(item.url),
        startedAt = item.startedAt,
        endedAt = item.endedAt,
        frames = item.frames,
        outcome = item.outcome,
        transcript = cipher.open(item.transcript)
    )

    fun mqttSubscriptionToEntity(item: MqttSubscriptionEntry, position: Int) =
        MqttSubscriptionEntity(item.topic, item.qos, position)

    fun mqttSubscriptionFromEntity(item: MqttSubscriptionEntity) =
        MqttSubscriptionEntry(item.topic, item.qos)

    fun wsTemplateToEntity(item: WsMessageTemplate, position: Int) = WsTemplateEntity(
        id = item.id,
        name = item.name,
        content = cipher.seal(item.content),
        binary = item.binary,
        encoding = item.encoding,
        createdAt = item.createdAt,
        position = position
    )

    fun wsTemplateFromEntity(item: WsTemplateEntity) = WsMessageTemplate(
        id = item.id,
        name = item.name,
        content = cipher.open(item.content),
        binary = item.binary,
        encoding = item.encoding,
        createdAt = item.createdAt
    )

    private fun <T> decodeList(raw: String, serializer: () -> kotlinx.serialization.KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(serializer()), raw) }
            .getOrDefault(emptyList())
}

/**
 * 字段级加解密接口。生产用 Keystore，测试用直通实现，
 * 让映射逻辑可以在 JVM 单测里跑。
 */
interface FieldCipher {
    fun seal(plain: String): String
    fun open(value: String): String
}

/** 不做任何处理，仅用于测试或设备不支持 Keystore 时降级 */
object PlainCipher : FieldCipher {
    override fun seal(plain: String): String = plain
    override fun open(value: String): String = value
}

/** 敏感字符串列表：整体序列化为一个 JSON 列，每个元素单独加密 */
fun FieldCipher.sealAll(values: List<String>): String =
    Json.encodeToString(ListSerializer(String.serializer()), values.map { seal(it) })

fun FieldCipher.openAll(raw: String): List<String> =
    runCatching {
        Json.decodeFromString(ListSerializer(String.serializer()), raw)
    }.getOrDefault(emptyList()).map { open(it) }

