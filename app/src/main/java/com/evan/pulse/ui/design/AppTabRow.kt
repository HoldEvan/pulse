package com.evan.pulse.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowWithContour as MiuixTabRowWithContour

/**
 * 分段选择器：Miuix 皮肤使用带滑动轮廓的标签栏（内部 LazyList 自带滚动），
 * Material 皮肤使用文字下方短横线指示器的标签样式；[scrollable] 只影响
 * Material 皮肤，令其改为可横向滚动。
 *
 * 尺寸参数只在非默认值时生效，两套皮肤都按同一份几何约束渲染，
 * 因此同一页面换肤后标签栏占位一致，不会把下方内容顶来顶去。
 */
@Composable
fun AppTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    maxWidth: Dp = Dp.Unspecified,
    height: Dp = 40.dp,
    cornerRadius: Dp = 10.dp,
    itemSpacing: Dp = 4.dp,
    scrollable: Boolean = false,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        // TabRowWithContour 内部用 LazyList 实现并自带滚动（自动滚到选中项），
        // 外层再包 horizontalScroll 会让 LazyRow 拿到无限宽度约束直接崩溃。
        MiuixTabRowWithContour(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            height = height,
            cornerRadius = cornerRadius,
            itemSpacing = itemSpacing
        )
        return
    }

    Row(
        modifier = modifier
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
            .heightIn(min = maxOf(height, 48.dp))
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(if (scrollable) itemSpacing else 0.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTabIndex == index
            Box(
                modifier = (if (scrollable) Modifier else Modifier.weight(1f))
                    .widthIn(min = minWidth)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onTabSelected(index) }
                    )
                    .heightIn(min = maxOf(height, 48.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = if (scrollable) 12.dp else 8.dp)
                ) {
                    AppText(
                        text = label,
                        maxLines = 1,
                        overflow = if (scrollable) TextOverflow.Clip else TextOverflow.Ellipsis,
                        style = AppTheme.typography.footnote1,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(24.dp)
                        .height(3.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
