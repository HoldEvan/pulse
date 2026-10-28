package com.evan.pulse.ui.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptApiCatalogTest {

    @Test
    fun includesNamesDeclaredInTheScript() {
        val script = """
            function login(user) { return user; }
            let local = 1;
            var snapshot = () => 2;
            total = 3;
        """.trimIndent()

        val methods = ScriptApiCatalog.match("log", script)
        assertTrue(methods.any { it.path == "login" && it.isMethod })

        val variables = ScriptApiCatalog.match("loc", script)
        assertTrue(variables.any { it.path == "local" && !it.isMethod })

        val arrow = ScriptApiCatalog.match("snap", script)
        assertTrue(arrow.any { it.path == "snapshot" && it.isMethod })

        val assigned = ScriptApiCatalog.match("tot", script)
        assertTrue(assigned.any { it.path == "total" && !it.isMethod })
    }

    @Test
    fun doesNotTreatMemberAccessAsVariable() {
        val script = "pm.request.method = 'GET';"
        val result = ScriptApiCatalog.match("meth", script)
        assertTrue(result.none { it.path == "method" })
    }

    @Test
    fun dotPrefixOnlyMatchesPmPaths() {
        val result = ScriptApiCatalog.match("pm.re", "function require() {}")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.path.startsWith("pm.re") })
    }

    @Test
    fun fallsBackToJavaScriptGlobals() {
        val result = ScriptApiCatalog.match("con")
        assertTrue(result.any { it.path == "console" })
    }

    @Test
    fun keepsEachNameOnce() {
        val script = "let dup = 1; dup = 2; function dupFn() {}"
        val result = ScriptApiCatalog.match("dup", script)
        assertEquals(2, result.count { it.path == "dup" || it.path == "dupFn" })
    }

    @Test
    fun completesConsoleMembersAfterDot() {
        val result = ScriptApiCatalog.match("console.")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.path.startsWith("console.") })
        assertTrue(result.any { it.path == "console.log" && it.isMethod })
        assertTrue(result.any { it.path == "console.warn" && it.isMethod })
    }

    @Test
    fun completesJsonMembersAfterDot() {
        val result = ScriptApiCatalog.match("JSON.")
        assertTrue(result.any { it.path == "JSON.parse" && it.isMethod })
        assertTrue(result.any { it.path == "JSON.stringify" && it.isMethod })
        assertTrue(result.all { it.path.startsWith("JSON.") })
    }

    @Test
    fun builtinMethodsInsertParentheses() {
        val all = ScriptApiCatalog.tokens + ScriptApiCatalog.memberTokens +
            ScriptApiCatalog.match("parseInt")
        for (path in listOf("console.log", "JSON.stringify", "Math.max", "Object.keys", "parseInt")) {
            val token = all.first { it.path == path }
            assertTrue("$path should be a method", token.isMethod)
            assertEquals("$path()", token.insert)
            assertEquals("$path should place the caret inside", 1, token.cursorBack)
        }
    }

    @Test
    fun completesMathMembersAfterDot() {
        val result = ScriptApiCatalog.match("Math.")
        assertTrue(result.any { it.path == "Math.max" && it.insert == "Math.max()" })
        assertTrue(result.all { it.path.startsWith("Math.") })
    }

    @Test
    fun removesPmConsoleFromCatalog() {
        assertTrue(ScriptApiCatalog.match("pm.console").isEmpty())
        assertTrue(
            ScriptApiCatalog.tokens.none {
                it.path.startsWith("pm.console") || it.path == "pm.log"
            }
        )
    }
}