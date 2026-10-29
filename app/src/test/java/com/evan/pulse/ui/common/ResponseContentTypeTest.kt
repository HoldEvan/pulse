package com.evan.pulse.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预览入口是否出现只看响应头 Content-Type，正文兜底仅用于没有响应头的老记录 */
class ResponseContentTypeTest {

    @Test
    fun htmlTypeIgnoresCharsetParameters() {
        assertTrue(isHtmlResponse("text/html; charset=utf-8", "<p>hi</p>"))
        assertTrue(isHtmlResponse("TEXT/HTML", "<p>hi</p>"))
        assertTrue(isHtmlResponse("application/xhtml+xml", "<html></html>"))
    }

    @Test
    fun nonHtmlTypeIsNotPreviewedEvenWhenBodyLooksLikeHtml() {
        assertFalse(isHtmlResponse("application/json", "<html></html>"))
        assertFalse(isHtmlResponse("text/plain", "<!doctype html><html></html>"))
    }

    @Test
    fun missingTypeFallsBackToBodySniffing() {
        assertTrue(isHtmlResponse(null, "  <!DOCTYPE html><html></html>"))
        assertTrue(isHtmlResponse("", "<html><body>x</body></html>"))
        assertFalse(isHtmlResponse(null, """{"a":1}"""))
    }

    @Test
    fun mediaTypesAreDetected() {
        assertTrue(isMediaType("image/png"))
        assertTrue(isMediaType("audio/mpeg"))
        assertTrue(isMediaType("video/mp4"))
        assertFalse(isMediaType("text/html"))
        assertFalse(isMediaType("application/octet-stream"))
    }
}
