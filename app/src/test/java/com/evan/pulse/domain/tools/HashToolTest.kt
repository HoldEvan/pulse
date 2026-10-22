package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class HashToolTest {

    @Test
    fun `md5 of empty is empty`() {
        assertEquals("", HashTool.hash("", HashAlgorithm.MD5))
    }

    @Test
    fun `md5 of hello`() {
        assertEquals(
            "5d41402abc4b2a76b9719d911017c592",
            HashTool.hash("hello", HashAlgorithm.MD5)
        )
    }

    @Test
    fun `sha256 of hello`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            HashTool.hash("hello", HashAlgorithm.SHA256)
        )
    }

    @Test
    fun `sha1 of hello`() {
        assertEquals(
            "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
            HashTool.hash("hello", HashAlgorithm.SHA1)
        )
    }
}