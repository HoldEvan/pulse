package com.evan.pulse.ui.ws

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
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
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.WsMessageTemplate
import com.evan.pulse.domain.model.WsConnectionState
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.CollapsibleSection
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.connectionStateColor
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.EventLine
import com.evan.pulse.ui.common.InsecureTlsRow
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.ValueHints
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppSmallTitle
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.domain.net.BinaryEncoding
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Send
import com.evan.pulse.ui.design.AppButton
import com.evan.pulse.ui.history.UiEventLine

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

/** WS 流面板：顶部状态 + URL + 发送 / 清空 / 连接，底部消息流，已收数据主色容器底色 */
@Composable
fun WsScreen(modifier: Modifier = Modifier, viewModel: WsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val lines = remember(history) { history.map { UiEventLine(it.text, it.isSystem, it.timestamp) } }
    val missingVars = viewModel.missingVars
    val systemLabel = stringResource(R.string.ws_direction_system)
    val recvLabel = stringResource(R.string.ws_direction_recv)
    var showSave by remember { mutableStateOf(false) }
    val templates by viewModel.templates.collectAsState()
    var showTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var templateNameError by remember { mutableStateOf(false) }

    // 与 SSE / MQTT 一致：整页纵向滚动，事件流给固定高度，
    // 否则配置项一多就把下方的输入区挤出屏幕且无法滚动
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
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppTextField(
                    value = viewModel.url,
                    onValueChange = { viewModel.url = it },
                    label = stringResource(R.string.ws_url_label),
                    modifier = Modifier.fillMaxWidth()
                )
                ValueHints(hints = viewModel.placeholderHints(viewModel.url))
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    AppTextField(
                        value = viewModel.pingIntervalSec,
                        onValueChange = { viewModel.pingIntervalSec = it.filter(Char::isDigit) },
                        label = stringResource(R.string.ws_heartbeat_interval_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.5f)
                    )
                    AppTextField(
                        value = viewModel.pingPayload,
                        onValueChange = { viewModel.pingPayload = it },
                        label = stringResource(R.string.ws_heartbeat_payload_label),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
                AppTextField(
                    value = viewModel.subprotocols,
                    onValueChange = { viewModel.subprotocols = it },
                    label = stringResource(R.string.ws_subprotocols_label),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                ValueHints(hints = viewModel.placeholderHints(viewModel.subprotocols))
            }
            // 握手请求头默认折叠，鉴权头等按需展开；放在内边距之外，
            // 标题行的水波纹才能横向贴住卡片左右边缘，纵向也留出更大的点击区域
            CollapsibleSection(
                title = stringResource(R.string.tab_headers),
                itemCount = viewModel.headers.count { it.key.isNotBlank() },
                initiallyExpanded = false,
                horizontalPadding = 12.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                // 与折叠标题行留出间距，避免展开后第一行输入框贴着标题
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    KeyValueEditor(
                        items = viewModel.headers,
                        keyLabel = stringResource(R.string.kv_header_key),
                        valueLabel = stringResource(R.string.kv_value_label),
                        onChange = viewModel::updateHeaders,
                        onAdd = viewModel::addHeader,
                        onRemove = viewModel::removeHeader,
                        valueHintProvider = viewModel::placeholderHints
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
            AppSwitchRow(
                title = stringResource(R.string.ws_auto_reconnect_label),
                summary = stringResource(R.string.ws_auto_reconnect_summary),
                checked = viewModel.autoReconnect,
                onCheckedChange = viewModel::updateAutoReconnect
            )
            if (viewModel.autoReconnect) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp)
                ) {
                    Row {
                        AppTextField(
                            value = viewModel.reconnectBaseDelaySec,
                            onValueChange = { viewModel.updateReconnectBase(it.filter(Char::isDigit)) },
                            label = stringResource(R.string.ws_reconnect_base_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        AppTextField(
                            value = viewModel.reconnectMaxDelaySec,
                            onValueChange = { viewModel.updateReconnectMax(it.filter(Char::isDigit)) },
                            label = stringResource(R.string.ws_reconnect_max_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    if (!viewModel.reconnectDelaysValid) {
                        AppText(
                            stringResource(R.string.ws_reconnect_delay_error),
                            color = Color(0xFFD32F2F),
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        onClick = { viewModel.connect() },
                        enabled = state == WsConnectionState.IDLE || state == WsConnectionState.CLOSED || state == WsConnectionState.FAILED,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(MiuixIcons.Light.Link, contentDescription = null)
                        AppText(stringResource(R.string.action_connect), modifier = Modifier.padding(start = 4.dp))
                    }
                    AppButton(
                        onClick = { viewModel.disconnect() },
                        enabled = state == WsConnectionState.CONNECTED || state == WsConnectionState.CONNECTING,
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
                contentDescription = stringResource(R.string.action_clear),
                modifier = Modifier
                    .roundedPressable(onClick = { viewModel.clearLog() })
                    .padding(6.dp)
            )
        }
        AppCard(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .subPageOverScroll(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = subPageLazyListState()
            ) {
                items(lines) { line ->
                    EventLine(line, systemLabel, recvLabel)
                }
            }
        }

        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            // 常用消息模板：折叠展示，点击填充输入框，右上角保存当前输入
            CollapsibleSection(
                title = stringResource(R.string.ws_templates_title),
                itemCount = templates.size,
                initiallyExpanded = false,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                templates.forEach { template ->
                    WsTemplateRow(
                        template = template,
                        onApply = { viewModel.applyTemplate(template) },
                        onDelete = { viewModel.deleteTemplate(template.id) }
                    )
                }
                AppTextButton(
                    text = stringResource(R.string.ws_template_save),
                    onClick = {
                        templateName = ""
                        templateNameError = false
                        showTemplateDialog = true
                    },
                    enabled = !viewModel.templateLimitReached,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AppSwitchRow(
                title = stringResource(
                    if (viewModel.binaryMode) R.string.ws_send_binary
                    else R.string.ws_send_text
                ),
                summary = if (viewModel.binaryMode) {
                    stringResource(R.string.ws_binary_mode_summary)
                } else {
                    null
                },
                checked = viewModel.binaryMode,
                onCheckedChange = {
                    viewModel.binaryMode = it
                    viewModel.dismissBinaryError()
                }
            )
            if (viewModel.binaryMode) {
                AppDropdown(
                    title = stringResource(R.string.ws_binary_encoding),
                    items = listOf(
                        stringResource(R.string.ws_binary_hex),
                        stringResource(R.string.ws_binary_base64)
                    ),
                    selectedIndex = if (viewModel.binaryEncoding == BinaryEncoding.HEX) 0 else 1,
                    modifier = Modifier.padding(bottom = 6.dp),
                    onSelectedIndexChange = {
                        viewModel.binaryEncoding =
                            if (it == 0) BinaryEncoding.HEX else BinaryEncoding.BASE64
                        viewModel.dismissBinaryError()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = viewModel.input,
                    onValueChange = { viewModel.input = it },
                    label = stringResource(
                        if (viewModel.binaryMode) R.string.ws_binary_input_label
                        else R.string.ws_input_label
                    ),
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    onClick = { viewModel.send() },
                    enabled = state == WsConnectionState.CONNECTED,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    AppIcon(
                        MiuixIcons.Light.Send,
                        contentDescription = stringResource(R.string.action_send)
                    )
                }
            }
            viewModel.binaryError?.let { message ->
                AppText(
                    text = message,
                    color = AppTheme.colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        BottomBarClearance()
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

    AppDialog(
        show = showTemplateDialog,
        title = stringResource(R.string.ws_template_save),
        onDismissRequest = { showTemplateDialog = false }
    ) {
        Column {
            AppTextField(
                value = templateName,
                onValueChange = {
                    templateName = it
                    templateNameError = false
                },
                label = stringResource(R.string.ws_template_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AppText(
                text = stringResource(R.string.ws_template_hint, viewModel.input.length),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (templateNameError) {
                AppText(
                    text = stringResource(R.string.ws_template_invalid),
                    color = AppTheme.colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 10.dp)) {
                AppTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showTemplateDialog = false },
                    modifier = Modifier.weight(1f)
                )
                AppTextButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        if (viewModel.saveTemplate(templateName, viewModel.input)) {
                            showTemplateDialog = false
                        } else {
                            templateNameError = true
                        }
                    },
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    SaveRequestDialog(
        show = showSave,
        type = RequestType.WS,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false },
        onSaved = viewModel::markSaved
    )
}

/** 模板行：左侧名称与内容预览点击填充，右侧删除 */
@Composable
private fun WsTemplateRow(
    template: WsMessageTemplate,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onApply)
                .padding(vertical = 6.dp)
        ) {
            AppText(template.name)
            AppText(
                text = if (template.binary) {
                    stringResource(R.string.ws_template_binary_badge, template.encoding, template.content)
                } else {
                    template.content
                },
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary,
                maxLines = 1
            )
        }
        AppIconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            AppIcon(
                imageVector = MiuixIcons.Light.Delete,
                contentDescription = stringResource(R.string.ws_template_delete)
            )
        }
    }
}

@Composable
private fun stateLabel(state: WsConnectionState): String = when (state) {
    WsConnectionState.IDLE -> stringResource(R.string.state_closed)
    WsConnectionState.CONNECTING -> stringResource(R.string.state_connecting)
    WsConnectionState.CONNECTED -> stringResource(R.string.state_opened)
    WsConnectionState.CLOSED -> stringResource(R.string.state_disconnected)
    WsConnectionState.FAILED -> stringResource(R.string.state_failed)
}
