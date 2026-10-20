package com.evan.pulse.ui.design

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/**
 * 文字按钮：Miuix 皮肤是带底色的胶囊按钮，Material 皮肤是 M3 的 TextButton。
 *
 * 尺寸由各皮肤自己的默认规格决定，调用点只表达「一段文字 + 点击」，
 * 不用 minWidth/cornerRadius 这类 Miuix 专有参数，避免换肤后布局错位。
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    M3TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = if (filled) {
            ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else ButtonDefaults.textButtonColors(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AppText(
            text = text,
            style = AppTheme.typography.button,
            maxLines = 1
        )
    }
}
