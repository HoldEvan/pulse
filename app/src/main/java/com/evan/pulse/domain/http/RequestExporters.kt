package com.evan.pulse.domain.http

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.buildGraphqlRequestBody
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 请求导出格式：全部产出可直接粘贴到外部工具的纯文本 */
enum class HttpExportFormat {
    CURL_BASH,
    CURL_POWERSHELL,
    HTTP_RAW,
    PYTHON_REQUESTS,
    JS_FETCH
}

/** multipart 原始报文用的固定边界值 */
private const val MULTIPART_BOUNDARY = "pulse-form-data"

/** 由 body 长度决定的头，交给各工具自行计算 */
private const val CONTENT_LENGTH = "content-length"

/** 由 body 类型决定的头：用户声明优先，未声明时按 body 类型推断 */
private const val CONTENT_TYPE = "content-type"

/**
 * 把当前请求配置导出为 [format] 格式的纯文本。
 *
 * [HttpCallConfig.headers] 需要已包含鉴权头（与发送链路一致），导出器本身不读 auth 字段，
 * 因此该函数不依赖 Android，可直接在 JVM 单测里调用。
 */
fun HttpCallConfig.exportTo(format: HttpExportFormat): String = when (format) {
    HttpExportFormat.CURL_BASH -> buildCurl("curl", ::shQuote)
    HttpExportFormat.CURL_POWERSHELL -> buildCurl("curl.exe", ::psQuote)
    HttpExportFormat.HTTP_RAW -> buildRawHttp()
    HttpExportFormat.PYTHON_REQUESTS -> buildPythonRequests()
    HttpExportFormat.JS_FETCH -> buildJsFetch()
}

private fun HttpCallConfig.sendableParams(): List<KeyValue> =
    queryParams.filter { it.enabled && it.key.isNotBlank() }

private fun HttpCallConfig.formFieldsToSend(): List<FormField> =
    formFields.filter { it.name.isNotBlank() && !it.isFile }

private fun String.isContentTypeHeader(): Boolean = equals(CONTENT_TYPE, ignoreCase = true)

/** query 参数拼接后的完整地址；地址里已有 query 时用 & 续接，避免吃掉原有参数 */
private fun HttpCallConfig.fullUrl(): String {
    val query = sendableParams().joinToString("&") {
        "${percentEncode(it.key)}=${percentEncode(it.value)}"
    }
    if (query.isEmpty()) return url
    return if (url.contains("?")) "$url&$query" else "$url?$query"
}

private data class UrlInfo(
    val full: String,
    val host: String?,
    val target: String
)

/** 拆出原始报文要的 Host 与请求目标；地址非法或含未解析占位符时回落到整串地址 */
private fun HttpCallConfig.urlInfo(): UrlInfo {
    val full = fullUrl()
    val uri = runCatching { URI(full) }.getOrNull() ?: return UrlInfo(full, null, full)
    if (uri.scheme == null) return UrlInfo(full, null, full)
    val query = uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
    val host = uri.host?.takeIf { it.isNotEmpty() }?.let { name ->
        val port = uri.port
        if (port > 0 && port != 80 && port != 443) "$name:$port" else name
    }
    val path = uri.path.ifEmpty { "/" }
    return UrlInfo(full, host, path + query)
}

/**
 * 导出用请求头：去掉必然重复的 content-length 与空白的 content-type，
 * content-type 保留用户声明，缺省时按 body 类型补一个。
 */
private fun HttpCallConfig.exportHeaders(): List<KeyValue> {
    val kept = headers.filter {
        it.enabled &&
            it.key.isNotBlank() &&
            !it.key.equals(CONTENT_LENGTH, ignoreCase = true) &&
            !(it.key.isContentTypeHeader() && it.value.isBlank())
    }
    val inferred = inferredContentType() ?: return kept
    val declared = kept.firstOrNull { it.key.isContentTypeHeader() }?.value
    val contentType = declared ?: inferred
    return listOf(KeyValue("Content-Type", contentType)) +
        kept.filterNot { it.key.isContentTypeHeader() }
}

/** body 是否真有内容；与发送引擎一致，空 body 不追加 content-type */
private fun HttpCallConfig.hasBodyContent(): Boolean = when (bodyType) {
    BodyType.NONE -> false
    BodyType.JSON, BodyType.RAW, BodyType.GRAPHQL -> body.isNotBlank()
    BodyType.FORM_URLENCODED, BodyType.FORM_DATA -> formFieldsToSend().isNotEmpty()
}

/** body 类型推断出的 content-type；FORM_DATA 的 boundary 由各自工具生成，故返回 null */
private fun HttpCallConfig.inferredContentType(): String? {
    if (!hasBodyContent()) return null
    return when (bodyType) {
        BodyType.NONE -> null
        BodyType.JSON, BodyType.GRAPHQL -> "application/json"
        BodyType.RAW -> "text/plain"
        BodyType.FORM_URLENCODED -> "application/x-www-form-urlencoded"
        BodyType.FORM_DATA -> null
    }
}

private fun HttpCallConfig.buildCurl(program: String, quote: (String) -> String): String {
    val fields = formFieldsToSend()
    return buildList {
        add(program)
        add("-X ${method.value}")
        add(quote(urlInfo().full))
        exportHeaders().forEach { add("-H ${quote("${it.key}: ${it.value}")}") }
        when (bodyType) {
            BodyType.NONE -> Unit
            BodyType.JSON, BodyType.RAW -> if (body.isNotBlank()) add("--data-binary ${quote(body)}")
            BodyType.GRAPHQL -> buildGraphqlRequestBody(body, graphqlVariables)
                .takeIf { it.isNotBlank() }
                ?.let { add("--data-binary ${quote(it)}") }
            BodyType.FORM_URLENCODED -> fields.forEach {
                add("--data-urlencode ${quote("${it.name}=${it.value}")}")
            }
            BodyType.FORM_DATA -> fields.forEach { add("--form ${quote("${it.name}=${it.value}")}") }
        }
    }.joinToString(" ")
}

private fun HttpCallConfig.buildRawHttp(): String {
    val info = urlInfo()
    val fields = formFieldsToSend()
    val sb = StringBuilder("${method.value} ${info.target} HTTP/1.1\n")
    info.host?.let { sb.append("Host: $it\n") }

    if (bodyType == BodyType.FORM_DATA && fields.isNotEmpty()) {
        sb.append("Content-Type: multipart/form-data; boundary=$MULTIPART_BOUNDARY\n")
        exportHeaders().forEach { sb.append("${it.key}: ${it.value}\n") }
        sb.append("\n")
        fields.forEach { field ->
            sb.append("--$MULTIPART_BOUNDARY\r\n")
            sb.append("Content-Disposition: form-data; name=\"${field.name}\"\r\n\r\n")
            sb.append("${field.value}\r\n")
        }
        sb.append("--$MULTIPART_BOUNDARY--\r\n")
    } else {
        exportHeaders().forEach { sb.append("${it.key}: ${it.value}\n") }
        sb.append("\n")
        when (bodyType) {
            BodyType.JSON, BodyType.RAW -> if (body.isNotBlank()) sb.append(body)
            BodyType.GRAPHQL -> buildGraphqlRequestBody(body, graphqlVariables)
                .takeIf { it.isNotBlank() }
                ?.let { sb.append(it) }
            BodyType.FORM_URLENCODED -> sb.append(fields.joinToString("&") {
                "${percentEncode(it.name)}=${percentEncode(it.value)}"
            })
            else -> Unit
        }
    }
    return sb.toString()
}

private fun HttpCallConfig.buildPythonRequests(): String {
    val params = sendableParams()
    val headers = exportHeaders()
    val fields = formFieldsToSend()
    val sb = StringBuilder("import requests\n\n")
    // params 单独交给 requests 处理，地址里不再重复拼一遍 query
    sb.append("url = ${codeString(url)}\n")

    if (params.isNotEmpty()) {
        sb.append("\nparams = {\n")
        params.forEach { sb.append("    ${codeString(it.key)}: ${codeString(it.value)},\n") }
        sb.append("}\n")
    }
    if (headers.isNotEmpty()) {
        sb.append("\nheaders = {\n")
        headers.forEach { sb.append("    ${codeString(it.key)}: ${codeString(it.value)},\n") }
        sb.append("}\n")
    }

    sb.append("\nresponse = requests.request(\n")
    sb.append("    ${codeString(method.value)},\n")
    sb.append("    url,\n")
    if (params.isNotEmpty()) sb.append("    params=params,\n")
    if (headers.isNotEmpty()) sb.append("    headers=headers,\n")
    when (bodyType) {
        BodyType.NONE -> Unit
        BodyType.JSON, BodyType.RAW -> if (body.isNotBlank()) sb.append("    data=${codeString(body)},\n")
        BodyType.GRAPHQL -> buildGraphqlRequestBody(body, graphqlVariables)
            .takeIf { it.isNotBlank() }
            ?.let { sb.append("    data=${codeString(it)},\n") }
        BodyType.FORM_URLENCODED -> if (fields.isNotEmpty()) {
            sb.append("    data={${fields.joinToString(", ") {
                "${codeString(it.name)}: ${codeString(it.value)}"
            }}},\n")
        }
        BodyType.FORM_DATA -> if (fields.isNotEmpty()) {
            sb.append("    files={${fields.joinToString(", ") {
                "${codeString(it.name)}: (${codeString(it.name)}, ${codeString(it.value)})"
            }}},\n")
        }
    }
    sb.append(")\n\nprint(response.text)\n")
    return sb.toString()
}

private fun HttpCallConfig.buildJsFetch(): String {
    val info = urlInfo()
    val headers = exportHeaders()
    val fields = formFieldsToSend()
    val sb = StringBuilder()

    if (bodyType == BodyType.FORM_DATA && fields.isNotEmpty()) {
        sb.append("const formData = new FormData();\n")
        fields.forEach { sb.append("formData.append(${jsString(it.name)}, ${jsString(it.value)});\n") }
        sb.append("\n")
    }

    sb.append("const response = await fetch(${jsString(info.full)}, {\n")
    sb.append("  method: ${jsString(method.value)},\n")
    if (headers.isNotEmpty()) {
        sb.append("  headers: {\n")
        headers.forEach { sb.append("    ${jsString(it.key)}: ${jsString(it.value)},\n") }
        sb.append("  },\n")
    }
    when (bodyType) {
        BodyType.NONE -> Unit
        BodyType.JSON, BodyType.RAW -> if (body.isNotBlank()) sb.append("  body: ${jsString(body)}\n")
        BodyType.GRAPHQL -> buildGraphqlRequestBody(body, graphqlVariables)
            .takeIf { it.isNotBlank() }
            ?.let { sb.append("  body: ${jsString(it)}\n") }
        BodyType.FORM_URLENCODED -> if (fields.isNotEmpty()) {
            sb.append("  body: new URLSearchParams({\n")
            fields.forEach { sb.append("    ${jsString(it.name)}: ${jsString(it.value)},\n") }
            sb.append("  })\n")
        }
        BodyType.FORM_DATA -> if (fields.isNotEmpty()) sb.append("  body: formData\n")
    }
    sb.append("});\nconsole.log(await response.text());\n")
    return sb.toString()
}

private fun percentEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun psQuote(value: String): String = "'" + value.replace("'", "''") + "'"

/** Python 用的双引号字符串转义 */
private fun codeString(value: String): String =
    "\"" + value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\""

/** JavaScript 单引号字符串；JSON 体里只有双引号，单引号包裹时无需转义，贴出来更好读 */
private fun jsString(value: String): String =
    "'" + value.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "'"
