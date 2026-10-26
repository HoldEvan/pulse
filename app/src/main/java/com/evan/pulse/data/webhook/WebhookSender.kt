package com.evan.pulse.data.webhook

import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.net.HttpEngine
import com.evan.pulse.domain.webhook.WebhookEvent
import com.evan.pulse.domain.webhook.WebhookFormat
import com.evan.pulse.domain.webhook.WebhookPayload
import java.net.URLEncoder

/**
 * 告警 Webhook 发送器。
 *
 * 走应用内统一的 [HttpEngine]，因此代理设置与工具其它请求一致；
 * 发送失败只吞掉异常，不打断拨测记录的写入流程。
 */
class WebhookSender(
    private val httpEngine: HttpEngine,
) {

    /** Webhook 请求超时，告警链路不宜久等 */
    private val timeoutMs = 10_000L

    /** 按设置发送一次事件；未启用或地址为空时直接返回 false */
    suspend fun send(settings: AppSettings, event: WebhookEvent): Boolean {
        if (!settings.webhookEnabled) return false
        val url = settings.webhookUrl.trim()
        if (url.isEmpty()) return false
        val format = WebhookFormat.entries.getOrElse(settings.webhookFormatIndex) {
            WebhookFormat.GENERIC
        }
        val isGet = methodOf(settings) == HttpMethod.GET
        val body = if (isGet) "" else WebhookPayload.build(format, settings.webhookTemplate, event)
        val outcome = try {
            httpEngine.execute(
                HttpCallConfig(
                    method = methodOf(settings),
                    url = buildUrl(settings),
                    headers = buildHeaders(settings),
                    bodyType = if (isGet) BodyType.NONE else BodyType.RAW,
                    body = body,
                    timeoutMs = timeoutMs,
                    proxy = settings.toProxyConfig(),
                )
            )
        } catch (_: Exception) {
            return false
        }
        return outcome is HttpCallOutcome.Success && outcome.statusCode in 200..299
    }

    /**
     * 内置 Content-Type + 用户附加头。
     *
     * 同名头由用户配置覆盖（例如企业微信需要自定义 charset），
     * 因此先放默认值再按顺序剔除同名项，最后一个生效。
     * GET 不带请求体，也就不需要 Content-Type。
     */
    internal fun buildHeaders(settings: AppSettings): List<KeyValue> {
        val result = mutableListOf<KeyValue>()
        if (methodOf(settings) != HttpMethod.GET) {
            result += KeyValue("Content-Type", "application/json; charset=utf-8")
        }
        settings.webhookHeaders
            .filter { it.enabled && it.key.isNotBlank() }
            .forEach { header ->
                result.removeAll { it.key.equals(header.key.trim(), ignoreCase = true) }
                result += KeyValue(header.key.trim(), header.value)
            }
        return result
    }

    /** 请求方法：下标 1 为 GET，其余按 POST 处理 */
    internal fun methodOf(settings: AppSettings): HttpMethod =
        if (settings.webhookMethodIndex == 1) HttpMethod.GET else HttpMethod.POST

    /**
     * 把启用的查询参数按 `key=value` 追加到地址后。
     *
     * 地址已带 `?` 时用 `&` 衔接，以 `?`/`&` 结尾时不重复分隔符；
     * 键与值都做 URL 编码，空格编成 %20 而不是 `+`。
     */
    internal fun buildUrl(settings: AppSettings): String {
        val base = settings.webhookUrl.trim()
        val params = settings.webhookParams.filter { it.enabled && it.key.isNotBlank() }
        if (params.isEmpty()) return base
        val query = params.joinToString("&") { param ->
            "${encode(param.key.trim())}=${encode(param.value)}"
        }
        val separator = when {
            base.isEmpty() -> ""
            !base.contains('?') -> "?"
            base.endsWith("?") || base.endsWith("&") -> ""
            else -> "&"
        }
        return base + separator + query
    }

    private fun encode(text: String): String =
        URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** 设置页「发送测试」：固定构造一条测试事件 */
    suspend fun sendTest(settings: AppSettings): Boolean = send(
        settings = settings.copy(webhookEnabled = true),
        event = WebhookEvent(
            name = "Pulse Webhook 测试",
            status = "测试",
            detail = "收到这条消息说明 Webhook 配置可用",
            durationMs = 0L,
            checkedAt = System.currentTimeMillis(),
            type = "HTTP",
            address = buildUrl(settings),
        )
    )
}
