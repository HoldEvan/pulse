package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 单个 pager 页内使用的轻量 Scaffold：只承载随页滑动的顶栏与其下的内容。
 *
 * 仿 miuix Scaffold 的布局：bodyContent 铺满 (0,0) 全屏，topBar 叠加在其上方；
 * 顶栏高度在每次布局时经 SubcomposeLayout 测得，写入单个可变 PaddingValues，
 * 供内容经 [LocalTopBarInset] 在组合阶段读取作为滚动容器顶部让位。
 */
@Composable
fun TabPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    // 与 miuix ScaffoldLayout 一致：记住并更新单个 PaddingValues，
    // 避免顶栏高度每帧变化引发内容子树重组
    val contentPadding = remember {
        object : PaddingValues {
            var paddingHolder by mutableStateOf(PaddingValues(0.dp))

            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = paddingHolder.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = paddingHolder.calculateBottomPadding()
        }
    }
    val topBarContent: @Composable () -> Unit = remember(topBar) { { Box { topBar() } } }
    val bodyContent: @Composable () -> Unit =
        remember(content, contentPadding) { { Box { content(contentPadding) } } }
    SubcomposeLayout(modifier = modifier) { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val topBarPlaceable =
            subcompose("tabPageTopBar", topBarContent)
                .first()
                .measure(looseConstraints)

        // 顶栏为空时不留白，内容顶到页首；状态栏由顶栏自身消费
        contentPadding.paddingHolder =
            PaddingValues(
                top = if (topBarPlaceable.width == 0 && topBarPlaceable.height == 0) {
                    0.dp
                } else {
                    topBarPlaceable.height.toDp()
                }
            )

        val bodyContentPlaceable =
            subcompose("tabPageContent", bodyContent)
                .first()
                .measure(looseConstraints)

        layout(layoutWidth, layoutHeight) {
            bodyContentPlaceable.place(0, 0)
            topBarPlaceable.place(0, 0)
        }
    }
}
