package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Scaffold 的 PaddingValues 顶栏高度在收缩动画期间每帧变化，两者都在 layout 阶段
 * 读取该值：顶栏动画只触发重排与重绘，内容组合保持稳定。
 *
 * [topBarPadding] 供非滚动容器与页面外层使用：顶部内边距随收缩实时变化，视口变高、
 * 内容保持完整，大标题收缩成小标题吸顶。
 */

/** 非滚动容器顶部内边距：视口高度随顶栏收缩实时变化 */
fun Modifier.topBarPadding(paddingValues: PaddingValues): Modifier = layout { measurable, constraints ->
    val top = paddingValues.calculateTopPadding().roundToPx()
    val placeable = if (top <= 0) {
        measurable.measure(constraints)
    } else {
        val childMaxHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - top).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        measurable.measure(
            constraints.copy(
                minHeight = constraints.minHeight.coerceAtMost(childMaxHeight),
                maxHeight = childMaxHeight
            )
        )
    }
    layout(placeable.width, placeable.height + top) {
        placeable.placeRelative(0, top)
    }
}
