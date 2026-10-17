package com.evan.pulse.ui.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.http.HttpScreen
import com.evan.pulse.ui.http.HttpViewModel
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.mqtt.MqttScreen
import com.evan.pulse.ui.sse.SseScreen
import com.evan.pulse.ui.ws.WsScreen
import top.yukonga.miuix.kmp.basic.TabRowWithContour

private val testTabLabels = listOf(
    R.string.tab_http,
    R.string.tab_sse,
    R.string.tab_ws,
    R.string.tab_mqtt
)

/** 测试页：HTTP / WS / MQTT / SSE 四个协议页通过顶部 TabRow 切换，协议选择由外层 TabState 持有 */
@Composable
fun TestScreen(
    modifier: Modifier = Modifier,
    onOpenScriptEditor: (Int) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    protocolTab: Int = 0,
    onProtocolTabChange: (Int) -> Unit = {},
    // 必须显式传入：脚本编辑页是独立 route entry，与这里默认 viewModel() 的实例不同，
    // 共用同一实例才能让脚本编辑内容回显并保存
    httpViewModel: HttpViewModel
) {
    Column(modifier = modifier.fillMaxSize().padding(top = LocalTopBarInset.current)) {
        TabRowWithContour(
            tabs = testTabLabels.localizedLabels(),
            selectedTabIndex = protocolTab,
            onTabSelected = onProtocolTabChange,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when (protocolTab) {
            0 -> HttpScreen(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onOpenScriptEditor = onOpenScriptEditor,
                onOpenHistory = onOpenHistory,
                viewModel = httpViewModel
            )

            1 -> SseScreen(modifier = Modifier.weight(1f).fillMaxWidth())
            2 -> WsScreen(modifier = Modifier.weight(1f).fillMaxWidth())
            else -> MqttScreen(modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
