// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

package com.evan.pulse.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.basic.NavigationItem
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.rememberTiltLight
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

private val LocalIosTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 胶囊边缘的高光描边：`Dual` 峰值让一个光源在轮廓上产生对称的两个亮点，
 * 对应原来 `BloomStroke(dualPeak = true)` 的效果。光源位置由 [rememberTiltLight]
 * 随设备倾角偏移，替代旧版手写的重力角旋转。
 */
private val iosIndicatorSpecular: EdgeStroke = EdgeStroke(
    width = 1.dp,
    bevel = 2.dp,
    color = Color.White.copy(alpha = 0.12f),
    primary = EdgeLightSource(0.5f, -0.3f, -0.05f, Color.White, 1f),
    secondary = EdgeLightSource(0.5f, 0.8f, -0.5f, Color.White, 0.4f),
    peak = EdgePeak.Dual,
)

/** 让高光的两路光源跟随设备倾角，避免胶囊边缘的亮点固定死在一个方向 */
@Composable
private fun rememberTiltedSpecular(base: EdgeStroke): EdgeStroke {
    val primary = rememberTiltLight(base.primary)
    val secondary = rememberTiltLight(base.secondary)
    return remember(base, primary, secondary) {
        base.copy(primary = primary, secondary = secondary)
    }
}

/** 悬浮玻璃胶囊的高度，与下方各分支的 `.height(64.dp)` 保持一致 */
internal val LiquidGlassPillHeight: Dp = 64.dp

/**
 * 悬浮玻璃底栏从屏幕底边占用的竖向空间：胶囊高度 + 底部安全区。
 *
 * 底栏浮在内容之上，页面需要用它给可滚动内容留位，否则滚到底部时最后几条会被胶囊遮住。
 */
@Composable
internal fun liquidGlassBottomInset(): Dp {
    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomPadding = when (platform()) {
        Platform.IOS -> 20.dp

        else -> {
            if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 36.dp
        }
    }
    return LiquidGlassPillHeight + bottomPadding
}

@Composable
internal fun LiquidGlassNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val accentColor = AppTheme.colors.primary
    val tabContentColor = AppTheme.colors.onSurface
    val surfaceContainer = AppTheme.colors.surfaceContainer
    val containerColor = if (isBlurActive) Color.Transparent else surfaceContainer

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onItemClickUpdated by rememberUpdatedState(onItemClick)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position ->
                position.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onItemClickUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onItemClickUpdated(index)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    // 以 dampedDrag 为键：position lambda 捕获了它，捕获到过期实例会让按压点冻结
    val interactiveHighlight = remember(animationScope, isLtr, dampedDrag) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    val specular = rememberTiltedSpecular(iosIndicatorSpecular)

    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }

    val bottomPaddingValue = liquidGlassBottomInset() - LiquidGlassPillHeight

    val tabsContent: @Composable RowScope.() -> Unit = {
        val tabScale = LocalIosTabScale.current
        items.forEachIndexed { index, item ->
            // 选中项用强调色、未选中项调淡；玻璃效果下这是静态时唯一的位置线索
            val tabColor = if (index == currentIndex) {
                accentColor
            } else {
                tabContentColor.copy(alpha = 0.55f)
            }
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        selected = index == currentIndex
                        role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val isActivationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        if (isActivationKey) {
                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = CenterHorizontally,
            ) {
                AppIcon(
                    modifier = Modifier.size(22.dp),
                    imageVector = item.icon,
                    tint = tabColor,
                    // 装饰性图标：相邻的标签已说明条目名称，避免 TalkBack 重复播报
                    contentDescription = if (showLabels) null else item.label,
                )
                if (showLabels) {
                    AppText(
                        text = item.label,
                        color = tabColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPaddingValue, start = 24.dp, end = 24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                Row(
                    modifier = Modifier
                        .selectableGroup()
                        .onSizeChanged { coords ->
                            totalWidthPx = coords.width.toFloat()
                            val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .graphicsLayer { translationX = panelOffset }
                        .dropShadow(
                            shape = pillShape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = if (isDark) 0.2f else 0.1f,
                            ),
                        )
                        .then(
                            if (isBlurActive && backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        padding = maxOf(padding, 40.dp.toPx())
                                        vibrancy()
                                        blur(
                                            4.dp.toPx(),
                                            4.dp.toPx(),
                                        )
                                        lens(
                                            refractionHeight = 24.dp.toPx(),
                                            refractionAmount = 24.dp.toPx(),
                                        )
                                    },
                                    layerBlock = {
                                        val width = size.width.coerceAtLeast(1f)
                                        val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDrag.pressProgress)
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = {
                                        drawRect(containerColor)
                                        drawEdgeStroke(pillShape, specular, alpha = 0.75f)
                                    },
                                )
                            } else {
                                Modifier
                                    .background(containerColor, pillShape)
                            },
                        )
                        .then(
                            if (isBlurActive) {
                                interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                            } else {
                                Modifier
                            },
                        )
                        .then(dampedDrag.modifier)
                        .height(64.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            if (isBlurActive && backdrop != null) {
                CompositionLocalProvider(
                    LocalIosTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
                    LocalContentColor provides accentColor,
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                            .then(interactiveHighlight.modifier)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabsContent,
                    )
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                if (isBlurActive && combinedBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val singleTabWidth = tabWidthPx
                                val progressOffset = dampedDrag.value * singleTabWidth
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            }
                            .drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { pillShape },
                                effects = {
                                    // 折射静置时就有一层，按压时再增强，否则选中项静置时几乎看不出玻璃
                                    val progress = dampedDrag.pressProgress
                                    lens(
                                        refractionHeight = (8.dp + 7.dp * progress).toPx(),
                                        refractionAmount = (10.dp + 9.dp * progress).toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                layerBlock = {
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val v = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val progress = dampedDrag.pressProgress
                                    // 选中胶囊的整体提亮，配合下方阴影形成凸起感
                                    drawRect(
                                        color = Color.White.copy(
                                            alpha = (if (isDark) 0.12f else 0.24f) *
                                                (1f - 0.45f * progress)
                                        )
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.06f * progress))
                                    drawEdgeStroke(
                                        shape = pillShape,
                                        stroke = specular,
                                        alpha = 0.5f + 0.5f * progress,
                                    )
                                },
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 5.dp + 4.dp * dampedDrag.pressProgress,
                                    color = Color.Black.copy(alpha = 0.18f),
                                    alpha = 0.7f + 0.3f * dampedDrag.pressProgress,
                                )
                            }
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDrag.value * tabWidthPx
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            }
                            .clip(pillShape)
                            .background(accentColor.copy(alpha = 0.22f), pillShape)
                            .height(56.dp)
                            .width(tabWidthDp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides accentColor) {
                            Row(
                                modifier = Modifier
                                    .clearAndSetSemantics {}
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                    .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                    .height(56.dp)
                                    .graphicsLayer {
                                        val progressOffset = dampedDrag.value * tabWidthPx
                                        translationX = if (isLtr) -progressOffset else progressOffset
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                content = tabsContent,
                            )
                        }
                    }
                }
            }
        }
    }
}
