package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.assertion.AssertionCheck
import com.evan.pulse.domain.assertion.ResponseAssertion
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppTextField

/**
 * 断言编辑与结果展示区：HTTP / WS / MQTT 三处共用同一套类型与运算符，
 * 避免各页面各写一份下拉和文案。
 */
@Composable
fun AssertionSection(
    assertions: List<ResponseAssertion>,
    onAssertionsChange: (List<ResponseAssertion>) -> Unit,
    results: List<AssertionCheck> = emptyList(),
    summary: String? = null,
    summaryPassed: Boolean = true,
    hint: String = stringResource(R.string.http_assertions_hint)
) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(
                text = hint,
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            assertions.forEachIndexed { index, assertion ->
                AssertionEditorRow(
                    assertion = assertion,
                    onChange = { updated ->
                        onAssertionsChange(assertions.mapIndexed { i, a -> if (i == index) updated else a })
                    },
                    onRemove = { onAssertionsChange(assertions.filterIndexed { i, _ -> i != index }) }
                )
            }
            AppTextButton(
                text = stringResource(R.string.monitor_assert_add),
                onClick = { onAssertionsChange(assertions + ResponseAssertion()) },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (summary != null) {
                AppText(
                    text = summary,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = if (summaryPassed) Color(0xFF2FA37A) else Color(0xFFD32F2F)
                )
            }
            if (results.isNotEmpty()) {
                val failed = results.count { !it.passed }
                AppText(
                    text = if (failed == 0) {
                        stringResource(R.string.http_assertions_all_passed)
                    } else {
                        stringResource(R.string.http_assertions_failed, failed)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = if (failed == 0) Color(0xFF2FA37A) else Color(0xFFD32F2F)
                )
                results.forEach { check ->
                    val label = stringResource(assertionTypeLabelRes(check.assertion.type))
                    val detail = check.detail
                    MonospaceBlock(
                        "${if (check.passed) "PASS" else "FAIL"} $label ${check.assertion.expression}" +
                            if (detail.isBlank()) "" else " - $detail"
                    )
                }
            }
        }
    }
}

/** 单条断言：类型下拉 + 运算符（按类型动态给出）+ 输入框 */
@Composable
fun AssertionEditorRow(
    assertion: ResponseAssertion,
    onChange: (ResponseAssertion) -> Unit,
    onRemove: () -> Unit
) {
    val needsExpected = ResponseAssertion.needsExpected(assertion.type)
    val operators = ResponseAssertion.operatorsFor(assertion.type)
    val expressionLabel = when (assertion.type.uppercase()) {
        ResponseAssertion.TYPE_JSON_PATH -> R.string.monitor_assert_path
        ResponseAssertion.TYPE_HEADER -> R.string.assert_header_name
        ResponseAssertion.TYPE_STATUS -> R.string.assert_status_expression_hint
        ResponseAssertion.TYPE_DURATION -> R.string.assert_duration_expression_hint
        else -> R.string.monitor_assert_keyword
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题直接显示当前选中的类型：showValue 关掉时若 title 也留空，这一行就只剩箭头，看不到回显
            AppDropdown(
                title = stringResource(assertionTypeLabelRes(assertion.type)),
                items = assertionTypeOptions.map { stringResource(assertionTypeLabelRes(it)) },
                selectedIndex = assertionTypeOptions.indexOf(assertion.type.uppercase()).coerceAtLeast(0),
                showValue = false,
                filled = true,
                modifier = Modifier.weight(1f),
                onSelectedIndexChange = { index ->
                    val type = assertionTypeOptions.getOrElse(index) { ResponseAssertion.TYPE_CONTAINS }
                    onChange(
                        assertion.copy(
                            type = type,
                            operator = ResponseAssertion.defaultOperator(type)
                        )
                    )
                }
            )
            AppIconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                AppIcon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
        }
        if (operators.isNotEmpty()) {
            val currentOperator = ResponseAssertion.effectiveOperator(assertion)
            AppDropdown(
                title = stringResource(R.string.assert_operator_label),
                items = operators.map { stringResource(assertionOperatorLabelRes(it)) },
                selectedIndex = operators.indexOf(currentOperator).coerceAtLeast(0),
                filled = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                onSelectedIndexChange = { index ->
                    val operator = operators.getOrElse(index) { ResponseAssertion.defaultOperator(assertion.type) }
                    onChange(assertion.copy(operator = operator))
                }
            )
        }
        AppTextField(
            value = assertion.expression,
            onValueChange = { onChange(assertion.copy(expression = it)) },
            label = stringResource(expressionLabel),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        if (needsExpected) {
            AppTextField(
                value = assertion.expected,
                onValueChange = { onChange(assertion.copy(expected = it)) },
                label = stringResource(R.string.monitor_assert_expected),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}
