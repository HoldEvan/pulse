package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏磨砂背景的采样层：捕获页面内容供顶栏实时模糊取样；
 * 运行时不支持 RuntimeShader 的设备返回 null，顶栏退化为纯色。
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) return null
    // 铺页面背景色而非 surface：顶栏模糊结果与内容背景同色，
    // 未滚动时顶栏与内容交界无色差（视觉上的"顶栏阴影"）
    val backgroundColor = MiuixTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
}

/** 顶栏磨砂的混合色：以 surface 色调和模糊后的页面内容 */
@Composable
private fun barBlurColors(): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
    )
)

/**
 * 磨砂顶栏容器：backdrop 有效时对采样到的页面内容做实时模糊，
 * 作为顶栏背景，滚动内容从顶栏下方穿过时呈毛玻璃效果。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
    scrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = if (isBlurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = barBlurColors()
            )
        } else {
            Modifier
        }
    ) {
        content()
    }
}

/** 磨砂激活时顶栏自身透明，露出模糊背景；否则回退 surface 纯色 */
@Composable
fun blurredBarColor(isBlurActive: Boolean): Color =
    if (isBlurActive) Color.Transparent else MiuixTheme.colorScheme.surface
