package com.evan.pulse.domain.assertion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseAssertionsTest {

    private val body = """{"code":0,"message":"ok","items":[{"id":7}]}"""

    @Test
    fun containsPassesWhenKeywordPresent() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(ResponseAssertion(ResponseAssertion.TYPE_CONTAINS, "\"message\":\"ok\""))
        )
        assertEquals(1, results.size)
        assertTrue(results.single().passed)
    }

    @Test
    fun containsFailsWhenKeywordMissing() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(ResponseAssertion(ResponseAssertion.TYPE_CONTAINS, "error"))
        )
        assertFalse(results.single().passed)
    }

    @Test
    fun notContainsInvertsTheCheck() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_NOT_CONTAINS, "error"),
                ResponseAssertion(ResponseAssertion.TYPE_NOT_CONTAINS, "ok")
            )
        )
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
    }

    @Test
    fun jsonPathComparesFirstMatchWithExpected() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.code", "0"),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.code", "1"),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.items[0].id", "7")
            )
        )
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
        assertTrue(results[2].passed)
    }

    @Test
    fun missingJsonPathFails() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.nope", "x"))
        )
        assertFalse(results.single().passed)
    }

    @Test
    fun blankExpressionIsSkipped() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(
                ResponseAssertion(),
                ResponseAssertion(ResponseAssertion.TYPE_CONTAINS, "ok")
            )
        )
        assertEquals(1, results.size)
        assertEquals(1, results.single().index)
    }

    @Test
    fun placeholdersResolveFromVariables() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.message", "{{expected}}")),
            mapOf("expected" to "ok")
        )
        assertTrue(results.single().passed)
    }

    @Test
    fun undefinedPlaceholderFails() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(ResponseAssertion(ResponseAssertion.TYPE_CONTAINS, "{{missing}}"))
        )
        assertFalse(results.single().passed)
    }

    @Test
    fun needsExpectedOnlyForJsonPath() {
        assertTrue(ResponseAssertion.needsExpected(ResponseAssertion.TYPE_JSON_PATH))
        assertFalse(ResponseAssertion.needsExpected(ResponseAssertion.TYPE_CONTAINS))
    }

    // ---- 状态码 / 响应头 / 耗时 / 运算符 ----

    private val target = AssertionTarget(
        body = body,
        statusCode = 200,
        headers = mapOf("Content-Type" to "application/json; charset=utf-8", "X-Request-Id" to "abc"),
        durationMs = 120
    )

    @Test
    fun statusEqualsAndWildcard() {
        val results = ResponseAssertions.evaluate(
            target,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_STATUS, "200"),
                ResponseAssertion(ResponseAssertion.TYPE_STATUS, "2xx"),
                ResponseAssertion(ResponseAssertion.TYPE_STATUS, "404")
            )
        )
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
        assertFalse(results[2].passed)
    }

    @Test
    fun statusWithComparisonOperator() {
        val results = ResponseAssertions.evaluate(
            target,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_STATUS, "199", operator = AssertionOperator.GT),
                ResponseAssertion(ResponseAssertion.TYPE_STATUS, "300", operator = AssertionOperator.LT)
            )
        )
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
    }

    @Test
    fun statusMissingFails() {
        val results = ResponseAssertions.evaluate(
            AssertionTarget(body = body),
            listOf(ResponseAssertion(ResponseAssertion.TYPE_STATUS, "200"))
        )
        assertFalse(results.single().passed)
    }

    @Test
    fun headerContainsAndExists() {
        val results = ResponseAssertions.evaluate(
            target,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_HEADER, "content-type", "json", operator = AssertionOperator.CONTAINS),
                ResponseAssertion(ResponseAssertion.TYPE_HEADER, "X-Request-Id", operator = AssertionOperator.EXISTS),
                ResponseAssertion(ResponseAssertion.TYPE_HEADER, "X-Missing", operator = AssertionOperator.EXISTS)
            )
        )
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
        assertFalse(results[2].passed)
    }

    @Test
    fun durationRespectsThreshold() {
        val results = ResponseAssertions.evaluate(
            target,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_DURATION, "500"),
                ResponseAssertion(ResponseAssertion.TYPE_DURATION, "100"),
                ResponseAssertion(ResponseAssertion.TYPE_DURATION, "100", operator = AssertionOperator.GTE)
            )
        )
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
        assertTrue(results[2].passed)
    }

    @Test
    fun jsonPathNumericAndTypeOperators() {
        val results = ResponseAssertions.evaluate(
            body,
            listOf(
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.items[0].id", "5", operator = AssertionOperator.GTE),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.code", operator = AssertionOperator.IS_NUMBER),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.message", operator = AssertionOperator.IS_STRING),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.items", operator = AssertionOperator.IS_ARRAY),
                ResponseAssertion(ResponseAssertion.TYPE_JSON_PATH, "$.code", operator = AssertionOperator.IS_ARRAY)
            )
        )
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
        assertTrue(results[2].passed)
        assertTrue(results[3].passed)
        assertFalse(results[4].passed)
    }

    @Test
    fun defaultOperatorDependsOnType() {
        assertEquals(AssertionOperator.LTE, ResponseAssertion.defaultOperator(ResponseAssertion.TYPE_DURATION))
        assertEquals(AssertionOperator.EQUALS, ResponseAssertion.defaultOperator(ResponseAssertion.TYPE_JSON_PATH))
    }
}
