package com.evan.pulse.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults

/** 默认保持填充框，允许编辑弹窗为包含的输入框统一启用轻描边。 */
internal val LocalAppTextFieldOutline = staticCompositionLocalOf { false }

/**
 * 输入框：Miuix 皮肤是带浮动标签的圆角填充框，Material 皮肤是 M3 的 OutlinedTextField。
 *
 * 参数只保留两套皮肤共有的语义：值、变更回调、标签、行数、键盘类型。
 * useLabelAsPlaceholder 在 Miuix 下把标签当占位文字，在 Material 下等价为 placeholder，
 * 两边的可见文案一致。outline 只为 Miuix 增加未聚焦轻描边，默认关闭。
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
    outline: Boolean = LocalAppTextFieldOutline.current,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        val interactionSource = if (outline) remember { MutableInteractionSource() } else null
        val outlinedModifier = if (interactionSource != null) {
            val isFocused by interactionSource.collectIsFocusedAsState()
            val density = LocalDensity.current
            val squircleEnabled = isSquircleEnabled()
            val shape = remember(density, squircleEnabled) {
                GenericShape { size, _ ->
                    addSquircleRect(
                        width = size.width,
                        height = size.height,
                        cornerRadius = with(density) { MiuixTextFieldDefaults.CornerRadius.toPx() },
                        squircleEnabled = squircleEnabled
                    )
                }
            }
            // rc01 原生边框未聚焦宽度为 0；聚焦时移除此描边，交回原生强调。
            if (isFocused) modifier else modifier.border(1.dp, AppTheme.colors.outline, shape)
        } else {
            modifier
        }
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = outlinedModifier,
            label = label,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            textStyle = textStyle ?: AppTheme.typography.main,
            interactionSource = interactionSource
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
