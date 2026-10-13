package com.evan.pulse.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 二级页/标签页内容向顶栏上报"内容是否超出屏幕"的通道，由 SubPageScreen 与 TabsScreen 提供 */
val LocalSubPageCanScroll = compositionLocalOf<MutableState<Boolean>?> { null }

/**
 * 页面滚动容器专用的回弹：内容不足一屏（canScroll=false）时不挂回弹效果，
 * 页面完全静止，避免"内容没超出也能拖动"的观感；内容超出后回弹正常生效。
 */
@Composable
fun Modifier.subPageOverScroll(): Modifier {
    val canScroll = LocalSubPageCanScroll.current
    return if (canScroll?.value != false) this.overScrollVertical() else this
}

/** 页面级纵向滚动容器使用：内容不足一屏时顶栏大标题保持展开 */
@Composable
fun subPageScrollState(): ScrollState {
    val state = rememberScrollState()
    report { state.maxValue > 0 }
    return state
}

/** LazyColumn 版滚动容器使用：列表不足一屏时顶栏大标题保持展开 */
@Composable
fun subPageLazyListState(): LazyListState {
    val state = rememberLazyListState()
    report { state.canScrollForward || state.canScrollBackward }
    return state
}

/**
 * 用 snapshotFlow 收集"内容可滚动与否"，仅在布尔翻转时写入槽位；
 * 顶栏收缩动画期间 maxValue 每帧变化，若在组合期读取会导致页面反复重组。
 */
@Composable
private fun report(canScrollNow: () -> Boolean) {
    val slot = LocalSubPageCanScroll.current ?: return
    LaunchedEffect(slot) {
        snapshotFlow { canScrollNow() }.collect { slot.value = it }
    }
}
