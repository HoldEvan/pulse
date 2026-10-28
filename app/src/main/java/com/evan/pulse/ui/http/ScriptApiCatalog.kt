package com.evan.pulse.ui.http

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.evan.pulse.R

/**
 * 脚本编辑器的一条内置补全项。
 *
 * [path] 是可被前缀匹配的完整成员路径；[detail] 展示调用签名或简短说明；
 * [detailRes] 是可选的多语言说明资源（优先于 [detail] 展示）；
 * [insert] 是选中后真正写入文本的内容；[cursorBack] 表示插入后光标回退的字符数，
 * 用于把光标停在一对圆括号中间。
 */
data class ScriptApiToken(
    val path: String,
    val detail: String = "",
    @StringRes val detailRes: Int = 0,
    val insert: String = path,
    val cursorBack: Int = 0,
    /** 方法调用（展示为方法图标），false 表示变量/属性 */
    val isMethod: Boolean = false,
)

/** 补全项的展示说明：多语言资源优先，其次静态签名文本 */
@Composable
fun ScriptApiToken.detailText(): String =
    if (detailRes != 0) stringResource(detailRes) else detail

/**
 * pm 脚本运行时暴露的内置变量与函数清单，供代码编辑器做补全提示。
 *
 * 清单与 [com.evan.pulse.domain.script.RhinoScriptExecutor] 里 buildPm 手工搭建的
 * 原生对象保持一致，改动 pm 的 API 时需要同步这里。
 */
object ScriptApiCatalog {

    private const val MAX_SUGGESTIONS = 40

    val tokens: List<ScriptApiToken> = buildList {
        add(prop("pm", detailRes = R.string.script_api_pm_root))

        add(prop("pm.request"))
        add(prop("pm.request.method"))
        add(prop("pm.request.query"))
        add(prop("pm.request.url"))
        add(prop("pm.request.url.full"))
        add(fn("pm.request.url.getFull", "getFull()"))
        add(fn("pm.request.url.getScheme", "getScheme()"))
        add(fn("pm.request.url.getHostname", "getHostname()"))
        add(fn("pm.request.url.getPath", "getPath()"))
        add(fn("pm.request.url.getPathname", "getPathname()"))
        add(fn("pm.request.url.getHash", "getHash()"))
        add(fn("pm.request.url.getQuery", "getQuery()"))
        add(fn("pm.request.url.getCredentials", "getCredentials()"))
        add(fn("pm.request.url.getPort", "getPort()"))
        add(fn("pm.request.url.getHost", "getHost()"))
        add(fn("pm.request.url.setQuery", "setQuery(query)"))
        add(prop("pm.request.url.query.count"))
        add(fn("pm.request.url.query.get", "get(name)"))
        add(fn("pm.request.url.query.has", "has(name)"))
        add(fn("pm.request.url.query.add", "add(name, value)"))
        add(fn("pm.request.url.query.remove", "remove(name)"))
        add(fn("pm.request.url.query.toQuery", "toQuery()"))
        add(fn("pm.request.url.query.toObject", "toObject()"))
        add(prop("pm.request.headers"))
        add(prop("pm.request.headers.count"))
        add(fn("pm.request.headers.get", "get(name)"))
        add(fn("pm.request.headers.has", "has(name)"))
        add(fn("pm.request.headers.each", "each(fn)"))
        add(fn("pm.request.headers.add", "add(name, value)"))
        add(fn("pm.request.headers.remove", "remove(name)"))
        add(prop("pm.request.body"))
        add(prop("pm.request.body.mode"))
        add(prop("pm.request.body.text"))
        add(prop("pm.request.body.json"))
        add(fn("pm.request.getHeaders", "getHeaders()"))
        add(fn("pm.request.getHeader", "getHeader(name)"))
        add(fn("pm.request.setHeader", "setHeader(name, value)"))
        add(fn("pm.request.getBody", "getBody()"))
        add(fn("pm.request.setMethod", "setMethod(method)"))
        add(fn("pm.request.setUrl", "setUrl(url)"))

        add(prop("pm.variables"))
        add(prop("pm.variables.count"))
        add(fn("pm.variables.get", "get(key)"))
        add(fn("pm.variables.has", "has(key)"))
        add(fn("pm.variables.set", "set(key, value)"))
        add(fn("pm.variables.delete", "delete(key)"))
        add(fn("pm.variables.unshift", "unshift(key, value, persist)"))
        add(fn("pm.variables.all", "all()"))

        add(prop("pm.environment", detailRes = R.string.script_api_scope_alias))
        add(fn("pm.environment.get", "get(key)"))
        add(fn("pm.environment.set", "set(key, value)"))
        add(prop("pm.globals", detailRes = R.string.script_api_scope_alias))
        add(fn("pm.globals.get", "get(key)"))
        add(fn("pm.globals.set", "set(key, value)"))

        add(prop("pm.iteration"))
        add(prop("pm.iterationCount"))

        add(prop("pm.response", detailRes = R.string.script_api_response_post_only))
        add(prop("pm.response.statusCode"))
        add(prop("pm.response.status"))
        add(prop("pm.response.headers"))
        add(fn("pm.response.headers.get", "get(name)"))
        add(fn("pm.response.headers.has", "has(name)"))
        add(fn("pm.response.headers.each", "each(fn)"))
        add(prop("pm.response.size"))
        add(prop("pm.response.time"))
        add(prop("pm.response.duration"))
        add(prop("pm.response.contentType"))
        add(fn("pm.response.text", "text()"))
        add(fn("pm.response.json", "json()"))
        add(fn("pm.response.jsonp", "jsonp(key)"))
        add(fn("pm.response.isJson", "isJson()"))
        add(prop("pm.response.to().json", "to().json()", insert = "pm.response.to().json()", isMethod = true))
        add(prop("pm.response.to().text", "to().text()", insert = "pm.response.to().text()", isMethod = true))

        add(fn("pm.test", "test(name, fn)"))
    }

    /**
     * 原生 JS 对象的成员，只在输入 `console.`、`JSON.` 这类带点前缀时提示；
     * 裸词补全只列对象名，避免一上来就塞满成员。
     *
     * 方法一律走 [fn]：插入 `name()` 并让光标停在括号内，省得再手动补括号。
     */
    val memberTokens: List<ScriptApiToken> = listOf(
        fn("console.log", "log(...args)"),
        fn("console.info", "info(...args)"),
        fn("console.warn", "warn(...args)"),
        fn("console.error", "error(...args)"),
        fn("console.debug", "debug(...args)"),
        fn("JSON.parse", "parse(text, reviver)"),
        fn("JSON.stringify", "stringify(value, replacer, space)"),
        fn("Math.abs", "abs(x)"),
        fn("Math.ceil", "ceil(x)"),
        fn("Math.floor", "floor(x)"),
        fn("Math.round", "round(x)"),
        fn("Math.trunc", "trunc(x)"),
        fn("Math.max", "max(...values)"),
        fn("Math.min", "min(...values)"),
        fn("Math.pow", "pow(base, exponent)"),
        fn("Math.sqrt", "sqrt(x)"),
        fn("Math.random", "random()"),
        fn("Object.keys", "keys(obj)"),
        fn("Object.values", "values(obj)"),
        fn("Object.entries", "entries(obj)"),
        fn("Object.assign", "assign(target, ...sources)"),
        fn("Object.freeze", "freeze(obj)"),
        fn("Object.hasOwn", "hasOwn(obj, key)"),
        fn("Array.isArray", "isArray(value)"),
        fn("Array.from", "from(iterable)"),
        fn("Array.of", "of(...items)"),
        fn("String.fromCharCode", "fromCharCode(...codes)"),
        fn("Number.isInteger", "isInteger(value)"),
        fn("Number.isNaN", "isNaN(value)"),
        fn("Number.isFinite", "isFinite(value)"),
        fn("Number.parseInt", "parseInt(str, radix)"),
        fn("Number.parseFloat", "parseFloat(str)"),
        fn("Date.now", "now()"),
    )

    /**
     * 按当前正在输入的词元过滤补全项。
     *
     * 含 `.` 的词元按完整路径前缀匹配（pm API 加原生 `console.` / `JSON.` 成员）；
     * 不含 `.` 的裸词元额外按最后一段匹配，这样输入 `set` 也能提示出
     * `pm.request.setHeader` 等函数。
     *
     * [source] 是编辑器里的脚本全文：裸词元还会合并 JavaScript 内置全局名与脚本里
     * 通过 `function` / `var` / `let` / `const` / 赋值定义的名字，让使用者自己的
     * 函数和变量也能被补全。
     */
    fun match(token: String, source: CharSequence = ""): List<ScriptApiToken> {
        val lower = token.lowercase()
        if (lower.isEmpty()) return emptyList()
        val dot = lower.contains('.')
        val pool = if (dot) tokens + memberTokens else tokens
        val matched = pool.asSequence()
            .filter { it.path != token }
            .filter { candidate ->
                val path = candidate.path.lowercase()
                if (dot) {
                    path.startsWith(lower)
                } else {
                    path.startsWith(lower) || path.substringAfterLast('.').startsWith(lower)
                }
            }
            .toList()
        if (dot) return matched.take(MAX_SUGGESTIONS)

        val seen = matched.mapTo(HashSet()) { it.path }
        val extra = ArrayList<ScriptApiToken>()
        for (global in globals) {
            if (!global.path.startsWith(token) || !seen.add(global.path)) continue
            extra += global
        }
        for (declared in declaredNames(source)) {
            if (!declared.name.startsWith(token) || !seen.add(declared.name)) continue
            extra += ScriptApiToken(
                path = declared.name,
                insert = declared.name,
                isMethod = declared.isMethod
            )
        }
        return (matched + extra).take(MAX_SUGGESTIONS)
    }

    /** JavaScript 运行时内置的全局对象与函数，只在输入裸词元时补充 */
    private val globals: List<ScriptApiToken> = listOf(
        prop("console"),
        prop("Math"),
        prop("JSON"),
        prop("Object"),
        prop("Array"),
        prop("String"),
        prop("Number"),
        prop("Boolean"),
        prop("Date"),
        prop("RegExp"),
        prop("Map"),
        prop("Set"),
        fn("parseInt", "parseInt(str, radix)"),
        fn("parseFloat", "parseFloat(str)"),
        fn("isNaN", "isNaN(value)"),
        fn("isFinite", "isFinite(value)"),
        fn("encodeURIComponent", "encodeURIComponent(str)"),
        fn("decodeURIComponent", "decodeURIComponent(str)"),
    )

    private class DeclaredName(val name: String, val isMethod: Boolean)

    /**
     * 脚本里定义的名字：`function foo`、`var/let/const foo`，以及 `foo = ...` 赋值。
     *
     * 只认声明位置，避免把 `pm.request.method` 这类成员访问当成可补全变量。
     */
    private fun declaredNames(source: CharSequence): List<DeclaredName> {
        val result = LinkedHashMap<String, DeclaredName>()
        for (m in declarationPattern.findAll(source)) {
            val name = m.groupValues[1].ifEmpty {
                m.groupValues[2].ifEmpty { m.groupValues[3] }
            }
            if (name.isEmpty() || name in reservedWords || result.containsKey(name)) continue
            val isMethod = m.groupValues[1].isNotEmpty() ||
                isFunctionAssignment(source, m.range.last + 1)
            result[name] = DeclaredName(name, isMethod)
        }
        return result.values.toList()
    }

    private fun isFunctionAssignment(source: CharSequence, from: Int): Boolean {
        val rhsEnd = minOf(source.length, from + 80)
        val rhs = source.subSequence(from, rhsEnd).toString()
            .substringAfter('=')
            .takeWhile { it != ';' && it != '\n' }
            .trim()
        return rhs.startsWith("function") || rhs.contains("=>")
    }

    private val declarationPattern = Regex(
        """function\s+([A-Za-z_$][\w$]*)|(?:var|let|const)\s+([A-Za-z_$][\w$]*)|(?<![\w$.])([A-Za-z_$][\w$]*)\s*=(?![=>])"""
    )

    private val reservedWords = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
        "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this",
        "throw", "try", "typeof", "var", "void", "while", "with", "yield",
        "true", "false", "null", "undefined",
    )

    private fun prop(
        path: String,
        detail: String = "",
        @StringRes detailRes: Int = 0,
        insert: String = path,
        isMethod: Boolean = false,
    ): ScriptApiToken = ScriptApiToken(
        path = path,
        detail = detail,
        detailRes = detailRes,
        insert = insert,
        isMethod = isMethod
    )

    private fun fn(path: String, signature: String): ScriptApiToken =
        ScriptApiToken(path = path, detail = signature, insert = "$path()", cursorBack = 1, isMethod = true)
}
