package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu as MiuixIconDropdownMenu

@Composable
fun AppIconDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    minWidth: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            minHeight = minHeight,
            minWidth = minWidth,
            content = content
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val items = entries.flatMap { entry ->
        if (entry.enabled) entry.items else entry.items.map { it.copy(enabled = false) }
    }
    Box(modifier = modifier) {
        AppIconButton(onClick = { expanded = true }, enabled = enabled && items.any { it.enabled }) {
            content()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            androidx.compose.foundation.layout.Row {
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
