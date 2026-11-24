package com.evan.pulse.ui.design

import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults

/** 图标按钮：Miuix 是圆角按压块，Material 是 M3 的圆形水波纹按钮 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Miuix 按压块的最小尺寸；Material 皮肤按 M3 规范固定 48dp，忽略该值 */
    minWidth: Dp? = null,
    minHeight: Dp? = null,
    content: @Composable () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = minWidth ?: IconButtonDefaults.MinWidth,
            minHeight = minHeight ?: IconButtonDefaults.MinHeight,
            content = content
        )
    } else {
        M3IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content
        )
    }
}
