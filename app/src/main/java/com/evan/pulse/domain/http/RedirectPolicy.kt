package com.evan.pulse.domain.http

import com.evan.pulse.domain.model.HttpMethod
import java.net.URI

/**
 * 一次重定向的目标：下一跳地址、应使用的方法、是否丢弃请求体与鉴权头。
 *
 * 规则对齐浏览器与 OkHttp：
 * - 303 一律改为 GET 并丢弃请求体；
 * - 301/302 只在原方法是 POST 时改为 GET（其他方法保持）；
 * - 307/308 保持方法与请求体；
 * - 跨主机时丢弃 Authorization，避免把凭据带给第三方。
 */
data class RedirectTarget(
    val url: String,
    val method: HttpMethod,
    val dropBody: Boolean,
    val dropAuthorization: Boolean
)

object RedirectPolicy {

    /** 只有 3xx 且带 Location 才可跟随 */
    fun canFollow(statusCode: Int): Boolean = statusCode in 300..399

    /**
     * 计算下一跳；[location] 为相对地址时按 [currentUrl] 解析。
     * 地址无法解析或不是 http(s) 时返回 null，调用方应当把 3xx 原样展示。
     */
    fun next(
        currentUrl: String,
        location: String,
        method: HttpMethod,
        statusCode: Int
    ): RedirectTarget? {
        if (!canFollow(statusCode)) return null
        val raw = location.trim()
        if (raw.isEmpty()) return null
        val target = runCatching { URI(currentUrl).resolve(raw) }.getOrNull() ?: return null
        val scheme = target.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val current = runCatching { URI(currentUrl) }.getOrNull()
        val sameOrigin = current != null &&
            current.scheme.equals(target.scheme, ignoreCase = true) &&
            current.host.equals(target.host, ignoreCase = true) &&
            effectivePort(current) == effectivePort(target)

        val toGet = statusCode == 303 ||
            ((statusCode == 301 || statusCode == 302) && method.value.equals("POST", ignoreCase = true))
        return RedirectTarget(
            url = target.toString(),
            method = if (toGet) HttpMethod.GET else method,
            dropBody = toGet,
            dropAuthorization = !sameOrigin
        )
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
