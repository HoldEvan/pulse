package com.evan.pulse.domain.script

import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig

/** 脚本可读写的键值对 */
data class ScriptKv(var key: String, var value: String)

/** 脚本可读写的表单字段 */
data class ScriptForm(var name: String, var value: String)

/**
 * 脚本能读写的请求视图。
 *
 * 前置脚本拿到的是这个对象，执行完后用 [toCallConfig] 转成真正发送的配置，
 * 因此脚本对 method、headers、url、query、body 的改动都会原样生效。
 */
class MutableRequest(
    var method: String,
    var url: String,
    var headers: MutableList<ScriptKv>,
    var params: MutableList<ScriptKv>,
    var bodyType: BodyType,
    var bodyText: String,
    var formFields: MutableList<ScriptForm>
) {
    fun toCallConfig(proxy: ProxyConfig): HttpCallConfig = HttpCallConfig(
        method = HttpMethod.parse(method),
        url = url,
        headers = headers.map { KeyValue(it.key, it.value, true) },
        queryParams = params.map { KeyValue(it.key, it.value, true) },
        bodyType = bodyType,
        body = bodyText,
        formFields = formFields.map { FormField(name = it.name, value = it.value) },
        proxy = proxy
    )
}

/** 脚本里 pm.test 的记录结果 */
data class TestRecord(val name: String, val passed: Boolean, val detail: String)

/** 单次脚本执行的结果 */
sealed interface ScriptRunResult {
    data object Skipped : ScriptRunResult

    data class Ok(val console: List<String>, val tests: List<TestRecord>) : ScriptRunResult

    data class Failed(
        val message: String,
        val console: List<String>,
        val tests: List<TestRecord>
    ) : ScriptRunResult
}

fun ScriptRunResult.consoleLines(): List<String> = when (val r = this) {
    is ScriptRunResult.Ok -> r.console
    is ScriptRunResult.Failed -> r.console
    ScriptRunResult.Skipped -> emptyList()
}

fun ScriptRunResult.testRecords(): List<TestRecord> = when (val r = this) {
    is ScriptRunResult.Ok -> r.tests
    is ScriptRunResult.Failed -> r.tests
    ScriptRunResult.Skipped -> emptyList()
}

fun ScriptRunResult.errorMessage(): String? = (this as? ScriptRunResult.Failed)?.message

/** Kotlin 值到 JS 值的转换，由具体解释器实现 */
fun interface JsConverter {
    fun toJs(value: Any?): Any?
}

/**
 * 脚本可写的变量存储。
 *
 * 写入立即持久化，因此脚本设过的值后续请求可以直接用 {{var}} 引用。
 */
interface ScriptEnvWriter {
    fun replaceActiveVars(variables: List<Pair<String, String>>)

    fun removeActiveVar(name: String)
}

/**
 * pm.variables / pm.environment / pm.globals 共用的变量作用域。
 *
 * 三者在同一份活动环境上读写：本客户端没有 Postman 的分层 scope，
 * 环境变量就是唯一的持久变量层，所以 pm.globals.set 也写进活动环境。
 */
class VariableScope(
    private val envLookup: () -> Map<String, String>,
    private val envWriter: ScriptEnvWriter
) {
    /**
     * 数据驱动运行注入的当前行变量，优先级高于环境变量、低于脚本本次写入，
     * 与 Postman 的 data 变量语义一致。
     */
    var dataVars: Map<String, String> = emptyMap()

    /** 本次请求内脚本写入的值，按写入顺序排列 */
    private val written = LinkedHashMap<String, String>()
    private val removed = LinkedHashSet<String>()

    fun get(name: String): String? = written[name] ?: dataVars[name] ?: envLookup()[name]

    fun has(name: String): Boolean = get(name) != null

    fun set(name: String, value: String) {
        written[name] = value
        removed.remove(name)
        sync(atFront = false)
    }

    fun delete(name: String) {
        written.remove(name)
        removed.add(name)
        envWriter.removeActiveVar(name)
    }

    /** 插到最前面；overwrite 为 false 且键已存在时保留原值，只挪位置 */
    fun unshift(name: String, value: String, overwrite: Boolean) {
        val existing = all()[name]
        written[name] = if (existing != null && !overwrite) existing else value
        removed.remove(name)
        sync(atFront = true)
    }

    /** 当前全部变量：脚本本次写入的排在前面 */
    fun all(): Map<String, String> {
        val base = LinkedHashMap<String, String>()
        for ((k, v) in envLookup()) if (k !in removed) base[k] = v
        for ((k, v) in dataVars) if (k !in removed) base[k] = v
        for ((k, v) in written) base[k] = v
        return base
    }

    /** 把当前作用域整体写回活动环境 */
    private fun sync(atFront: Boolean) {
        val base = LinkedHashMap<String, String>()
        for ((k, v) in envLookup()) if (k !in removed) base[k] = v

        val merged = LinkedHashMap<String, String>()
        for ((k, v) in written) {
            if (atFront) merged[k] = v else base[k] = v
        }
        for ((k, v) in base) merged.putIfAbsent(k, v)
        envWriter.replaceActiveVars(merged.toList())
    }
}

/** 脚本运行时共享的会话状态：控制台输出与断言记录 */
interface ScriptSandbox {
    val scope: VariableScope

    val console: MutableList<String>

    val tests: MutableList<TestRecord>

    fun log(level: String, text: String)

    fun recordTest(name: String, passed: Boolean, detail: String)

    fun test(name: String, fn: () -> Unit)
}

/** 每次执行脚本创建一个新的会话 */
class ScriptSession(
    override val scope: VariableScope,
    /** 文件夹变量作用域；为空时脚本里没有 pm.collectionVariables */
    val collectionScope: VariableScope? = null
) : ScriptSandbox {

    override val console = mutableListOf<String>()
    override val tests = mutableListOf<TestRecord>()

    override fun log(level: String, text: String) {
        console += if (level.isEmpty()) text else "$level: $text"
    }

    override fun recordTest(name: String, passed: Boolean, detail: String) {
        tests += TestRecord(name, passed, detail)
        log("test", "${if (passed) "PASS" else "FAIL"} $name")
    }

    override fun test(name: String, fn: () -> Unit) {
        try {
            fn()
            recordTest(name, true, "")
        } catch (e: Throwable) {
            recordTest(name, false, formatError(e))
        }
    }
}

/** JS 抛错经 Rhino 桥会带上脚本位置（pulse-script#9），展示时去掉 */
private fun formatError(error: Throwable): String =
    error.message?.let { Regex("\\s*\\([^()]*\\)$").replaceFirst(it, "") } ?: "unknown error"
