package com.evan.pulse.ui.sse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.EventLine
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.KeyValueEditor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link

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

/** SSE 单向流页面：顶部状态 + URL + 事件过滤 / 重连 / 请求头，底部消息流 */
@Composable
fun SseScreen(modifier: Modifier = Modifier, viewModel: SseViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lines = viewModel.lines
    val missingVars = viewModel.missingVars
    val systemLabel = stringResource(R.string.ws_direction_system)
    val recvLabel = stringResource(R.string.sse_direction_recv)
    var showSave by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        SectionTitleRow(
            label = stringResource(R.string.action_connect),
            onSaveClick = { showSave = true }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部表单区随标题固定，下面内容整体滚动；事件流卡片再在内部滚动
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = viewModel.url,
                            onValueChange = { viewModel.url = it },
                            label = stringResource(R.string.sse_url_label),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        )
                        ConnectionStateIndicator(
                            color = stateColor(state),
                            label = stateLabel(state)
                        )
                    }
                    TextField(
                        value = viewModel.eventFilter,
                        onValueChange = { viewModel.eventFilter = it },
                        label = stringResource(R.string.sse_event_filter_label),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    TextField(
                        value = viewModel.reconnectDelaySec,
                        onValueChange = { viewModel.reconnectDelaySec = it },
                        label = stringResource(R.string.sse_reconnect_delay_label),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    CheckboxPreference(
                        title = stringResource(R.string.sse_auto_reconnect_label),
                        checked = viewModel.autoReconnect,
                        onCheckedChange = { viewModel.autoReconnect = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    CardSectionTitle(
                        text = stringResource(R.string.sse_headers_title),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        KeyValueEditor(
                            items = viewModel.headers,
                            keyLabel = stringResource(R.string.kv_header_key),
                            valueLabel = stringResource(R.string.kv_value_label),
                            onChange = viewModel::updateHeaders,
                            onAdd = viewModel::addHeader,
                            onRemove = viewModel::removeHeader
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.connect() },
                            enabled = state == SseConnectionState.IDLE || state == SseConnectionState.CLOSED || state == SseConnectionState.FAILED,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(MiuixIcons.Light.Link, contentDescription = null)
                            Text(stringResource(R.string.action_connect), modifier = Modifier.padding(start = 4.dp))
                        }
                        Button(
                            onClick = { viewModel.disconnect() },
                            enabled = state == SseConnectionState.CONNECTED || state == SseConnectionState.CONNECTING,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTitle(text = stringResource(R.string.event_stream_title), modifier = Modifier.weight(1f))
                CopyIconButton(
                    text = eventLogText(lines),
                    label = stringResource(R.string.cd_copy_stream)
                )
                Icon(
                    imageVector = MiuixIcons.Light.Delete,
                    contentDescription = stringResource(R.string.cd_clear_stream),
                    modifier = Modifier
                        .clickable { viewModel.clearLog() }
                        .padding(6.dp)
                )
            }
            Card(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(lines) { line ->
                        EventLine(line, systemLabel, recvLabel)
                    }
                }
            }
            BottomBarClearance()
        }
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
        type = RequestType.SSE,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false }
    )
}

@Composable
private fun stateLabel(state: SseConnectionState): String = when (state) {
    SseConnectionState.IDLE -> stringResource(R.string.state_closed)
    SseConnectionState.CONNECTING -> stringResource(R.string.state_connecting)
    SseConnectionState.CONNECTED -> stringResource(R.string.state_opened)
    SseConnectionState.CLOSED -> stringResource(R.string.state_disconnected)
    SseConnectionState.FAILED -> stringResource(R.string.state_failed)
}

@Composable
private fun stateColor(state: SseConnectionState): Color = when (state) {
    SseConnectionState.IDLE -> Color.Gray
    SseConnectionState.CONNECTING -> Color(0xFF1565C0)
    SseConnectionState.CONNECTED -> Color(0xFF2E7D32)
    SseConnectionState.CLOSED -> Color.Gray
    SseConnectionState.FAILED -> Color(0xFFC62828)
}
