package com.evan.pulse.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ExpandList

@Composable
internal fun Modifier.expandListRotation(expanded: Boolean): Modifier {
    if (LocalUiStyle.current != UiStyle.MIUIX) return this
    val angle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "ExpandListRotation"
    )
    return graphicsLayer { rotationZ = angle }
}

/**
 * 可折叠的分组区块：标题行点击展开/收起，标题右侧显示条目数。
 *
 * 与 [CollapsibleKeyValueTable] 共用同一套折叠标题样式，折叠状态按标题记忆，
 * 分组增删后不会串到别的分组上。[trailing] 用于在标题行右侧放分组级操作按钮。
 */
@Composable
fun CollapsibleSection(
    title: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 6.dp,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            // clickable 放在 padding 之前，水波纹才能横向铺满父容器（贴住卡片左右边缘）
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            trailing?.invoke()
            AppText(
                text = stringResource(R.string.kv_table_count, itemCount),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppIcon(
                // Miuix 用列表展开图标；MD3 保留方向箭头表达当前折叠状态。
                imageVector = if (LocalUiStyle.current == UiStyle.MIUIX) {
                    MiuixIcons.Glass.Light.ExpandList
                } else if (expanded) {
                    Icons.Filled.ExpandLess
                } else {
                    Icons.Filled.ExpandMore
                },
                contentDescription = stringResource(
                    if (expanded) R.string.kv_table_collapse else R.string.kv_table_expand
                ),
                tint = AppTheme.colors.onSurfaceSummary,
                modifier = Modifier.padding(start = 4.dp).expandListRotation(expanded)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                content = content
            )
        }
    }
}
