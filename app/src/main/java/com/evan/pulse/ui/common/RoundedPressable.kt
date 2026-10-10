package com.evan.pulse.ui.common

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 空指示器：不用 clickable 默认的矩形涟漪时传它 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun hashCode() = 0
    override fun equals(other: Any?) = other is NoIndication

    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() = drawContent()
    }
}

/**
 * 带圆角按压反馈的可点击修饰符。
 *
 * 本仓库 Compose 版本的 clickable 没有 shape 参数，默认涟漪是矩形的；这里关掉系统涟漪，
 * 改成按压时画一块 [cornerRadius] 圆角的半透明矩形。
 *
 * 圆角高亮用 drawWithContent 画，而不是放进 IndicationNodeFactory：clickable 会把指示器节点插到
 * 链条最内侧，只包住内容本身，外层的 padding 不在它里面，高亮就会紧贴文字。
 *
 * Compose 修饰符链是「先写的在外层」，padding 只给它的内层让出空间，所以本修饰符必须写在
 * padding 之前，padding 才会同时算进点击区和圆角高亮：
 * `Modifier.roundedPressable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp)`
 *
 * 反过来写成 `.padding(...).roundedPressable {}` 时 drawWithContent 落在 padding 内层，
 * 点击区和高亮都只剩内容本身的大小，看起来就像按压反馈没有生效。
 */
@Composable
fun Modifier.roundedPressable(
    cornerRadius: Dp = 6.dp,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val highlight = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    return this
        .clickable(interactionSource = interactionSource, indication = NoIndication, onClick = onClick)
        .drawWithContent {
            if (pressed) {
                drawRoundRect(
                    color = highlight,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                )
            }
            drawContent()
        }
}

