package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.evan.pulse.ui.common.LocalBlurEnabled
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/** Only provide a scene that excludes the tabs themselves, such as the content below a header. */
internal val LocalTabBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Cards supply their actual surface colour for inline tabs. */
internal val LocalTabSurfaceColor = staticCompositionLocalOf<Color?> { null }

/**
 * Inline tabs have no scrolling content underneath them. Record their host surface in a separate
 * sibling layer instead of sampling an ancestor that also contains the glass being drawn.
 */
@Composable
internal fun AppTabGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Backdrop, Boolean) -> Unit,
) {
    val enabled = LocalBlurEnabled.current && isRuntimeShaderSupported()
    val sceneBackdrop = LocalTabBackdrop.current.takeIf { enabled }
    val surfaceColor = LocalTabSurfaceColor.current ?: AppTheme.colors.surface
    // GlassSegmentedTabRow requires a non-null backdrop even when its material is disabled.
    val inlineBackdrop = if (sceneBackdrop == null) {
        rememberLayerBackdrop(onDraw = { drawRect(surfaceColor) })
    } else {
        null
    }
    Box(modifier = modifier) {
        if (enabled && inlineBackdrop != null) {
            // The empty source draws nothing on screen; onDraw records only into the backdrop.
            Box(Modifier.matchParentSize().layerBackdrop(inlineBackdrop))
        }
        content(sceneBackdrop ?: requireNotNull(inlineBackdrop), enabled)
    }
}
