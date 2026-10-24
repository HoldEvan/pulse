package com.evan.pulse.domain.model

import com.evan.pulse.domain.net.HttpTimeline
import com.evan.pulse.domain.net.NetworkThrottleConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HTTP 方法。
 *
 * 用值类而非枚举承载：调试时常遇到 WebDAV 的 PROPFIND、MKCOL 等扩展方法，
 * 固定枚举会把这类请求挡在门外。常用方法以伴生常量提供，界面下拉仍按固定顺序展示。
 */
@JvmInline
value class HttpMethod(val value: String) {
    override fun toString(): String = value

    companion object {
        val GET = HttpMethod("GET")
        val POST = HttpMethod("POST")
        val PUT = HttpMethod("PUT")
        val DELETE = HttpMethod("DELETE")
        val PATCH = HttpMethod("PATCH")
        val HEAD = HttpMethod("HEAD")
        val OPTIONS = HttpMethod("OPTIONS")
        val TRACE = HttpMethod("TRACE")

        /** 下拉里展示的常用方法，顺序即界面顺序 */
        val COMMON: List<HttpMethod> = listOf(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE)

        /** 解析用户输入或快照里的方法名；空串回落到 GET，其余统一大写 */
        fun parse(raw: String): HttpMethod =
            HttpMethod(raw.trim().uppercase().ifEmpty { GET.value })

        /** 方法名只允许字母与连字符，避免把空格等字符发到请求行里 */
        fun isValid(raw: String): Boolean =
            raw.isNotBlank() && raw.all { it.isLetter() || it == '-' }
    }
}

enum class BodyType {
    NONE, JSON, RAW, FORM_URLENCODED, FORM_DATA, GRAPHQL
}

enum class AuthType {
    NONE, BEARER, BASIC, APIKEY
}

enum class HttpErrorType {
    TIMEOUT, DNS, NETWORK, SSL, CANCELLED, UNKNOWN
}

data class KeyValue(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true
)

data class FormField(
    val name: String = "",
    val value: String = "",
    val isFile: Boolean = false,
    val fileName: String? = null,
    val fileUri: String? = null
)

data class AuthConfig(
    val type: AuthType = AuthType.NONE,
    val bearerToken: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    /** API Key 认证：自定义请求头名，留空回退到 X-API-Key */
    val apiKeyName: String = "",
    val apiKeyValue: String = ""
)

data class HttpCallConfig(
    val method: HttpMethod,
    val url: String,
    val headers: List<KeyValue> = emptyList(),
    val queryParams: List<KeyValue> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val body: String = "",
    /** GraphQL 时的 variables JSON 文本；body 存查询语句 */
    val graphqlVariables: String = "",
    val formFields: List<FormField> = emptyList(),
    val auth: AuthConfig = AuthConfig(),
    val timeoutMs: Long = 30_000L,
    val proxy: ProxyConfig = ProxyConfig(),
    /** 弱网模拟：默认关闭，零开销直发 */
    val throttle: NetworkThrottleConfig = NetworkThrottleConfig(),
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试 */
    val insecureTls: Boolean = false
)

sealed interface HttpCallOutcome {
    data class Success(
        val statusCode: Int,
        val statusText: String,
        val headers: List<KeyValue>,
        val body: String,
        val durationMs: Long,
        val responseSizeBytes: Long,
        val timeline: HttpTimeline = HttpTimeline(),
        /** 响应原始字节，供媒体（image/audio/video）预览；文本响应可为空 */
        val bodyBytes: ByteArray? = null,
        /** 实际发出的请求头（含引擎补充的默认头），用于结果页展示真实请求 */
        val requestHeaders: List<KeyValue> = emptyList()
    ) : HttpCallOutcome

    data class Failure(
        val errorType: HttpErrorType,
        val message: String
    ) : HttpCallOutcome
}

/**
 * 把 GraphQL 查询与变量包装成标准请求体 {"query": "...", "variables": {...}}。
 * variables 不是合法 JSON 时按省略处理，由服务端返回的 GraphQL 错误暴露问题。
 */
fun buildGraphqlRequestBody(query: String, variablesJson: String): String {
    if (query.isBlank()) return ""
    val trimmed = variablesJson.trim()
    val variables = if (trimmed.isEmpty()) {
        null
    } else {
        runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
    }
    return buildJsonObject {
        put("query", query)
        variables?.let { put("variables", it) }
    }.toString()
}
