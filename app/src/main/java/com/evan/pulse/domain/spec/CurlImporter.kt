package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.FormFieldEntry
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.domain.model.HttpMethod
import java.net.URI
import java.net.URLDecoder

/** cURL 导入结果：建议名称 + 可直接入库的快照 */
data class CurlImportResult(val name: String, val snapshot: HttpSnapshot)

/**
 * 把粘贴的 cURL 命令还原成请求快照。
 *
 * 只覆盖调试场景常用的参数：方法、地址、请求头、请求体、表单、Basic 认证、
 * Cookie、跟随重定向与跳过证书校验。`-d @file` 这类需要读取本地文件的形式
 * 按字面量处理——移动端没有对应的文件上下文，用户导入后再手动选文件即可。
 */
object CurlImporter {

    private val METHOD_FLAGS = setOf("-X", "--request")
    private val HEADER_FLAGS = setOf("-H", "--header")
    private val DATA_FLAGS = setOf("-d", "--data", "--data-raw", "--data-ascii", "--data-binary")
    private val URLENCODE_FLAGS = setOf("--data-urlencode")
    private val FORM_FLAGS = setOf("-F", "--form")
    private val USER_FLAGS = setOf("-u", "--user")
    private val COOKIE_FLAGS = setOf("-b", "--cookie")
    private val USER_AGENT_FLAGS = setOf("-A", "--user-agent")
    private val REFERER_FLAGS = setOf("-e", "--referer")
    private val URL_FLAGS = setOf("--url")
    private val MAX_REDIR_FLAGS = setOf("--max-redirs")

    /** 无参数、可以直接忽略的开关 */
    private val IGNORED_FLAGS = setOf(
        "--compressed", "-s", "--silent", "-S", "--show-error", "-i", "--include",
        "-v", "--verbose", "-f", "--fail", "--no-buffer", "-N", "--globoff"
    )

    fun parse(raw: String): CurlImportResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SpecImportException(SpecError.EMPTY, "cURL 内容为空")
        }
        val tokens = tokenize(text)
        if (tokens.isEmpty() || !tokens.first().substringAfterLast('/').startsWith("curl")) {
            throw SpecImportException(SpecError.NOT_RECOGNIZED, "不是以 curl 开头的命令")
        }

        var url: String? = null
        var method: String? = null
        var followRedirects = false
        var insecureTls = false
        var maxRedirects = 10
        var basicAuth: Pair<String, String>? = null
        var cookieHeader: String? = null
        val headers = mutableListOf<KeyValueEntry>()
        val dataParts = mutableListOf<String>()
        val urlEncoded = mutableListOf<Pair<String, String>>()
        val formFields = mutableListOf<FormFieldEntry>()

        var index = 1
        fun value(): String? {
            if (index + 1 >= tokens.size) return null
            index += 1
            return tokens[index]
        }

        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token in METHOD_FLAGS -> method = value() ?: method
                token in HEADER_FLAGS -> value()?.let { header ->
                    val colon = header.indexOf(':')
                    if (colon > 0) {
                        headers += KeyValueEntry(
                            key = header.substring(0, colon).trim(),
                            value = header.substring(colon + 1).trim()
                        )
                    }
                }

                token in DATA_FLAGS -> value()?.let { dataParts += it }
                token in URLENCODE_FLAGS -> value()?.let { rawValue ->
                    val eq = rawValue.indexOf('=')
                    if (eq > 0) {
                        urlEncoded += rawValue.substring(0, eq) to
                            URLDecoder.decode(rawValue.substring(eq + 1), "UTF-8")
                    } else {
                        dataParts += rawValue
                    }
                }

                token in FORM_FLAGS -> value()?.let { form ->
                    val eq = form.indexOf('=')
                    if (eq > 0) {
                        val name = form.substring(0, eq)
                        val value = form.substring(eq + 1)
                        if (value.startsWith("@")) {
                            formFields += FormFieldEntry(
                                name = name,
                                value = "",
                                isFile = true,
                                fileName = value.removePrefix("@")
                            )
                        } else {
                            formFields += FormFieldEntry(name = name, value = value)
                        }
                    }
                }

                token in USER_FLAGS -> value()?.let { credential ->
                    val colon = credential.indexOf(':')
                    basicAuth = if (colon >= 0) {
                        credential.substring(0, colon) to credential.substring(colon + 1)
                    } else {
                        credential to ""
                    }
                }

                token in COOKIE_FLAGS -> value()?.let { cookie ->
                    // -b 也可能是 cookie 文件路径，只有含 = 时才当作值
                    if (!cookie.startsWith("@") && cookie.contains('=')) cookieHeader = cookie
                }

                token in USER_AGENT_FLAGS -> value()?.let { headers += KeyValueEntry("User-Agent", it) }
                token in REFERER_FLAGS -> value()?.let { headers += KeyValueEntry("Referer", it) }
                token in URL_FLAGS -> url = value() ?: url
                token in MAX_REDIR_FLAGS -> value()?.toIntOrNull()?.let { maxRedirects = it }
                token == "-G" || token == "--get" -> method = "GET"
                token == "-I" || token == "--head" -> method = "HEAD"
                token == "-L" || token == "--location" -> followRedirects = true
                token == "-k" || token == "--insecure" -> insecureTls = true
                token in IGNORED_FLAGS -> Unit
                token.startsWith("-") -> Unit
                url == null -> url = token
            }
            index += 1
        }

        val rawUrl = url ?: throw SpecImportException(SpecError.NOT_RECOGNIZED, "命令里没有地址")
        val (baseUrl, queryParams) = splitQuery(rawUrl)

        val isForm = formFields.isNotEmpty()
        val isUrlEncoded = urlEncoded.isNotEmpty()
        val bodyText = when {
            isForm -> ""
            isUrlEncoded -> urlEncoded.joinToString("&") { (k, v) -> "$k=$v" }
            else -> dataParts.joinToString("&")
        }
        val resolvedMethod = HttpMethod.parse(
            method ?: when {
                isForm || bodyText.isNotEmpty() -> "POST"
                else -> "GET"
            }
        )
        val contentType = headers.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        val bodyType = when {
            isForm -> "FORM_DATA"
            isUrlEncoded -> "FORM_URLENCODED"
            bodyText.isEmpty() -> "NONE"
            contentType?.contains("json", ignoreCase = true) == true ||
                bodyText.trimStart().let { it.startsWith("{") || it.startsWith("[") } -> "JSON"
            else -> "RAW"
        }

        // -G 时数据拼到查询串上，而不是放进请求体
        val getData = method?.equals("GET", ignoreCase = true) == true && bodyText.isNotEmpty()
        val params = buildList {
            addAll(queryParams)
            if (getData) addAll(parseFormPairs(bodyText))
        }

        // Cookie 与普通请求头一样随快照保存；已显式给了 Cookie 头时不再覆盖
        val allHeaders = if (cookieHeader != null &&
            headers.none { it.key.equals("Cookie", ignoreCase = true) }
        ) {
            headers + KeyValueEntry("Cookie", cookieHeader)
        } else {
            headers
        }

        val snapshot = HttpSnapshot(
            method = resolvedMethod.value,
            url = baseUrl,
            params = params,
            headers = allHeaders,
            bodyType = if (getData) "NONE" else bodyType,
            bodyText = if (getData) "" else bodyText,
            formFields = formFields,
            authType = if (basicAuth != null) "BASIC" else "NONE",
            basicUsername = basicAuth?.first.orEmpty(),
            basicPassword = basicAuth?.second.orEmpty(),
            followRedirects = followRedirects,
            maxRedirects = maxRedirects.coerceIn(0, 20),
            insecureTls = insecureTls
        )
        return CurlImportResult(suggestName(baseUrl), snapshot)
    }

    /** 建议名称：取路径最后一段，没有路径时用主机名 */
    internal fun suggestName(url: String): String {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val last = path.trim('/').substringAfterLast('/')
        if (last.isNotBlank()) return last
        return runCatching { URI(url).host.orEmpty() }.getOrDefault("").ifBlank { "cURL 导入" }
    }

    /** 把地址里的 query 拆成参数表，地址本身只保留 scheme://host/path */
    internal fun splitQuery(url: String): Pair<String, List<KeyValueEntry>> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url to emptyList()
        val rawQuery = uri.rawQuery ?: return url to emptyList()
        val base = URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath ?: "",
            null,
            null
        ).toString()
        val params = parseFormPairs(rawQuery)
        return base to params
    }

    private fun parseFormPairs(raw: String): List<KeyValueEntry> =
        raw.split('&').filter { it.isNotBlank() }.map { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) {
                KeyValueEntry(key = decode(pair), value = "")
            } else {
                KeyValueEntry(
                    key = decode(pair.substring(0, eq)),
                    value = decode(pair.substring(eq + 1))
                )
            }
        }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /**
     * 把命令行拆成参数：支持单引号、双引号、反斜杠续行与常见转义。
     *
     * 只实现 curl 复制出来的形态，不做完整 shell 语义（变量展开、命令替换等）。
     */
    internal fun tokenize(raw: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var started = false
        var i = 0

        fun flush() {
            if (started) {
                tokens += current.toString()
                current.clear()
                started = false
            }
        }

        while (i < raw.length) {
            val c = raw[i]
            when {
                quote == '\'' -> {
                    if (c == '\'') quote = null else current.append(c)
                }

                quote == '"' -> {
                    if (c == '"') {
                        quote = null
                    } else if (c == '\\' && i + 1 < raw.length) {
                        i += 1
                        current.append(
                            when (val next = raw[i]) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                else -> next
                            }
                        )
                    } else {
                        current.append(c)
                    }
                }

                c == '\'' || c == '"' -> {
                    quote = c
                    started = true
                }

                c == '\\' && i + 1 < raw.length && (raw[i + 1] == '\n' || raw[i + 1] == '\r') -> {
                    // 续行：吞掉反斜杠与换行
                    i += if (raw[i + 1] == '\r' && i + 2 < raw.length && raw[i + 2] == '\n') 2 else 1
                }

                c == '\\' && i + 1 < raw.length -> {
                    i += 1
                    current.append(raw[i])
                    started = true
                }

                c.isWhitespace() -> flush()
                else -> {
                    current.append(c)
                    started = true
                }
            }
            i += 1
        }
        flush()
        return tokens
    }
}
