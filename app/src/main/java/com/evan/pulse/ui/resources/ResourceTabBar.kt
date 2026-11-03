package com.evan.pulse.ui.resources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle
import com.evan.pulse.ui.l10n.localizedLabels

internal val resourceTabLabels = listOf(
    R.string.tab_projects,
    R.string.tab_collection,
    R.string.tab_env
)

/**
 * 资源页顶部的项目/集合/环境标签条：与顶栏同处磨砂背景内、盖在内容之上，
 * 滚动时固定在顶栏下方，内容从其下方穿过。
 *
 * 高度经 [onHeightChange] 回传给承载页，用于计算内容滚动容器的顶部让位。
 */
@Composable
fun ResourceTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChange: (Dp) -> Unit = {}
) {
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) }
    ) {
        AppTabRow(
            tabs = resourceTabLabels.localizedLabels(),
            selectedTabIndex = selectedIndex.coerceIn(0, resourceTabLabels.lastIndex),
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        // Material 皮肤标签栏更矮，补一点间距，与 Miuix 的视觉高度对齐
        if (LocalUiStyle.current == UiStyle.MATERIAL) {
            Spacer(Modifier.height(8.dp))
        }
    }
}
