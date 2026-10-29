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
}
