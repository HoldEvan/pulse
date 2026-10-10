package com.evan.pulse.domain.model

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD
}

enum class BodyType {
    NONE, JSON, RAW, FORM_URLENCODED, FORM_DATA
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
    val formFields: List<FormField> = emptyList(),
    val auth: AuthConfig = AuthConfig(),
    val timeoutMs: Long = 30_000L,
    val proxy: ProxyConfig = ProxyConfig()
)

sealed interface HttpCallOutcome {
    data class Success(
        val statusCode: Int,
        val statusText: String,
        val headers: List<KeyValue>,
        val body: String,
        val durationMs: Long,
        val responseSizeBytes: Long
    ) : HttpCallOutcome

    data class Failure(
        val errorType: HttpErrorType,
        val message: String
    ) : HttpCallOutcome
}
