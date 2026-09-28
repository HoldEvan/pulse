package com.pulse.apitester.ui.mqtt

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
import androidx.compose.material.icons.filled.Close
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
import com.pulse.apitester.domain.model.MqttConnectionState
import com.pulse.apitester.ui.history.UiEventLine
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MqttScreen(modifier: Modifier = Modifier, viewModel: MqttViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lines = viewModel.lines
    val missingVars = viewModel.missingVars

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SmallTitle(text = "Broker 连接")
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
                        value = viewModel.brokerUrl,
                        onValueChange = { viewModel.brokerUrl = it },
                        label = "tcp:// 或 ssl:// 地址",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = viewModel.clientId,
                        onValueChange = { viewModel.clientId = it },
                        label = "Client ID",
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = viewModel.keepAliveSec,
                        onValueChange = { viewModel.keepAliveSec = it },
                        label = "KeepAlive(s)",
                        modifier = Modifier
                            .weight(0.6f)
                            .padding(start = 8.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = viewModel.username,
                        onValueChange = { viewModel.username = it },
                        label = "用户名（可选）",
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = "密码（可选）",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.connect() },
                        enabled = state == MqttConnectionState.IDLE || state == MqttConnectionState.DISCONNECTED || state == MqttConnectionState.FAILED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Text("连接", modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.disconnect() },
                        enabled = state == MqttConnectionState.CONNECTED,
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

        SmallTitle(text = "订阅")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = viewModel.subTopic,
                        onValueChange = { viewModel.subTopic = it },
                        label = "Topic，支持 + 与 # 通配符",
                        modifier = Modifier.weight(1f)
                    )
                    SuperDropdown(
                        items = listOf("QoS 0", "QoS 1", "QoS 2"),
                        selectedIndex = viewModel.subQos,
                        title = "订阅 QoS",
                        modifier = Modifier
                            .weight(0.4f)
                            .padding(start = 6.dp),
                        showValue = true,
                        onSelectedIndexChange = { viewModel.subQos = it }
                    )
                    Button(
                        onClick = { viewModel.addSubscription() },
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text("订阅")
                    }
                }
                viewModel.subscriptions.forEachIndexed { index, sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${sub.topic} · QoS${sub.qos}",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "取消订阅",
                            modifier = Modifier
                                .clickable { viewModel.removeSubscription(index) }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        SmallTitle(text = "发布")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = viewModel.pubTopic,
                        onValueChange = { viewModel.pubTopic = it },
                        label = "目标 Topic",
                        modifier = Modifier.weight(1f)
                    )
                    SuperDropdown(
                        items = listOf("QoS 0", "QoS 1", "QoS 2"),
                        selectedIndex = viewModel.pubQos,
                        title = "发布 QoS",
                        modifier = Modifier
                            .weight(0.4f)
                            .padding(start = 6.dp),
                        showValue = true,
                        onSelectedIndexChange = { viewModel.pubQos = it }
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = viewModel.pubPayload,
                        onValueChange = { viewModel.pubPayload = it },
                        label = "负载内容，支持 {{var}}",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.publish() },
                        enabled = state == MqttConnectionState.CONNECTED,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发布")
                    }
                }
            }
        }

        SmallTitle(text = "消息流")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "清空消息流",
                modifier = Modifier
                    .clickable { viewModel.clearLog() }
                    .padding(end = 6.dp, bottom = 4.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                items(lines) { line ->
                    EventLine(line)
                }
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

private fun stateLabel(state: MqttConnectionState): String = when (state) {
    MqttConnectionState.IDLE -> "未连接"
    MqttConnectionState.CONNECTING -> "连接中"
    MqttConnectionState.CONNECTED -> "已连接"
    MqttConnectionState.DISCONNECTED -> "已断开"
    MqttConnectionState.FAILED -> "失败"
}

@Composable
private fun stateColor(state: MqttConnectionState): Color = when (state) {
    MqttConnectionState.IDLE -> Color.Gray
    MqttConnectionState.CONNECTING -> Color(0xFF1565C0)
    MqttConnectionState.CONNECTED -> Color(0xFF2E7D32)
    MqttConnectionState.DISCONNECTED -> Color.Gray
    MqttConnectionState.FAILED -> Color(0xFFC62828)
}

@Composable
private fun EventLine(line: UiEventLine) {
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
        Text(text = time, fontSize = 11.sp)
        Text(text = line.text, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
