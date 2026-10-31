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
    NONE, JSON, RAW, FORM_URLENCODED, FORM_DATA, GRAPHQL,
    /** 原始二进制：直接把所选文件的字节作为请求体发送 */
    FILE
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
    /** 总超时（毫秒），兜底上限；连接/读取未单独指定时各阶段也用它 */
    val timeoutMs: Long = 30_000L,
    /** 连接超时（毫秒），0 表示沿用 [timeoutMs] */
    val connectTimeoutMs: Long = 0,
    /** 读取超时（毫秒），0 表示沿用 [timeoutMs] */
    val readTimeoutMs: Long = 0,
    /** BodyType.FILE 时的文件 URI 与展示名 */
    val fileUri: String? = null,
    val fileName: String? = null,
    /** BodyType.FILE 时显式声明的 Content-Type，为空按扩展名推断 */
    val fileContentType: String? = null,
    val proxy: ProxyConfig = ProxyConfig(),
    /** 弱网模拟：默认关闭，零开销直发 */
    val throttle: NetworkThrottleConfig = NetworkThrottleConfig(),
    /** 跳过 TLS 证书校验：仅用于自签名/内网证书调试（等价于 tls.insecure，保留兼容） */
    val insecureTls: Boolean = false,
    /** 完整 TLS 策略：自定义 CA 与客户端证书；为空时按 [insecureTls] 处理 */
    val tls: TlsConfig = TlsConfig(),
    /** 是否自动跟随 3xx 重定向 */
    val followRedirects: Boolean = true,
    /** 最大跟随跳数，0 表示不跟随（即使 [followRedirects] 为 true） */
    val maxRedirects: Int = 10
) {
    /** 合并出实际生效的 TLS 策略：旧布尔开关优先并入 tls，避免两处字段不一致 */
    val effectiveTls: TlsConfig
        get() = if (insecureTls) tls.copy(insecure = true) else tls
}

/** 一次重定向跳转：请求的地址、服务端返回的状态码与本次实际使用的方法 */
data class HttpRedirectHop(
    val url: String,
    val statusCode: Int,
    val method: String
)

sealed interface HttpCallOutcome {    data class Success(
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
        val requestHeaders: List<KeyValue> = emptyList(),
        /** 重定向链：每跟随一跳记录一次，首跳不在此列 */
        val redirectChain: List<HttpRedirectHop> = emptyList()
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
