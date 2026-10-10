package com.evan.pulse.ui.mqtt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.localizedLabels
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val qosLabels = listOf(R.string.qos_0, R.string.qos_1, R.string.qos_2)

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

@Composable
fun MqttScreen(modifier: Modifier = Modifier, viewModel: MqttViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lines = viewModel.lines
    val missingVars = viewModel.missingVars
    var showSave by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SectionTitleRow(
            label = stringResource(R.string.mqtt_section_broker),
            onSaveClick = { showSave = true }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = viewModel.brokerUrl,
                        onValueChange = { viewModel.brokerUrl = it },
                        label = stringResource(R.string.mqtt_broker_url_label),
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
                        value = viewModel.clientId,
                        onValueChange = { viewModel.clientId = it },
                        label = stringResource(R.string.mqtt_client_id_label),
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = viewModel.keepAliveSec,
                        onValueChange = { viewModel.keepAliveSec = it },
                        label = stringResource(R.string.mqtt_keepalive_label),
                        modifier = Modifier
                            .weight(0.6f)
                            .padding(start = 8.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = viewModel.username,
                        onValueChange = { viewModel.username = it },
                        label = stringResource(R.string.mqtt_username_label),
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = stringResource(R.string.mqtt_password_label),
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
                        Icon(MiuixIcons.Light.Link, contentDescription = null)
                        Text(stringResource(R.string.action_connect), modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.disconnect() },
                        enabled = state == MqttConnectionState.CONNECTED,
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

        SmallTitle(text = stringResource(R.string.mqtt_title_sub))
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                items = qosLabels.localizedLabels(),
                selectedIndex = viewModel.subQos,
                title = stringResource(R.string.mqtt_qos_label),
                modifier = Modifier.fillMaxWidth(),
                showValue = true,
                onSelectedIndexChange = { viewModel.subQos = it }
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = viewModel.subTopic,
                        onValueChange = { viewModel.subTopic = it },
                        label = stringResource(R.string.mqtt_topic_label),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.addSubscription() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.action_subscribe))
                    }
                }
                viewModel.subscriptions.forEachIndexed { index, sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sub_item, sub.topic, sub.qos),
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.action_unsubscribe),
                            modifier = Modifier
                                .clickable { viewModel.removeSubscription(index) }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        SmallTitle(text = stringResource(R.string.mqtt_title_pub))
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                items = qosLabels.localizedLabels(),
                selectedIndex = viewModel.pubQos,
                title = stringResource(R.string.mqtt_pub_qos_label),
                modifier = Modifier.fillMaxWidth(),
                showValue = true,
                onSelectedIndexChange = { viewModel.pubQos = it }
            )
            Column(modifier = Modifier.padding(12.dp)) {
                TextField(
                    value = viewModel.pubTopic,
                    onValueChange = { viewModel.pubTopic = it },
                    label = stringResource(R.string.mqtt_pub_topic_label),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = viewModel.pubPayload,
                        onValueChange = { viewModel.pubPayload = it },
                        label = stringResource(R.string.mqtt_pub_payload_label),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.publish() },
                        enabled = state == MqttConnectionState.CONNECTED,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(MiuixIcons.Light.Send, contentDescription = stringResource(R.string.action_publish))
                    }
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
                contentDescription = stringResource(R.string.cd_clear_stream),
                modifier = Modifier
                    .roundedPressable(onClick = { viewModel.clearLog() })
                    .padding(6.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().height(360.dp).padding(bottom = 0.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(lines) { line ->
                    EventLine(line)
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
        type = RequestType.MQTT,
        defaultName = viewModel.pubTopic.ifEmpty { viewModel.brokerUrl },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false }
    )
}

@Composable
private fun stateLabel(state: MqttConnectionState): String = when (state) {
    MqttConnectionState.IDLE -> stringResource(R.string.state_closed)
    MqttConnectionState.CONNECTING -> stringResource(R.string.state_connecting)
    MqttConnectionState.CONNECTED -> stringResource(R.string.state_opened)
    MqttConnectionState.DISCONNECTED -> stringResource(R.string.state_disconnected)
    MqttConnectionState.FAILED -> stringResource(R.string.state_failed)
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
