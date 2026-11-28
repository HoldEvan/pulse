package com.evan.pulse.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 使用固定的玻璃底座，官方 GlassSegmentedTabRow 的选项在内部滚动并跟随选中项。
 * Material 保留文字下划线标签；[scrollable] 控制其是否允许横向滚动。
 * [minWidth] / [maxWidth] 约束单个标签宽度，[height] 保留页面占位。
 * [cornerRadius] 为原轮廓版本的兼容参数，Miuix 使用官方胶囊形状和固定间距；
 * [itemSpacing] 仅用于 Material 的可滚动标签。
 * [shadowPadding] 为 Miuix 底座预留阴影空间，父容器已有边距时可单独调整。
 * [fillEqually] 让 Miuix 选项均分可用宽度，适合 HTTP 编辑栏这类固定选项。
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
    shadowPadding: PaddingValues = AppSegmentedTabShadowPadding,
    fillEqually: Boolean = false,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixGlassTabs(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            height = height,
            shadowPadding = shadowPadding,
            fillEqually = fillEqually,
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

@Composable
private fun MiuixGlassTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
    minWidth: Dp,
    maxWidth: Dp,
    height: Dp,
    shadowPadding: PaddingValues,
    fillEqually: Boolean,
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val selectedStyle = MiuixTheme.textStyles.subtitle
    val normalStyle = MiuixTheme.textStyles.body2
    val measuredWidth = remember(tabs, textMeasurer, density, selectedStyle, normalStyle) {
        with(density) {
            tabs.maxOf { label ->
                maxOf(
                    textMeasurer.measure(AnnotatedString(label), style = selectedStyle, softWrap = false, maxLines = 1).size.width,
                    textMeasurer.measure(AnnotatedString(label), style = normalStyle, softWrap = false, maxLines = 1).size.width,
                )
            }.toDp()
        }
    }
    val widthLimit = if (maxWidth.value.isNaN()) null else maxOf(maxWidth, minWidth)
    val naturalItemWidth = maxOf(minWidth, measuredWidth + GlassSegmentedTabRowDefaults.TabPaddingHorizontal * 2)
    val itemWidth = widthLimit?.let { minOf(naturalItemWidth, it) } ?: naturalItemWidth
    val gap = GlassSegmentedTabRowDefaults.TabGap
    val trackPadding = GlassSegmentedTabRowDefaults.TrackPadding
    val gapsWidth = gap * (tabs.size - 1)
    val desiredWidth = itemWidth * tabs.size + gapsWidth + trackPadding * 2
    val scrollState = rememberScrollState()

    AppTabGlassBackdrop(modifier = modifier.padding(shadowPadding)) { backdrop, glassEnabled ->
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportWidth = if (constraints.hasBoundedWidth) this.maxWidth else desiredWidth
            val expandedWidth = maxOf(viewportWidth, desiredWidth)
            val rowWidth = if (fillEqually) {
                viewportWidth
            } else {
                widthLimit?.let {
                    minOf(expandedWidth, it * tabs.size + gapsWidth + trackPadding * 2)
                } ?: expandedWidth
            }
            val actualItemWidth = (rowWidth - gapsWidth - trackPadding * 2) / tabs.size
            val index = selectedTabIndex.coerceIn(0, tabs.lastIndex)
            val compactLabels = fillEqually && actualItemWidth < naturalItemWidth
            val contentColor = MiuixTheme.colorScheme.onSurface
            val selectedColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else contentColor
            LaunchedEffect(index, tabs, rowWidth, viewportWidth, density, scrollState.maxValue) {
                if (viewportWidth <= 0.dp || scrollState.maxValue == Int.MAX_VALUE) return@LaunchedEffect
                val start = with(density) { ((actualItemWidth + gap) * index).roundToPx() }
                val end = start + with(density) { (actualItemWidth + trackPadding * 2).roundToPx() }
                val viewport = with(density) { viewportWidth.roundToPx() }
                val current = scrollState.value
                val target = when {
                    start < current -> start
                    end > current + viewport -> end - viewport
                    else -> current
                }.coerceIn(0, scrollState.maxValue)
                if (target != current) scrollState.animateScrollTo(target)
            }
            AppSegmentedTabSurface(
                backdrop = backdrop,
                glassEnabled = glassEnabled,
                modifier = Modifier.width(minOf(rowWidth, viewportWidth)),
                height = height,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .then(if (fillEqually) Modifier else Modifier.horizontalScroll(scrollState))
                ) {
                    // The surface stays fixed; only overflowing, natural-width options scroll.
                    Box(Modifier.width(rowWidth)) {
                        GlassSegmentedTabRow(
                            tabs = tabs,
                            selectedIndex = index,
                            onSelect = onTabSelected,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            surfaceAlpha = 0f,
                            material = null,
                            stroke = null,
                            shadow = null,
                            trackColor = Color.Transparent,
                            height = height,
                            selectedContentColor = if (compactLabels) Color.Transparent else selectedColor,
                            contentColor = if (compactLabels) Color.Transparent else contentColor,
                        )
                        if (compactLabels) {
                            // The library reserves 16dp per side. Compact visual labels let the
                            // existing options fit, while its tabs keep semantics, focus and input.
                            Row(
                                modifier = Modifier.matchParentSize()
                                    .padding(trackPadding)
                                    .clearAndSetSemantics {},
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                tabs.forEachIndexed { tabIndex, label ->
                                    Box(
                                        modifier = Modifier.weight(1f).height(height - trackPadding * 2),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppText(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            style = if (index == tabIndex) selectedStyle else normalStyle,
                                            color = if (index == tabIndex) selectedColor else contentColor,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        if (rowWidth > viewportWidth) {
                            AppSegmentedTabOverflowTargets(
                                tabCount = tabs.size,
                                onSelect = onTabSelected,
                                modifier = Modifier.matchParentSize(),
                                height = height,
                            )
                        }
                    }
                }
            }
        }
    }
}
