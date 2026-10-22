package com.evan.pulse.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtCodecTest {

    @Test
    fun `decode three part token`() {
        // 手动构造: header {"alg":"HS256"} , payload {"sub":"123"}
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig"
        val result = JwtCodec.decode(token)
        assertTrue(result.valid)
        assertTrue(result.header.contains("HS256"))
        assertTrue(result.payload.contains("\"123\""))
        assertEquals("sig", result.signature)
    }

    @Test
    fun `decode empty returns invalid`() {
        val result = JwtCodec.decode("")
        assertFalse(result.valid)
        assertEquals("empty", result.error)
    }

    @Test
    fun `decode wrong segment count returns invalid`() {
        assertFalse(JwtCodec.decode("a.b").valid)
        assertFalse(JwtCodec.decode("a.b.c.d").valid)
    }

    @Test
    fun `decode bad base64 returns invalid`() {
        val result = JwtCodec.decode("!!!.!!!.!!!")
        assertFalse(result.valid)
        assertTrue(result.error == "header" || result.error == "payload")
    }
}