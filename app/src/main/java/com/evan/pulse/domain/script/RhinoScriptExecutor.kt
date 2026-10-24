package com.evan.pulse.domain.script

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.mozilla.javascript.Callable as JsCallable
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.ErrorReporter
import org.mozilla.javascript.IdFunctionCall
import org.mozilla.javascript.IdFunctionObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.concurrent.Callable as FutureCallable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Supplier

/** 脚本执行的硬超时：解释执行没法中途打断，只能放弃这个线程 */
private const val SCRIPT_TIMEOUT_MS = 5_000L

private const val SCRIPT_FILE_NAME = "pulse-script"

/** 只放行本包，脚本永远摸不到 Kotlin 类 */
private class PulseClassShutter : ClassShutter {
    override fun visibleToScripts(className: String): Boolean = className.startsWith("com.evan.pulse.")
}

/** 收集解释器抛出的警告和错误 */
private class CollectorReporter : ErrorReporter {
    val messages = mutableListOf<String>()

    override fun warning(message: String, filename: String, line: Int, lineSource: String, lineOffset: Int) {
        messages += "warning: ${message.trim()}"
    }

    override fun error(message: String, filename: String, line: Int, lineSource: String, lineOffset: Int) {
        messages += "error: ${message.trim()}"
    }

    override fun runtimeError(
        message: String,
        filename: String,
        line: Int,
        lineSource: String,
        lineOffset: Int
    ): EvaluatorException {
        messages += "error: ${message.trim()}"
        return EvaluatorException(message, filename, line, lineSource, lineOffset)
    }
}

/**
 * 基于 Mozilla Rhino 的脚本执行器。
 *
 * 只走解释执行（optimizationLevel = -1），不触发 ASM 编译，省掉字节码生成链路，
 * 也让 R8 能更彻底地收缩。脚本跑在独立线程里，用 Future 拿结果并施加超时。
 *
 * pm 层刻意不用 Rhino 的 Java 桥：把 Kotlin 类交给 Rhino 反射，解释器自己会去
 * 加载 java.lang 的类型，既慢又绕过 ClassShutter。这里全部用原生 JS 对象加
 * IdFunctionObject 手动搭，反射面完全由 pm 的 API 决定。
 */
class RhinoScriptExecutor : ScriptExecutor {

    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "pulse-script").apply { isDaemon = true }
    }

    override fun runPre(
        script: String,
        request: MutableRequest,
        sandbox: ScriptSession
    ): ScriptRunResult = run(script, sandbox) { cx, scope ->
        buildPm(cx, scope, request, sandbox, response = null)
    }

    override fun runPost(
        script: String,
        request: HttpCallConfig,
        outcome: HttpCallOutcome,
        sandbox: ScriptSession
    ): ScriptRunResult = run(script, sandbox) { cx, scope ->
        val mutable = MutableRequest(
            method = request.method.value,
            url = request.url,
            headers = request.headers.map { ScriptKv(it.key, it.value) }.toMutableList(),
            params = request.queryParams.map { ScriptKv(it.key, it.value) }.toMutableList(),
            bodyType = request.bodyType,
            bodyText = request.body,
            formFields = request.formFields.map { ScriptForm(it.name, it.value) }.toMutableList()
        )
        buildPm(cx, scope, mutable, sandbox, response = responseView(cx, scope, sandbox, outcome))
    }

    private fun run(
        script: String,
        sandbox: ScriptSession,
        buildPm: (Context, Scriptable) -> Scriptable
    ): ScriptRunResult {
        if (script.isBlank()) return ScriptRunResult.Skipped
        val future = pool.submit(FutureCallable<ScriptRunResult> { execute(script, sandbox, buildPm) })
        return try {
            future.get(SCRIPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            ScriptRunResult.Failed(
                "脚本执行超时（超过 ${SCRIPT_TIMEOUT_MS}ms）",
                sandbox.console.toList(),
                sandbox.tests.toList()
            )
        } catch (e: ExecutionException) {
            ScriptRunResult.Failed(
                "脚本执行线程异常: ${describeThrowable(e.cause)}",
                sandbox.console.toList(),
                sandbox.tests.toList()
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            ScriptRunResult.Failed(
                "脚本执行被中断",
                sandbox.console.toList(),
                sandbox.tests.toList()
            )
        }
    }

    private fun execute(
        script: String,
        sandbox: ScriptSession,
        buildPm: (Context, Scriptable) -> Scriptable
    ): ScriptRunResult {
        val cx = Context.enter()
        val reporter = CollectorReporter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.setErrorReporter(reporter)
            cx.setClassShutter(PulseClassShutter())
            val scope = cx.initStandardObjects()
            scope.defineProperty("pm", buildPm(cx, scope), 0)
            cx.evaluateString(scope, script, SCRIPT_FILE_NAME, 1, null)
        } catch (t: Throwable) {
            return ScriptRunResult.Failed(
                describeFailure(t, reporter),
                sandbox.console.toList(),
                sandbox.tests.toList()
            )
        } finally {
            Context.exit()
        }
        return ScriptRunResult.Ok(sandbox.console.toList(), sandbox.tests.toList())
    }

    /** 把异常连同根因链拼成可读文本，便于定位静态初始化失败等隐藏根因 */
private fun describeThrowable(error: Throwable?): String {
    if (error == null) return "未知错误"
    val chain = mutableListOf<String>()
    var cur: Throwable? = error
    while (cur != null) {
        val label = if (cur is ExceptionInInitializerError) "初始器错误" else cur.javaClass.simpleName
        chain += "$label: ${cur.message ?: "(无信息)"}"
        cur = cur.cause
    }
    return chain.joinToString(" <- ")
}

private fun describeFailure(error: Throwable, reporter: CollectorReporter): String {
        val lines = mutableListOf<String>()
        readJsStack(error)?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += it }
        lines += reporter.messages
        if (lines.isEmpty()) lines += "${error.javaClass.simpleName}: ${error.message ?: "script error"}"
        return lines.joinToString("\n")
    }

    private fun readJsStack(error: Throwable): String? {
        if (error !is Scriptable) return null
        return runCatching {
            val raw = error.get("stack", error)
            raw?.takeIf { value -> !Undefined.isUndefined(value) }?.toString()
        }.getOrNull()
    }
}

/** JS 值构造器与原生对象装配器。Context 是线程私有的，所以每次都用 getCurrentContext 取 */
private class PmBuilder(val cx: Context, val scope: Scriptable) {

    fun node(): PmNode = PmNode(this)

    fun js(value: Any?): Any = when (value) {
        null -> Undefined.SCRIPTABLE_UNDEFINED
        is NativeArray -> value.also { it.setParentScope(scope) }
        else -> value
    }

    fun fn(name: String, handler: (Array<Any?>) -> Any?): JsCallable =
        IdFunctionObject(
            IdFunctionCall { function, c, s, thisObj, rawArgs ->
                val args: Array<Any?> = rawArgs ?: emptyArray()
                js(handler(args))
            },
            scope, -1, name, 0, scope
        )

    fun newObject(): ScriptableObject =
        (cx.newObject(scope) as ScriptableObject).also { it.setPrototype(scope) }

    fun jsonValue(element: JsonElement): Any = when (element) {
        is JsonObject -> newObject().also { obj ->
            for ((key, value) in element) obj.defineProperty(key, jsonValue(value), 0)
        }
        is JsonArray -> NativeArray(
            element.map { value -> jsonValue(value) }.toTypedArray()
        ).also { it.setParentScope(scope) }
        is JsonPrimitive -> when {
            element is JsonNull -> Undefined.SCRIPTABLE_UNDEFINED
            element.isString -> element.content
            else -> parseNumberOrUndefined(element.content)
        }
    }
}

/** 一个 pm 子对象：固定值、可读写属性和函数都挂在同一个原生 JS 对象上 */
private class PmNode(private val b: PmBuilder) {

    val self: ScriptableObject = b.newObject()

    fun value(name: String, source: () -> Any?) {
        self.defineProperty(name, b.js(source()), 0)
    }

    fun prop(name: String, setter: ((Any?) -> Unit)? = null, getter: () -> Any?) {
        self.defineProperty(
            name,
            Supplier<Any> { b.js(getter()) },
            Consumer<Any> { raw -> setter?.invoke(raw) },
            0
        )
    }

    fun method(name: String, handler: (Array<Any?>) -> Any?) {
        self.defineProperty(name, b.fn(name, handler), 0)
    }
}

private fun arg(args: Array<Any?>, index: Int): Any? = args.getOrNull(index)

private val scriptJson = Json { ignoreUnknownKeys = true }

private fun toStr(value: Any?): String = when (value) {
    null -> ""
    is Undefined -> ""
    is Scriptable -> runCatching { Context.toString(value) }.getOrDefault(value.toString())
    is Double -> toJsNumber(value)
    is Float -> toJsNumber(value.toDouble())
    else -> value.toString()
}

/**
 * pm.console 里打印对象时按 JSON 展开，方便查看响应/请求结构。
 * [toStr] 对 Scriptable 走 Context.toString，对象只会输出 [object Object]；
 * 这里在对象场景换成递归序列化。普通流程仍走 Context.toString，避免改动语义。
 */
private fun consoleStr(value: Any?): String = when (value) {
    null, is Undefined -> ""
    is Scriptable -> scriptableJson(value, mutableSetOf())
    is Boolean -> value.toString()
    is Double, is Float -> toJsNumber((value as Number).toDouble())
    else -> toStr(value)
}

private fun scriptableJson(value: Scriptable, visiting: MutableSet<Scriptable>): String {
    if (value is NativeArray) {
        if (!visiting.add(value)) return "[Circular]"
        val items = buildList {
            for (i in 0 until value.length.toInt()) {
                val item = value.get(i, value)
                add(
                    when {
                        item is Undefined || item == null -> "null"
                        item is Scriptable -> scriptableJson(item, visiting)
                        else -> jsonScalar(item)
                    }
                )
                if (i >= MAX_JSON_NODES - 1) { add("…"); break }
            }
        }
        visiting.remove(value)
        return "[${items.joinToString(", ")}]"
    }
    if (value !is ScriptableObject) return toStr(value)
    if (!visiting.add(value)) return "[Circular]"
    val ids = value.ids
    val parts = ArrayList<String>(ids.size.coerceAtMost(MAX_JSON_NODES))
    var added = 0
    for (i in 0 until ids.size) {
        if (added >= MAX_JSON_NODES) { parts += "\"…\""; break }
        val key = ids[i].toString()
        val item = value.get(key, value)
        if (item is JsCallable) continue
        val rendered = when {
            item is Undefined || item == null -> "null"
            item is Scriptable -> scriptableJson(item, visiting)
            else -> jsonScalar(item)
        }
        parts += "\"$key\": $rendered"
        added++
    }
    visiting.remove(value)
    return "{${parts.joinToString(", ")}}"
}

private fun jsonScalar(value: Any): String = when (value) {
    is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    is Boolean -> value.toString()
    is Double, is Float -> toJsNumber((value as Number).toDouble())
    is Int, is Long -> value.toString()
    else -> "\"$value\""
}

private const val MAX_JSON_NODES = 200

/** JS 数字过桥是 Double，整数值不该被显示成 3.0 */
private fun toJsNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val range = Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()
    if (value in range && value % 1.0 == 0.0) return value.toLong().toString()
    return value.toString()
}

private fun toNumber(value: Any?): Double = when (value) {
    null -> 0.0
    is Undefined -> Double.NaN
    is Number -> value.toDouble()
    is Boolean -> if (value) 1.0 else 0.0
    else -> runCatching { Context.toString(value).trim().toDoubleOrNull() }.getOrNull() ?: Double.NaN
}

private fun toBool(value: Any?): Boolean = when (value) {
    null -> false
    is Undefined -> false
    is Boolean -> value
    is Number -> value.toDouble() != 0.0
    is String -> value.trim().equals("true", ignoreCase = true)
    else -> runCatching { Context.toBoolean(value) }.getOrDefault(false)
}

/** 解析 JS 端传过来的对象，写回 Kotlin JSON 时用 */
private fun toJsObject(value: Any?): JsonElement = when {
    value == null -> JsonNull
    Undefined.isUndefined(value) -> JsonNull
    value is Scriptable -> toJsonObject(value)
    value is String -> JsonPrimitive(value)
    value is Boolean -> JsonPrimitive(value)
    value is Number -> JsonPrimitive(value.toLong())
    else -> JsonPrimitive(Context.toString(value))
}

private fun toJsonObject(value: Scriptable): JsonElement {
    if (value is NativeArray) {
        val items = ArrayList<JsonElement>(value.size)
        for (i in 0 until value.size) items += toJsObject(value[i])
        return JsonArray(items)
    }
    if (value is ScriptableObject) {
        val entries = LinkedHashMap<String, JsonElement>()
        for (id in value.ids.orEmpty()) {
            val name = id.toString()
            if (name.isEmpty()) continue
            entries[name] = toJsObject(value.get(name, value))
        }
        return JsonObject(entries)
    }
    return JsonPrimitive(Context.toString(value))
}

private fun parseJson(text: String): JsonElement? =
    runCatching { scriptJson.parseToJsonElement(text) }.getOrNull()

/** JSON 数字节点转 JS 值：整数用 long，其余按双精度 */
private fun parseNumberOrUndefined(text: String): Any {
    val trimmed = text.trim()
    if (trimmed == "true") return true
    if (trimmed == "false") return false
    trimmed.toLongOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it }
    return Undefined.SCRIPTABLE_UNDEFINED
}

/** 解析后的 URL 各段，供 pm.request.url 读取 */
private data class ParsedUrl(
    val scheme: String,
    val username: String,
    val password: String,
    val hostname: String,
    val port: String,
    val path: String,
    val query: String,
    val pairs: List<ScriptKv>,
    val hash: String
) {
    companion object {
        fun parse(raw: String): ParsedUrl {
            var rest = raw
            var scheme = ""
            val schemeEnd = rest.indexOf("://")
            if (schemeEnd > 0) {
                scheme = rest.substring(0, schemeEnd)
                rest = rest.substring(schemeEnd + 3)
            }

            var username = ""
            var password = ""
            val slash = rest.indexOf('/')
            var hostPart = rest
            if (slash >= 0) {
                hostPart = rest.substring(0, slash)
                rest = rest.substring(slash)
            }
            val at = hostPart.lastIndexOf('@')
            if (at >= 0) {
                val cred = hostPart.substring(0, at)
                hostPart = hostPart.substring(at + 1)
                val colon = cred.indexOf(':')
                username = if (colon >= 0) cred.substring(0, colon) else cred
                password = if (colon >= 0) cred.substring(colon + 1) else ""
            }

            var hostname = hostPart
            var port = ""
            val bracket = hostname.indexOf(']')
            val colon = if (bracket >= 0) {
                val after = hostname.indexOf(':', bracket)
                if (after < 0) -1 else after
            } else {
                hostname.lastIndexOf(':')
            }
            if (colon >= 0) {
                hostname = hostname.substring(0, colon)
                port = hostPart.substring(colon + 1)
            }

            var path = rest
            var query = ""
            var hash = ""
            val h = path.indexOf('#')
            if (h >= 0) {
                hash = path.substring(h + 1)
                path = path.substring(0, h)
            }
            val q = path.indexOf('?')
            if (q >= 0) {
                query = path.substring(q + 1)
                path = path.substring(0, q)
            }
            val pairs = query.split('&')
                .filter { it.isNotBlank() }
                .map { pair ->
                    val eq = pair.indexOf('=')
                    if (eq >= 0) ScriptKv(pair.substring(0, eq), pair.substring(eq + 1))
                    else ScriptKv(pair, "")
                }
            return ParsedUrl(scheme, username, password, hostname, port, path, query, pairs, hash)
        }
    }
}

/** 重建 URL：只改 query 段，其余部分原样保留 */
private fun replaceQuery(raw: String, query: String): String {
    val parsed = ParsedUrl.parse(raw)
    val base = buildString {
        if (parsed.scheme.isNotEmpty()) append(parsed.scheme).append("://")
        val cred = listOf(parsed.username, parsed.password).filter { it.isNotEmpty() }.joinToString(":")
        if (cred.isNotEmpty()) append(cred).append("@")
        if (parsed.hostname.isNotEmpty()) append(parsed.hostname)
        if (parsed.port.isNotEmpty()) append(':').append(parsed.port)
        append(parsed.path)
    }
    val next = query.trim().removePrefix("?")
    return if (next.isEmpty()) base else "$base?$next"
}

private fun headerObject(
    b: PmBuilder,
    headers: MutableList<ScriptKv>,
    writable: Boolean,
    sandbox: ScriptSession
): ScriptableObject {
    val n = b.node()
    n.prop("count") { headers.size }
    n.method("get") { args ->
        val name = toStr(arg(args, 0))
        headers.firstOrNull { kv -> kv.key.equals(name, ignoreCase = true) }?.value
    }
    n.method("has") { args ->
        headers.any { kv -> kv.key.equals(toStr(arg(args, 0)), ignoreCase = true) }
    }
    n.method("each") { args ->
        val fn = arg(args, 0) as? JsCallable
        if (fn != null) {
            for (kv in headers) {
                val row = b.newObject()
                row.defineProperty("key", kv.key, 0)
                row.defineProperty("value", kv.value, 0)
                fn.call(b.cx, b.scope, b.scope, arrayOf<Any?>(row))
            }
        }
        null
    }
    if (writable) {
        n.method("add") { args ->
            headers += ScriptKv(toStr(arg(args, 0)), toStr(arg(args, 1)))
            null
        }
        n.method("remove") { args ->
            val name = toStr(arg(args, 0))
            val index = headers.indexOfFirst { kv -> kv.key.equals(name, ignoreCase = true) }
            if (index >= 0) headers.removeAt(index)
            null
        }
    } else {
        n.method("remove") {
            sandbox.log("warn", "pm.response.headers 是只读的，无法删除")
            null
        }
    }
    return n.self
}

/**
 * pm.request.url.query：读写都直接落在 URL 上。
 *
 * 改查询时顺手清掉 params 表——发送时 queryParams 会追加到 URL 后面，
 * 两份都留着同一个参数会被重复拼进最终请求。
 */
private fun queryObject(
    b: PmBuilder,
    request: MutableRequest
): ScriptableObject {
    val n = b.node()
    fun pairs(): List<ScriptKv> = ParsedUrl.parse(request.url).pairs
    fun serialize(pairs: List<ScriptKv>): String =
        replaceQuery(request.url, pairs.joinToString("&") { kv -> "${kv.key}=${kv.value}" })

    n.prop("count") { pairs().size }
    n.method("get") { args ->
        val name = toStr(arg(args, 0))
        pairs().firstOrNull { kv -> kv.key.equals(name, ignoreCase = true) }?.value
    }
    n.method("has") { args ->
        pairs().any { kv -> kv.key.equals(toStr(arg(args, 0)), ignoreCase = true) }
    }
    n.method("add") { args ->
        val name = toStr(arg(args, 0))
        if (name.isBlank()) {
            null
        } else {
            val next = pairs().toMutableList()
            next += ScriptKv(name, toStr(arg(args, 1)))
            request.url = serialize(next)
            request.params.clear()
            null
        }
    }
    n.method("remove") { args ->
        val name = toStr(arg(args, 0))
        val next = pairs().filter { kv -> !kv.key.equals(name, ignoreCase = true) }
        request.url = serialize(next)
        request.params.clear()
        null
    }
    n.method("toQuery") { pairs().joinToString("&") { kv -> "${kv.key}=${kv.value}" } }
    n.method("toObject") {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (kv in pairs()) grouped.getOrPut(kv.key) { mutableListOf() } += kv.value
        val obj = b.newObject()
        for ((key, values) in grouped) {
            val v = if (values.size == 1) values.first() else b.js(NativeArray(values.toTypedArray()))
            obj.defineProperty(key, v, 0)
        }
        obj
    }
    return n.self
}

private fun urlObject(
    b: PmBuilder,
    request: MutableRequest,
    sandbox: ScriptSession
): ScriptableObject {
    val n = b.node()
    n.prop("full") { request.url }
    n.prop("query") { queryObject(b, request) }
    n.method("getFull") { request.url }
    n.method("toString") { request.url }
    n.method("getScheme") { ParsedUrl.parse(request.url).scheme.ifBlank { null } }
    n.method("getHostname") { ParsedUrl.parse(request.url).hostname.ifBlank { null } }
    n.method("getPath") { ParsedUrl.parse(request.url).path.ifBlank { null } }
    n.method("getPathname") { ParsedUrl.parse(request.url).path.ifBlank { null } }
    n.method("getHash") { ParsedUrl.parse(request.url).hash.ifBlank { null } }
    n.method("getQuery") { ParsedUrl.parse(request.url).query.ifBlank { null } }
    n.method("getCredentials") {
        val p = ParsedUrl.parse(request.url)
        if (p.username.isEmpty() && p.password.isEmpty()) null else b.newObject().also { obj ->
            obj.defineProperty("username", p.username, 0)
            obj.defineProperty("password", p.password, 0)
        }
    }
    n.method("getPort") { ParsedUrl.parse(request.url).port.toIntOrNull()?.takeIf { it != 0 } }
    n.method("getHost") {
        val p = ParsedUrl.parse(request.url)
        when {
            p.hostname.isEmpty() -> null
            p.port.isEmpty() -> p.hostname
            else -> "${p.hostname}:${p.port}"
        }
    }
    n.method("setQuery") { args ->
        val query = toStr(arg(args, 0))
        if (query.isBlank()) {
            sandbox.log("warn", "pm.request.url.setQuery 传入空值，未修改 URL")
        } else {
            request.url = replaceQuery(request.url, query)
            request.params.clear()
        }
        null
    }
    return n.self
}

private fun bodyObject(
    b: PmBuilder,
    request: MutableRequest
): ScriptableObject {
    val n = b.node()
    n.prop("mode", setter = { request.bodyType = bodyTypeOf(toStr(it)) }, getter = { bodyModeName(request.bodyType) })
    n.prop("text", setter = { request.bodyText = toStr(it) }, getter = { request.bodyText })
    n.prop(
        "json",
        setter = { value ->
            request.bodyType = BodyType.JSON
            request.bodyText = scriptJson.encodeToString(JsonElement.serializer(), toJsObject(value))
        },
        getter = { parseJson(request.bodyText)?.let { element -> b.jsonValue(element) } }
    )
    return n.self
}

private fun requestObject(
    b: PmBuilder,
    request: MutableRequest,
    sandbox: ScriptSession
): ScriptableObject {
    val n = b.node()
    n.prop("method", setter = { request.method = toStr(it) }, getter = { request.method })
    n.prop("headers") { headerObject(b, request.headers, writable = true, sandbox) }
    n.prop("url") { urlObject(b, request, sandbox) }
    n.prop("body") { bodyObject(b, request) }
    n.prop("query") {
        ParsedUrl.parse(request.url).pairs.joinToString("&") { kv -> "${kv.key}=${kv.value}" }
    }
    n.method("getHeaders") { headerObject(b, request.headers, writable = true, sandbox) }
    n.method("getHeader") { args ->
        val name = toStr(arg(args, 0))
        request.headers.firstOrNull { kv -> kv.key.equals(name, ignoreCase = true) }?.value
    }
    n.method("setHeader") { args ->
        addOrReplace(request.headers, toStr(arg(args, 0)), toStr(arg(args, 1)))
        null
    }
    n.method("getBody") {
        if (request.bodyType == BodyType.NONE) null else request.bodyText
    }
    n.method("setMethod") { args -> request.method = toStr(arg(args, 0)).uppercase() }
    n.method("setUrl") { args -> request.url = toStr(arg(args, 0)) }
    return n.self
}

private fun variableObject(
    b: PmBuilder,
    sandbox: ScriptSession,
    scope: VariableScope = sandbox.scope
): ScriptableObject {
    val vars = scope
    val n = b.node()
    n.prop("count") { vars.all().size }
    n.method("get") { args -> vars.get(toStr(arg(args, 0))) }
    n.method("has") { args -> vars.has(toStr(arg(args, 0))) }
    n.method("set") { args ->
        vars.set(toStr(arg(args, 0)), toStr(arg(args, 1)))
        null
    }
    n.method("delete") { args ->
        vars.delete(toStr(arg(args, 0)))
        null
    }
    n.method("unshift") { args ->
        vars.unshift(toStr(arg(args, 0)), toStr(arg(args, 1)), toBool(arg(args, 2)))
        null
    }
    n.method("all") {
        val obj = b.newObject()
        vars.all().forEach { (key, value) -> obj.defineProperty(key, value, 0) }
        obj
    }
    return n.self
}

private fun consoleObject(
    b: PmBuilder,
    sandbox: ScriptSession
): ScriptableObject {
    val n = b.node()
    n.method("log") { args -> sandbox.log("", args.joinToString(" ") { value -> consoleStr(value) }) }
    n.method("warn") { args -> sandbox.log("warn", args.joinToString(" ") { value -> consoleStr(value) }) }
    n.method("error") { args -> sandbox.log("error", args.joinToString(" ") { value -> consoleStr(value) }) }
    return n.self
}

private fun responseView(
    cx: Context,
    scope: Scriptable,
    sandbox: ScriptSession,
    outcome: HttpCallOutcome
): ScriptableObject? {
    val success = outcome as? HttpCallOutcome.Success ?: return null
    val b = PmBuilder(cx, scope)
    val headers = success.headers.map { ScriptKv(it.key, it.value) }.toMutableList()
    val contentType = headers
        .firstOrNull { kv -> kv.key.equals("content-type", ignoreCase = true) }?.value ?: ""
    val parsed = parseJson(success.body)?.let { element -> b.jsonValue(element) }

    val n = b.node()
    n.prop("statusCode") { success.statusCode }
    n.prop("status") { success.statusText }
    n.prop("headers") { headerObject(b, headers, writable = false, sandbox) }
    n.prop("size") { success.responseSizeBytes }
    n.prop("time") { success.durationMs }
    n.prop("duration") { success.durationMs }
    n.prop("filename") { "" }
    n.prop("fileName") { "" }
    n.prop("contentType") { contentType }
    n.method("text") { success.body }
    n.method("json") { parsed }
    n.method("jsonp") { args ->
        val key = toStr(arg(args, 0))
        if (parsed !is ScriptableObject) null
        else parsed.get(key, parsed)
    }
    n.method("isJson") { parseJson(success.body) != null }
    n.method("to") {
        val inner = b.node()
        inner.method("text") { success.body }
        inner.method("json") { parsed }
        inner.method("jsonp") { args ->
            val key = toStr(arg(args, 0))
            if (parsed !is ScriptableObject) null else parsed.get(key, parsed)
        }
        inner.self
    }
    return n.self
}

private fun buildPm(
    cx: Context,
    scope: Scriptable,
    request: MutableRequest,
    sandbox: ScriptSession,
    response: ScriptableObject?
): ScriptableObject {
    val b = PmBuilder(cx, scope)
    val n = b.node()
    n.prop("request") { requestObject(b, request, sandbox) }
    n.prop("variables") { variableObject(b, sandbox) }
    n.prop("environment") { variableObject(b, sandbox) }
    n.prop("globals") { variableObject(b, sandbox) }
    sandbox.collectionScope?.let { vars ->
        n.prop("collectionVariables") { variableObject(b, sandbox, vars) }
    }
    n.prop("console") { consoleObject(b, sandbox) }
    n.value("iteration") { 0 }
    n.value("iterationCount") { 1 }
    if (response != null) n.value("response") { response }
    n.method("test") { args ->
        val name = toStr(arg(args, 0))
        val fn = arg(args, 1) as? JsCallable
            ?: throw RuntimeException("pm.test 的第二个参数必须是函数")
        sandbox.test(name) { fn.call(b.cx, b.scope, b.scope, emptyArray()) }
        null
    }
    n.method("log") { args -> sandbox.log("", args.joinToString(" ") { value -> consoleStr(value) }) }
    return n.self
}

private fun addOrReplace(headers: MutableList<ScriptKv>, key: String, value: String) {
    val index = headers.indexOfFirst { kv -> kv.key.equals(key, ignoreCase = true) }
    if (index >= 0) headers[index] = ScriptKv(key, value) else headers += ScriptKv(key, value)
}

private fun bodyModeName(type: BodyType): String = when (type) {
    BodyType.NONE -> "none"
    BodyType.JSON, BodyType.RAW -> "raw"
    BodyType.FORM_URLENCODED -> "urlencoded"
    BodyType.FORM_DATA -> "form-data"
    BodyType.GRAPHQL -> "graphql"
}

private fun bodyTypeOf(name: String): BodyType = when (name.trim().lowercase()) {
    "raw", "text", "json" -> BodyType.RAW
    "urlencoded", "url-encoded", "x-www-form-urlencoded" -> BodyType.FORM_URLENCODED
    "form-data", "multipart", "form" -> BodyType.FORM_DATA
    "none", "", "null" -> BodyType.NONE
    else -> BodyType.RAW
}
