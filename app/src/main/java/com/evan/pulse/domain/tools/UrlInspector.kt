package com.evan.pulse.domain.tools

/**
 * 一个查询参数。保留原始文本，拼装时可原样还原；[decodedName] / [decodedValue] 用于展示。
 */
data class QueryParam(val name: String, val value: String) {
    val decodedName: String get() = UrlInspector.decode(name)
    val decodedValue: String get() = UrlInspector.decode(value)
}

/**
 * URL 拆解结果。
 *
 * [error] 为语义编码，非空表示无法解析或端口非法；此时其余字段仍是尽力解析的产物，
 * 便于用户看到问题出在哪一段。[schemeInferred] 表示输入没写协议，按 http 补齐。
 */
data class ParsedUrl(
    val scheme: String = "",
    val userInfo: String = "",
    val host: String = "",
    val port: Int? = null,
    val path: String = "",
    val params: List<QueryParam> = emptyList(),
    val fragment: String = "",
    val schemeInferred: Boolean = false,
    val error: String? = null,
) {
    val user: String get() = userInfo.substringBefore(':', userInfo)
    val password: String get() = if (userInfo.contains(':')) userInfo.substringAfter(':') else ""
    val hasUserInfo: Boolean get() = userInfo.isNotEmpty()

    /** 同一参数名出现多次时为 true，常见于筛选类接口 */
    val hasDuplicateParams: Boolean
        get() = params.groupBy { it.name }.any { it.value.size > 1 }

    /** 反向拼装：字段顺序与原始 URL 一致，端口仅在显式指定时输出 */
    fun rebuild(): String {
        val sb = StringBuilder()
        if (scheme.isNotEmpty()) sb.append(scheme).append("://")
        if (userInfo.isNotEmpty()) sb.append(userInfo).append('@')
        sb.append(host)
        if (port != null) sb.append(':').append(port)
        sb.append(path)
        if (params.isNotEmpty()) {
            sb.append('?')
            sb.append(params.joinToString("&") { p ->
                if (p.value.isEmpty()) p.name else "${p.name}=${p.value}"
            })
        }
        if (fragment.isNotEmpty()) sb.append('#').append(fragment)
        return sb.toString()
    }
}

/**
 * 手写 URL 拆解器：比 [java.net.URI] 宽容，粘贴带空格的地址不会直接失败。
 *
 * 覆盖调试常见形态：`scheme://user:pass@host:port/path?a=1&b=2#frag`，也接受
 * 无协议的 `host/path`（按 http 补齐并标记）与无层级的 `mailto:a@b`。
 * IPv6 字面量用方括号识别，`[::1]:8080` 会拆出 host 与 port。
 */
object UrlInspector {

    private const val DEFAULT_SCHEME = "http"

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: String): ParsedUrl {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedUrl(error = "empty")

        var rest = text
        var scheme = ""
        var inferred = false

        val schemeMatch = SCHEME.find(rest)
        if (schemeMatch != null) {
            scheme = schemeMatch.groupValues[1].lowercase()
            val after = schemeMatch.groupValues[2]
            if (after.startsWith("//")) {
                rest = after.removePrefix("//")
            } else {
                // 无层级的方案（mailto:、tel:），整体当作路径
                return ParsedUrl(
                    scheme = scheme,
                    path = after,
                    error = null,
                )
            }
        } else {
            inferred = true
            scheme = DEFAULT_SCHEME
        }

        var fragment = ""
        val hashAt = rest.indexOf('#')
        if (hashAt >= 0) {
            fragment = rest.substring(hashAt + 1)
            rest = rest.substring(0, hashAt)
        }

        var query = ""
        val queryAt = rest.indexOf('?')
        if (queryAt >= 0) {
            query = rest.substring(queryAt + 1)
            rest = rest.substring(0, queryAt)
        }

        val slashAt = rest.indexOf('/')
        val authority = if (slashAt >= 0) rest.substring(0, slashAt) else rest
        val path = if (slashAt >= 0) rest.substring(slashAt) else ""

        var hostPart = authority
        var userInfo = ""
        val atAt = authority.lastIndexOf('@')
        if (atAt >= 0) {
            userInfo = authority.substring(0, atAt)
            hostPart = authority.substring(atAt + 1)
        }

        var host = hostPart
        var port: Int? = null
        var error: String? = null

        if (hostPart.startsWith("[")) {
            val close = hostPart.indexOf(']')
            if (close < 0) {
                error = "invalid"
            } else {
                host = hostPart.substring(0, close + 1)
                val afterBracket = hostPart.substring(close + 1)
                if (afterBracket.startsWith(":")) {
                    val portText = afterBracket.substring(1)
                    val parsed = portText.toIntOrNull()
                    if (parsed == null || parsed !in 1..65535) error = "port" else port = parsed
                } else if (afterBracket.isNotEmpty()) {
                    error = "invalid"
                }
            }
        } else {
            val colonAt = hostPart.lastIndexOf(':')
            if (colonAt >= 0) {
                host = hostPart.substring(0, colonAt)
                val portText = hostPart.substring(colonAt + 1)
                if (portText.isEmpty()) {
                    error = "port"
                } else {
                    val parsed = portText.toIntOrNull()
                    if (parsed == null || parsed !in 1..65535) error = "port" else port = parsed
                }
            }
        }

        if (host.isEmpty()) error = error ?: "host"

        return ParsedUrl(
            scheme = scheme,
            userInfo = userInfo,
            host = host,
            port = port,
            path = path,
            params = parseParams(query),
            fragment = fragment,
            schemeInferred = inferred,
            error = error,
        )
    }

    /** 拆查询串：保留顺序与重复项，`a` 与 `a=` 都算空值 */
    fun parseParams(query: String): List<QueryParam> {
        if (query.isEmpty()) return emptyList()
        return query.split('&')
            .filter { it.isNotEmpty() }
            .map { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    QueryParam(name = pair, value = "")
                } else {
                    QueryParam(name = pair.substring(0, eq), value = pair.substring(eq + 1))
                }
            }
    }

    /**
     * 百分号解码：`+` 按查询串语义还原为空格，非法片段原样保留，
     * 避免调试时因为一个坏参数看不到其余内容。
     */
    fun decode(value: String): String {
        if (value.isEmpty() || (!value.contains('%') && !value.contains('+'))) return value
        val bytes = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '+' -> {
                    bytes.write(' '.code)
                    i++
                }
                ch == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code == null) {
                        bytes.write(ch.code)
                        i++
                    } else {
                        bytes.write(code)
                        i += 3
                    }
                }
                else -> {
                    val encoded = ch.toString().toByteArray(Charsets.UTF_8)
                    bytes.write(encoded, 0, encoded.size)
                    i++
                }
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
