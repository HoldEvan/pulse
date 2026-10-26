package com.evan.pulse.data.webhook

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.data.settings.decodeKeyValueList
import com.evan.pulse.data.settings.encodeKeyValueList
import com.evan.pulse.domain.crypto.SecretBox
import com.evan.pulse.domain.crypto.SecretField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.net.HttpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookHeadersTest {

    /** 只用于构造 WebhookSender；本文件不触发真实发送 */
    private class FakeEngine : HttpEngine {
        override suspend fun execute(config: HttpCallConfig): HttpCallOutcome =
            throw UnsupportedOperationException("测试不发送请求")
    }

    private class ReverseBox : SecretBox {
        override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
        override fun decrypt(sealed: ByteArray): ByteArray = sealed.reversedArray()
    }

    private val box = ReverseBox()
    private val seal: (String) -> String = { SecretField.seal(it, box) }

    @Test
    fun roundTripKeepsKeyValueAndEnabledState() {
        val source = listOf(
            KeyValueEntry("Authorization", "Bearer token-1"),
            KeyValueEntry("X-Empty", ""),
            KeyValueEntry("X-Off", "off", enabled = false)
        )
        assertEquals(source, decodeKeyValueList(encodeKeyValueList(source, seal), box))
    }

    @Test
    fun valuesAreSealedWhileKeysStayReadable() {
        val raw = encodeKeyValueList(listOf(KeyValueEntry("X-Sign", "secret-value")), seal)
        assertTrue(raw.contains("X-Sign"))
        assertFalse(raw.contains("secret-value"))
    }

    @Test
    fun corruptedOrEmptyPayloadFallsBackToEmptyList() {
        assertEquals(emptyList<KeyValueEntry>(), decodeKeyValueList("", box))
        assertEquals(emptyList<KeyValueEntry>(), decodeKeyValueList("{not json", box))
        assertEquals(emptyList<KeyValueEntry>(), decodeKeyValueList("""[{"key":1}]""", box))
    }

    @Test
    fun plaintextValuesFromOlderVersionsStayReadable() {
        val raw = """[{"key":"Accept","value":"application/json","enabled":true}]"""
        assertEquals(
            listOf(KeyValueEntry("Accept", "application/json", true)),
            decodeKeyValueList(raw, null)
        )
    }

    @Test
    fun userHeaderOverridesBuiltInContentType() {
        val settings = AppSettings(
            webhookHeaders = listOf(
                KeyValueEntry("content-type", "application/x-www-form-urlencoded"),
                KeyValueEntry("Authorization", "Bearer abc")
            )
        )
        val headers = WebhookSender(FakeEngine()).buildHeaders(settings)
        assertEquals(
            listOf(
                com.evan.pulse.domain.model.KeyValue("content-type", "application/x-www-form-urlencoded"),
                com.evan.pulse.domain.model.KeyValue("Authorization", "Bearer abc")
            ),
            headers
        )
    }

    @Test
    fun blankAndDisabledHeadersAreIgnored() {
        val settings = AppSettings(
            webhookHeaders = listOf(
                KeyValueEntry("", "ignored"),
                KeyValueEntry("X-Off", "ignored", enabled = false),
                KeyValueEntry("X-Keep", "1")
            )
        )
        val headers = WebhookSender(FakeEngine()).buildHeaders(settings)
        assertEquals(2, headers.size)
        assertEquals("Content-Type", headers.first().key)
        assertEquals("X-Keep", headers.last().key)
    }

    @Test
    fun defaultContentTypeUsedWhenNoUserHeader() {
        val headers = WebhookSender(FakeEngine()).buildHeaders(AppSettings())
        assertEquals(
            listOf(com.evan.pulse.domain.model.KeyValue("Content-Type", "application/json; charset=utf-8")),
            headers
        )
    }
}
