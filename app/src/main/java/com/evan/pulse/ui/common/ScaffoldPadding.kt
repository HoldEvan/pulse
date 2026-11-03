package com.evan.pulse.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 顶栏高度在收缩动画期间每帧变化，内容在组合阶段读取该值作为滚动容器的顶部让位：
 * 内容铺满顶栏下方、首项以顶部内边距让位，滚动时内容从顶栏下方穿过，
 * 磨砂顶栏得以取样到内容。
 */

/** 标签页/二级页内容滚动容器顶部让位：由承载顶栏的 Scaffold 提供其当前高度 */
val LocalTopBarInset = compositionLocalOf { 0.dp }

/**
 * 吸顶标签条在顶栏区域中的额外高度：标签条与顶栏共用同一块磨砂背景且盖在内容之上，
 * 内容滚动容器据此在顶栏让位之外再留出这段距离，首项才能完整露出、滚动时从标签条下方穿过。
 */
val LocalStickyTabInset = compositionLocalOf { 0.dp }
