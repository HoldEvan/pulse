package com.evan.pulse.domain.http

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestExportersTest {

    private val BASE_URL = "https://api.example.com:8443/v1/users"

    private fun config(
        method: HttpMethod = HttpMethod.POST,
        url: String = BASE_URL,
        params: List<KeyValue> = emptyList(),
        headers: List<KeyValue> = emptyList(),
        bodyType: BodyType = BodyType.JSON,
        body: String = "",
        formFields: List<FormField> = emptyList()
    ) = HttpCallConfig(
        method = method,
        url = url,
        headers = headers,
        queryParams = params,
        bodyType = bodyType,
        body = body,
        formFields = formFields
    )

    @Test
    fun curlBashExportsPostWithJsonBody() {
        val out = config(body = """{"name":"ann"}""").exportTo(HttpExportFormat.CURL_BASH)
        assertEquals(
            """curl -X POST 'https://api.example.com:8443/v1/users' -H 'Content-Type: application/json' --data-binary '{"name":"ann"}'""",
            out
        )
    }

    @Test
    fun curlBashEscapesSingleQuotesWithBackslashEscape() {
        val out = config(body = "it's a body").exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("""--data-binary 'it'\''s a body'"""))
    }

    @Test
    fun curlPowershellUsesExeNameAndDoublesSingleQuotes() {
        val out = config(body = "it's a body").exportTo(HttpExportFormat.CURL_POWERSHELL)
        assertTrue(out.startsWith("curl.exe -X POST '"))
        assertTrue(out.contains("""--data-binary 'it''s a body'"""))
        assertFalse(out.contains("curl -X"))
    }

    @Test
    fun getWithoutBodyEmitsNoBodyArgument() {
        val out = config(method = HttpMethod.GET, bodyType = BodyType.NONE, url = "https://api.example.com/p")
            .exportTo(HttpExportFormat.CURL_BASH)
        assertEquals("curl -X GET 'https://api.example.com/p'", out)
    }

    @Test
    fun blankJsonBodyAddsNoContentType() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.JSON,
            url = "https://api.example.com/p"
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertEquals("curl -X GET 'https://api.example.com/p'", out)
    }

    @Test
    fun emptyFormDataAddsNoContentType() {
        val out = config(
            method = HttpMethod.POST,
            bodyType = BodyType.FORM_DATA,
            url = "https://api.example.com/p"
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertEquals("curl -X POST 'https://api.example.com/p'", out)
    }

    @Test
    fun queryParamsArePercentEncodedInUrl() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.NONE,
            url = "https://api.example.com/search",
            params = listOf(KeyValue("q", "a b&c=d"), KeyValue("off", ""))
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("https://api.example.com/search?q=a%20b%26c%3Dd&off="))
    }

    @Test
    fun queryParamsAreJoinedToExistingQueryWithAmpersand() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.NONE,
            url = "https://api.example.com/search?x=1",
            params = listOf(KeyValue("y", "2"))
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("https://api.example.com/search?x=1&y=2"))
    }

    @Test
    fun disabledParamsAndHeadersAreSkipped() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.NONE,
            url = "https://api.example.com/p",
            params = listOf(KeyValue("on", "1"), KeyValue("off", "2", enabled = false)),
            headers = listOf(KeyValue("A", "1"), KeyValue("B", "2", enabled = false))
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("?on=1"))
        assertFalse(out.contains("off"))
        assertTrue(out.contains("-H 'A: 1'"))
        assertFalse(out.contains("-H 'B: 2'"))
    }

    @Test
    fun httpRawEmitsRequestLineHostAndBody() {
        val out = config(body = """{"name":"ann"}""").exportTo(HttpExportFormat.HTTP_RAW)
        assertEquals(
            "POST /v1/users HTTP/1.1\n" +
                "Host: api.example.com:8443\n" +
                "Content-Type: application/json\n" +
                "\n" +
                """{"name":"ann"}""",
            out
        )
    }

    @Test
    fun httpRawOmitsDefaultPortFromHost() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.NONE,
            url = "https://api.example.com/p?x=1"
        ).exportTo(HttpExportFormat.HTTP_RAW)
        assertTrue(out.startsWith("GET /p?x=1 HTTP/1.1\nHost: api.example.com\n\n"))
    }

    @Test
    fun httpRawFallsBackToWholeUrlWhenAddressIsNotParseable() {
        val out = config(
            method = HttpMethod.GET,
            bodyType = BodyType.NONE,
            url = "{{baseUrl}}/ping"
        ).exportTo(HttpExportFormat.HTTP_RAW)
        assertEquals("GET {{baseUrl}}/ping HTTP/1.1\n\n", out)
    }

    @Test
    fun pythonExportsParamsAndHeadersWithoutBody() {
        val out = config(
            method = HttpMethod.GET,
            url = "https://api.example.com/v1/users",
            params = listOf(KeyValue("page", "1")),
            headers = listOf(KeyValue("X-Api-Key", "k1"))
        ).exportTo(HttpExportFormat.PYTHON_REQUESTS)
        assertEquals(
            "import requests\n\n" +
                "url = \"https://api.example.com/v1/users\"\n" +
                "\n" +
                "params = {\n" +
                "    \"page\": \"1\",\n" +
                "}\n" +
                "\n" +
                "headers = {\n" +
                "    \"X-Api-Key\": \"k1\",\n" +
                "}\n" +
                "\n" +
                "response = requests.request(\n" +
                "    \"GET\",\n" +
                "    url,\n" +
                "    params=params,\n" +
                "    headers=headers,\n" +
                ")\n" +
                "\n" +
                "print(response.text)\n",
            out
        )
    }

    @Test
    fun pythonFormUrlencodedUsesDataDict() {
        val out = config(
            bodyType = BodyType.FORM_URLENCODED,
            formFields = listOf(FormField(name = "a", value = "1 2"), FormField(name = "b", value = "x"))
        ).exportTo(HttpExportFormat.PYTHON_REQUESTS)
        assertTrue(out.contains("    data={\"a\": \"1 2\", \"b\": \"x\"},\n"))
        assertTrue(out.contains("Content-Type\": \"application/x-www-form-urlencoded\""))
    }

    @Test
    fun pythonFormDataUsesFilesDict() {
        val out = config(
            bodyType = BodyType.FORM_DATA,
            formFields = listOf(FormField(name = "file", value = "abc"))
        ).exportTo(HttpExportFormat.PYTHON_REQUESTS)
        assertTrue(out.contains("    files={\"file\": (\"file\", \"abc\")},\n"))
    }

    @Test
    fun jsFetchExportsMethodHeadersAndBody() {
        val out = config(
            url = "https://api.example.com/v1/users",
            headers = listOf(KeyValue("X-Token", "t1")),
            body = """{"name":"ann"}"""
        ).exportTo(HttpExportFormat.JS_FETCH)
        val expected = listOf(
            "const response = await fetch('https://api.example.com/v1/users', {",
            "  method: 'POST',",
            "  headers: {",
            "    'Content-Type': 'application/json',",
            "    'X-Token': 't1',",
            "  },",
            """  body: '{"name":"ann"}'""",
            "});",
            "console.log(await response.text());"
        ).joinToString("\n") + "\n"
        assertEquals(expected, out)
    }

    @Test
    fun jsFetchFormUrlencodedUsesUrlSearchParams() {
        val out = config(
            bodyType = BodyType.FORM_URLENCODED,
            formFields = listOf(FormField(name = "a", value = "1"))
        ).exportTo(HttpExportFormat.JS_FETCH)
        assertTrue(out.contains("  body: new URLSearchParams({\n    'a': '1',\n  })\n"))
    }

    @Test
    fun jsFetchFormDataBuildsFormDataObjectAndOmitsContentType() {
        val out = config(
            bodyType = BodyType.FORM_DATA,
            formFields = listOf(FormField(name = "file", value = "abc"))
        ).exportTo(HttpExportFormat.JS_FETCH)
        assertTrue(out.contains("const formData = new FormData();\nformData.append('file', 'abc');\n"))
        assertTrue(out.contains("  body: formData\n"))
        assertFalse(out.contains("Content-Type"))
    }

    @Test
    fun jsStringEscapesBackslashAndSingleQuote() {
        val out = config(
            headers = listOf(KeyValue("X-Q", "it's"), KeyValue("X-P", "a\\b")),
            bodyType = BodyType.JSON,
            body = "it's"
        ).exportTo(HttpExportFormat.JS_FETCH)
        assertTrue(out.contains("body: 'it\\'s'"))
        assertTrue(out.contains("'X-Q': 'it\\'s',"))
        assertTrue(out.contains("'X-P': 'a\\\\b',"))
    }

    @Test
    fun curlFormUrlencodedUsesDataUrlencodePerField() {
        val out = config(
            bodyType = BodyType.FORM_URLENCODED,
            formFields = listOf(FormField(name = "a", value = "1"))
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("--data-urlencode 'a=1'"))
        assertTrue(out.contains("-H 'Content-Type: application/x-www-form-urlencoded'"))
    }

    @Test
    fun curlFormDataUsesFormPerFieldWithoutContentType() {
        val out = config(
            bodyType = BodyType.FORM_DATA,
            formFields = listOf(FormField(name = "file", value = "abc"))
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("--form 'file=abc'"))
        assertFalse(out.contains("Content-Type"))
    }

    @Test
    fun httpRawMultipartEmitsBoundaryHeaderAndClosingMarker() {
        val out = config(
            bodyType = BodyType.FORM_DATA,
            formFields = listOf(FormField(name = "file", value = "abc"))
        ).exportTo(HttpExportFormat.HTTP_RAW)
        assertTrue(out.contains("Content-Type: multipart/form-data; boundary=pulse-form-data"))
        assertTrue(out.contains("Content-Disposition: form-data; name=\"file\""))
        assertTrue(out.contains("--pulse-form-data--\r\n"))
    }

    @Test
    fun declaredContentTypeIsKeptAndNeverDuplicated() {
        val out = config(
            headers = listOf(KeyValue("Content-Type", "application/xml")),
            body = "{}"
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertEquals(1, out.split("Content-Type").size - 1)
        assertTrue(out.contains("-H 'Content-Type: application/xml'"))
        assertFalse(out.contains("application/json"))
    }

    @Test
    fun contentLengthIsDroppedBecauseTheBodyLengthIsImplicit() {
        val out = config(
            headers = listOf(KeyValue("Content-Length", "99")),
            body = "{}"
        ).exportTo(HttpExportFormat.HTTP_RAW)
        assertFalse(out.contains("Content-Length"))
    }

    @Test
    fun fileFieldsAreNotExportedAsPlainValues() {
        val out = config(
            bodyType = BodyType.FORM_DATA,
            formFields = listOf(
                FormField(name = "note", value = "1"),
                FormField(name = "upload", value = "", isFile = true, fileName = "a.txt")
            )
        ).exportTo(HttpExportFormat.CURL_BASH)
        assertTrue(out.contains("--form 'note=1'"))
        assertFalse(out.contains("upload"))
    }
}
