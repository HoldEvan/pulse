package com.evan.pulse.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.http.HttpScreen
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.mqtt.MqttScreen
import com.evan.pulse.ui.sse.SseScreen
import com.evan.pulse.ui.ws.WsScreen
import top.yukonga.miuix.kmp.basic.TabRowWithContour

private val homeTabLabels = listOf(
    R.string.tab_http,
    R.string.tab_sse,
    R.string.tab_ws,
    R.string.tab_mqtt
)

/** 首页：HTTP / WS / MQTT / SSE 四个协议页通过顶部 TabRow 切换 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, onOpenScriptEditor: (Int) -> Unit = {}) {
    var protocol by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRowWithContour(
            tabs = homeTabLabels.localizedLabels(),
            selectedTabIndex = protocol,
            onTabSelected = { protocol = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when (protocol) {
            0 -> HttpScreen(modifier = Modifier.fillMaxSize(), onOpenScriptEditor = onOpenScriptEditor)
            1 -> SseScreen(modifier = Modifier.fillMaxSize())
            2 -> WsScreen(modifier = Modifier.fillMaxSize())
            else -> MqttScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
