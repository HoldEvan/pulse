package com.evan.pulse.domain.assertion

import com.evan.pulse.R
import com.evan.pulse.domain.JsonPath
import com.evan.pulse.domain.extractString
import com.evan.pulse.ui.l10n.L10n

/**
 * 响应断言：发送请求后按顺序校验响应体，用来判断这次请求是否"业务成功"。
 *
 * 类型与拨测目标保持一致，便于两处共用同一套表达方式：
 * - CONTAINS 响应体包含关键字
 * - NOT_CONTAINS 响应体不包含关键字
 * - JSON_PATH 取 JSONPath 首个匹配值与期望值相等
 */
data class ResponseAssertion(
    val type: String = TYPE_CONTAINS,
    val expression: String = "",
    val expected: String = ""
) {
    companion object {
        const val TYPE_CONTAINS = "CONTAINS"
        const val TYPE_NOT_CONTAINS = "NOT_CONTAINS"
        const val TYPE_JSON_PATH = "JSON_PATH"

        /** 关键字类断言只需要 expression，JSONPath 还需要 expected */
        fun needsExpected(type: String): Boolean = type.equals(TYPE_JSON_PATH, ignoreCase = true)
    }
}

/** 单条断言的求值结果，detail 为可直接展示的失败原因 */
data class AssertionCheck(
    val index: Int,
    val assertion: ResponseAssertion,
    val passed: Boolean,
    val detail: String = ""
)

/**
 * 断言求值：只依赖响应体文本与变量表，便于单元测试。
 * 表达式支持 `{{变量}}` 占位符，缺失时该条直接判失败并说明缺了哪些变量。
 */
object ResponseAssertions {

    private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*}}""")

    /**
     * 依次求值；expression 为空的条目视为未填写，直接跳过不产生结果。
     * 返回顺序与 [assertions] 一致，未填写的条目不会出现在结果里。
     */
    fun evaluate(
        body: String,
        assertions: List<ResponseAssertion>,
        vars: Map<String, String> = emptyMap()
    ): List<AssertionCheck> = assertions.mapIndexedNotNull { index, assertion ->
        if (assertion.expression.isBlank()) return@mapIndexedNotNull null
        check(body, index, assertion, vars)
    }

    private fun check(
        body: String,
        index: Int,
        assertion: ResponseAssertion,
        vars: Map<String, String>
    ): AssertionCheck {
        val missing = LinkedHashSet<String>()
        val expression = resolve(assertion.expression, vars, missing)
        val expected = resolve(assertion.expected, vars, missing)
        val position = (index + 1).toString()
        if (missing.isNotEmpty()) {
            return AssertionCheck(
                index = index,
                assertion = assertion,
                passed = false,
                detail = L10n.t(
                    R.string.monitor_assert_vars_missing,
                    position,
                    missing.joinToString(", ")
                )
            )
        }
        if (assertion.type.equals(ResponseAssertion.TYPE_NOT_CONTAINS, ignoreCase = true)) {
            val passed = !body.contains(expression)
            return AssertionCheck(
                index = index,
                assertion = assertion,
                passed = passed,
                detail = if (passed) {
                    ""
                } else {
                    L10n.t(R.string.monitor_assert_not_contains_failed, expression)
                }
            )
        }
        if (assertion.type.equals(ResponseAssertion.TYPE_JSON_PATH, ignoreCase = true)) {
            val hits = JsonPath.eval(body, expression)
            if (hits.isNullOrEmpty()) {
                return AssertionCheck(
                    index = index,
                    assertion = assertion,
                    passed = false,
                    detail = L10n.t(R.string.monitor_assert_jsonpath_missing, expression)
                )
            }
            val actual = hits.first().extractString()
            val passed = actual == expected
            return AssertionCheck(
                index = index,
                assertion = assertion,
                passed = passed,
                detail = if (passed) {
                    ""
                } else {
                    L10n.t(
                        R.string.monitor_assert_jsonpath_failed,
                        expression,
                        expected,
                        actual.take(120)
                    )
                }
            )
        }
        val passed = body.contains(expression)
        return AssertionCheck(
            index = index,
            assertion = assertion,
            passed = passed,
            detail = if (passed) "" else L10n.t(R.string.monitor_assert_contains_failed, expression)
        )
    }

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
