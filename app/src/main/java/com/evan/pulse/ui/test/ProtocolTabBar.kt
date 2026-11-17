package com.evan.pulse.ui.test

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
import com.evan.pulse.ui.l10n.localizedLabels

internal val protocolTabLabels = listOf(
    R.string.tab_http,
    R.string.tab_sse,
    R.string.tab_ws,
    R.string.tab_mqtt
)

/**
 * 调试页顶部的协议标签条：与顶栏同处磨砂背景内、盖在内容之上，
 * 滚动时固定在顶栏下方，内容从其下方穿过。
 *
 * 高度经 [onHeightChange] 回传给承载页，用于计算内容滚动容器的顶部让位。
 */
@Composable
fun ProtocolTabBar(
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
            tabs = protocolTabLabels.localizedLabels(),
            selectedTabIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        // 吸顶后标签条紧贴磨砂顶栏底边，补一段间距让下方内容不显得拥挤
        Spacer(Modifier.height(8.dp))
    }
}
