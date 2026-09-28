package com.pulse.apitester.ui.ws

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.apitester.domain.model.WsConnectionState
import com.pulse.apitester.ui.history.UiEventLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val prettyJson = Json { prettyPrint = true }

@Composable
fun WsScreen(modifier: Modifier = Modifier, viewModel: WsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lines = viewModel.lines
    val missingVars = viewModel.missingVars

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SmallTitle(text = "连接")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stateLabel(state),
                        color = stateColor(state),
                        modifier = Modifier
                            .background(stateColor(state).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    TextField(
                        value = viewModel.url,
                        onValueChange = { viewModel.url = it },
                        label = "ws:// 或 wss:// 地址，支持 {{var}}",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.connect() },
                        enabled = state == WsConnectionState.IDLE || state == WsConnectionState.CLOSED || state == WsConnectionState.FAILED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Text("连接", modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.disconnect() },
                        enabled = state == WsConnectionState.CONNECTED || state == WsConnectionState.CONNECTING,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null)
                        Text("断开", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                viewModel.lastError?.let { err ->
                    Text(
                        text = "错误：$err",
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        SmallTitle(text = "消息流")
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "清空",
                        modifier = Modifier
                            .clickable { viewModel.clearLog() }
                            .padding(6.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        EventLine(line)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = viewModel.input,
                onValueChange = { viewModel.input = it },
                label = "要发送的消息",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.send() },
                enabled = state == WsConnectionState.CONNECTED,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "发送")
            }
        }
    }

    missingVars?.let { missing ->
        SuperDialog(
            show = true,
            title = "缺失环境变量",
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                Text("以下变量在激活环境中不存在：")
                Text(missing.joinToString(", "), modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    text = "知道了",
                    onClick = { viewModel.dismissMissing() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun stateLabel(state: WsConnectionState): String = when (state) {
    WsConnectionState.IDLE -> "未连接"
    WsConnectionState.CONNECTING -> "连接中"
    WsConnectionState.CONNECTED -> "已连接"
    WsConnectionState.CLOSED -> "已断开"
    WsConnectionState.FAILED -> "失败"
}

@Composable
private fun stateColor(state: WsConnectionState): Color = when (state) {
    WsConnectionState.IDLE -> Color.Gray
    WsConnectionState.CONNECTING -> Color(0xFF1565C0)
    WsConnectionState.CONNECTED -> Color(0xFF2E7D32)
    WsConnectionState.CLOSED -> Color.Gray
    WsConnectionState.FAILED -> Color(0xFFC62828)
}

@Composable
private fun EventLine(line: UiEventLine) {
    val directionLabel = if (line.isSystem) "系统" else "收"
    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(line.timestamp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (line.isSystem) MiuixTheme.colorScheme.surfaceVariant else MiuixTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Text(text = "$time · $directionLabel", fontSize = 11.sp)
        Text(
            text = prettyLine(line.text),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun prettyLine(text: String): String = try {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
} catch (_: Exception) {
    text
}
