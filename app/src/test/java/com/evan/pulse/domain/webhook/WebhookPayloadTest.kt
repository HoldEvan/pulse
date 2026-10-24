package com.evan.pulse.domain.webhook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookPayloadTest {

    private val event = WebhookEvent(
        name = "线上 API",
        status = "故障",
        detail = "HTTP 500",
        durationMs = 1234L,
        checkedAt = 1_700_000_000_000L,
        type = "HTTP",
        address = "https://api.example.com/health"
    )

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun renderReplacesAllKnownPlaceholders() {
        val text = WebhookPayload.render("{{name}}|{{status}}|{{detail}}|{{duration}}", event)
        assertEquals("线上 API|故障|HTTP 500|1234", text)
    }

    @Test
    fun renderDropsUnknownPlaceholders() {
        assertEquals("Pulse ", WebhookPayload.render("Pulse {{unknown}}", event))
    }

    @Test
    fun genericFormatUsesTemplateAsWholeBody() {
        val body = WebhookPayload.build(WebhookFormat.GENERIC, """{"msg":"{{name}}"}""", event)
        assertEquals("""{"msg":"线上 API"}""", body)
    }

    @Test
    fun genericFormatFallsBackToDefaultTemplate() {
        val body = WebhookPayload.build(WebhookFormat.GENERIC, "", event)
        val json = parse(body)
        assertEquals("线上 API", json["name"]!!.jsonPrimitive.content)
        assertEquals("故障", json["status"]!!.jsonPrimitive.content)
        assertEquals("1700000000000", json["time"]!!.jsonPrimitive.content)
    }

    @Test
    fun wecomAndDingTalkWrapContent() {
        for (format in listOf(WebhookFormat.WECOM, WebhookFormat.DINGTALK)) {
            val json = parse(WebhookPayload.build(format, "{{name}} {{status}}", event))
            assertEquals("text", json["msgtype"]!!.jsonPrimitive.content)
            assertEquals(
                "线上 API 故障",
                json["text"]!!.jsonObject["content"]!!.jsonPrimitive.content
            )
        }
    }

    @Test
    fun feishuAndSlackUseTheirOwnShape() {
        val feishu = parse(WebhookPayload.build(WebhookFormat.FEISHU, "{{detail}}", event))
        assertEquals("text", feishu["msg_type"]!!.jsonPrimitive.content)
        assertEquals("HTTP 500", feishu["content"]!!.jsonObject["text"]!!.jsonPrimitive.content)

        val slack = parse(WebhookPayload.build(WebhookFormat.SLACK, "{{detail}}", event))
        assertEquals("HTTP 500", slack["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun escapesQuotesAndNewlinesInPlatformPayloads() {
        val tricky = event.copy(detail = "line1\nline2 \"quoted\" \\ end")
        val json = parse(WebhookPayload.build(WebhookFormat.SLACK, "{{detail}}", tricky))
        assertEquals("line1\nline2 \"quoted\" \\ end", json["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun defaultsDifferByFormat() {
        val generic = WebhookPayload.defaultTemplateFor(WebhookFormat.GENERIC)
        val slack = WebhookPayload.defaultTemplateFor(WebhookFormat.SLACK)
        assertTrue(generic.startsWith("{"))
        assertTrue(slack.startsWith("{{status}}"))
        assertFalse(slack.contains("\"msgtype\""))
    }
}
