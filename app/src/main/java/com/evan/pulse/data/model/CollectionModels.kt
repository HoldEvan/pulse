package com.evan.pulse.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 集合文件夹：属于某个项目，通过 parentId 组成无限嵌套的树 */
data class CollectionInfo(
    val id: Long,
    val projectId: Long,
    /** 父文件夹 id；为空表示该项目的根文件夹 */
    val parentId: Long?,
    val name: String
)

enum class RequestType { HTTP, WS, MQTT, SSE }

/** 集合内保存的单个接口 */
data class SavedRequest(
    val id: Long,
    val collectionId: Long,
    val type: RequestType,
    val name: String,
    /** 协议页面的完整可恢复快照（JSON） */
    val data: String,
    val createdAt: Long
)

/** 导入计划中的一条接口：显示名与 HTTP 快照 JSON */
data class ImportedItem(val name: String, val data: String)

/** 导入计划中的一个标签分组 */
data class ImportGroup(val tag: String, val items: List<ImportedItem>)

/** 导入结果：新建集合名与实际写入的接口数量 */
data class ImportResult(val collectionName: String, val requestCount: Int)


@Serializable
data class KeyValueEntry(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true
)

@Serializable
data class FormFieldEntry(
    val name: String = "",
    val value: String = "",
    val isFile: Boolean = false,
    val fileName: String? = null
)

@Serializable
data class HttpSnapshot(
    val method: String = "GET",
    val url: String = "",
    val params: List<KeyValueEntry> = emptyList(),
    val headers: List<KeyValueEntry> = emptyList(),
    val bodyType: String = "NONE",
    val bodyText: String = "",
    val formFields: List<FormFieldEntry> = emptyList(),
    val authType: String = "NONE",
    val bearerToken: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    val apiKeyName: String = "",
    val apiKeyValue: String = "",
    /** 发送前执行的脚本 */
    val preScript: String = "",
    /** 拿到响应后执行的脚本 */
    val postScript: String = ""
)

@Serializable
data class WsSnapshot(
    val url: String = "",
    val pingIntervalSec: Int = 0,
    val pingPayload: String = "ping"
)

@Serializable
data class MqttSnapshot(
    val brokerUrl: String = "",
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val keepAliveSec: String = "60",
    val subTopic: String = "",
    val subQos: Int = 0,
    val pubTopic: String = "",
    val pubQos: Int = 0
)

@Serializable
data class SseSnapshot(
    val url: String = "",
    val headers: List<KeyValueEntry> = emptyList(),
    val eventFilter: String = "",
    val autoReconnect: Boolean = false,
    val reconnectDelaySec: Int = 3
)

/** 快照统一序列化配置：字段只增不减，旧快照缺字段时用默认值兜底 */
val snapshotJson = Json { ignoreUnknownKeys = true }

/**
 * 快照里的主地址，供集合列表和名称一起显示。
 * MQTT 的订阅/发布主题才是真正的操作对象，主题非空时优先显示主题。
 */
fun SavedRequest.targetUrl(): String = runCatching {
    when (type) {
        RequestType.HTTP -> snapshotJson.decodeFromString(HttpSnapshot.serializer(), data).url
        RequestType.WS -> snapshotJson.decodeFromString(WsSnapshot.serializer(), data).url
        RequestType.SSE -> snapshotJson.decodeFromString(SseSnapshot.serializer(), data).url
        RequestType.MQTT -> {
            val mqtt = snapshotJson.decodeFromString(MqttSnapshot.serializer(), data)
            mqtt.subTopic.ifEmpty { mqtt.pubTopic }.ifEmpty { mqtt.brokerUrl }
        }
    }
}.getOrDefault("")

/**
 * HTTP 快照里保存的方法，供集合行直接显示 GET/POST 这类徽章。
 *
 * 非 HTTP 类型没有方法概念，返回 null 由界面回落显示协议名。
 */
fun SavedRequest.httpMethod(): String? = when (type) {
    RequestType.HTTP -> runCatching {
        snapshotJson.decodeFromString(HttpSnapshot.serializer(), data).method
    }.getOrNull()?.takeIf { it.isNotBlank() }
    else -> null
}
