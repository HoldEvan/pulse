// Adapted from compose-miuix-ui's GlassDropdownPreference and GlassPopupItem.
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.evan.pulse.ui.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout
import androidx.compose.material3.LocalContentColor as MaterialContentColor
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor

/** Retains the popup host until the glass panel completes its exit animation. */
@Stable
internal class MiuixDropdownState {
    var expanded by mutableStateOf(false)
        private set
    val mounted = mutableStateOf(false)
    var contentMounted by mutableStateOf(false)

    fun setOpen(open: Boolean) {
        expanded = open
        if (open) {
            mounted.value = true
        } else if (!contentMounted) {
            mounted.value = false
        }
    }
}

/** Hosts icon menus with the same glass surface and content padding as preference selectors. */
@Composable
internal fun MiuixDropdownPopup(
    state: MiuixDropdownState,
    anchorBounds: Rect?,
    backdrop: Backdrop?,
    visuals: GlassPopupVisuals,
    anchor: GlassPopupAnchor? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.mounted.value) return
    PopupLayout(
        visible = state.mounted,
        enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val bounds = anchorBounds ?: return@Box
            GlassDropdownPopup(
                show = state.expanded && enabled,
                onDismissRequest = { state.setOpen(false) },
                anchorBounds = bounds,
                anchor = anchor,
                backdrop = backdrop,
                visuals = visuals,
            ) {
                DisposableEffect(state) {
                    state.contentMounted = true
                    onDispose {
                        state.contentMounted = false
                        state.mounted.value = false
                    }
                }
                content()
            }
        }
    }
}

/** Matches GlassPopupItem's default rounded feedback while accepting composable icons. */
@Composable
internal fun MiuixDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: (@Composable (Modifier) -> Unit)? = null,
    summary: String? = null,
    enabled: Boolean = true,
    showSelection: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemShape = remember { RoundedCornerShape(GlassPopupDefaults.ItemPressRadius) }
    val pressedColor = GlassPopupDefaults.itemPressedColor()
    val indication = remember(pressedColor) {
        MiuixIndication(color = pressedColor.copy(alpha = 1f), pressAlpha = pressedColor.alpha)
    }
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.4f)
        selected -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceContainer
    }
    val summaryColor = MiuixTheme.colorScheme.onSurfaceContainerVariant.let {
        if (enabled) it else it.copy(alpha = 0.4f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GlassPopupDefaults.ItemMinHeight)
            .padding(horizontal = GlassPopupDefaults.ItemPressInset)
            .clip(itemShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
            )
            .padding(
                horizontal = GlassPopupDefaults.ItemPaddingHorizontal - GlassPopupDefaults.ItemPressInset,
                vertical = GlassPopupDefaults.ItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            MiuixContentColor provides color,
            MaterialContentColor provides color,
        ) {
            icon?.invoke(
                Modifier
                    .padding(end = GlassPopupDefaults.ItemIconGap)
                    .size(GlassPopupDefaults.ItemIconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2,
                        color = summaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showSelection || selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = GlassPopupDefaults.ItemIconGap)
                        .size(GlassPopupDefaults.ItemTickSize),
                    tint = if (selected) color else Color.Transparent,
                )
            }
        }
    }
}
