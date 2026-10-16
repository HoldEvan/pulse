package com.evan.pulse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncodingConversionsTest {

    @Test
    fun base64RoundTripsAsciiAndChinese() {
        for (text in listOf("hello", "中文测试", "emoji \uD83D\uDE00", "a+b/c=")) {
            assertEquals(text, base64Decode(base64Encode(text)))
        }
    }

    @Test
    fun base64EncodeMatchesKnownVectors() {
        assertEquals("", base64Encode(""))
        assertEquals("aGVsbG8=", base64Encode("hello"))
        assertEquals("5Lit5paH", base64Encode("中文"))
    }

    @Test
    fun base64DecodeToleratesLineBreaks() {
        assertEquals("hello", base64Decode("aGVs\nbG8="))
    }

    @Test
    fun base64DecodeReturnsNullOnInvalidInput() {
        assertNull(base64Decode("!!!not-base64!!!"))
    }

    @Test
    fun parseRadixAcceptsValidDigitsPerBase() {
        assertEquals(255L, parseRadix("ff", 16))
        assertEquals(255L, parseRadix("FF", 16))
        assertEquals(255L, parseRadix("377", 8))
        assertEquals(255L, parseRadix("11111111", 2))
        assertEquals(255L, parseRadix("255", 10))
        assertEquals(-255L, parseRadix("-ff", 16))
        assertEquals(255L, parseRadix(" 255 ", 10))
    }

    @Test
    fun parseRadixRejectsInvalidOrOverflowingInput() {
        assertNull(parseRadix("8", 8))
        assertNull(parseRadix("2", 2))
        assertNull(parseRadix("xyz", 10))
        assertNull(parseRadix("99999999999999999999", 10))
        assertNull(parseRadix("", 10))
    }

    @Test
    fun formatRadixProducesEachBase() {
        assertEquals("11111111", formatRadix(255, 2))
        assertEquals("377", formatRadix(255, 8))
        assertEquals("255", formatRadix(255, 10))
        assertEquals("ff", formatRadix(255, 16))
        assertEquals("-ff", formatRadix(-255, 16))
    }

    @Test
    fun urlEncodeUsesPercentEscapesForSpacesAndReservedChars() {
        assertEquals("a%20b", urlEncode("a b"))
        assertEquals("%E4%B8%AD%E6%96%87", urlEncode("中文"))
        assertEquals("a%26b%3Dc%3F", urlEncode("a&b=c?"))
    }

    @Test
    fun urlDecodeReversesEncodingAndAcceptsPlusAsSpace() {
        assertEquals("a b", urlDecode("a%20b"))
        assertEquals("a b", urlDecode("a+b"))
        assertEquals("中文", urlDecode("%E4%B8%AD%E6%96%87"))
    }

    @Test
    fun urlDecodeReturnsNullOnMalformedEscapes() {
        assertNull(urlDecode("100%"))
        assertNull(urlDecode("%G1"))
    }

    @Test
    fun unicodeEscapeKeepsAsciiAndEncodesNonAscii() {
        assertEquals("a\\u4e2d", unicodeEscape("a中"))
        assertEquals("", unicodeEscape(""))
        assertEquals("abc", unicodeEscape("abc"))
    }

    @Test
    fun unicodeEscapeEncodesSurrogatePairsIndividually() {
        assertEquals("\\ud83d\\ude00", unicodeEscape("\uD83D\uDE00"))
    }

    @Test
    fun unicodeUnescapeRestoresEscapedChars() {
        assertEquals("中文", unicodeUnescape("\\u4e2d\\u6587"))
        assertEquals("a中", unicodeUnescape("a\\u4e2d"))
        // 字面量里的转义反斜杠原样保留，其后构成 \uXXXX 的部分仍被还原
        assertEquals("\\中", unicodeUnescape("\\\\u4e2d"))
    }

    @Test
    fun unicodeUnescapeKeepsInvalidSequencesAsText() {
        assertEquals("\\uZZZZ", unicodeUnescape("\\uZZZZ"))
        assertEquals("\\u12", unicodeUnescape("\\u12"))
    }

    @Test
    fun asciiEncodeListsDecimalAndHexCodes() {
        assertEquals(listOf(65, 66), asciiDecimalCodes("AB"))
        assertEquals(listOf("41", "42"), asciiHexCodes("AB"))
        assertEquals(listOf("4E2D"), asciiHexCodes("中"))
    }

    @Test
    fun asciiDecodeAcceptsDecimalAndPrefixedHexTokens() {
        assertEquals("AB", asciiFromCodes("65 66"))
        assertEquals("AB", asciiFromCodes("65,66"))
        assertEquals("AB", asciiFromCodes("0x41 0x42"))
        assertEquals("中", asciiFromCodes("20013"))
        assertEquals("A", asciiFromCodes("  65  "))
    }

    @Test
    fun asciiDecodeRejectsOutOfRangeOrSurrogateCodes() {
        assertNull(asciiFromCodes("1114112"))
        assertNull(asciiFromCodes("-1"))
        assertNull(asciiFromCodes("55296"))
        assertNull(asciiFromCodes("abc"))
        assertNull(asciiFromCodes("，，"))
    }
}
