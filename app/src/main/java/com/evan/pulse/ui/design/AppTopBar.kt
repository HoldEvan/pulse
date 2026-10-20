package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState as rememberMaterialTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState as rememberMiuixTopAppBarState

/**
 * 顶栏滚动行为：同时持有两套皮肤的收缩状态，调用点不需要区分皮肤。
 *
 * Miuix 的大标题收缩由 MiuixScrollBehavior 驱动，Material 由 M3 的
 * exitUntilCollapsedScrollBehavior 驱动；内容区只需要挂当前皮肤那一条
 * nestedScrollConnection，避免两条连接互相抢滚动事件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class AppTopBarBehavior internal constructor(
    val miuix: ScrollBehavior,
    val material: TopAppBarScrollBehavior,
    private val materialSkin: Boolean
) {
    val nestedScrollConnection: NestedScrollConnection
        get() = if (materialSkin) material.nestedScrollConnection else miuix.nestedScrollConnection

    /** 当前皮肤顶栏是否仍处于展开态，用于内容变短时判断是否需要复位 */
    val isExpanded: Boolean
        get() = if (materialSkin) {
            material.state.heightOffset >= -1f
        } else {
            miuix.state.heightOffset >= -1f
        }

    /** 展开顶栏：内容变短后避免顶栏卡在收缩态；两套皮肤的状态一起复位，换肤后不残留 */
    fun expand() {
        miuix.state.heightOffset = 0f
        miuix.state.contentOffset = 0f
        material.state.heightOffset = 0f
        material.state.contentOffset = 0f
    }
}

/** 创建一个两套皮肤都可用的顶栏滚动行为；canScroll 用于内容不足一屏时抑制收缩 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppTopBarBehavior(canScroll: () -> Boolean = { true }): AppTopBarBehavior {
    val materialSkin = isMaterialStyle
    val miuixState = rememberMiuixTopAppBarState()
    val miuix = MiuixScrollBehavior(state = miuixState, canScroll = canScroll)
    val material = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberMaterialTopAppBarState()
    )
    return remember(miuix, material, materialSkin) {
        AppTopBarBehavior(miuix, material, materialSkin)
    }
}

/**
 * 大标题顶栏：Miuix 皮肤用 TopAppBar 的大标题收缩，
 * Material 皮肤用 MediumTopAppBar，同样随滚动收缩成单行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    color: Color,
    behavior: AppTopBarBehavior,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = titleColor,
            subtitle = subtitle ?: "",
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = behavior.miuix
        )
        return
    }

    val colors = AppTheme.colors
    MediumTopAppBar(
        title = {
            Column {
                AppText(
                    text = title,
                    color = if (titleColor == Color.Unspecified) colors.onSurface else titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.typography.title3
                )
                if (!subtitle.isNullOrEmpty()) {
                    AppText(
                        text = subtitle,
                        color = if (subtitleColor == Color.Unspecified) {
                            colors.onSurfaceSummary
                        } else {
                            subtitleColor
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = AppTheme.typography.footnote1
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = color,
            titleContentColor = if (titleColor == Color.Unspecified) colors.onSurface else titleColor,
            navigationIconContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface
        ),
        scrollBehavior = behavior.material
    )
}

/**
 * 单行顶栏：用于不需要大标题的页面（关于页等）。
 *
 * Material 皮肤用 M3 的 TopAppBar，屏幕边缘到边缘的背景交给调用方的磨砂容器，
 * 因此这里不额外加状态栏内边距。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSmallTopBar(
    title: String,
    color: Color,
    behavior: AppTopBarBehavior,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    defaultWindowInsetsPadding: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSmallTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = titleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = behavior.miuix,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding
        )
        return
    }

    val colors = AppTheme.colors
    TopAppBar(
        title = {
            AppText(
                text = title,
                color = if (titleColor == Color.Unspecified) colors.onSurface else titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.headline1
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = if (defaultWindowInsetsPadding) {
            TopAppBarDefaults.windowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = color,
            titleContentColor = if (titleColor == Color.Unspecified) colors.onSurface else titleColor,
            navigationIconContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface
        ),
        scrollBehavior = behavior.material
    )
}
