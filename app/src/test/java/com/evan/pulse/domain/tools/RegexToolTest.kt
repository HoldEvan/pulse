package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexToolTest {

    @Test
    fun `empty pattern reports error`() {
        val r = RegexTool.test("", "abc")
        assertEquals("empty", r.patternError)
    }

    @Test
    fun `invalid pattern reports error`() {
        val r = RegexTool.test("(", "abc")
        assertNotNull(r.patternError)
    }

    @Test
    fun `no match returns false`() {
        val r = RegexTool.test("\\d+", "abc")
        assertTrue(!r.matched)
        assertEquals(0, r.count)
    }

    @Test
    fun `match captures groups`() {
        val r = RegexTool.test("(\\d{4})-(\\d{2})", "order 2026-01 and 2027-02")
        assertTrue(r.matched)
        assertEquals(2, r.count)
        // 第一组
        assertEquals("2026-01", r.groups[0][0])
        assertEquals("2026", r.groups[0][1])
        assertEquals("01", r.groups[0][2])
        // 第二组
        assertEquals("2027-02", r.groups[1][0])
    }

    @Test
    fun `global match with overlapping false`() {
        val r = RegexTool.test("\\b\\w+\\b", "hi there")
        assertTrue(r.matched)
        assertEquals(2, r.count)
    }
}