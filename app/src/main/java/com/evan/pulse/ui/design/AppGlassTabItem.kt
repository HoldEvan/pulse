package com.evan.pulse.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults

/**
 * rc01's segmented track consumes horizontal drags to move its indicator. When the track overflows,
 * these sibling targets let the enclosing horizontalScroll handle swipes while keeping tab taps.
 * Accessibility and keyboard selection stay on the official track underneath.
 */
@Composable
internal fun AppSegmentedTabOverflowTargets(
    tabCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = GlassSegmentedTabRowDefaults.Height,
) {
    val padding = GlassSegmentedTabRowDefaults.TrackPadding
    val shape = GlassSegmentedTabRowDefaults.shape(height - padding * 2)
    Row(
        modifier = modifier.padding(padding),
        horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap),
    ) {
        repeat(tabCount) { index ->
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .focusProperties { canFocus = false }
                    .clickable { onSelect(index) }
                    .clearAndSetSemantics {},
            )
        }
    }
}
