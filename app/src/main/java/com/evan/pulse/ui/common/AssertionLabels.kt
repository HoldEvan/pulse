package com.evan.pulse.ui.common

import androidx.annotation.StringRes
import com.evan.pulse.R
import com.evan.pulse.domain.assertion.AssertionOperator
import com.evan.pulse.domain.assertion.ResponseAssertion

/** 断言类型对应的文案资源：HTTP 页与历史详情共用，避免两处各写一份 when */
@StringRes
fun assertionTypeLabelRes(type: String): Int = when (type.uppercase()) {
    ResponseAssertion.TYPE_NOT_CONTAINS -> R.string.monitor_assert_not_contains
    ResponseAssertion.TYPE_JSON_PATH -> R.string.monitor_assert_jsonpath
    ResponseAssertion.TYPE_STATUS -> R.string.assert_type_status
    ResponseAssertion.TYPE_HEADER -> R.string.assert_type_header
    ResponseAssertion.TYPE_DURATION -> R.string.assert_type_duration
    else -> R.string.monitor_assert_contains
}

/** 断言运算符对应的文案资源 */
@StringRes
fun assertionOperatorLabelRes(operator: String): Int = when (operator.uppercase()) {
    AssertionOperator.NOT_EQUALS -> R.string.assert_op_not_equals
    AssertionOperator.CONTAINS -> R.string.assert_op_contains
    AssertionOperator.NOT_CONTAINS -> R.string.assert_op_not_contains
    AssertionOperator.GT -> R.string.assert_op_gt
    AssertionOperator.GTE -> R.string.assert_op_gte
    AssertionOperator.LT -> R.string.assert_op_lt
    AssertionOperator.LTE -> R.string.assert_op_lte
    AssertionOperator.EXISTS -> R.string.assert_op_exists
    AssertionOperator.IS_NUMBER -> R.string.assert_op_is_number
    AssertionOperator.IS_STRING -> R.string.assert_op_is_string
    AssertionOperator.IS_BOOLEAN -> R.string.assert_op_is_boolean
    AssertionOperator.IS_ARRAY -> R.string.assert_op_is_array
    AssertionOperator.IS_OBJECT -> R.string.assert_op_is_object
    AssertionOperator.IS_NULL -> R.string.assert_op_is_null
    else -> R.string.assert_op_equals
}

/** 断言类型下拉的顺序 */
val assertionTypeOptions: List<String> = listOf(
    ResponseAssertion.TYPE_CONTAINS,
    ResponseAssertion.TYPE_NOT_CONTAINS,
    ResponseAssertion.TYPE_JSON_PATH,
    ResponseAssertion.TYPE_STATUS,
    ResponseAssertion.TYPE_HEADER,
    ResponseAssertion.TYPE_DURATION
)
