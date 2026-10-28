package com.evan.pulse.domain.script

/**
 * 轻量的 JavaScript 格式化器。
 *
 * 只重排空白与换行，不改写任何 token：字符串、模板字符串、正则字面量与注释都按原样
 * 保留，避免格式化把脚本改坏。缩进固定为两个空格。
 *
 * 换行保留策略：token 流里保留原始换行，只有在分号省略（ASI）会影响语义的位置才重新
 * 输出换行，例如 `return` 后面的换行、两个独立语句之间的换行。
 */
object JsFormatter {

    private const val INDENT = "  "

    fun format(source: String): String {
        val tokens = tokenize(source)
        if (tokens.isEmpty()) return ""

        val out = StringBuilder(source.length + source.length / 2)
        var indent = 0
        var parens = 0
        var brackets = 0
        var lineStart = true
        var last: Token? = null
        var index = 0
        // 待配对的问号：记录出现时的括号层与花括号层，用来判断冒号是三元还是对象/标签
        val pendingTernary = ArrayDeque<IntArray>()

        fun writeIndent() {
            if (!lineStart) return
            repeat(indent) { out.append(INDENT) }
            lineStart = false
        }

        fun trimTrailingSpace() {
            while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
        }

        fun space() {
            if (lineStart || out.isEmpty()) return
            if (out.last() != ' ' && out.last() != '\n') out.append(' ')
        }

        fun newline() {
            trimTrailingSpace()
            if (out.isEmpty()) return
            if (out.last() != '\n') out.append('\n')
            lineStart = true
        }

        fun put(text: String) {
            writeIndent()
            out.append(text)
        }

        fun putTight(text: String) {
            trimTrailingSpace()
            writeIndent()
            out.append(text)
        }

        fun nextSignificantIndex(from: Int): Int {
            var i = from
            while (i < tokens.size) {
                val kind = tokens[i].kind
                if (kind != TokenKind.NEWLINE &&
                    kind != TokenKind.LINE_COMMENT &&
                    kind != TokenKind.BLOCK_COMMENT
                ) {
                    return i
                }
                i++
            }
            return -1
        }

        fun nextSignificant(from: Int): Token? {
            val at = nextSignificantIndex(from)
            return if (at >= 0) tokens[at] else null
        }

        while (index < tokens.size) {
            val token = tokens[index]

            when (token.kind) {
                TokenKind.NEWLINE -> {
                    if (!lineStart && parens == 0 && brackets == 0 && last != null) {
                        val next = nextSignificant(index + 1)
                        if (last.kind == TokenKind.LINE_COMMENT || last.kind == TokenKind.BLOCK_COMMENT) {
                            newline()
                        } else if (breaksHere(last, next)) {
                            newline()
                        }
                    }
                    index++
                    continue
                }

                TokenKind.LINE_COMMENT -> {
                    if (!lineStart) space()
                    put(token.text)
                    last = token
                    index++
                    continue
                }

                TokenKind.BLOCK_COMMENT -> {
                    if (parens > 0 || brackets > 0) {
                        space()
                        put(token.text)
                        space()
                    } else {
                        if (!lineStart) space()
                        put(token.text)
                    }
                    last = token
                    index++
                    continue
                }

                else -> Unit
            }

            when (token.text) {
                "{" -> {
                    val closeIndex = nextSignificantIndex(index + 1)
                    if (closeIndex >= 0 && tokens[closeIndex].text == "}") {
                        if (spaceAfterValue(last)) space()
                        put("{}")
                        last = tokens[closeIndex]
                        index = closeIndex + 1
                        continue
                    }
                    if (spaceAfterValue(last)) space()
                    put("{")
                    indent++
                    newline()
                }

                "}" -> {
                    if (indent > 0) indent--
                    newline()
                    put("}")
                    val next = nextSignificant(index + 1)
                    if (next != null && next.text in blockJoiners) space()
                }

                ";" -> {
                    putTight(";")
                    if (parens == 0) newline() else space()
                }

                "(" -> {
                    if (spaceBeforeParen(last)) space()
                    put("(")
                    parens++
                }

                ")" -> {
                    if (parens > 0) parens--
                    putTight(")")
                }

                "[" -> {
                    if (spaceBeforeBracket(last)) space()
                    put("[")
                    brackets++
                }

                "]" -> {
                    if (brackets > 0) brackets--
                    putTight("]")
                }

                "," -> {
                    putTight(",")
                    space()
                }

                ".", "?." -> putTight(token.text)

                ":" -> {
                    val ternaryHere = pendingTernary.lastOrNull()
                        ?.let { it[0] == parens && it[1] == brackets && it[2] == indent } == true
                    if (ternaryHere) {
                        pendingTernary.removeLast()
                        space()
                        put(":")
                        space()
                    } else {
                        putTight(":")
                        space()
                    }
                }

                "?" -> {
                    pendingTernary.addLast(intArrayOf(parens, brackets, indent))
                    space()
                    put("?")
                    space()
                }

                "=>" -> {
                    space()
                    put("=>")
                    space()
                }

                "!", "~" -> {
                    if (spaceBeforeValue(last)) space()
                    put(token.text)
                }

                "++", "--" -> {
                    if (endsValue(last)) {
                        putTight(token.text)
                    } else {
                        if (spaceBeforeValue(last)) space()
                        put(token.text)
                    }
                }

                else -> {
                    when {
                        token.kind == TokenKind.IDENT && token.text in keywords -> {
                            if (spaceBeforeValue(last)) space()
                            put(token.text)
                        }

                        token.text in binaryOperators -> {
                            val unary = (token.text == "+" || token.text == "-") && !endsValue(last)
                            if (unary) {
                                if (spaceBeforeValue(last)) space()
                                put(token.text)
                            } else {
                                space()
                                put(token.text)
                                space()
                            }
                        }

                        else -> {
                            if (spaceBeforeValue(last)) space()
                            put(token.text)
                        }
                    }
                }
            }
            last = token
            index++
        }

        val result = out.toString().trimEnd()
        return if (result.isEmpty()) "" else "$result\n"
    }

    private fun breaksHere(last: Token, next: Token?): Boolean {
        if (last.kind == TokenKind.IDENT && last.text in lineBreakingKeywords) {
            return next == null || next.text !in continuationTokens
        }
        if (!endsValue(last)) return false
        if (next == null) return true
        if (next.text == "{") return last.text != ")"
        return next.text !in continuationTokens
    }

    private fun endsValue(token: Token?): Boolean {
        if (token == null) return false
        return when (token.kind) {
            TokenKind.NUMBER, TokenKind.STRING, TokenKind.TEMPLATE, TokenKind.REGEX -> true
            TokenKind.IDENT -> token.text !in keywords || token.text in valueKeywords
            TokenKind.PUNCT -> token.text in setOf(")", "]", "}", "++", "--")
            else -> false
        }
    }

    private fun spaceAfterValue(last: Token?): Boolean {
        val text = last?.text ?: return false
        return text !in setOf("(", "[", "{", "=", ":", "=>", "!", "~", "?", "...", ".", "?.")
    }

    private fun spaceBeforeValue(last: Token?): Boolean {
        val text = last?.text ?: return false
        return text !in setOf("(", "[", "{", ".", "?.", "!", "~", "++", "--", "...")
    }

    private fun spaceBeforeParen(last: Token?): Boolean {
        val text = last?.text ?: return false
        if (text in controlKeywords) return true
        return text in setOf(
            "function", "return", "typeof", "void", "delete", "new",
            "yield", "await", "case", "in", "of", "instanceof",
        )
    }

    private fun spaceBeforeBracket(last: Token?): Boolean {
        val text = last?.text ?: return false
        return text in setOf("return", "typeof", "case", "in", "of", "instanceof", "void", "delete")
    }

    private val controlKeywords = setOf("if", "for", "while", "switch", "catch", "with")

    private val blockJoiners = setOf("else", "catch", "finally")

    private val lineBreakingKeywords = setOf("return", "break", "continue", "throw", "yield", "debugger")

    private val valueKeywords = setOf("this", "super", "null", "true", "false", "undefined")

    private val keywords = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break",
        "continue", "return", "throw", "try", "catch", "finally", "function", "class",
        "extends", "super", "new", "delete", "typeof", "instanceof", "in", "of", "void",
        "var", "let", "const", "this", "null", "true", "false", "undefined", "yield",
        "await", "async", "static", "get", "set", "import", "export", "from", "as",
    )

    private val binaryOperators = setOf(
        "=", "+", "-", "*", "/", "%", "**", "==", "===", "!=", "!==", "<", ">",
        "<=", ">=", "&&", "||", "??", "&", "|", "^", "<<", ">>", ">>>", "+=", "-=",
        "*=", "/=", "%=", "**=", "&=", "|=", "^=", "<<=", ">>=", ">>>=",
    )

    private val continuationTokens: Set<String> = buildSet {
        addAll(binaryOperators)
        addAll(
            listOf(
                ".", "?.", ";", ",", ")", "]", "}", "(", "[", "?", ":", "=>",
                "else", "catch", "finally",
            )
        )
    }

    private fun tokenize(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        var lastSignificant: Token? = null

        fun add(kind: TokenKind, text: String) {
            val token = Token(kind, text)
            tokens.add(token)
            if (kind != TokenKind.LINE_COMMENT &&
                kind != TokenKind.BLOCK_COMMENT &&
                kind != TokenKind.NEWLINE
            ) {
                lastSignificant = token
            }
        }

        while (i < source.length) {
            val c = source[i]
            when {
                c == '\n' || c == '\r' -> {
                    if (tokens.lastOrNull()?.kind != TokenKind.NEWLINE) {
                        add(TokenKind.NEWLINE, "\n")
                    }
                    i++
                }

                c.isWhitespace() -> i++

                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    val start = i
                    while (i < source.length && source[i] != '\n') i++
                    add(TokenKind.LINE_COMMENT, source.substring(start, i))
                }

                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    val start = i
                    i += 2
                    while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(source.length)
                    add(TokenKind.BLOCK_COMMENT, source.substring(start, i))
                }

                c == '\'' || c == '"' -> {
                    val start = i
                    val quote = c
                    i++
                    while (i < source.length) {
                        if (source[i] == '\\') i += 2
                        else if (source[i] == quote) {
                            i++
                            break
                        } else i++
                    }
                    add(TokenKind.STRING, source.substring(start, i.coerceAtMost(source.length)))
                }

                c == '`' -> {
                    val start = i
                    i++
                    while (i < source.length) {
                        if (source[i] == '\\') i += 2
                        else if (source[i] == '`') {
                            i++
                            break
                        } else i++
                    }
                    add(TokenKind.TEMPLATE, source.substring(start, i.coerceAtMost(source.length)))
                }

                c.isDigit() || (c == '.' && i + 1 < source.length && source[i + 1].isDigit()) -> {
                    val start = i
                    while (i < source.length) {
                        val ch = source[i]
                        if (ch.isLetterOrDigit() || ch == '.' || ch == '_') {
                            i++
                        } else if ((ch == '+' || ch == '-') && i > start &&
                            (source[i - 1] == 'e' || source[i - 1] == 'E')
                        ) {
                            i++
                        } else break
                    }
                    add(TokenKind.NUMBER, source.substring(start, i))
                }

                c.isLetter() || c == '_' || c == '$' -> {
                    val start = i
                    while (i < source.length &&
                        (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '$')
                    ) {
                        i++
                    }
                    add(TokenKind.IDENT, source.substring(start, i))
                }

                c == '/' && regexAllowed(lastSignificant?.text) -> {
                    val start = i
                    i++
                    var inClass = false
                    while (i < source.length) {
                        val ch = source[i]
                        if (ch == '\\') {
                            i += 2
                            continue
                        }
                        if (ch == '[') inClass = true
                        else if (ch == ']') inClass = false
                        else if (ch == '/' && !inClass) {
                            i++
                            while (i < source.length && source[i].isLetter()) i++
                            break
                        } else if (ch == '\n') {
                            break
                        }
                        i++
                    }
                    add(TokenKind.REGEX, source.substring(start, i.coerceAtMost(source.length)))
                }

                else -> {
                    val punctuator = punctuators.firstOrNull { source.startsWith(it, i) } ?: c.toString()
                    add(TokenKind.PUNCT, punctuator)
                    i += punctuator.length
                }
            }
        }
        return tokens
    }

    private fun regexAllowed(last: String?): Boolean {
        if (last == null) return true
        if (last in regexPrefixKeywords) return true
        return last in regexPrefixPunctuators
    }

    private val regexPrefixKeywords = setOf(
        "return", "typeof", "instanceof", "in", "of", "new", "delete", "void",
        "case", "do", "else", "yield", "await",
    )

    private val regexPrefixPunctuators = setOf(
        "(", "[", "{", ",", ";", ":", "=", "==", "===", "!=", "!==", "+", "-", "*",
        "%", "**", "!", "~", "&&", "||", "??", "?", "<", ">", "<=", ">=", "=>", "&",
        "|", "^", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", ">>>",
        "<<=", ">>=", ">>>=",
    )

    private val punctuators = listOf(
        ">>>=", "===", "!==", "**=", ">>>", "<<=", ">>=", "...", "?.", "??", "=>",
        "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=", "**", "<<", ">>", "{", "}", "(", ")", "[", "]", ";", ",",
        "<", ">", "+", "-", "*", "/", "%", "=", "!", "&", "|", "^", "~", "?", ":", ".",
    )

    private enum class TokenKind {
        IDENT, NUMBER, STRING, TEMPLATE, REGEX, LINE_COMMENT, BLOCK_COMMENT, PUNCT, NEWLINE
    }

    private data class Token(val kind: TokenKind, val text: String)
}
