package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors as MiuixCardColors
import top.yukonga.miuix.kmp.squircle.squircleClip

/** Miuix 卡片的圆角半径，与 Miuix Card 默认值一致 */
private val CardCornerRadius = 16.dp

/**
 * 卡片内容裁剪：让卡片内的可点击行（偏好行、列表项）的水波纹沿卡片圆角收边。
 *
 * Miuix 与 Material 的圆角规格不同，各取各的形状，裁剪边界才和外沿一致。
 */
@Composable
fun Modifier.cardContentClip(): Modifier =
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        squircleClip(CardCornerRadius)
    } else {
        clip(MaterialTheme.shapes.extraLarge)
    }

/**
 * 卡片：Miuix 皮肤用 Miuix Card，Material 皮肤用 M3 的 tonal Card。
 *
 * 内容边距留给调用方（与 Miuix Card 默认 insideMargin = 0 一致），
 * 换肤后同一份页面代码的间距不会变化。可点击卡片在 Material 下走
 * Card(onClick) 以获得水波纹与语义点击。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showIndication: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedContentColor = contentColor ?: AppTheme.colors.onSurface
    val resolvedContainerColor = containerColor ?: AppTheme.colors.surfaceContainer
    val cardContent: @Composable ColumnScope.() -> Unit = {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            Column(modifier = Modifier.cardContentClip().padding(contentPadding)) {
                content()
            }
        }
    }
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixCard(
            modifier = modifier,
            colors = MiuixCardColors(
                color = resolvedContainerColor,
                contentColor = resolvedContentColor
            ),
            showIndication = showIndication,
            onClick = onClick,
            content = cardContent
        )
        return
    }

    val colors = CardDefaults.cardColors(
        containerColor = resolvedContainerColor,
        contentColor = resolvedContentColor
    )
    val shape = MaterialTheme.shapes.extraLarge
    if (onClick == null) {
        M3Card(modifier = modifier, shape = shape, colors = colors, content = cardContent)
    } else {
        M3Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            content = cardContent
        )
    }
}
