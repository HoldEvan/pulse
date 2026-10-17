package com.evan.pulse.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.ConnectionStateIndicator
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onOpenRecordsList: () -> Unit = {}
) {
    val targets by viewModel.targets.collectAsState()
    val records by viewModel.records.collectAsState()
    val runs by viewModel.runs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(bottom = 12.dp),
        state = subPageLazyListState()
    ) {
        item(key = "records_title") {
            SectionTitleRow(
                label = stringResource(R.string.monitor_records_title),
                actions = {
                    IconButton(onClick = onOpenRecordsList) {
                        Icon(
                            imageVector = MiuixIcons.Light.More,
                            contentDescription = stringResource(R.string.monitor_records_more)
                        )
                    }
                }
            )
        }
        item(key = "records_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                if (records.isEmpty()) {
                    Text(
                        text = stringResource(R.string.monitor_records_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.outline
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

        item(key = "targets_card") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                if (targets.isNotEmpty()) {
                    val grouped = targets.groupBy { it.group }
                    val groups: List<Pair<String, List<MonitorTarget>>> = buildList {
                        grouped[UNGROUPED]?.let { add(UNGROUPED to it) }
                        grouped.filterKeys { it != UNGROUPED }.forEach { (name, list) ->
                            add(name to list)
                        }
                    }
                    groups.forEach { (groupName, groupTargets) ->
                        CardSectionTitle(
                            text = if (groupName.isBlank()) {
                                stringResource(R.string.monitor_group_ungrouped)
                            } else {
                                groupName
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp)
                        )
                        groupTargets.forEach { target ->
                            MonitorTargetRow(
                                target = target,
                                run = runs[target.id],
                                onToggleEnabled = { viewModel.toggleEnabled(target) },
                                onEdit = { viewModel.openEdit(target) },
                                onDelete = { viewModel.requestDelete(target) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                if (targets.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Light.Play,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MiuixTheme.colorScheme.outline
                        )
                        Text(
                            text = stringResource(R.string.monitor_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.outline
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
        OverlayDialog(
            show = true,
            title = if (viewModel.creating) {
                stringResource(R.string.monitor_create_title)
            } else {
                stringResource(R.string.monitor_edit_title)
            },
            onDismissRequest = { viewModel.closeEditor() }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = viewModel.nameInput,
                    onValueChange = { viewModel.nameInput = it },
                    label = stringResource(R.string.monitor_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = viewModel.groupInput,
                    onValueChange = { viewModel.groupInput = it },
                    label = stringResource(R.string.monitor_group_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.monitor_type_label),
                    items = monitorTypeLabels.localizedLabels(),
                    selectedIndex = viewModel.type.ordinal,
                    showValue = true,
                    modifier = Modifier.fillMaxWidth().squircleClip(12.dp),
                    onSelectedIndexChange = { viewModel.type = MonitorType.entries[it] }
                )
                if (viewModel.type != MonitorType.HTTP) {
                    TextField(
                        value = viewModel.hostInput,
                        onValueChange = { viewModel.hostInput = it },
                        label = stringResource(R.string.monitor_host_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                if (viewModel.type == MonitorType.PORT) {
                    TextField(
                        value = viewModel.portInput,
                        onValueChange = { viewModel.portInput = it },
                        label = stringResource(R.string.monitor_port_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                if (viewModel.type == MonitorType.HTTP) {
                    TextField(
                        value = viewModel.urlInput,
                        onValueChange = { viewModel.urlInput = it },
                        label = stringResource(R.string.monitor_url_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        TextField(
                            value = viewModel.statusMinInput,
                            onValueChange = { viewModel.statusMinInput = it },
                            label = stringResource(R.string.monitor_status_min_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.45f)
                        )
                        TextField(
                            value = viewModel.statusMaxInput,
                            onValueChange = { viewModel.statusMaxInput = it },
                            label = stringResource(R.string.monitor_status_max_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.45f)
                                .padding(start = 6.dp)
                        )
                    }
                }
                OverlayDropdownPreference(
                    title = stringResource(R.string.monitor_timeout_label),
                    items = timeoutOptions.map { durationLabel(it / 1000L) },
                    selectedIndex = timeoutOptions.indexOf(viewModel.timeoutMs)
                        .coerceAtLeast(0),
                    showValue = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).squircleClip(12.dp),
                    onSelectedIndexChange = { viewModel.timeoutMs = timeoutOptions[it] }
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.monitor_interval_label),
                    items = MONITOR_INTERVAL_PRESETS.map { durationLabel(it) },
                    selectedIndex = MONITOR_INTERVAL_PRESETS.indexOf(viewModel.intervalSec)
                        .coerceAtLeast(0),
                    showValue = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).squircleClip(12.dp),
                    onSelectedIndexChange = { viewModel.intervalSec = MONITOR_INTERVAL_PRESETS[it] }
                )
                CheckboxPreference(
                    title = stringResource(R.string.monitor_enabled_label),
                    checked = viewModel.enabled,
                    onCheckedChange = { viewModel.enabled = it },
                    modifier = Modifier.fillMaxWidth()
                )
                val validation = viewModel.editorValidation
                if (validation is TargetValidation.Invalid) {
                    Text(
                        text = stringResource(validation.message),
                        color = MiuixTheme.colorScheme.error,
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
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.closeEditor() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    viewModel.deleteTarget?.let { target ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.monitor_delete_title),
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                Text(stringResource(R.string.monitor_delete_confirm, target.name))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelDelete() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmDelete() },
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = target.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            if (run != null) {
                Text(
                    text = recordTimeFormat.format(Date(run.checkedAt)),
                    modifier = Modifier.padding(end = 10.dp),
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.outline
                )
            }
            ConnectionStateIndicator(color = stateColor, label = stateLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = targetSummary(target, run),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.outline
            )
            IconButton(
                onClick = onToggleEnabled,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = if (target.enabled) MiuixIcons.Light.Pause else MiuixIcons.Light.Play,
                    contentDescription = if (target.enabled) {
                        stringResource(R.string.monitor_pause)
                    } else {
                        stringResource(R.string.monitor_start)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            OverlayIconDropdownMenu(
                entries = listOf(
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = stringResource(R.string.cd_edit),
                                icon = { m ->
                                    Icon(
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
                                    Icon(
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
                Icon(
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
                color = STATE_DOWN_COLOR,
                label = stringResource(R.string.monitor_state_down)
            )
            Text(
                text = record.targetName,
                modifier = Modifier.padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(monitorTypeLabels[record.type.ordinal]),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.outline
            )
            Text(
                text = stringResource(R.string.duration_value, record.durationMs),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.outline
            )
            Text(
                text = recordTimeFormat.format(Date(record.checkedAt)),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.outline
            )
        }
        Text(
            text = record.message,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
