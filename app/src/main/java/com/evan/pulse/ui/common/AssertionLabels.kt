package com.evan.pulse.ui.common

import androidx.annotation.StringRes
import com.evan.pulse.R
import com.evan.pulse.domain.assertion.ResponseAssertion

/** 断言类型对应的文案资源：HTTP 页与历史详情共用，避免两处各写一份 when */
@StringRes
fun assertionTypeLabelRes(type: String): Int = when (type.uppercase()) {
    ResponseAssertion.TYPE_NOT_CONTAINS -> R.string.monitor_assert_not_contains
    ResponseAssertion.TYPE_JSON_PATH -> R.string.monitor_assert_jsonpath
    else -> R.string.monitor_assert_contains
}
