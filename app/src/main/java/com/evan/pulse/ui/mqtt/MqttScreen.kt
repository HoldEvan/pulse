package com.evan.pulse.ui.mqtt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.domain.model.MqttConnectionState
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.connectionStateColor
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.InsecureTlsRow
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.history.UiEventLine
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppSmallTitle
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.squircle.squircleBackground
import com.evan.pulse.ui.design.AppButton

private val qosLabels = listOf(R.string.qos_0, R.string.qos_1, R.string.qos_2)

@Composable
private fun SectionTitleRow(status: @Composable () -> Unit, onSaveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 沿用 SmallTitle 的内边距节奏，保持与原分组标题一致的间距
        Box(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) { status() }
        AppIconButton(onClick = onSaveClick, modifier = Modifier.size(36.dp)) {
            AppIcon(
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
    val subscriptions by viewModel.subscriptions.collectAsState()
    var showSave by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
    ) {
        SectionTitleRow(
            status = {
                ConnectionStateIndicator(
                    color = connectionStateColor(state),
                    label = stateLabel(state)
                )
            },
            onSaveClick = { if (!viewModel.saveOverLoaded()) showSave = true }
        )
        Column(modifier = Modifier.fillMaxWidth()) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppTextField(
                    value = viewModel.brokerUrl,
                    onValueChange = { viewModel.brokerUrl = it },
                    label = stringResource(R.string.mqtt_broker_url_label),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    AppTextField(
                        value = viewModel.clientId,
                        onValueChange = { viewModel.clientId = it },
                        label = stringResource(R.string.mqtt_client_id_label),
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = viewModel.keepAliveSec,
                        onValueChange = { viewModel.keepAliveSec = it.filter(Char::isDigit) },
                        label = stringResource(R.string.mqtt_keepalive_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.6f)
                            .padding(start = 8.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    AppTextField(
                        value = viewModel.username,
                        onValueChange = { viewModel.username = it },
                        label = stringResource(R.string.mqtt_username_label),
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = stringResource(R.string.mqtt_password_label),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
            // 开关行铺满卡片宽度，水波纹才能沿卡片圆角收边
            AppSwitchRow(
                title = stringResource(R.string.use_proxy_label),
                summary = stringResource(R.string.use_proxy_summary),
                checked = viewModel.useProxy,
                onCheckedChange = { viewModel.useProxy = it }
            )
            InsecureTlsRow(
                checked = viewModel.insecureTls,
                onCheckedChange = { viewModel.insecureTls = it }
            )
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        onClick = { viewModel.connect() },
                        enabled = state == MqttConnectionState.IDLE || state == MqttConnectionState.DISCONNECTED || state == MqttConnectionState.FAILED,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(MiuixIcons.Light.Link, contentDescription = null)
                        AppText(stringResource(R.string.action_connect), modifier = Modifier.padding(start = 4.dp))
                    }
                    AppButton(
                        onClick = { viewModel.disconnect() },
                        enabled = state == MqttConnectionState.CONNECTED,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(Icons.Filled.LinkOff, contentDescription = null)
                        AppText(stringResource(R.string.action_disconnect), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                viewModel.lastError?.let { err ->
                    AppText(
                        text = stringResource(R.string.mqtt_state_error, err),
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        AppSmallTitle(text = stringResource(R.string.mqtt_title_sub))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            AppDropdown(
                items = qosLabels.localizedLabels(),
                selectedIndex = viewModel.subQos,
                title = stringResource(R.string.mqtt_qos_label),
                modifier = Modifier.fillMaxWidth(),
                showValue = true,
                onSelectedIndexChange = { viewModel.subQos = it }
            )
            Column(modifier = Modifier.padding(12.dp)) {
                // 通配符与 QoS 语法较长，放在输入框外侧，输入框标签只留 Topic
                AppText(
                    text = stringResource(R.string.mqtt_topic_hint),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = viewModel.subTopic,
                        onValueChange = {
                            viewModel.subTopic = it
                            viewModel.clearFilterError()
                        },
                        label = stringResource(R.string.mqtt_topic_label),
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        onClick = { viewModel.addSubscription() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        AppText(stringResource(R.string.action_subscribe))
                    }
                }
                if (viewModel.invalidTopics.isNotEmpty()) {
                    AppText(
                        text = stringResource(
                            R.string.mqtt_filter_invalid_list,
                            viewModel.invalidTopics.joinToString(", ")
                        ),
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // Topic 树是保存 filter 的纯投影；展开/收起只改本地折叠集合，不动订阅数据（需求 4.1）
                val tree = remember(subscriptions) { buildMqttTopicTree(subscriptions) }
                var collapsed by remember { mutableStateOf(emptySet<String>()) }
                tree.forEach { root ->
                    TopicBranch(
                        node = root,
                        depth = 0,
                        collapsed = collapsed,
                        onToggle = { path ->
                            collapsed = if (path in collapsed) collapsed - path else collapsed + path
                        },
                        onRemove = viewModel::removeSubscription,
                        onCycleQos = viewModel::cycleSubscriptionQos
                    )
                }
            }
        }

        AppSmallTitle(text = stringResource(R.string.mqtt_title_pub))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            AppDropdown(
                items = qosLabels.localizedLabels(),
                selectedIndex = viewModel.pubQos,
                title = stringResource(R.string.mqtt_pub_qos_label),
                modifier = Modifier.fillMaxWidth(),
                showValue = true,
                onSelectedIndexChange = { viewModel.pubQos = it }
            )
            Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
                AppTextField(
                    value = viewModel.pubTopic,
                    onValueChange = { viewModel.pubTopic = it },
                    label = stringResource(R.string.mqtt_pub_topic_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 开关行铺满卡片宽度，水波纹才能沿卡片圆角收边
            AppSwitchRow(
                title = stringResource(R.string.mqtt_pub_retain_label),
                summary = stringResource(R.string.mqtt_pub_retain_summary),
                checked = viewModel.pubRetain,
                onCheckedChange = { viewModel.pubRetain = it }
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = viewModel.pubPayload,
                        onValueChange = { viewModel.pubPayload = it },
                        label = stringResource(R.string.mqtt_pub_payload_label),
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        onClick = { viewModel.publish() },
                        enabled = state == MqttConnectionState.CONNECTED,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        AppIcon(MiuixIcons.Light.Send, contentDescription = stringResource(R.string.action_publish))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSmallTitle(
                text = stringResource(R.string.event_stream_title),
                modifier = Modifier.weight(1f)
            )
            CopyIconButton(
                text = eventLogText(lines),
                label = stringResource(R.string.cd_copy_stream)
            )
            AppIcon(
                imageVector = MiuixIcons.Light.Delete,
                contentDescription = stringResource(R.string.cd_clear_stream),
                modifier = Modifier
                    .roundedPressable(onClick = { viewModel.clearLog() })
                    .padding(6.dp)
            )
        }
        AppCard(modifier = Modifier.fillMaxWidth().height(360.dp).padding(bottom = 0.dp)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
        AppDialog(
            show = true,
            title = stringResource(R.string.missing_env_title),
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                AppText(stringResource(R.string.missing_env_desc))
                AppText(missing.joinToString(", "), modifier = Modifier.padding(vertical = 8.dp))
                AppTextButton(
                    text = stringResource(R.string.action_got_it),
                    onClick = { viewModel.dismissMissing() },
                    primary = true,
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
        onDismiss = { showSave = false },
        onSaved = viewModel::markSaved
    )
}

@Composable
private fun TopicBranch(
    node: MqttTopicNode,
    depth: Int,
    collapsed: Set<String>,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCycleQos: (String) -> Unit
) {
    val expanded = node.path !in collapsed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(node.path) }
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(depth) { Spacer(modifier = Modifier.width(12.dp)) }
        AppText(text = if (expanded) "▾" else "▸", fontSize = 13.sp)
        AppText(text = node.segment, modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
    }
    if (expanded) {
        node.leaves.forEach { leaf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(depth + 1) { Spacer(modifier = Modifier.width(12.dp)) }
                AppText(
                    text = stringResource(R.string.sub_item, leaf.topic, leaf.qos),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCycleQos(leaf.topic) },
                    fontSize = 13.sp
                )
                AppIcon(
                    imageVector = MiuixIcons.Light.Close,
                    contentDescription = stringResource(R.string.action_unsubscribe),
                    modifier = Modifier
                        .clickable { onRemove(leaf.topic) }
                        .padding(6.dp)
                )
            }
        }
        node.children.forEach { TopicBranch(it, depth + 1, collapsed, onToggle, onRemove, onCycleQos) }
    }
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
private fun EventLine(line: UiEventLine) {
    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(line.timestamp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .squircleBackground(
                if (line.isSystem) {
                    AppTheme.colors.surfaceVariant
                } else {
                    AppTheme.colors.primary.copy(alpha = 0.15f)
                },
                16.dp
            )
            .padding(8.dp)
    ) {
        AppText(text = time, fontSize = 11.sp)
        AppText(text = line.text, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
