package com.evan.pulse.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `maskUrl 保留协议主机与路径并替换查询值`() {
        val masked = Redactor.maskUrl("https://api.example.com/v1/users?token=abc123&page=2")
        assertEquals("https://api.example.com/v1/users?token=***&page=***", masked)
    }

    @Test
    fun `maskUrl 去掉认证信息`() {
        val masked = Redactor.maskUrl("https://user:pa55@example.com/path")
        assertEquals("https://***@example.com/path", masked)
        assertFalse(masked.contains("pa55"))
    }

    @Test
    fun `maskUrl 丢弃 fragment 内容`() {
        val masked = Redactor.maskUrl("https://example.com/page#access_token=xyz")
        assertEquals("https://example.com/page#***", masked)
    }

    @Test
    fun `maskUrl 长路径段只留前缀与长度`() {
        val token = "x".repeat(40)
        val masked = Redactor.maskUrl("https://api.telegram.org/$token/sendMessage")
        assertFalse(masked.contains(token))
        assertTrue(masked.contains("…(40)"))
    }

    @Test
    fun `maskUrl 空串与普通文本保持可用`() {
        assertEquals("", Redactor.maskUrl(""))
        assertEquals("relative/path", Redactor.maskUrl("relative/path"))
    }

    @Test
    fun `maskHeaderLine 整体替换敏感头`() {
        assertEquals("Authorization: ***", Redactor.maskHeaderLine("Authorization: Bearer abcdefghij"))
        assertEquals("Cookie: ***", Redactor.maskHeaderLine("Cookie: sid=abc; uid=42"))
        assertEquals("X-Api-Key: ***", Redactor.maskHeaderLine("X-Api-Key: k-123"))
    }

    @Test
    fun `maskHeaderLine 普通头保留但脱敏值`() {
        val masked = Redactor.maskHeaderLine("User-Agent: Pulse/0.2.1 (me@example.com)")
        assertTrue(masked.startsWith("User-Agent: "))
        assertFalse(masked.contains("me@example.com"))
    }

    @Test
    fun `maskText 替换 Bearer 凭证`() {
        val masked = Redactor.maskText("failed with Bearer sk-abcdef123456")
        assertEquals("failed with Bearer ***", masked)
    }

    @Test
    fun `maskText 替换 JWT`() {
        val jwt = "eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0.SflKxwRJSM"
        val masked = Redactor.maskText("token=$jwt in body")
        assertFalse(masked.contains(jwt))
    }

    @Test
    fun `maskText 替换邮箱与手机号`() {
        val masked = Redactor.maskText("联系 alice.wang@example.com 或 13800138000")
        assertEquals("联系 *** 或 ***", masked)
    }

    @Test
    fun `maskText 处理键值对形式的敏感字段`() {
        val masked = Redactor.maskText("password=hunter2&user=evan")
        assertEquals("password=***&user=evan", masked)
        val jsonLike = Redactor.maskText("\"api_key\": \"secret-value\", \"page\": 1")
        assertFalse(jsonLike.contains("secret-value"))
    }

    @Test
    fun `maskText 不含敏感内容时原样返回`() {
        assertEquals("连接超时 3000ms", Redactor.maskText("连接超时 3000ms"))
        assertEquals("", Redactor.maskText(""))
    }
}
