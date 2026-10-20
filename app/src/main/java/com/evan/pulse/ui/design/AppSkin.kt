package com.evan.pulse.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.evan.pulse.R

/** 界面风格：决定整体控件使用 Miuix 还是 Material 3 设计语言 */
enum class UiStyle { MIUIX, MATERIAL }

/** 界面风格选项，下标与 AppSettings.uiStyleIndex 对应；0 为当前默认的 Miuix */
val UiStyleEntries: List<Pair<Int, UiStyle>> = listOf(
    R.string.ui_style_miuix to UiStyle.MIUIX,
    R.string.ui_style_material to UiStyle.MATERIAL
)

val UiStyleLabels: List<Int> = UiStyleEntries.map { it.first }

/**
 * 皮肤能力矩阵。
 *
 * 两套皮肤不是像素级互译：磨砂、squircle、玻璃底栏是 Miuix 的视觉语言，
 * Material 3 用 tonal elevation 表达层级。把差异收敛成布尔能力，
 * 调用点按能力判断，而不是到处写 `if (style == MATERIAL)`。
 */
@Immutable
data class UiCapabilities(
    /** 顶栏实时磨砂（采样页面内容做模糊） */
    val blurBars: Boolean,
    /** squircle 按压轮廓，替代水波纹 */
    val squirclePress: Boolean,
    /** 液态玻璃悬浮底栏 */
    val glassBottomBar: Boolean,
    /** 大标题随滚动收缩为小标题 */
    val largeTitleCollapse: Boolean,
    /** 用表面色阶表达层级，替代阴影 */
    val tonalElevation: Boolean,
)

private val MiuixCapabilities = UiCapabilities(
    blurBars = true,
    squirclePress = true,
    glassBottomBar = true,
    largeTitleCollapse = true,
    tonalElevation = false,
)

private val MaterialCapabilities = UiCapabilities(
    blurBars = false,
    squirclePress = false,
    glassBottomBar = false,
    largeTitleCollapse = true,
    tonalElevation = true,
)

fun UiStyle.capabilities(): UiCapabilities = when (this) {
    UiStyle.MIUIX -> MiuixCapabilities
    UiStyle.MATERIAL -> MaterialCapabilities
}

/** 当前界面风格，由主题层按设置项提供 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.MIUIX }

/** 当前皮肤能力，由主题层按 [LocalUiStyle] 提供 */
val LocalUiCapabilities = staticCompositionLocalOf { MiuixCapabilities }

/** 当前皮肤能力 */
val uiCapabilities: UiCapabilities
    @Composable @ReadOnlyComposable
    get() = LocalUiCapabilities.current

/** 当前是否为 Material 风格 */
val isMaterialStyle: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalUiStyle.current == UiStyle.MATERIAL
