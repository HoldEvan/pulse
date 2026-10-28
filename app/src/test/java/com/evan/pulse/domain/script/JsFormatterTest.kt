package com.evan.pulse.domain.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsFormatterTest {

    private fun check(input: String, expected: String) {
        assertEquals(expected.trimIndent().trim(), JsFormatter.format(input).trim())
    }

    @Test
    fun formatsBlocksAndStatements() {
        check(
            "var a=1;if(a>0){b=2;}",
            """
            var a = 1;
            if (a > 0) {
              b = 2;
            }
            """,
        )
    }

    @Test
    fun formatsFunctionBody() {
        check(
            "function login(user){return user.name;}",
            """
            function login(user) {
              return user.name;
            }
            """,
        )
    }

    @Test
    fun keepsComments() {
        check(
            """
            // hello
            var a = 1; // tail
            /* block */
            var b = 2;
            """,
            """
            // hello
            var a = 1;
            // tail
            /* block */
            var b = 2;
            """,
        )
    }

    @Test
    fun keepsStringsAndTemplatesIntact() {
        check(
            "var s = \"a;{b}\";var t = `x;${'$'}{y}`;",
            """
            var s = "a;{b}";
            var t = `x;${'$'}{y}`;
            """,
        )
    }

    @Test
    fun separatesRegexFromDivision() {
        check(
            "var re=/a\\/b/g;var x=y/2;",
            """
            var re = /a\/b/g;
            var x = y / 2;
            """,
        )
    }

    @Test
    fun keepsLineBreakAfterReturn() {
        val formatted = JsFormatter.format("function f(){return\n1}")
        assertTrue(formatted.contains("return\n  1"))
    }

    @Test
    fun splitsStatementsWithoutSemicolons() {
        check(
            "var a = 1\nvar b = 2",
            """
            var a = 1
            var b = 2
            """,
        )
    }

    @Test
    fun formatsForLoopHeader() {
        check(
            "for(let i=0;i<3;i++){f(i);}",
            """
            for (let i = 0; i < 3; i++) {
              f(i);
            }
            """,
        )
    }

    @Test
    fun formatsObjectArgument() {
        check(
            "foo({a:1,b:2})",
            """
            foo({
              a: 1, b: 2
            })
            """,
        )
    }

    @Test
    fun formatsEmptyObject() {
        check("x={};", "x = {};")
    }

    @Test
    fun keepsTernaryReadable() {
        check("var r=a?b:c;", "var r = a ? b : c;")
    }

    @Test
    fun isIdempotent() {
        val source = """
            function handle(ctx){var url=ctx.request.url;if(url===""){throw new Error("empty");}
            // build query
            var q={a:1,b:2};return [url,q];}
        """.trimIndent()
        val once = JsFormatter.format(source)
        assertEquals(once, JsFormatter.format(once))
    }

    @Test
    fun emptySourceStaysEmpty() {
        assertEquals("", JsFormatter.format(""))
        assertEquals("", JsFormatter.format("   \n\n"))
    }
}
