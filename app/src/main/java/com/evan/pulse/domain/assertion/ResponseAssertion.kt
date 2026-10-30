package com.evan.pulse.domain.assertion

import com.evan.pulse.R
import com.evan.pulse.domain.JsonPath
import com.evan.pulse.domain.extractString
import com.evan.pulse.ui.l10n.L10n
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 响应断言：发送请求后按顺序校验响应，用来判断这次请求是否"业务成功"。
 *
 * 类型：
 * - CONTAINS / NOT_CONTAINS 响应体包含 / 不包含关键字
 * - JSON_PATH 取 JSONPath 首个匹配值，按 [operator] 与期望值比较（默认相等）
 * - STATUS 校验 HTTP 状态码，expression 支持 `200`、`2xx` 通配或配合比较运算符
 * - HEADER 校验响应头：expression 为头名，expected 为期望值
 * - DURATION 校验耗时阈值：expression 为毫秒数，默认要求小于等于
 *
 * [operator] 为空时按类型取默认运算符（见 [defaultOperator]），保证旧数据
 * 只用 type/expression/expected 也能正确求值。
 */
data class ResponseAssertion(
    val type: String = TYPE_CONTAINS,
    val expression: String = "",
    val expected: String = "",
    val operator: String = ""
) {
    companion object {
        const val TYPE_CONTAINS = "CONTAINS"
        const val TYPE_NOT_CONTAINS = "NOT_CONTAINS"
        const val TYPE_JSON_PATH = "JSON_PATH"
        const val TYPE_STATUS = "STATUS"
        const val TYPE_HEADER = "HEADER"
        const val TYPE_DURATION = "DURATION"

        /** 需要第二个输入框（期望值）的类型 */
        fun needsExpected(type: String): Boolean = when (type.uppercase()) {
            TYPE_JSON_PATH, TYPE_HEADER -> true
            else -> false
        }

        /** 该类型支持的运算符集合，供 UI 下拉使用 */
        fun operatorsFor(type: String): List<String> = when (type.uppercase()) {
            TYPE_STATUS -> listOf(
                AssertionOperator.EQUALS,
                AssertionOperator.NOT_EQUALS,
                AssertionOperator.GT,
                AssertionOperator.GTE,
                AssertionOperator.LT,
                AssertionOperator.LTE
            )

            TYPE_HEADER -> listOf(
                AssertionOperator.EQUALS,
                AssertionOperator.NOT_EQUALS,
                AssertionOperator.CONTAINS,
                AssertionOperator.EXISTS
            )

            TYPE_DURATION -> listOf(
                AssertionOperator.LTE,
                AssertionOperator.LT,
                AssertionOperator.GTE,
                AssertionOperator.GT
            )

            TYPE_JSON_PATH -> listOf(
                AssertionOperator.EQUALS,
                AssertionOperator.NOT_EQUALS,
                AssertionOperator.CONTAINS,
                AssertionOperator.GT,
                AssertionOperator.GTE,
                AssertionOperator.LT,
                AssertionOperator.LTE,
                AssertionOperator.EXISTS,
                AssertionOperator.IS_NUMBER,
                AssertionOperator.IS_STRING,
                AssertionOperator.IS_BOOLEAN,
                AssertionOperator.IS_ARRAY,
                AssertionOperator.IS_OBJECT,
                AssertionOperator.IS_NULL
            )

            else -> emptyList()
        }

        /** 未显式指定运算符时的默认值 */
        fun defaultOperator(type: String): String = when (type.uppercase()) {
            TYPE_DURATION -> AssertionOperator.LTE
            else -> AssertionOperator.EQUALS
        }

        fun effectiveOperator(assertion: ResponseAssertion): String =
            assertion.operator.ifBlank { defaultOperator(assertion.type) }

        /** 该类型是否需要用户填写运算符（关键字断言不需要） */
        fun supportsOperator(type: String): Boolean = operatorsFor(type).isNotEmpty()
    }
}

/** 断言运算符常量 */
object AssertionOperator {
    const val EQUALS = "EQUALS"
    const val NOT_EQUALS = "NOT_EQUALS"
    const val CONTAINS = "CONTAINS"
    const val NOT_CONTAINS = "NOT_CONTAINS"
    const val GT = "GT"
    const val GTE = "GTE"
    const val LT = "LT"
    const val LTE = "LTE"
    const val EXISTS = "EXISTS"
    const val IS_NUMBER = "IS_NUMBER"
    const val IS_STRING = "IS_STRING"
    const val IS_BOOLEAN = "IS_BOOLEAN"
    const val IS_ARRAY = "IS_ARRAY"
    const val IS_OBJECT = "IS_OBJECT"
    const val IS_NULL = "IS_NULL"
}

/**
 * 断言求值的目标：HTTP 有状态码/响应头/耗时，WS/MQTT 只有消息正文。
 * 缺省字段为 null 时，相关类型断言会判失败并说明"无该字段"，而不是静默跳过。
 */
data class AssertionTarget(
    val body: String = "",
    val statusCode: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long? = null
)

/** 单条断言的求值结果，detail 为可直接展示的失败原因 */
data class AssertionCheck(
    val index: Int,
    val assertion: ResponseAssertion,
    val passed: Boolean,
    val detail: String = ""
)

/**
 * 断言求值：只依赖目标内容与变量表，便于单元测试。
 * 表达式支持 `{{变量}}` 占位符，缺失时该条直接判失败并说明缺了哪些变量。
 */
object ResponseAssertions {

    // 结尾的 } 必须转义：JVM 把孤立的 } 当字面量，Android 的 ICU 正则直接抛 PatternSyntaxException
    private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*\}\}""")

    /** 状态码通配写法：2xx / 4XX 等 */
    private val STATUS_WILDCARD = Regex("""^([1-5])[xX]{2}$""")

    /**
     * 依次求值；expression 为空的条目视为未填写，直接跳过不产生结果。
     * 返回顺序与 [assertions] 一致，未填写的条目不会出现在结果里。
     */
    fun evaluate(
        body: String,
        assertions: List<ResponseAssertion>,
        vars: Map<String, String> = emptyMap()
    ): List<AssertionCheck> = evaluate(AssertionTarget(body = body), assertions, vars)

    fun evaluate(
        target: AssertionTarget,
        assertions: List<ResponseAssertion>,
        vars: Map<String, String> = emptyMap()
    ): List<AssertionCheck> = assertions.mapIndexedNotNull { index, assertion ->
        if (assertion.expression.isBlank()) return@mapIndexedNotNull null
        check(target, index, assertion, vars)
    }

    private fun check(
        target: AssertionTarget,
        index: Int,
        assertion: ResponseAssertion,
        vars: Map<String, String>
    ): AssertionCheck {
        val missing = LinkedHashSet<String>()
        val expression = resolve(assertion.expression, vars, missing)
        val expected = resolve(assertion.expected, vars, missing)
        if (missing.isNotEmpty()) {
            return fail(
                index,
                assertion,
                L10n.t(R.string.monitor_assert_vars_missing, (index + 1).toString(), missing.joinToString(", "))
            )
        }
        val op = ResponseAssertion.effectiveOperator(assertion)
        return when (assertion.type.uppercase()) {
            ResponseAssertion.TYPE_STATUS -> checkStatus(target, index, assertion, expression, op)
            ResponseAssertion.TYPE_HEADER -> checkHeader(target, index, assertion, expression, expected, op)
            ResponseAssertion.TYPE_DURATION -> checkDuration(target, index, assertion, expression, op)
            ResponseAssertion.TYPE_JSON_PATH -> checkJsonPath(target, index, assertion, expression, expected, op)
            ResponseAssertion.TYPE_NOT_CONTAINS -> {
                val passed = !target.body.contains(expression)
                if (passed) pass(index, assertion) else fail(
                    index, assertion, L10n.t(R.string.monitor_assert_not_contains_failed, expression)
                )
            }

            else -> {
                val passed = target.body.contains(expression)
                if (passed) pass(index, assertion) else fail(
                    index, assertion, L10n.t(R.string.monitor_assert_contains_failed, expression)
                )
            }
        }
    }

    private fun checkStatus(
        target: AssertionTarget,
        index: Int,
        assertion: ResponseAssertion,
        expression: String,
        op: String
    ): AssertionCheck {
        val status = target.statusCode
            ?: return fail(index, assertion, L10n.t(R.string.assert_status_missing))
        // 通配写法只对相等/不等有意义
        val wildcard = STATUS_WILDCARD.matchEntire(expression.trim())
        if (wildcard != null && (op == AssertionOperator.EQUALS || op == AssertionOperator.NOT_EQUALS)) {
            val prefix = wildcard.groupValues[1]
            val matches = status.toString().startsWith(prefix)
            val passed = if (op == AssertionOperator.EQUALS) matches else !matches
            return if (passed) pass(index, assertion) else fail(
                index, assertion, L10n.t(R.string.assert_status_failed, expression, status)
            )
        }
        val passed = compare(op, status.toString(), expression)
        return if (passed) pass(index, assertion) else fail(
            index, assertion, L10n.t(R.string.assert_status_failed, "$op $expression", status)
        )
    }

    private fun checkHeader(
        target: AssertionTarget,
        index: Int,
        assertion: ResponseAssertion,
        name: String,
        expected: String,
        op: String
    ): AssertionCheck {
        val actual = target.headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
        if (op == AssertionOperator.EXISTS) {
            return if (actual != null) pass(index, assertion) else fail(
                index, assertion, L10n.t(R.string.assert_header_missing, name)
            )
        }
        if (actual == null) {
            return fail(index, assertion, L10n.t(R.string.assert_header_missing, name))
        }
        val passed = compare(op, actual, expected)
        return if (passed) pass(index, assertion) else fail(
            index, assertion, L10n.t(R.string.assert_header_failed, name, expected, actual.take(120))
        )
    }

    private fun checkDuration(
        target: AssertionTarget,
        index: Int,
        assertion: ResponseAssertion,
        expression: String,
        op: String
    ): AssertionCheck {
        val duration = target.durationMs
            ?: return fail(index, assertion, L10n.t(R.string.assert_duration_missing))
        val passed = compare(op, duration.toString(), expression)
        return if (passed) pass(index, assertion) else fail(
            index, assertion, L10n.t(R.string.assert_duration_failed, "$op $expression", duration)
        )
    }

    private fun checkJsonPath(
        target: AssertionTarget,
        index: Int,
        assertion: ResponseAssertion,
        expression: String,
        expected: String,
        op: String
    ): AssertionCheck {
        val hits = JsonPath.eval(target.body, expression)
        if (op == AssertionOperator.EXISTS) {
            return if (!hits.isNullOrEmpty()) pass(index, assertion) else fail(
                index, assertion, L10n.t(R.string.monitor_assert_jsonpath_missing, expression)
            )
        }
        if (hits.isNullOrEmpty()) {
            return fail(index, assertion, L10n.t(R.string.monitor_assert_jsonpath_missing, expression))
        }
        val element = hits.first()
        // 类型校验直接看元素类型，不做字符串化
        if (op.startsWith("IS_")) {
            val passed = typeMatches(op, element)
            return if (passed) pass(index, assertion) else fail(
                index, assertion, L10n.t(R.string.assert_type_failed, op.removePrefix("IS_").lowercase(), element.extractString().take(60))
            )
        }
        val actual = element.extractString()
        val passed = compare(op, actual, expected)
        return if (passed) pass(index, assertion) else fail(
            index, assertion, L10n.t(R.string.monitor_assert_jsonpath_failed, expression, expected, actual.take(120))
        )
    }

    /** 统一比较：数值运算符要求两侧都能解析成数字，其余按字符串处理 */
    private fun compare(op: String, actual: String, expected: String): Boolean = when (op.uppercase()) {
        AssertionOperator.EQUALS -> actual == expected
        AssertionOperator.NOT_EQUALS -> actual != expected
        AssertionOperator.CONTAINS -> actual.contains(expected)
        AssertionOperator.NOT_CONTAINS -> !actual.contains(expected)
        AssertionOperator.GT -> numeric(actual, expected) { a, b -> a > b }
        AssertionOperator.GTE -> numeric(actual, expected) { a, b -> a >= b }
        AssertionOperator.LT -> numeric(actual, expected) { a, b -> a < b }
        AssertionOperator.LTE -> numeric(actual, expected) { a, b -> a <= b }
        AssertionOperator.EXISTS -> actual.isNotEmpty()
        else -> actual == expected
    }

    private inline fun numeric(
        actual: String,
        expected: String,
        crossinline test: (Double, Double) -> Boolean
    ): Boolean {
        val a = actual.trim().toDoubleOrNull() ?: return false
        val b = expected.trim().toDoubleOrNull() ?: return false
        return test(a, b)
    }

    private fun typeMatches(op: String, element: JsonElement): Boolean = when (op) {
        AssertionOperator.IS_NULL -> element is JsonNull
        AssertionOperator.IS_ARRAY -> element is JsonArray
        AssertionOperator.IS_OBJECT -> element is JsonObject
        AssertionOperator.IS_STRING -> element is JsonPrimitive && element.isString
        AssertionOperator.IS_BOOLEAN -> element is JsonPrimitive && !element.isString &&
            (element.content == "true" || element.content == "false")

        AssertionOperator.IS_NUMBER -> element is JsonPrimitive && !element.isString &&
            element.content != "true" && element.content != "false" &&
            element.content.toDoubleOrNull() != null

        else -> false
    }

    private fun pass(index: Int, assertion: ResponseAssertion) =
        AssertionCheck(index = index, assertion = assertion, passed = true)

    private fun fail(index: Int, assertion: ResponseAssertion, detail: String) =
        AssertionCheck(index = index, assertion = assertion, passed = false, detail = detail)

    private fun resolve(text: String, vars: Map<String, String>, missing: MutableSet<String>): String =
        PLACEHOLDER.replace(text) { match ->
            val name = match.groupValues[1]
            val value = vars[name]
            if (value == null) {
                missing += name
                match.value
            } else {
                value
            }
        }
}
