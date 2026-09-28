package com.pulse.apitester.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import com.pulse.apitester.ui.env.EnvironmentScreen
import com.pulse.apitester.ui.history.HistoryScreen
import com.pulse.apitester.ui.http.HttpScreen
import com.pulse.apitester.ui.mqtt.MqttScreen
import com.pulse.apitester.ui.project.ProjectBar
import com.pulse.apitester.ui.ws.WsScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

private data class PulseTab(
    val label: String,
    val icon: ImageVector
)

@Composable
fun PulseApp() {
    val tabs = remember {
        listOf(
            PulseTab("HTTP", Icons.Filled.Http),
            PulseTab("WS", Icons.Filled.SwapHoriz),
            PulseTab("MQTT", Icons.Filled.Hub),
            PulseTab("环境", Icons.Filled.Tune),
            PulseTab("历史", Icons.Filled.History)
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { ProjectBar() },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = tab.label
                    )
                }
            }
        }
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            )
        when (selectedTab) {
            0 -> HttpScreen(modifier = contentModifier)
            1 -> WsScreen(modifier = contentModifier)
            2 -> MqttScreen(modifier = contentModifier)
            3 -> EnvironmentScreen(modifier = contentModifier)
            4 -> HistoryScreen(modifier = contentModifier)
        }
    }
}
