package com.evan.pulse.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * 输入框：Miuix 皮肤是带浮动标签的圆角填充框，Material 皮肤是 M3 的 OutlinedTextField。
 *
 * 参数只保留两套皮肤共有的语义：值、变更回调、标签、行数、键盘类型。
 * useLabelAsPlaceholder 在 Miuix 下把标签当占位文字，在 Material 下等价为 placeholder，
 * 两边的可见文案一致。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    useLabelAsPlaceholder: Boolean = false,
    textStyle: TextStyle? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            textStyle = textStyle ?: AppTheme.typography.main
        )
        return
    }

    val colors = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (useLabelAsPlaceholder) null else {
            { AppText(text = label, style = AppTheme.typography.footnote1) }
        },
        placeholder = if (useLabelAsPlaceholder) {
            { AppText(text = label, style = AppTheme.typography.main) }
        } else {
            null
        },
        textStyle = textStyle ?: AppTheme.typography.main,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(AppTheme.radii.control),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedContainerColor = colors.surfaceContainer,
            unfocusedContainerColor = colors.surfaceContainer,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceSummary,
            cursorColor = colors.primary
        )
    )
}
