package com.evan.pulse.domain.diagnostics

/**
 * 诊断信息导出前的脱敏。
 *
 * 诊断包会被用户主动外发（贴到工单、聊天窗口），因此宁可多删也不能漏：
 * URL 只保留协议、主机、路径骨架与查询键，请求头按名称判定，正文里常见的
 * 令牌 / 邮箱 / JWT 一律替换成 `***`。
 */
object Redactor {

    private const val MASK = "***"

    /** 一旦出现就直接整体替换值的头名称（小写比较） */
    private val SECRET_HEADERS = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "api-key",
        "apikey",
        "x-auth-token",
        "x-access-token",
        "x-csrf-token",
        "x-xsrf-token",
        "token",
        "private-token",
        "x-gitlab-token",
        "x-amz-security-token"
    )

    /** 敏感键名：`password=xxx`、`"token": "xxx"` 这类写法按值替换 */
    private val SECRET_KEYS = listOf(
        "password",
        "passwd",
        "pwd",
        "secret",
        "token",
        "access_token",
        "refresh_token",
        "api_key",
        "apikey",
        "authorization",
        "cookie",
        "sessionid",
        "session_id",
        "signature",
        "sign",
        "credential",
        "private_key"
    )

    private val JWT = Regex("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}")
    private val BEARER = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=\\-]{6,}")
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<!\\d)(1[3-9]\\d{9})(?!\\d)")

    /**
     * 脱敏一段自由文本：JWT、Bearer/Basic 凭证、邮箱、手机号与敏感键值对。
     *
     * 顺序上先处理结构化令牌（JWT/Bearer），再处理键值对，最后处理邮箱与手机号，
     * 避免邮箱先被替换成 `***` 后又被键值规则二次匹配。
     */
    fun maskText(text: String): String {
        if (text.isEmpty()) return text
        var result = JWT.replace(text, MASK)
        result = BEARER.replace(result) { match -> match.groupValues[1] + " " + MASK }
        SECRET_KEYS.forEach { key ->
            val pattern = Regex(
                "(?i)([\"']?\\b$key\\b[\"']?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&}\\]]+)"
            )
            result = pattern.replace(result) { match -> match.groupValues[1] + MASK }
        }
        result = EMAIL.replace(result, MASK)
        result = PHONE.replace(result, MASK)
        return result
    }

    /**
     * 脱敏 URL：保留协议、主机、端口与路径骨架，认证信息与查询值全部替换。
     *
     * 路径段过长时（常见于把 token 塞进路径的机器人接口）只留前缀与长度，
     * 既保留可比对的形态，也不泄露具体内容。
     */
    fun maskUrl(raw: String): String {
        if (raw.isBlank()) return raw
        val schemeEnd = raw.indexOf("://")
        val scheme = if (schemeEnd > 0) raw.substring(0, schemeEnd + 3) else ""
        var rest = raw.substring(scheme.length)

        val fragmentStart = rest.indexOf('#')
        val fragment = if (fragmentStart >= 0) rest.substring(fragmentStart) else ""
        if (fragmentStart >= 0) rest = rest.substring(0, fragmentStart)

        val queryStart = rest.indexOf('?')
        val query = if (queryStart >= 0) rest.substring(queryStart) else ""
        if (queryStart >= 0) rest = rest.substring(0, queryStart)

        val at = rest.lastIndexOf('@')
        val authorityPart = if (at >= 0) {
            val host = rest.substring(at + 1)
            "$MASK@$host"
        } else {
            rest
        }

        val slash = authorityPart.indexOf('/')
        val maskedAuthority = if (slash >= 0) {
            authorityPart.substring(0, slash) + maskPath(authorityPart.substring(slash))
        } else {
            authorityPart
        }

        return buildString {
            append(scheme)
            append(maskedAuthority)
            if (query.isNotEmpty()) append(maskQuery(query))
            if (fragment.isNotEmpty()) append("#$MASK")
        }
    }

    /** 请求头行：敏感头整体替换值，其余按通用规则脱敏 */
    fun maskHeaderLine(line: String): String {
        val colon = line.indexOf(':')
        if (colon <= 0) return maskText(line)
        val name = line.substring(0, colon).trim()
        if (name.lowercase() in SECRET_HEADERS) return "$name: $MASK"
        return "$name: ${maskText(line.substring(colon + 1).trim())}"
    }

    /** 路径按段处理：正常段保留，疑似凭证的长段只留前缀 */
    private fun maskPath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            maskPathSegment(segment)
        }

    private fun maskPathSegment(segment: String): String {
        if (segment.isEmpty()) return segment
        if (segment.length <= LONG_SEGMENT) return maskText(segment)
        return segment.take(4) + "…(" + segment.length + ")"
    }

    /** 查询串：保留参数名与顺序，值一律替换，便于看出调用了哪些参数 */
    private fun maskQuery(query: String): String {
        val body = query.removePrefix("?")
        if (body.isEmpty()) return query
        val masked = body.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) pair else pair.substring(0, eq) + "=" + MASK
        }
        return "?$masked"
    }

    /** 超过该长度的路径段视为疑似凭证 */
    private const val LONG_SEGMENT = 32
}
