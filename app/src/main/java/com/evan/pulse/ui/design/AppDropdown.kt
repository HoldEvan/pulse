package com.evan.pulse.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixDropdownPreference

/**
 * 下拉选择行：Miuix 皮肤沿用 OverlayDropdownPreference（行内展开选项面板），
 * Material 皮肤用 M3 的 DropdownMenu 从行下方弹出。
 *
 * 参数只表达语义（标题、摘要、候选项、当前选中、选中回调），
 * 两套皮肤各自决定展开方式与行高。
 */
@Composable
fun AppDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    summary: String = "",
    showValue: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixDropdownPreference(
            title = title,
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            summary = summary,
            showValue = showValue,
            enabled = enabled,
            modifier = modifier
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val selected = items.getOrNull(selectedIndex).orEmpty()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val valueMaxWidth = maxWidth * 0.4f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && items.isNotEmpty()) { expanded = true }
                .padding(
                    horizontal = AppTheme.spacing.cardContent,
                    vertical = AppTheme.spacing.m
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    color = if (enabled) colors.onSurface else colors.disabledContent,
                    style = typography.main
                )
                if (summary.isNotEmpty()) {
                    AppText(
                        text = summary,
                        modifier = Modifier.padding(top = 2.dp),
                        color = colors.onSurfaceSummary,
                        style = typography.footnote1
                    )
                }
            }
            if (showValue) {
                AppText(
                    text = selected,
                    modifier = Modifier.padding(start = 12.dp).widthIn(max = valueMaxWidth),
                    color = if (enabled) colors.onSurfaceSecondary else colors.disabledContent,
                    style = typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.outline,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = AppTheme.spacing.cardContent)
        ) {
            Spacer(modifier = Modifier.size(20.dp))
            DropdownMenu(
                expanded = expanded && enabled && items.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                items.forEachIndexed { index, label ->
                    val isSelected = index == selectedIndex
                    DropdownMenuItem(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .semantics { this.selected = isSelected },
                        text = {
                            AppText(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else colors.onSurface
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelectedIndexChange(index)
                        }
                    )
                }
            }
        }
    }
}
