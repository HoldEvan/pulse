package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.evan.pulse.ui.design.AppTheme

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
    val surfaceColor = AppTheme.colors.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 顶栏磨砂的混合色：以 surface 色调和模糊后的页面内容。
 *  高斯样式叠色压到 0.15，让页面内容大幅透出磨砂（本应用内容多为 surface 同色卡片，
 *  叠色偏高会把模糊结果冲淡成纯色条，看不出磨砂）；渐进样式模糊越往下越弱，
 *  叠色略提到 0.3，让顶栏下半段仍有可见的底色可以承接标题。 */
@Composable
private fun barBlurColors(progressive: Boolean = false): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(
            color = AppTheme.colors.surface.copy(alpha = if (progressive) 0.3f else 0.15f)
        )
    )
)

/**
 * 磨砂顶栏容器：backdrop 有效时对采样到的页面内容做实时模糊，
 * 作为顶栏背景，滚动内容从顶栏下方穿过时呈毛玻璃效果。
 *
 * 高斯样式整条顶栏均匀模糊；渐进样式的模糊强度沿纵向从顶部到底部递减，
 * 顶栏下沿保持清晰，形成"顶部最糊、向下渐清"的毛玻璃过渡。
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
                    // 渐进样式用库内置的渐进模糊：模糊半径本身沿纵向递减（顶部最强、
                    // 底部归零），得到的是一段由糊到清的连续过渡。之前用「整条高斯模糊
                    // 再叠 DstIn 透明度遮罩」的做法只会把模糊结果整体擦淡，
                    // 擦到的地方直接露出清晰的页面内容，看起来就像没有模糊
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 18f,
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        colors = barBlurColors(progressive = true)
                    )
            )
        }
        content()
    }
}

/** 磨砂激活时顶栏自身透明，露出模糊背景；否则回退 surface 纯色 */
@Composable
fun blurredBarColor(isBlurActive: Boolean): Color =
    if (isBlurActive) Color.Transparent else AppTheme.colors.surface
