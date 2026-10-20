// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.evan.pulse.ui.about.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import kotlin.math.floor
import com.evan.pulse.ui.design.AppTheme

/** 从预设的颜色数组（每 4 个浮点一组 RGBA）解析出渐变色列表 */
private fun presetColors(config: BgEffectConfig.Config): List<Color> {
    val colors = config.colors1
    val count = colors.size / 4
    return (0 until count).map { i ->
        val base = i * 4
        Color(colors[base], colors[base + 1], colors[base + 2], colors[base + 3])
    }
}

/**
 * HyperOS 风格的动态渐变背景：AGSL 运行时着色器绘制光斑，
 * 颜色随时间在多组预设间插值流动；滚动时随 [alpha] 渐隐。
 * 设备不支持 RuntimeShader 时退化为透明容器。
 */
@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    isOs3Effect: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        // RuntimeShader 不可用时退化为同配色系的静态线性渐变，保证任何设备都有背景色
        val isDarkTheme = isSystemInDarkTheme()
        val preset = remember(isDarkTheme, isOs3Effect) {
            BgEffectConfig.get(DeviceType.PHONE, isDarkTheme, isOs3Effect)
        }
        val gradientColors = remember(preset) { presetColors(preset) }
        Box(modifier = modifier) {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .then(bgModifier)
                    .background(Brush.verticalGradient(gradientColors))
            )
            content()
        }
        return
    }
    Box(
        modifier = modifier,
    ) {
        val surface = AppTheme.colors.surface
        val isDarkTheme = isSystemInDarkTheme()
        val painter = remember(isOs3Effect) { BgEffectPainter(isOs3Effect) }

        val preset = remember(isDarkTheme, isOs3Effect) {
            BgEffectConfig.get(DeviceType.PHONE, isDarkTheme, isOs3Effect)
        }

        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect

            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    deviceType = DeviceType.PHONE,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                ),
        )
        content()
    }
}
