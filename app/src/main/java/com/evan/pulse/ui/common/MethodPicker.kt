package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/**
 * HTTP 方法选择器：常用方法下拉 + 「自定义」入口。
 *
 * 自定义方法（PROPFIND、MKCOL 这类）不在常用列表里时，下拉会把它显示成一个
 * 额外条目，选中状态因此不会丢；点击「自定义」打开输入框，只接受字母与连字符。
 */
@Composable
fun MethodPicker(
    method: HttpMethod,
    onMethodChange: (HttpMethod) -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    var showCustom by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    var customInvalid by remember { mutableStateOf(false) }

    val customLabel = stringResource(R.string.http_method_custom)
    val items = remember(method, customLabel) {
        buildList {
            HttpMethod.COMMON.forEach { add(it.value) }
            if (method !in HttpMethod.COMMON) add(method.value)
            add(customLabel)
        }
    }
    // 自定义方法不在列表里时也不会出现越界下标
    val selected = items.indexOf(method.value).takeIf { it >= 0 } ?: items.lastIndex

    AppDropdown(
        items = items,
        selectedIndex = selected,
        title = title,
        modifier = modifier,
        showValue = true,
        onSelectedIndexChange = { index ->
            if (index == items.lastIndex) {
                customInput = if (method in HttpMethod.COMMON) "" else method.value
                customInvalid = false
                showCustom = true
            } else {
                onMethodChange(HttpMethod.parse(items[index]))
            }
        }
    )

    if (showCustom) {
        AppDialog(
            show = true,
            title = stringResource(R.string.http_method_custom_title),
            onDismissRequest = { showCustom = false }
        ) {
            Column {
                AppTextField(
                    value = customInput,
                    onValueChange = {
                        customInput = it.uppercase()
                        customInvalid = false
                    },
                    label = stringResource(R.string.http_method_custom_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (customInvalid) {
                    AppText(
                        text = stringResource(R.string.http_method_custom_invalid),
                        color = AppTheme.colors.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                DialogActions(
                    onCancel = { showCustom = false },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = {
                        val raw = customInput.trim()
                        if (HttpMethod.isValid(raw)) {
                            onMethodChange(HttpMethod.parse(raw))
                            showCustom = false
                        } else {
                            customInvalid = true
                        }
                    }
                )
            }
        }
    }
}
