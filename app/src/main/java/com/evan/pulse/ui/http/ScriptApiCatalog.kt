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

        add(prop("pm.console"))
        add(fn("pm.console.log", "log(...args)"))
        add(fn("pm.console.warn", "warn(...args)"))
        add(fn("pm.console.error", "error(...args)"))
        add(fn("pm.log", "log(...args)"))

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
        add(prop("pm.response.to().json", "to().json()", insert = "pm.response.to().json()"))
        add(prop("pm.response.to().text", "to().text()", insert = "pm.response.to().text()"))

        add(fn("pm.test", "test(name, fn)"))
    }

    /**
     * 按当前正在输入的词元过滤补全项。
     *
     * 含 `.` 的词元按完整路径前缀匹配；不含 `.` 的裸词元额外按最后一段匹配，
     * 这样输入 `set` 也能提示出 `pm.request.setHeader` 等函数。
     */
    fun match(token: String): List<ScriptApiToken> {
        val lower = token.lowercase()
        if (lower.isEmpty()) return emptyList()
        val dot = lower.contains('.')
        return tokens.asSequence()
            .filter { it.path != token }
            .filter { candidate ->
                val path = candidate.path.lowercase()
                if (dot) {
                    path.startsWith(lower)
                } else {
                    path.startsWith(lower) || path.substringAfterLast('.').startsWith(lower)
                }
            }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    private fun prop(
        path: String,
        detail: String = "",
        @StringRes detailRes: Int = 0,
        insert: String = path,
    ): ScriptApiToken = ScriptApiToken(path = path, detail = detail, detailRes = detailRes, insert = insert)

    private fun fn(path: String, signature: String): ScriptApiToken =
        ScriptApiToken(path = path, detail = signature, insert = "$path()", cursorBack = 1)
}
