package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.evan.pulse.ui.common.FloatingNavItemWithLabel
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem

/** 底栏条目的皮肤无关描述：图标与文字由调用方给出，皮肤决定如何排布 */
data class AppBottomBarItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * 标签页底栏。
 *
 * Miuix 皮肤保留原来的三种形态：玻璃悬浮栏（由调用方按能力判断后单独绘制）、
 * 悬浮导航栏、贴边导航栏；Material 皮肤统一使用 M3 的 NavigationBar，
 * 悬浮与否属于 Miuix 的视觉语言，Material 下不做悬浮变体。
 */
@Composable
fun AppBottomBar(
    items: List<AppBottomBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    floating: Boolean = false,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        if (floating) {
            FloatingNavigationBar(
                modifier = modifier,
                color = AppTheme.colors.surfaceContainer
            ) {
                items.forEachIndexed { index, item ->
                    if (showLabels) {
                        FloatingNavItemWithLabel(
                            selected = selectedIndex == index,
                            onClick = { onItemSelected(index) },
                            icon = item.icon,
                            label = item.label
                        )
                    } else {
                        FloatingNavigationBarItem(
                            modifier = Modifier.size(56.dp),
                            selected = selectedIndex == index,
                            onClick = { onItemSelected(index) },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            }
        } else {
            MiuixNavigationBar(
                modifier = modifier,
                mode = if (showLabels) {
                    NavigationBarDisplayMode.IconAndText
                } else {
                    NavigationBarDisplayMode.IconOnly
                }
            ) {
                items.forEachIndexed { index, item ->
                    MiuixNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onItemSelected(index) },
                        icon = item.icon,
                        label = item.label
                    )
                }
            }
        }
        return
    }

    NavigationBar(modifier = modifier) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = materialAppIcon(item.icon),
                        contentDescription = null
                    )
                },
                label = if (showLabels) {
                    { AppText(text = item.label, style = AppTheme.typography.footnote2) }
                } else {
                    null
                }
            )
        }
    }
}
