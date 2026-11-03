package com.evan.pulse.ui.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evan.pulse.ui.common.LocalStickyTabInset
import com.evan.pulse.ui.http.HttpScreen
import com.evan.pulse.ui.http.HttpViewModel
import com.evan.pulse.ui.mqtt.MqttScreen
import com.evan.pulse.ui.sse.SseScreen
import com.evan.pulse.ui.ws.WsScreen

/**
 * 测试页：HTTP / SSE / WS / MQTT 四个协议页的容器。
 *
 * 协议标签条由外层的磨砂顶栏承载（见 [ProtocolTabBar]），本容器只负责按
 * [protocolTab] 切换页面，并把标签条高度经 [LocalStickyTabInset] 透传给各协议页，
 * 让它们的滚动内容在顶栏让位之外再留出标签条高度，从而从磨砂标签条下方穿过。
 */
@Composable
fun TestScreen(
    modifier: Modifier = Modifier,
    onOpenScriptEditor: (Int) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenCookies: () -> Unit = {},
    onOpenHtmlPreview: (() -> Unit)? = null,
    protocolTab: Int = 0,
    stickyTabInset: Dp = 0.dp,
    // 必须显式传入：脚本编辑页是独立 route entry，与这里默认 viewModel() 的实例不同，
    // 共用同一实例才能让脚本编辑内容回显并保存
    httpViewModel: HttpViewModel
) {
    CompositionLocalProvider(LocalStickyTabInset provides stickyTabInset) {
        Column(modifier = modifier.fillMaxSize()) {
            when (protocolTab) {
                0 -> HttpScreen(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onOpenScriptEditor = onOpenScriptEditor,
                    onOpenHistory = onOpenHistory,
                    onOpenCookies = onOpenCookies,
                    onOpenHtmlPreview = onOpenHtmlPreview,
                    viewModel = httpViewModel
                )

                1 -> SseScreen(modifier = Modifier.weight(1f).fillMaxWidth())
                2 -> WsScreen(modifier = Modifier.weight(1f).fillMaxWidth())
                else -> MqttScreen(modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
