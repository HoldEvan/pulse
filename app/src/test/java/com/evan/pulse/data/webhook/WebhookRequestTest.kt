package com.evan.pulse.data.webhook

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.net.HttpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookRequestTest {

    /** 只用于构造 WebhookSender；本文件不触发真实发送 */
    private class FakeEngine : HttpEngine {
        override suspend fun execute(config: HttpCallConfig): HttpCallOutcome =
            throw UnsupportedOperationException("测试不发送请求")
    }

    private val sender = WebhookSender(FakeEngine())

    @Test
    fun defaultMethodIsPost() {
        assertEquals(HttpMethod.POST, sender.methodOf(AppSettings()))
    }

    @Test
    fun methodIndexOneMeansGet() {
        assertEquals(HttpMethod.GET, sender.methodOf(AppSettings(webhookMethodIndex = 1)))
        assertEquals(HttpMethod.POST, sender.methodOf(AppSettings(webhookMethodIndex = 2)))
    }

    @Test
    fun getSkipsBuiltInContentType() {
        val headers = sender.buildHeaders(AppSettings(webhookMethodIndex = 1))
        assertTrue(headers.isEmpty())
    }

    @Test
    fun urlWithoutParamsStaysUntouched() {
        val settings = AppSettings(webhookUrl = "https://example.com/hook")
        assertEquals("https://example.com/hook", sender.buildUrl(settings))
    }

    @Test
    fun paramsAreAppendedAsQuery() {
        val settings = AppSettings(
            webhookUrl = "https://example.com/hook",
            webhookParams = listOf(
                KeyValueEntry("token", "abc 123"),
                KeyValueEntry("level", "high")
            )
        )
        assertEquals(
            "https://example.com/hook?token=abc%20123&level=high",
            sender.buildUrl(settings)
        )
    }

    @Test
    fun paramsMergeIntoExistingQuery() {
        val settings = AppSettings(
            webhookUrl = "https://example.com/hook?source=app",
            webhookParams = listOf(KeyValueEntry("token", "t"))
        )
        assertEquals("https://example.com/hook?source=app&token=t", sender.buildUrl(settings))
    }

    @Test
    fun trailingSeparatorIsNotDuplicated() {
        assertEquals(
            "https://example.com/hook?token=t",
            sender.buildUrl(
                AppSettings(
                    webhookUrl = "https://example.com/hook?",
                    webhookParams = listOf(KeyValueEntry("token", "t"))
                )
            )
        )
        assertEquals(
            "https://example.com/hook?source=app&token=t",
            sender.buildUrl(
                AppSettings(
                    webhookUrl = "https://example.com/hook?source=app&",
                    webhookParams = listOf(KeyValueEntry("token", "t"))
                )
            )
        )
    }

    @Test
    fun blankAndDisabledParamsAreIgnored() {
        val settings = AppSettings(
            webhookUrl = "https://example.com/hook",
            webhookParams = listOf(
                KeyValueEntry("", "ignored"),
                KeyValueEntry("off", "ignored", enabled = false),
                KeyValueEntry("token", "t")
            )
        )
        assertEquals("https://example.com/hook?token=t", sender.buildUrl(settings))
    }
}
