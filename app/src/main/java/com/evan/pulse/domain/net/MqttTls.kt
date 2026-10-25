package com.evan.pulse.domain.net

import java.net.URI
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import java.security.SecureRandom

/**
 * MQTT TLS 相关辅助：识别加密地址、解析目标端点、按「是否跳过校验」构建套接字工厂。
 *
 * Paho 只认 `ssl://` 作为 TLS 地址；`tcp://` 即使端口是 8883 也按明文处理，
 * 因此这里不做端口推断，严格按 scheme 判断，避免把明文连接误当成 TLS。
 */
object MqttTls {

    /** Paho v3 唯一支持的 TLS scheme */
    const val SECURE_SCHEME = "ssl"

    /** ssl:// 未写端口时 Paho 的默认端口 */
    const val DEFAULT_SECURE_PORT = 8883

    fun isSecure(brokerUrl: String): Boolean =
        schemeOf(brokerUrl).equals(SECURE_SCHEME, ignoreCase = true)

    /** 取出 `://` 前的 scheme；没有 scheme 时返回空串 */
    fun schemeOf(brokerUrl: String): String {
        val index = brokerUrl.indexOf("://")
        return if (index <= 0) "" else brokerUrl.substring(0, index)
    }

    /**
     * 解析 Broker 的 host 与 port，供代理隧道使用。
     * 端口缺省时用 [DEFAULT_SECURE_PORT]；无法解析出 host 时返回 null。
     */
    fun endpoint(brokerUrl: String): Pair<String, Int>? {
        val trimmed = brokerUrl.trim()
        if (trimmed.isEmpty()) return null
        runCatching {
            val uri = URI(trimmed)
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val port = if (uri.port > 0) uri.port else DEFAULT_SECURE_PORT
            host to port
        }.getOrNull()?.let { return it }
        // URI 对含下划线等非标准主机名可能解析失败，退回手工切分
        val authority = trimmed.substringAfter("://", "").substringBefore('/')
            .substringAfter('@')
        if (authority.isBlank()) return null
        val host = authority.substringBeforeLast(':', authority)
        val port = authority.substringAfterLast(':', "").toIntOrNull()
        if (host.isBlank()) return null
        return host to (port ?: DEFAULT_SECURE_PORT)
    }

    /** 默认校验或跳过校验的 TLS 套接字工厂 */
    fun socketFactory(insecure: Boolean): SSLSocketFactory =
        if (insecure) {
            InsecureTls.insecureSocketFactory
        } else {
            SSLContext.getInstance("TLS").apply {
                init(null, null, SecureRandom())
            }.socketFactory
        }
}
