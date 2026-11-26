package com.evan.pulse.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry

/**
 * 图标按钮弹出的操作菜单。
 *
 * 两套皮肤共用同一个半透明面板：miuix-glass 的图标菜单会丢弃 [DropdownEntry] 里的图标，
 * 这里改用 M3 的 DropdownMenu 承载半透明容器，保留图标、摘要与点击回调，且不压暗窗口。
 */
@Composable
fun AppIconDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    minWidth: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val items = entries.flatMap { entry ->
        if (entry.enabled) entry.items else entry.items.map { it.copy(enabled = false) }
    }
    Box(modifier = modifier) {
        AppIconButton(
            onClick = { expanded = true },
            enabled = enabled && items.any { it.enabled },
            minWidth = minWidth,
            minHeight = minHeight
        ) {
            content()
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.12f))
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    text = {
                        Column {
                            Row {
                                item.icon?.invoke(Modifier.padding(end = 8.dp))
                                AppText(text = item.text, modifier = Modifier.weight(1f))
                            }
                            item.summary?.let { AppText(text = it, modifier = Modifier.fillMaxWidth()) }
                        }
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick?.invoke()
                    }
                )
            }
        }
    }
}
