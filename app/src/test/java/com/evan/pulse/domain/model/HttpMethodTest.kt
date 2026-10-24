package com.evan.pulse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpMethodTest {

    @Test
    fun `parse trims and uppercases`() {
        assertEquals("PROPFIND", HttpMethod.parse(" propfind ").value)
        assertEquals("GET", HttpMethod.parse("get").value)
        assertEquals("GET", HttpMethod.parse("").value)
        assertEquals("GET", HttpMethod.parse("   ").value)
    }

    @Test
    fun `common methods keep the display order`() {
        assertEquals(
            listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"),
            HttpMethod.COMMON.map { it.value }
        )
    }

    @Test
    fun `validation accepts letters and hyphens only`() {
        assertTrue(HttpMethod.isValid("PROPFIND"))
        assertTrue(HttpMethod.isValid("M-SEARCH"))
        assertFalse(HttpMethod.isValid(""))
        assertFalse(HttpMethod.isValid("GET POST"))
        assertFalse(HttpMethod.isValid("GET2"))
        assertFalse(HttpMethod.isValid("GET/"))
    }

    @Test
    fun `value equality works for snapshots and configs`() {
        assertEquals(HttpMethod.PUT, HttpMethod.parse("put"))
        assertEquals(HttpMethod("PROPFIND"), HttpMethod.parse("propfind"))
    }
}
