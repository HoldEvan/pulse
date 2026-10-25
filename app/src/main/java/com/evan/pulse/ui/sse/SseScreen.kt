package com.evan.pulse.ui.sse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.SseConnectionState
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.connectionStateColor
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.EventLine
import com.evan.pulse.ui.common.InsecureTlsRow
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.MethodPicker
import com.evan.pulse.ui.common.MarkdownBlock
import com.evan.pulse.ui.common.ValueHints
import com.evan.pulse.ui.common.eventLogText
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppSmallTitle
import androidx.compose.ui.text.input.KeyboardType
import com.evan.pulse.ui.design.AppCheckboxRow
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppSwitch
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Link
import com.evan.pulse.ui.design.AppButton

private val sseBodyTypes = listOf(BodyType.NONE, BodyType.JSON, BodyType.RAW)
private val sseBodyTypeLabels = listOf(
    R.string.body_none,
    R.string.body_json,
    R.string.body_raw
)
private val sseTabLabels = listOf(
    R.string.tab_params,
    R.string.tab_headers,
    R.string.tab_body,
    R.string.tab_network
)
private val previewTabLabels = listOf(
    R.string.sse_preview_tab_raw,
    R.string.sse_preview_tab_markdown
)

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

/** SSE 单向流页面：方法/参数/请求头/请求体 + 拼接预览（原始/Markdown），底部消息流 */
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
                MethodPicker(
                    method = viewModel.method,
                    onMethodChange = { viewModel.method = it },
                    title = stringResource(R.string.sse_method_label),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(modifier = Modifier.padding(12.dp)) {
                    AppTextField(
                        value = viewModel.url,
                        onValueChange = { viewModel.url = it },
                        label = stringResource(R.string.sse_url_label),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ValueHints(hints = viewModel.placeholderHints(viewModel.url))
                    AppTextField(
                        value = viewModel.eventFilter,
                        onValueChange = { viewModel.eventFilter = it },
                        label = stringResource(R.string.sse_event_filter_label),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    AppTextField(
                        value = viewModel.reconnectDelaySec,
                        onValueChange = { viewModel.reconnectDelaySec = it },
                        label = stringResource(R.string.sse_reconnect_delay_label),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    AppTextField(
                        value = viewModel.lastEventIdInput,
                        onValueChange = { viewModel.lastEventIdInput = it },
                        label = stringResource(R.string.sse_last_event_id_label),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    ValueHints(
                        hints = viewModel.placeholderHints(viewModel.lastEventIdInput)
                    )
                    AppCheckboxRow(
                        title = stringResource(R.string.sse_auto_reconnect_label),
                        checked = viewModel.autoReconnect,
                        onCheckedChange = { viewModel.autoReconnect = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTabRow(
                        tabs = sseTabLabels.localizedLabels(),
                        selectedTabIndex = viewModel.activeTab,
                        onTabSelected = { viewModel.activeTab = it },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                // Tab 内容区贴卡片边缘（与 HTTP 请求卡一致）：类型下拉行贴左右卡边，
                // 具体内容分支再自行内缩；上方内缩列的 12dp 底边距充当 Tab 行间距
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (viewModel.activeTab) {
                        0 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            KeyValueEditor(
                                items = viewModel.params,
                                keyLabel = stringResource(R.string.kv_param_key),
                                valueLabel = stringResource(R.string.kv_value_label),
                                onChange = viewModel::updateParams,
                                onAdd = viewModel::addParam,
                                onRemove = viewModel::removeParam,
                                valueHintProvider = viewModel::placeholderHints
                            )
                        }

                        1 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
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

                        2 -> BodyEditor(viewModel)

                        3 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            AppTextField(
                                value = viewModel.timeoutSec,
                                onValueChange = { viewModel.timeoutSec = it.filter(Char::isDigit) },
                                label = stringResource(R.string.timeout_sec_label),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(
                                    text = stringResource(R.string.use_proxy_label),
                                    modifier = Modifier.weight(1f)
                                )
                                AppSwitch(
                                    checked = viewModel.useProxy,
                                    onCheckedChange = { viewModel.useProxy = it }
                                )
                            }
                            InsecureTlsRow(
                                checked = viewModel.insecureTls,
                                onCheckedChange = { viewModel.insecureTls = it },
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
                // 2dp 顶边距与 Tab 内容分支的 12dp 底边距合成原来的 14dp 行距
                Column(modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppButton(
                            onClick = { viewModel.connect() },
                            enabled = state == SseConnectionState.IDLE || state == SseConnectionState.CLOSED || state == SseConnectionState.FAILED,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(MiuixIcons.Light.Link, contentDescription = null)
                            AppText(stringResource(R.string.sse_connect_button), modifier = Modifier.padding(start = 4.dp))
                        }
                        AppButton(
                            onClick = { viewModel.disconnect() },
                            enabled = state == SseConnectionState.CONNECTED || state == SseConnectionState.CONNECTING,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(Icons.Filled.LinkOff, contentDescription = null)
                            AppText(stringResource(R.string.sse_disconnect_button), modifier = Modifier.padding(start = 4.dp))
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

            AppCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppTextField(
                        value = viewModel.previewPath,
                        onValueChange = { viewModel.previewPath = it },
                        label = stringResource(R.string.sse_preview_path_label),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTabRow(
                            tabs = previewTabLabels.localizedLabels(),
                            selectedTabIndex = viewModel.previewTab,
                            onTabSelected = { viewModel.previewTab = it },
                            modifier = Modifier.weight(1f)
                        )
                        CopyIconButton(
                            text = viewModel.previewText,
                            label = stringResource(R.string.cd_copy_preview),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        AppIcon(
                            imageVector = MiuixIcons.Light.Delete,
                            contentDescription = stringResource(R.string.cd_clear_preview),
                            modifier = Modifier
                                .clickable { viewModel.clearPreview() }
                                .padding(6.dp)
                        )
                    }
                    if (viewModel.previewText.isBlank()) {
                        AppText(
                            text = stringResource(R.string.sse_preview_empty),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(min = 120.dp, max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (viewModel.previewTab == 0) {
                                AppText(
                                    text = viewModel.previewText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                MarkdownBlock(
                                    markdown = viewModel.previewText,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // 事件统计：总帧数与按事件名分组计数，断点可一键回填到输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppSmallTitle(text = stringResource(R.string.sse_stats_title), modifier = Modifier.weight(1f))
                AppText(
                    text = stringResource(R.string.sse_stats_total, viewModel.stats.total),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val breakdown = viewModel.stats.breakdown()
                    if (breakdown.isEmpty()) {
                        AppText(
                            text = stringResource(R.string.sse_stats_empty),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        breakdown.forEach { (name, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(text = name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                AppText(
                                    text = stringResource(R.string.sse_stats_count, count),
                                    fontSize = 13.sp,
                                    color = AppTheme.colors.onSurfaceSummary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = stringResource(
                                R.string.sse_resume_current,
                                viewModel.stats.lastEventId ?: stringResource(R.string.sse_resume_none)
                            ),
                            fontSize = 12.sp,
                            color = AppTheme.colors.onSurfaceSummary,
                            modifier = Modifier.weight(1f)
                        )
                        AppTextButton(
                            text = stringResource(R.string.sse_resume_adopt),
                            onClick = { viewModel.adoptLastEventId() },
                            enabled = viewModel.stats.lastEventId != null
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
                AppSmallTitle(text = stringResource(R.string.event_stream_title), modifier = Modifier.weight(1f))
                CopyIconButton(
                    text = eventLogText(lines),
                    label = stringResource(R.string.cd_copy_stream)
                )
                AppIcon(
                    imageVector = MiuixIcons.Light.Delete,
                    contentDescription = stringResource(R.string.cd_clear_stream),
                    modifier = Modifier
                        .clickable { viewModel.clearLog() }
                        .padding(6.dp)
                )
            }
            AppCard(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
        type = RequestType.SSE,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false },
        onSaved = viewModel::markSaved
    )
}

@Composable
private fun BodyEditor(viewModel: SseViewModel) {
    Column(horizontalAlignment = Alignment.Start) {
        AppDropdown(
            items = sseBodyTypeLabels.localizedLabels(),
            selectedIndex = sseBodyTypes.indexOf(viewModel.bodyType),
            title = stringResource(R.string.body_type_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { viewModel.bodyType = sseBodyTypes[it] }
        )
        // 内容列仅在类型有内容时渲染：NONE 时空列的内边距会在卡片底部留白
        if (viewModel.bodyType != BodyType.NONE) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                AppTextField(
                    value = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    label = if (viewModel.bodyType == BodyType.JSON) {
                        stringResource(R.string.body_json_label)
                    } else {
                        stringResource(R.string.body_raw_label)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8
                )
            }
        }
    }
}

@Composable
private fun stateLabel(state: SseConnectionState): String = when (state) {
    SseConnectionState.IDLE -> stringResource(R.string.state_closed)
    SseConnectionState.CONNECTING -> stringResource(R.string.state_connecting)
    SseConnectionState.CONNECTED -> stringResource(R.string.state_opened)
    SseConnectionState.CLOSED -> stringResource(R.string.state_disconnected)
    SseConnectionState.FAILED -> stringResource(R.string.state_failed)
}
