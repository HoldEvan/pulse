package com.evan.pulse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 表结构。
 *
 * 设计约定：
 * - 每个领域模块一组表，模块之间不共享行，新增模块只需加表与 DAO，不改已有表。
 * - 列表顺序用 `position` 列显式保存，避免依赖插入顺序（SQLite 不保证）。
 * - 深层嵌套、整体读写的结构（请求头列表、拨测断言）存 JSON 文本列；
 *   需要按字段查询的（变量、Cookie、历史）拆成独立列或独立表。
 * - 敏感值在写入实体前已加密，读回后再解密，表里不存在明文。
 * - 自增计数器与单例配置放 [StoreMetaEntity]，避免为每个计数器建表。
 */

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Long,
    val name: String
)

@Entity(tableName = "environments", indices = [Index("projectId")])
data class EnvironmentEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val name: String,
    val isActive: Boolean
)

@Entity(tableName = "environment_vars", primaryKeys = ["envId", "position"])
data class EnvironmentVarEntity(
    val envId: Long,
    val position: Int,
    val name: String,
    val value: String
)

@Entity(tableName = "collections", indices = [Index("projectId")])
data class CollectionEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val parentId: Long?,
    val name: String,
    val position: Int
)

@Entity(tableName = "collection_vars", primaryKeys = ["collectionId", "position"])
data class CollectionVarEntity(
    val collectionId: Long,
    val position: Int,
    val key: String,
    val value: String,
    val enabled: Boolean
)

@Entity(tableName = "saved_requests", indices = [Index("collectionId")])
data class SavedRequestEntity(
    @PrimaryKey val id: Long,
    val collectionId: Long,
    val type: String,
    val name: String,
    val data: String,
    val createdAt: Long,
    val position: Int
)

@Entity(tableName = "history", indices = [Index("projectId"), Index("createdAt")])
data class HistoryEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val statusText: String,
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long,
    val responseSizeBytes: Long,
    val requestHeaders: String,
    val requestBody: String,
    val requestBodyTruncated: Boolean,
    val responseHeaders: String,
    val responseBody: String,
    val responseBodyTruncated: Boolean,
    val favorite: Boolean,
    /** 断言快照的 JSON（密文），v2 新增 */
    val assertions: String = ""
)

@Entity(
    tableName = "cookies",
    primaryKeys = ["projectId", "domain", "name", "path"],
    indices = [Index("projectId")]
)
data class CookieEntity(
    val projectId: Long,
    val domain: String,
    val name: String,
    val path: String,
    val value: String,
    val expiresAtMs: Long
)

@Entity(tableName = "monitor_targets", indices = [Index("projectId")])
data class MonitorTargetEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val url: String,
    val timeoutMs: Long,
    val intervalSec: Long,
    val statusMin: Int,
    val statusMax: Int,
    val latencyThresholdMs: Long,
    val enabled: Boolean,
    val group: String,
    val createdAt: Long,
    val method: String,
    val headers: String,
    val bodyType: String,
    val bodyText: String,
    val authType: String,
    val bearerToken: String,
    val basicUsername: String,
    val basicPassword: String,
    val apiKeyName: String,
    val apiKeyValue: String,
    val assertions: String
)

@Entity(tableName = "monitor_records", indices = [Index("targetId"), Index("projectId")])
data class MonitorRecordEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val targetId: Long,
    val targetName: String,
    val type: String,
    val message: String,
    val durationMs: Long,
    val checkedAt: Long,
    val ok: Boolean
)

@Entity(tableName = "monitor_history", indices = [Index("targetId")])
data class MonitorHistoryEntity(
    @PrimaryKey val id: Long,
    val targetId: Long,
    val ok: Boolean,
    val slow: Boolean,
    val durationMs: Long,
    val checkedAt: Long
)

@Entity(tableName = "ws_sessions")
data class WsSessionEntity(
    @PrimaryKey val id: Long,
    val endpoint: String,
    val text: String,
    val isSystem: Boolean,
    val timestamp: Long
)

@Entity(tableName = "mqtt_subscriptions")
data class MqttSubscriptionEntity(
    @PrimaryKey val topic: String,
    val qos: Int,
    val position: Int
)

@Entity(tableName = "ws_templates")
data class WsTemplateEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val content: String,
    val binary: Boolean,
    val encoding: String,
    val createdAt: Long,
    val position: Int
)

/** 各 Store 的自增 id 与单例配置，按字符串键存放，新增模块直接加键即可 */
@Entity(tableName = "store_meta")
data class StoreMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
