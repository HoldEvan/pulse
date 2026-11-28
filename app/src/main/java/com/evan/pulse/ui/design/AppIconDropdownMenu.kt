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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

/**
 * 图标按钮弹出的操作菜单。
 *
 * Miuix 皮肤与选择器共用玻璃面板、背景采样和按压反馈，并保留 [DropdownEntry] 的图标和摘要。
 * Material 皮肤继续使用 M3 DropdownMenu。
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
    val items = entries.flatMap { entry ->
        if (entry.enabled) entry.items else entry.items.map { it.copy(enabled = false) }
    }
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixIconDropdownMenu(
            items = items,
            modifier = modifier,
            enabled = enabled,
            minHeight = minHeight,
            minWidth = minWidth,
            content = content,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
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
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(12.dp)),
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

@Composable
private fun MiuixIconDropdownMenu(
    items: List<DropdownItem>,
    modifier: Modifier,
    enabled: Boolean,
    minHeight: Dp,
    minWidth: Dp,
    content: @Composable () -> Unit,
) {
    val popupState = remember { MiuixDropdownState() }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val actualEnabled = enabled && items.any { it.enabled }
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(actualEnabled) {
        if (!actualEnabled) popupState.setOpen(false)
    }
    Box(
        modifier = modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() },
    ) {
        AppIconButton(
            onClick = {
                popupState.setOpen(true)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
            enabled = actualEnabled,
            minWidth = minWidth,
            minHeight = minHeight,
            content = content,
        )
    }
    MiuixDropdownPopup(
        state = popupState,
        anchorBounds = anchorBounds,
        backdrop = LocalPopupBackdrop.current,
        visuals = rememberGlassPopupVisuals(),
        enabled = actualEnabled,
    ) {
        items.forEach { item ->
            MiuixDropdownItem(
                text = item.text,
                selected = item.selected,
                icon = item.icon,
                summary = item.summary,
                enabled = item.enabled,
                showSelection = false,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    popupState.setOpen(false)
                    item.onClick?.invoke()
                },
            )
        }
    }
}
