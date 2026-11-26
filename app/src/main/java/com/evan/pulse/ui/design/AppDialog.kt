package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassDialog

/**
 * 对话框遮罩：全应用统一一档压暗强度。
 *
 * Miuix 默认的遮罩深色下是 60% 黑，弹窗一多显得过重；
 * 这里统一降到深色 32% / 浅色 16%，两套皮肤、所有对话框共用。
 */
@Composable
private fun dialogDimColor(): Color {
    val isDark = AppTheme.colors.background.luminance() < 0.5f
    return Color.Black.copy(alpha = if (isDark) 0.32f else 0.16f)
}

/**
 * 对话框外壳：Miuix 皮肤用官方玻璃对话框 GlassDialog（磨砂采样经
 * [LocalPopupBackdrop]，未开启模糊时回退纯色填充），Material 皮肤用 M3 的
 * BasicAlertDialog + Surface 承载同一份内容。
 *
 * 只封装外壳（容器、圆角、底色、标题排版），内容由调用方提供，
 * 因此两套皮肤共用同一份对话框内容，不会出现两套文案。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        GlassDialog(
            show = show,
            backdrop = LocalPopupBackdrop.current,
            modifier = modifier,
            title = title,
            summary = summary,
            dimColor = dialogDimColor(),
            onDismissRequest = onDismissRequest,
            content = content
        )
        return
    }

    if (!show) return
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(AppTheme.radii.dialog),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                if (!title.isNullOrEmpty()) {
                    AppText(text = title, style = typography.headline1)
                }
                if (!summary.isNullOrEmpty()) {
                    AppText(
                        text = summary,
                        modifier = Modifier.padding(top = 8.dp),
                        color = colors.onSurfaceSummary,
                        style = typography.footnote1
                    )
                }
                Column(
                    modifier = Modifier.padding(top = if (title.isNullOrEmpty()) 0.dp else 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
