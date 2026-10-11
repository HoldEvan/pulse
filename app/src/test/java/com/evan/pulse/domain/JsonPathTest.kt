package com.evan.pulse.domain

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPathTest {

    private val sample = """
        {"choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}
    """.trimIndent()

    @Test
    fun extractsDeepStringField() {
        val hits = JsonPath.eval(sample, "$.choices[0].delta.content") ?: emptyList()
        assertEquals(1, hits.size)
        assertEquals("Hello", hits[0].jsonPrimitive.content)
    }

    @Test
    fun extractStringFallsBackToScalarContent() {
        val hits = JsonPath.eval(sample, "$.choices[0].index") ?: emptyList()
        assertEquals("0", hits[0].extractString())
    }

    @Test
    fun wildcardExpandsAllArrayElements() {
        val text = """{"items":[{"v":1},{"v":2},{"v":3}]}"""
        val hits = JsonPath.eval(text, "$.items[*].v") ?: emptyList()
        assertEquals(listOf("1", "2", "3"), hits.map { it.extractString() })
    }

    @Test
    fun bracketQuotedFieldIsSupported() {
        val text = """{"a b":{"c":"x"}}"""
        val hits = JsonPath.eval(text, "$['a b'].c") ?: emptyList()
        assertEquals("x", hits.single().extractString())
    }

    @Test
    fun rootOnlyReturnsWholeDocument() {
        val hits = JsonPath.eval(sample, "$") ?: emptyList()
        assertEquals(1, hits.size)
    }

    @Test
    fun missingFieldReturnsEmpty() {
        val hits = JsonPath.eval(sample, "$.choices[0].delta.nope")
        assertTrue(hits.isNullOrEmpty())
    }

    @Test
    fun nonJsonTextReturnsNull() {
        assertEquals(null, JsonPath.eval("{oops", "$.a"))
        assertEquals(null, JsonPath.eval("not json at all", "$.a"))
    }

    @Test
    fun sseDoneSentinelYieldsNothingToConcat() {
        // OpenAI 流式结束帧 data: [DONE]，JSONPath 命不中任何字段，
        // 返回空列表（不抛异常），拼接时安全跳过
        val hits = JsonPath.eval("[DONE]", "$.choices[0].delta.content")
        assertTrue(hits.isNullOrEmpty())
    }

    @Test
    fun invalidExpressionReturnsEmpty() {
        val text = """{"a":1}"""
        assertTrue(JsonPath.eval(Json.parseToJsonElement(text), "a.b[").isEmpty())
        assertTrue(JsonPath.eval(Json.parseToJsonElement(text), "a..b").isEmpty())
    }

    @Test
    fun arrayElementsExtractIntoList() {
        val text = """{"data":[{"b":"1"},{"b":"2"}]}"""
        val element = Json.parseToJsonElement(text)
        val hits = JsonPath.eval(element, "$.data[*].b")
        assertEquals(2, hits.size)
        assertEquals("1", hits.get(0).jsonPrimitive.content)
        assertEquals("2", hits.get(1).jsonPrimitive.content)
    }
}