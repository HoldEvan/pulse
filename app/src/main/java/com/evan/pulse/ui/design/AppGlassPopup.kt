package com.evan.pulse.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals

/** 根层内容采样层：弹出面板取它做实时模糊；关闭模糊时为 null，面板退化为半透明填充 */
val LocalPopupBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 弹出面板材质：有采样层时是玻璃模糊，没有时用半透明表面色兜底，两者都不压暗窗口 */
@Composable
internal fun rememberGlassPopupVisuals(): GlassPopupVisuals {
    val container = AppTheme.colors.surfaceContainer
    return GlassPopupDefaults.visuals(
        containerColor = container.copy(alpha = 0.92f)
    )
}
