package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏磨砂的模糊样式 */
enum class BarBlurStyle { GAUSSIAN, PROGRESSIVE }

/** 模糊效果总开关，由主题层按设置项提供；关闭时采样层返回 null，顶栏退化为纯色 */
val LocalBlurEnabled = staticCompositionLocalOf { true }

/** 顶栏模糊样式，由主题层按设置项提供 */
val LocalBarBlurStyle = staticCompositionLocalOf { BarBlurStyle.GAUSSIAN }

/**
 * 顶栏磨砂背景的采样层：捕获页面内容供顶栏实时模糊取样；
 * 关闭模糊或运行时不支持 RuntimeShader 的设备返回 null，顶栏退化为纯色。
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!LocalBlurEnabled.current) return null
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) return null
    // 铺 surface 底色与 miuix example 一致：未滚动时顶栏模糊结果与页面背景同色
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 顶栏磨砂的混合色：以 surface 色调和模糊后的页面内容。
 *  叠色压到 0.15，让页面内容大幅透出磨砂（本应用内容多为 surface 同色卡片，
 *  叠色偏高会把模糊结果冲淡成纯色条，看不出磨砂）。 */
@Composable
private fun barBlurColors(): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.15f))
    )
)

/**
 * 磨砂顶栏容器：backdrop 有效时对采样到的页面内容做实时模糊，
 * 作为顶栏背景，滚动内容从顶栏下方穿过时呈毛玻璃效果。
 *
 * 高斯样式整条顶栏均匀模糊；渐进样式先做整条高斯模糊，再用自上而下的
 * alpha 渐变遮罩把底部逐渐擦淡，形成"顶部最强、向下渐隐"的毛玻璃过渡。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
    content: @Composable () -> Unit
) {
    val progressive = LocalBarBlurStyle.current == BarBlurStyle.PROGRESSIVE
    val blurActive = isBlurActive && backdrop != null
    Box(
        modifier = if (blurActive && !progressive) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 14f,
                colors = barBlurColors()
            )
        } else {
            Modifier
        }
    ) {
        if (blurActive && progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // 独立离屏层：让 DstIn 遮罩只作用于本层模糊结果，不影响下层内容
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // 顶部保留、向下渐隐到完全透明，形成渐进模糊
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.45f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 24f,
                        colors = barBlurColors()
                    )
            )
        }
        content()
    }
}

/** 磨砂激活时顶栏自身透明，露出模糊背景；否则回退 surface 纯色 */
@Composable
fun blurredBarColor(isBlurActive: Boolean): Color =
    if (isBlurActive) Color.Transparent else MiuixTheme.colorScheme.surface
