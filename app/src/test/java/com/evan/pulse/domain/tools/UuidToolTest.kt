package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidToolTest {

    @Test
    fun `generate returns 36 char standard uuid`() {
        val u = UuidTool.generate()
        assertEquals(36, u.length)
        assertTrue(UuidTool.isValid(u))
    }

    @Test
    fun `generateCompact removes dashes`() {
        val c = UuidTool.generateCompact()
        assertEquals(32, c.length)
        assertFalse(c.contains('-'))
    }

    @Test
    fun `isValid accepts dashed and compact forms`() {
        assertTrue(UuidTool.isValid("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(UuidTool.isValid("550e8400e29b41d4a716446655440000"))
        assertFalse(UuidTool.isValid(""))
        assertFalse(UuidTool.isValid("not-a-uuid"))
        assertFalse(UuidTool.isValid("550e8400-e29b-41d4")) // 太短
    }

    @Test
    fun `normalize formats compact to dashed`() {
        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            UuidTool.normalize("550e8400e29b41d4a716446655440000")
        )
    }

    @Test
    fun `normalize rejects invalid hex`() {
        assertNull(UuidTool.normalize("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"))
    }
}