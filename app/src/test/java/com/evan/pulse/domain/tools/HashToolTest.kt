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

    @Test
    fun `sha384 of hello`() {
        assertEquals(
            "59e1748777448c69de6b800d7a33bbfb9ff1b463e44354c3553bcdb9c666fa90125a3c79f90397bdf5f6a13de828684f",
            HashTool.hash("hello", HashAlgorithm.SHA384)
        )
    }

    @Test
    fun `sha512 of hello`() {
        assertEquals(
            "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043",
            HashTool.hash("hello", HashAlgorithm.SHA512)
        )
    }

    @Test
    fun `base64 output of sha256`() {
        assertEquals(
            "LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=",
            HashTool.hash("hello", HashAlgorithm.SHA256, DigestOutput.BASE64)
        )
    }

    @Test
    fun `hmac md5 matches known vector`() {
        assertEquals(
            "80070713463e7749b90c2dc24911e275",
            HashTool.hmac("The quick brown fox jumps over the lazy dog", "key", HashAlgorithm.MD5)
        )
    }

    @Test
    fun `hmac sha1 matches known vector`() {
        assertEquals(
            "de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9",
            HashTool.hmac("The quick brown fox jumps over the lazy dog", "key", HashAlgorithm.SHA1)
        )
    }

    @Test
    fun `hmac sha256 matches rfc4231 case 2`() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            HashTool.hmac("what do ya want for nothing?", "Jefe", HashAlgorithm.SHA256)
        )
    }

    @Test
    fun `hmac sha384 matches rfc4231 case 2`() {
        assertEquals(
            "af45d2e376484031617f78d2b58a6b1b9c7ef464f5a01b47e42ec3736322445e8e2240ca5e69e2c78b3239ecfab21649",
            HashTool.hmac("what do ya want for nothing?", "Jefe", HashAlgorithm.SHA384)
        )
    }

    @Test
    fun `hmac sha512 matches rfc4231 case 2`() {
        assertEquals(
            "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
            HashTool.hmac("what do ya want for nothing?", "Jefe", HashAlgorithm.SHA512)
        )
    }

    @Test
    fun `hmac base64 output`() {
        assertEquals(
            "W9zBRr9gdU5qBCQmCJV1x1oAPwidJzmDnexYuWTsOEM=",
            HashTool.hmac(
                "what do ya want for nothing?",
                "Jefe",
                HashAlgorithm.SHA256,
                DigestOutput.BASE64
            )
        )
    }

    @Test
    fun `hmac with empty key is empty`() {
        assertEquals("", HashTool.hmac("data", "", HashAlgorithm.SHA256))
    }

    @Test
    fun `hmac accepts empty text`() {
        assertEquals(
            "5d5d139563c95b5967b9bd9a8c9b233a9dedb45072794cd232dc1b74832607d0",
            HashTool.hmac("", "key", HashAlgorithm.SHA256)
        )
    }
}
