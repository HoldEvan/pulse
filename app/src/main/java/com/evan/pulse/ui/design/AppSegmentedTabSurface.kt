package com.evan.pulse.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassShadows

// Float extends 8dp on every side. Leave 10dp so parent cards/scroll containers do not trim
// its soft edge, including density rounding and anti-aliasing.
internal val AppSegmentedTabShadowPadding = PaddingValues(10.dp)

/** A stationary glass track whose contents can scroll without moving or clipping its shadow. */
@Composable
internal fun AppSegmentedTabSurface(
    backdrop: Backdrop,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = GlassSegmentedTabRowDefaults.Height,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = GlassSegmentedTabRowDefaults.shape(height)
    val trackColor = GlassSegmentedTabRowDefaults.restingTrackColor()
    Box(
        modifier = modifier
            .height(height)
            .background(trackColor, shape)
            .glassPanel(
                backdrop = backdrop.takeIf { glassEnabled },
                shape = shape,
                material = GlassSegmentedTabRowDefaults.material(),
                stroke = GlassSegmentedTabRowDefaults.stroke(),
                shadow = GlassShadows.Float,
                shading = false,
                fallback = Modifier.background(trackColor, shape),
            ),
    ) {
        // Clip only the scrolling contents. The glass surface and its shadow are outside this clip.
        Box(Modifier.matchParentSize().clip(shape), content = content)
    }
}
