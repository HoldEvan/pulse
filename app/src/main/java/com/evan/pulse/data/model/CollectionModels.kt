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
    /** GraphQL 的 variables JSON 文本 */
    val graphqlVariables: String = "{}",
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
    val postScript: String = "",
    /** 请求超时秒数 */
    val timeoutSec: String = "30",
    /** 连接超时秒数，空串沿用总超时 */
    val connectTimeoutSec: String = "",
    /** 读取超时秒数，空串沿用总超时 */
    val readTimeoutSec: String = "",
    /** 原始二进制请求体：文件 URI 与展示名 */
    val bodyFileUri: String? = null,
    val bodyFileName: String? = null,
    /** 是否使用设置页的全局代理 */
    val useProxy: Boolean = true,
    /** 弱网模拟档位名（FIVE_G/FOUR_G/THREE_G/TWO_G/FORCE_TIMEOUT），NONE 表示关闭 */
    val throttlePreset: String = "NONE",
    /** 强制超时秒数，0 表示不启用 */
    val forceTimeoutSec: String = "",
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试 */
    val insecureTls: Boolean = false,
    /** 自定义 CA 证书 PEM，追加到系统信任库 */
    val customCaPem: String = "",
    /** 客户端证书 PEM（mTLS） */
    val clientCertPem: String = "",
    /** 客户端私钥 PEM（PKCS#8） */
    val clientKeyPem: String = "",
    /** 是否自动跟随 3xx 重定向 */
    val followRedirects: Boolean = true,
    /** 最大跟随跳数 */
    val maxRedirects: Int = 10,
    /** 响应断言：请求成功后按顺序校验响应体 */
    val assertions: List<AssertionEntry> = emptyList()
)

/** 快照里的单条响应断言 */
@Serializable
data class AssertionEntry(
    val type: String = "CONTAINS",
    val expression: String = "",
    val expected: String = "",
    /** 比较运算符；为空时按类型取默认值，兼容旧快照 */
    val operator: String = ""
)

@Serializable
data class WsSnapshot(
    val url: String = "",
    val pingIntervalSec: Int = 0,
    val pingPayload: String = "ping",
    /** 定时发送：开关、间隔与内容 */
    val autoSendEnabled: Boolean = false,
    val autoSendIntervalSec: Int = 10,
    val autoSendContent: String = "",
    /** 握手时附带的额外请求头，用于鉴权等场景 */
    val headers: List<KeyValueEntry> = emptyList(),
    /** Sec-WebSocket-Protocol 子协议，逗号分隔后按顺序协商 */
    val subprotocols: List<String> = emptyList(),
    /** 是否使用设置页的全局代理 */
    val useProxy: Boolean = true,
    val autoReconnect: Boolean = false,
    val reconnectBaseDelaySec: Int = 3,
    val reconnectMaxDelaySec: Int = 60,
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试 */
    val insecureTls: Boolean = false,
    /** 自定义 CA 证书 PEM，追加到系统信任库 */
    val customCaPem: String = "",
    /** 客户端证书 PEM（mTLS） */
    val clientCertPem: String = "",
    /** 客户端私钥 PEM（PKCS#8） */
    val clientKeyPem: String = "",
    /** 收到消息时执行的断言 */
    val assertions: List<AssertionEntry> = emptyList()
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
    val pubQos: Int = 0,
    /** 发布时是否带 retain 标志 */
    val pubRetain: Boolean = false,
    /** 是否使用设置页的全局代理 */
    val useProxy: Boolean = true,
    /** 跳过 TLS 证书校验：仅对 ssl:// 生效，用于自签名/内网 Broker 调试 */
    val insecureTls: Boolean = false,
    /** 自定义 CA 证书 PEM，追加到系统信任库 */
    val customCaPem: String = "",
    /** 客户端证书 PEM（mTLS） */
    val clientCertPem: String = "",
    /** 客户端私钥 PEM（PKCS#8） */
    val clientKeyPem: String = "",
    /** 收到消息时执行的断言 */
    val assertions: List<AssertionEntry> = emptyList(),
    /** 协议版本：5 或 3（3.1.1） */
    val mqttVersion: Int = 5,
    /** clean start / clean session */
    val cleanStart: Boolean = true,
    /** 会话过期秒数（仅 MQTT 5） */
    val sessionExpirySec: String = "0",
    /** 断线自动重连 */
    val autoReconnect: Boolean = true,
    /** 遗嘱：主题为空表示不启用 */
    val willTopic: String = "",
    val willPayload: String = "",
    val willQos: Int = 0,
    val willRetain: Boolean = false,
    /** 发布负载按二进制（HEX / BASE64）解码后发送 */
    val pubBinary: Boolean = false,
    val pubEncoding: String = "HEX"
)

@Serializable
data class SseSnapshot(
    val url: String = "",
    val method: String = "GET",
    val params: List<KeyValueEntry> = emptyList(),
    val headers: List<KeyValueEntry> = emptyList(),
    val bodyType: String = "NONE",
    val bodyText: String = "",
    val eventFilter: String = "",
    val autoReconnect: Boolean = false,
    val reconnectDelaySec: Int = 3,
    /** 指数退避重连：间隔逐次翻倍 */
    val exponentialBackoff: Boolean = true,
    /** 重连间隔上限（秒） */
    val maxBackoffSec: Int = 30,
    /** 重连抖动：间隔叠加 ±25% 随机量 */
    val jitter: Boolean = true,
    /** 最大连续重连次数，0 表示无限 */
    val maxRetries: Int = 0,
    /** 空闲超时（秒），0 表示不检测 */
    val idleTimeoutSec: Int = 0,
    /** 拼接预览的 JSONPath 表达式，空表示不提取 */
    val previewPath: String = "",
    /** 连接建立超时秒数 */
    val timeoutSec: Int = 15,
    /** 是否使用设置页的全局代理 */
    val useProxy: Boolean = true,
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试 */
    val insecureTls: Boolean = false,
    /** 自定义 CA 证书 PEM，追加到系统信任库 */
    val customCaPem: String = "",
    /** 客户端证书 PEM（mTLS） */
    val clientCertPem: String = "",
    /** 客户端私钥 PEM（PKCS#8） */
    val clientKeyPem: String = "",
    /** 断点续连起点：首次连接作为 Last-Event-ID 发送 */
    val lastEventId: String = "",
    /** 鉴权：取值为 AuthType 名称，旧快照缺字段时为 NONE */
    val authType: String = "NONE",
    val bearerToken: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    val apiKeyName: String = "",
    val apiKeyValue: String = ""
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
