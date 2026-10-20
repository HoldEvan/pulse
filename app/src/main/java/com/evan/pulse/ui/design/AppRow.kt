package com.evan.pulse.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 列表行：卡片里的设置项。
 *
 * Miuix 皮肤直接使用 miuix 的 preference 组件；Material 皮肤用 M3 的 ListItem，
 * 由同一个语义参数拼出行结构。两侧的左右内边距都取各自规格的默认值，
 * 调用方只负责卡片本身的外边距。
 */

/** 可点击的箭头行：标题 + 可选摘要 + 可选行尾内容 */
@Composable
fun AppArrowRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        ArrowPreference(
            title = title,
            summary = summary,
            modifier = modifier,
            enabled = enabled,
            startAction = startAction,
            endActions = endActions ?: {},
            onClick = onClick
        )
        return
    }

    ListItem(
        headlineContent = { AppText(text = title, style = AppTheme.typography.main) },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        supportingContent = summary?.let { text ->
            { AppText(text = text, style = AppTheme.typography.footnote1, color = AppTheme.colors.onSurfaceSummary) }
        },
        leadingContent = startAction,
        trailingContent = endActions?.let { action -> { Row { action() } } } ?: {
            AppIcon(
                imageVector = MiuixIcons.Light.ChevronForward,
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceSummary
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 开关行：整行可点，行尾是开关 */
@Composable
fun AppSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    ListItem(
        headlineContent = { AppText(text = title, style = AppTheme.typography.main) },
        modifier = modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange
        ),
        supportingContent = summary?.let { text ->
            { AppText(text = text, style = AppTheme.typography.footnote1, color = AppTheme.colors.onSurfaceSummary) }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 复选行：行首是复选框，点击整行切换 */
@Composable
fun AppCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        CheckboxPreference(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    ListItem(
        headlineContent = { AppText(text = title, style = AppTheme.typography.main) },
        modifier = modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange
        ),
        leadingContent = {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 单选行：行首是单选框，用于项目/环境等互斥选择 */
@Composable
fun AppRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    endActions: (@Composable RowScope.() -> Unit)? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        RadioButtonPreference(
            title = title,
            summary = summary,
            selected = selected,
            onClick = onClick,
            radioButtonLocation = RadioButtonLocation.Start,
            modifier = modifier,
            enabled = enabled,
            endActions = endActions
        )
        return
    }

    ListItem(
        headlineContent = { AppText(text = title, style = AppTheme.typography.main) },
        modifier = modifier.selectable(
            selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick
        ),
        supportingContent = summary?.let { text ->
            { AppText(text = text, style = AppTheme.typography.footnote1, color = AppTheme.colors.onSurfaceSummary) }
        },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        trailingContent = endActions?.let { action -> { Row { action() } } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 滑杆行：标题行可点（用于精确输入），下方是滑杆 */
@Composable
fun AppSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        SliderPreference(
            value = value,
            onValueChange = onValueChange,
            title = title,
            summary = summary,
            valueText = valueText,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            showKeyPoints = steps > 0,
            onValueChangeFinished = onValueChangeFinished
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppTheme.spacing.cardContent, vertical = AppTheme.spacing.m)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(text = title, style = AppTheme.typography.main)
                if (!summary.isNullOrEmpty()) {
                    AppText(
                        text = summary,
                        style = AppTheme.typography.footnote1,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
            if (!valueText.isNullOrEmpty()) {
                AppText(
                    text = valueText,
                    style = AppTheme.typography.body2,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.s),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

/** 普通信息行：不可点的标题 + 摘要，可带行首/行尾内容 */
@Composable
fun AppInfoRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        BasicComponent(
            title = title,
            summary = summary,
            modifier = modifier,
            enabled = enabled,
            startAction = startAction,
            endActions = endActions,
            onClick = onClick
        )
        return
    }

    ListItem(
        headlineContent = { AppText(text = title, style = AppTheme.typography.main) },
        modifier = if (onClick != null && enabled) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        supportingContent = summary?.let { text ->
            { AppText(text = text, style = AppTheme.typography.footnote1, color = AppTheme.colors.onSurfaceSummary) }
        },
        leadingContent = startAction,
        trailingContent = endActions?.let { action -> { Row { action() } } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
