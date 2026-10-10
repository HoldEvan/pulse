package com.evan.pulse.ui.ws

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.EventLine
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.roundedPressable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
private fun SectionTitleRow(label: String, onSaveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallTitle(text = label, modifier = Modifier.weight(1f))
        IconButton(onClick = onSaveClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = stringResource(R.string.cd_save_request)
            )
        }
    }
}

/** WS 流面板：顶部状态 + URL + 发送 / 清空 / 连接，底部消息流，已收数据主色容器底色 */
@Composable
fun WsScreen(modifier: Modifier = Modifier, viewModel: WsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lines = viewModel.lines
    val missingVars = viewModel.missingVars
    val systemLabel = stringResource(R.string.ws_direction_system)
    val recvLabel = stringResource(R.string.ws_direction_recv)
    var showSave by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SectionTitleRow(
            label = stringResource(R.string.action_connect),
            onSaveClick = { showSave = true }
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = viewModel.url,
                        onValueChange = { viewModel.url = it },
                        label = stringResource(R.string.ws_url_label),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )
                    ConnectionStateIndicator(
                        color = stateColor(state),
                        label = stateLabel(state)
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = viewModel.pingIntervalSec,
                        onValueChange = { viewModel.pingIntervalSec = it },
                        label = stringResource(R.string.ws_heartbeat_interval_label),
                        modifier = Modifier.weight(0.5f)
                    )
                    TextField(
                        value = viewModel.pingPayload,
                        onValueChange = { viewModel.pingPayload = it },
                        label = stringResource(R.string.ws_heartbeat_payload_label),
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
                        enabled = state == WsConnectionState.IDLE || state == WsConnectionState.CLOSED || state == WsConnectionState.FAILED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(MiuixIcons.Light.Link, contentDescription = null)
                        Text(stringResource(R.string.action_connect), modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.disconnect() },
                        enabled = state == WsConnectionState.CONNECTED || state == WsConnectionState.CONNECTING,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null)
                        Text(stringResource(R.string.action_disconnect), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                viewModel.lastError?.let { err ->
                    Text(
                        text = stringResource(R.string.mqtt_state_error, err),
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTitle(
                text = stringResource(R.string.event_stream_title),
                modifier = Modifier.weight(1f)
            )
            CopyIconButton(
                text = eventLogText(lines),
                label = stringResource(R.string.cd_copy_stream)
            )
            Icon(
                imageVector = MiuixIcons.Light.Delete,
                contentDescription = stringResource(R.string.action_clear),
                modifier = Modifier
                    .roundedPressable(onClick = { viewModel.clearLog() })
                    .padding(6.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    EventLine(line, systemLabel, recvLabel)
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
                label = stringResource(R.string.ws_input_label),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.send() },
                enabled = state == WsConnectionState.CONNECTED,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(MiuixIcons.Light.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
        BottomBarClearance()
    }

    missingVars?.let { missing ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.missing_env_title),
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                Text(stringResource(R.string.missing_env_desc))
                Text(missing.joinToString(", "), modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    text = stringResource(R.string.action_got_it),
                    onClick = { viewModel.dismissMissing() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    SaveRequestDialog(
        show = showSave,
        type = RequestType.WS,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false }
    )
}

@Composable
private fun stateLabel(state: WsConnectionState): String = when (state) {
    WsConnectionState.IDLE -> stringResource(R.string.state_closed)
    WsConnectionState.CONNECTING -> stringResource(R.string.state_connecting)
    WsConnectionState.CONNECTED -> stringResource(R.string.state_opened)
    WsConnectionState.CLOSED -> stringResource(R.string.state_disconnected)
    WsConnectionState.FAILED -> stringResource(R.string.state_failed)
}

@Composable
private fun stateColor(state: WsConnectionState): Color = when (state) {
    WsConnectionState.IDLE -> Color.Gray
    WsConnectionState.CONNECTING -> Color(0xFF1565C0)
    WsConnectionState.CONNECTED -> Color(0xFF2E7D32)
    WsConnectionState.CLOSED -> Color.Gray
    WsConnectionState.FAILED -> Color(0xFFC62828)
}
