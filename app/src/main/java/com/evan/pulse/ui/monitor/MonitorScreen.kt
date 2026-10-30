package com.evan.pulse.ui.monitor

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.addressText
import com.evan.pulse.data.model.MONITOR_INTERVAL_PRESETS
import com.evan.pulse.data.monitor.TargetValidation
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.data.model.MonitorAssertion
import com.evan.pulse.data.monitor.isAllEnabled
import com.evan.pulse.data.monitor.isGroupFullyEnabled
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.MethodPicker
import com.evan.pulse.ui.common.CollapsibleSection
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCheckboxRow
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Promotions
import com.evan.pulse.ui.design.AppIconDropdownMenu

private val recordTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private val STATE_OK_COLOR = Color(0xFF2E7D32)
private val STATE_DOWN_COLOR = Color(0xFFC62828)
private val STATE_IDLE_COLOR = Color(0xFF9E9E9E)

/** 未分组目标的空分组名 */
private const val UNGROUPED = ""

internal val monitorTypeLabels = listOf(
    R.string.monitor_type_ip,
    R.string.monitor_type_port,
    R.string.monitor_type_http
)

private val timeoutOptions = listOf(1000L, 3000L, 5000L, 10000L, 30000L, 60000L)

/** 拨测页：目标列表 + 未通记录，检查按各目标频率由调度器自动执行 */
@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel(),
    onOpenRecordsList: () -> Unit = {},
    onOpenTargetRecords: (MonitorTarget) -> Unit = {},
    onOpenTrend: (MonitorTarget) -> Unit = {}
) {
    val targets by viewModel.targets.collectAsState()
    val records by viewModel.failedRecords.collectAsState()
    val runs by viewModel.runs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current, bottom = 12.dp),
        state = subPageLazyListState()
    ) {
        item(key = "records_title") {
            SectionTitleRow(
                label = stringResource(R.string.monitor_records_title),
                actions = {
                    AppIconButton(onClick = onOpenRecordsList) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.More,
                            contentDescription = stringResource(R.string.monitor_records_more)
                        )
                    }
                }
            )
        }
        item(key = "records_card") {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                if (records.isEmpty()) {
                    AppText(
                        text = stringResource(R.string.monitor_records_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
                records.take(2).forEach { record ->
                    MonitorRecordRow(
                        record = record,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
        }

        item(key = "targets_title") {
            Spacer(modifier = Modifier.height(12.dp))
            SectionTitleRow(
                label = stringResource(R.string.monitor_targets_title),
                actions = {
                    if (targets.isNotEmpty()) {
                        AppIconDropdownMenu(
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = stringResource(R.string.monitor_enable_all),
                                            enabled = !isAllEnabled(targets),
                                            onClick = { viewModel.setAllEnabled(true) }
                                        ),
                                        DropdownItem(
                                            text = stringResource(R.string.monitor_pause_all),
                                            enabled = targets.any { it.enabled },
                                            onClick = { viewModel.setAllEnabled(false) }
                                        )
                                    )
                                )
                            )
                        ) {
                            AppIcon(
                                imageVector = MiuixIcons.Light.More,
                                contentDescription = stringResource(R.string.monitor_targets_more)
                            )
                        }
                    }
                }
            )
        }
        item(key = "targets_card") {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            // 横向内边距交给分组标题与行内容，卡片本身不再留边，分组标题的水波纹才能贴住左右边缘
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (targets.isNotEmpty()) {
                    val grouped = targets.groupBy { it.group }
                    val groups: List<Pair<String, List<MonitorTarget>>> = buildList {
                        grouped[UNGROUPED]?.let { add(UNGROUPED to it) }
                        grouped.filterKeys { it != UNGROUPED }.forEach { (name, list) ->
                            add(name to list)
                        }
                    }
                    groups.forEach { (groupName, groupTargets) ->
                        val groupEnabled = isGroupFullyEnabled(targets, groupName)
                        CollapsibleSection(
                            title = if (groupName.isBlank()) {
                                stringResource(R.string.monitor_group_ungrouped)
                            } else {
                                groupName
                            },
                            itemCount = groupTargets.size,
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalPadding = 8.dp,
                            trailing = {
                                AppIconButton(
                                    onClick = {
                                        viewModel.setGroupEnabled(groupName, !groupEnabled)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    AppIcon(
                                        imageVector = if (groupEnabled) {
                                            MiuixIcons.Light.Pause
                                        } else {
                                            MiuixIcons.Light.Play
                                        },
                                        contentDescription = stringResource(
                                            if (groupEnabled) R.string.monitor_group_pause
                                            else R.string.monitor_group_enable
                                        ),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        ) {
                            groupTargets.forEach { target ->
                                MonitorTargetRow(
                                    target = target,
                                    run = runs[target.id],
                                    onToggleEnabled = { viewModel.toggleEnabled(target) },
                                    onEdit = { viewModel.openEdit(target) },
                                    onDelete = { viewModel.requestDelete(target) },
                                    onTrend = { onOpenTrend(target) },
                                    onOpenRecords = { onOpenTargetRecords(target) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                if (targets.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Play,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = AppTheme.colors.onSurfaceSummary
                        )
                        AppText(
                            text = stringResource(R.string.monitor_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = AppTheme.colors.onSurfaceSummary
                        )
                    }
                }
            }
        }
        }

        item(key = "clearance") {
            BottomBarClearance()
        }
    }

    if (viewModel.showEditor) {
        AppDialog(
            show = true,
            title = if (viewModel.creating) {
                stringResource(R.string.monitor_create_title)
            } else {
                stringResource(R.string.monitor_edit_title)
            },
            onDismissRequest = { viewModel.closeEditor() }
        ) {
            // 字段多，按屏幕高度限制弹窗内容区并允许滚动；否则小屏上底部按钮点不到
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppTextField(
                    value = viewModel.nameInput,
                    onValueChange = { viewModel.nameInput = it },
                    label = stringResource(R.string.monitor_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = viewModel.groupInput,
                    onValueChange = { viewModel.groupInput = it },
                    label = stringResource(R.string.monitor_group_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                AppDropdown(
                    title = stringResource(R.string.monitor_type_label),
                    items = monitorTypeLabels.localizedLabels(),
                    selectedIndex = viewModel.type.ordinal,
                    showValue = true,
                    filled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onSelectedIndexChange = { viewModel.type = MonitorType.entries[it] }
                )
                if (viewModel.type == MonitorType.PORT) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTextField(
                            value = viewModel.hostInput,
                            onValueChange = { viewModel.hostInput = it },
                            label = stringResource(R.string.monitor_host_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(0.65f)
                        )
                        AppTextField(
                            value = viewModel.portInput,
                            onValueChange = { viewModel.portInput = it },
                            label = stringResource(R.string.monitor_port_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.35f)
                        )
                    }
                } else if (viewModel.type != MonitorType.HTTP) {
                    AppTextField(
                        value = viewModel.hostInput,
                        onValueChange = { viewModel.hostInput = it },
                        label = stringResource(R.string.monitor_host_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                if (viewModel.type == MonitorType.HTTP) {
                    AppTextField(
                        value = viewModel.urlInput,
                        onValueChange = { viewModel.urlInput = it },
                        label = stringResource(R.string.monitor_url_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        AppTextField(
                            value = viewModel.statusMinInput,
                            onValueChange = { viewModel.statusMinInput = it },
                            label = stringResource(R.string.monitor_status_min_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.45f)
                        )
                        AppTextField(
                            value = viewModel.statusMaxInput,
                            onValueChange = { viewModel.statusMaxInput = it },
                            label = stringResource(R.string.monitor_status_max_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.45f)
                                .padding(start = 6.dp)
                        )
                    }
                    MethodPicker(
                        method = HttpMethod(viewModel.method),
                        onMethodChange = { viewModel.method = it.value },
                        title = stringResource(R.string.monitor_method_label),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    CollapsibleSection(
                        title = stringResource(R.string.monitor_headers_label),
                        itemCount = viewModel.headers.count { it.key.isNotBlank() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        KeyValueEditor(
                            items = viewModel.headers,
                            keyLabel = stringResource(R.string.kv_header_key),
                            valueLabel = stringResource(R.string.kv_header_value),
                            onChange = { index, entry ->
                                viewModel.headers = viewModel.headers.mapIndexed { i, e ->
                                    if (i == index) entry else e
                                }
                            },
                            onAdd = { viewModel.headers = viewModel.headers + KeyValue() },
                            onRemove = { index ->
                                viewModel.headers =
                                    viewModel.headers.filterIndexed { i, _ -> i != index }
                            }
                        )
                    }
                    AppDropdown(
                        title = stringResource(R.string.body_type_label),
                        items = listOf(
                            stringResource(R.string.body_none),
                            stringResource(R.string.body_json),
                            stringResource(R.string.body_raw)
                        ),
                        selectedIndex = when (viewModel.bodyType.uppercase()) {
                            "JSON" -> 1
                            "RAW" -> 2
                            else -> 0
                        },
                        showValue = true,
                        filled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        onSelectedIndexChange = {
                            viewModel.bodyType = when (it) {
                                1 -> "JSON"
                                2 -> "RAW"
                                else -> "NONE"
                            }
                        }
                    )
                    if (viewModel.bodyType != "NONE") {
                        AppTextField(
                            value = viewModel.bodyText,
                            onValueChange = { viewModel.bodyText = it },
                            label = stringResource(R.string.monitor_body_label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                    AppDropdown(
                        title = stringResource(R.string.monitor_auth_label),
                        items = listOf(
                            stringResource(R.string.auth_none),
                            stringResource(R.string.auth_bearer),
                            stringResource(R.string.auth_basic),
                            stringResource(R.string.auth_apikey)
                        ),
                        selectedIndex = when (viewModel.authType.uppercase()) {
                            "BEARER" -> 1
                            "BASIC" -> 2
                            "APIKEY" -> 3
                            else -> 0
                        },
                        showValue = true,
                        filled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        onSelectedIndexChange = {
                            viewModel.authType = when (it) {
                                1 -> "BEARER"
                                2 -> "BASIC"
                                3 -> "APIKEY"
                                else -> "NONE"
                            }
                        }
                    )
                    when (viewModel.authType.uppercase()) {
                        "BEARER" -> AppTextField(
                            value = viewModel.bearerToken,
                            onValueChange = { viewModel.bearerToken = it },
                            label = stringResource(R.string.bearer_token_label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )

                        "BASIC" -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppTextField(
                                value = viewModel.basicUsername,
                                onValueChange = { viewModel.basicUsername = it },
                                label = stringResource(R.string.username_label),
                                modifier = Modifier.weight(0.5f)
                            )
                            AppTextField(
                                value = viewModel.basicPassword,
                                onValueChange = { viewModel.basicPassword = it },
                                label = stringResource(R.string.password_label),
                                modifier = Modifier.weight(0.5f)
                            )
                        }

                        "APIKEY" -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppTextField(
                                value = viewModel.apiKeyName,
                                onValueChange = { viewModel.apiKeyName = it },
                                label = stringResource(R.string.apikey_name_label),
                                modifier = Modifier.weight(0.5f)
                            )
                            AppTextField(
                                value = viewModel.apiKeyValue,
                                onValueChange = { viewModel.apiKeyValue = it },
                                label = stringResource(R.string.apikey_value_label),
                                modifier = Modifier.weight(0.5f)
                            )
                        }
                    }
                    CollapsibleSection(
                        title = stringResource(R.string.monitor_assertions_label),
                        itemCount = viewModel.assertions.size,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        val assertionTypeLabels = listOf(
                            stringResource(R.string.monitor_assert_contains),
                            stringResource(R.string.monitor_assert_not_contains),
                            stringResource(R.string.monitor_assert_jsonpath)
                        )
                        viewModel.assertions.forEachIndexed { index, assertion ->
                            val isJsonPath = assertion.type.equals("JSON_PATH", ignoreCase = true)
                            val typeIndex = when (assertion.type.uppercase()) {
                                "NOT_CONTAINS" -> 1
                                "JSON_PATH" -> 2
                                else -> 0
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 标题直接显示当前选中的类型：showValue 关掉时若 title 也留空，这一行就只剩箭头，看不到回显
                                AppDropdown(
                                    title = assertionTypeLabels[typeIndex],
                                    items = assertionTypeLabels,
                                    selectedIndex = typeIndex,
                                    showValue = false,
                                    filled = true,
                                    modifier = Modifier.weight(1f),
                                    onSelectedIndexChange = {
                                        viewModel.assertions = viewModel.assertions.mapIndexed { i, a ->
                                            if (i == index) {
                                                a.copy(
                                                    type = when (it) {
                                                        1 -> "NOT_CONTAINS"
                                                        2 -> "JSON_PATH"
                                                        else -> "CONTAINS"
                                                    }
                                                )
                                            } else a
                                        }
                                    }
                                )
                                AppIconButton(
                                    onClick = {
                                        viewModel.assertions =
                                            viewModel.assertions.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete)
                                    )
                                }
                            }
                            AppTextField(
                                value = assertion.expression,
                                onValueChange = { text ->
                                    viewModel.assertions = viewModel.assertions.mapIndexed { i, a ->
                                        if (i == index) a.copy(expression = text) else a
                                    }
                                },
                                label = stringResource(
                                    if (isJsonPath) R.string.monitor_assert_path
                                    else R.string.monitor_assert_keyword
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                            if (isJsonPath) {
                                AppTextField(
                                    value = assertion.expected,
                                    onValueChange = { text ->
                                        viewModel.assertions =
                                            viewModel.assertions.mapIndexed { i, a ->
                                                if (i == index) a.copy(expected = text) else a
                                            }
                                    },
                                    label = stringResource(R.string.monitor_assert_expected),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                            }
                        }
                        AppTextButton(
                            text = stringResource(R.string.monitor_assert_add),
                            onClick = {
                                viewModel.assertions =
                                    viewModel.assertions + MonitorAssertion()
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                AppDropdown(
                    title = stringResource(R.string.monitor_timeout_label),
                    items = timeoutOptions.map { durationLabel(it / 1000L) },
                    selectedIndex = timeoutOptions.indexOf(viewModel.timeoutMs)
                        .coerceAtLeast(0),
                    showValue = true,
                    filled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onSelectedIndexChange = { viewModel.timeoutMs = timeoutOptions[it] }
                )
                AppDropdown(
                    title = stringResource(R.string.monitor_interval_label),
                    items = MONITOR_INTERVAL_PRESETS.map { durationLabel(it) },
                    selectedIndex = MONITOR_INTERVAL_PRESETS.indexOf(viewModel.intervalSec)
                        .coerceAtLeast(0),
                    showValue = true,
                    filled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onSelectedIndexChange = { viewModel.intervalSec = MONITOR_INTERVAL_PRESETS[it] }
                )
                AppTextField(
                    value = viewModel.latencyInput,
                    onValueChange = { viewModel.latencyInput = it },
                    label = stringResource(R.string.monitor_latency_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                AppText(
                    text = stringResource(R.string.monitor_latency_hint),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
                AppCheckboxRow(
                    title = stringResource(R.string.monitor_enabled_label),
                    checked = viewModel.enabled,
                    onCheckedChange = { viewModel.enabled = it },
                    modifier = Modifier.fillMaxWidth()
                )
                val validation = viewModel.editorValidation
                if (validation is TargetValidation.Invalid) {
                    AppText(
                        text = stringResource(validation.message),
                        color = AppTheme.colors.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.closeEditor() },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.save() },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    viewModel.deleteTarget?.let { target ->
        AppDialog(
            show = true,
            title = stringResource(R.string.monitor_delete_title),
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                AppText(stringResource(R.string.monitor_delete_confirm, target.name))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelDelete() },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmDelete() },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 单个拨测目标：启停按钮控制启停，右侧提供编辑与删除 */
@Composable
private fun MonitorTargetRow(
    target: MonitorTarget,
    run: MonitorRun?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTrend: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = when {
        !target.enabled -> STATE_IDLE_COLOR
        run == null -> STATE_IDLE_COLOR
        run.ok -> STATE_OK_COLOR
        else -> STATE_DOWN_COLOR
    }
    val stateLabel = when {
        !target.enabled -> stringResource(R.string.monitor_state_disabled)
        run == null -> stringResource(R.string.monitor_state_idle)
        run.ok -> stringResource(R.string.monitor_state_ok)
        else -> stringResource(R.string.monitor_state_down)
    }
    Column(modifier = modifier.clickable(onClick = onOpenRecords)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = target.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            if (run != null) {
                AppText(
                    text = recordTimeFormat.format(Date(run.checkedAt)),
                    modifier = Modifier.padding(end = 10.dp),
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
            ConnectionStateIndicator(color = stateColor, label = stateLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = targetSummary(target, run),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppIconButton(
                onClick = onToggleEnabled,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(28.dp)
            ) {
                AppIcon(
                    imageVector = if (target.enabled) MiuixIcons.Light.Pause else MiuixIcons.Light.Play,
                    contentDescription = if (target.enabled) {
                        stringResource(R.string.monitor_pause)
                    } else {
                        stringResource(R.string.monitor_start)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            AppIconDropdownMenu(
                entries = listOf(
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = stringResource(R.string.monitor_trend),
                                icon = { m ->
                                    AppIcon(
                                        imageVector = MiuixIcons.Light.Promotions,
                                        contentDescription = null,
                                        modifier = m
                                    )
                                },
                                onClick = onTrend
                            ),
                            DropdownItem(
                                text = stringResource(R.string.cd_edit),
                                icon = { m ->
                                    AppIcon(
                                        imageVector = MiuixIcons.Light.Edit,
                                        contentDescription = null,
                                        modifier = m
                                    )
                                },
                                onClick = onEdit
                            ),
                            DropdownItem(
                                text = stringResource(R.string.action_delete),
                                icon = { m ->
                                    AppIcon(
                                        imageVector = MiuixIcons.Light.Delete,
                                        contentDescription = null,
                                        modifier = m
                                    )
                                },
                                onClick = onDelete
                            )
                        )
                    )
                ),
                minWidth = 28.dp,
                minHeight = 28.dp,
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = 4.dp)
            ) {
                AppIcon(
                    imageVector = MiuixIcons.Light.More,
                    contentDescription = stringResource(R.string.cd_more),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun MonitorRecordRow(record: MonitorRecord, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionStateIndicator(
                color = if (record.ok) STATE_OK_COLOR else STATE_DOWN_COLOR,
                label = stringResource(
                    if (record.ok) R.string.monitor_state_ok else R.string.monitor_state_down
                )
            )
            AppText(
                text = record.targetName,
                modifier = Modifier.padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = stringResource(monitorTypeLabels[record.type.ordinal]),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppText(
                text = stringResource(R.string.duration_value, record.durationMs),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppText(
                text = recordTimeFormat.format(Date(record.checkedAt)),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        AppText(
            text = record.message,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun targetSummary(target: MonitorTarget, run: MonitorRun?): String {
    val meta = stringResource(
        R.string.monitor_row_meta,
        stringResource(monitorTypeLabels[target.type.ordinal]),
        durationLabel(target.intervalSec)
    )
    val address = target.addressText()
    return when {
        !target.enabled -> "$address · ${stringResource(R.string.monitor_state_disabled)}"
        run == null -> "$address · $meta"
        else -> "$address · $meta · ${run.detail}"
    }
}

@Composable
private fun durationLabel(seconds: Long): String = when {
    seconds < 60L -> stringResource(R.string.monitor_interval_seconds, seconds.toInt())
    seconds < 3600L -> stringResource(R.string.monitor_interval_minutes, (seconds / 60L).toInt())
    else -> stringResource(R.string.monitor_interval_hours, (seconds / 3600L).toInt())
}
