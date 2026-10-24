package com.evan.pulse.domain.webhook

/** Webhook 平台格式：决定外层 JSON 结构，内层文案共用模板 */
enum class WebhookFormat {
    /** 通用：模板即完整请求体 */
    GENERIC,
    /** 企业微信机器人 */
    WECOM,
    /** 钉钉机器人 */
    DINGTALK,
    /** 飞书机器人 */
    FEISHU,
    /** Slack Incoming Webhook */
    SLACK,
}

/** 一次告警事件，供模板渲染使用 */
data class WebhookEvent(
    val name: String,
    /** 状态：故障 / 缓慢 / 恢复 */
    val status: String,
    val detail: String,
    val durationMs: Long,
    val checkedAt: Long,
    val type: String,
    val address: String,
) {
    /** 模板占位符取值；未知占位符原样保留为空串 */
    fun placeholders(): Map<String, String> = mapOf(
        "name" to name,
        "status" to status,
        "detail" to detail,
        "duration" to durationMs.toString(),
        "time" to checkedAt.toString(),
        "type" to type,
        "address" to address,
    )
}

/** 通用格式默认请求体模板 */
const val DEFAULT_WEBHOOK_TEMPLATE =
    """{"name":"{{name}}","status":"{{status}}","detail":"{{detail}}","address":"{{address}}","time":{{time}}}"""

/** 平台消息默认文案模板 */
const val DEFAULT_WEBHOOK_TEXT = "{{status}}｜{{name}}（{{address}}）{{detail}}"

/**
 * Webhook 请求体构建。
 *
 * - [WebhookFormat.GENERIC]：模板即完整请求体，直接做占位符替换；
 * - 其余平台：模板渲染成一段纯文本，包进各平台约定的 JSON 结构。
 *
 * 占位符语法 `{{name}}`，未知占位符替换为空串；渲染结果做 JSON 字符串转义。
 */
object WebhookPayload {

    fun build(format: WebhookFormat, template: String, event: WebhookEvent): String {
        val body = render(template.ifBlank { defaultTemplateFor(format) }, event)
        return when (format) {
            WebhookFormat.GENERIC -> body
            WebhookFormat.WECOM ->
                """{"msgtype":"text","text":{"content":"${escape(body)}"}}"""

            WebhookFormat.DINGTALK ->
                """{"msgtype":"text","text":{"content":"${escape(body)}"}}"""

            WebhookFormat.FEISHU ->
                """{"msg_type":"text","content":{"text":"${escape(body)}"}}"""

            WebhookFormat.SLACK ->
                """{"text":"${escape(body)}"}"""
        }
    }

    /** 默认模板：通用格式是 JSON 请求体，平台格式是纯文本文案 */
    fun defaultTemplateFor(format: WebhookFormat): String =
        if (format == WebhookFormat.GENERIC) DEFAULT_WEBHOOK_TEMPLATE else DEFAULT_WEBHOOK_TEXT

    /** 渲染模板：占位符替换后不做转义（由调用方按需转义） */
    fun render(template: String, event: WebhookEvent): String {
        val vars = event.placeholders()
        var result = template
        for ((key, value) in vars) {
            result = result.replace("{{$key}}", value)
        }
        return result.replace(PLACEHOLDER, "")
    }

    /** JSON 字符串转义：引号、反斜杠、换行与控制字符 */
    fun escape(text: String): String = buildString(text.length + 8) {
        for (ch in text) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    private val PLACEHOLDER = Regex("""\{\{[A-Za-z0-9_]+\}\}""")
}
