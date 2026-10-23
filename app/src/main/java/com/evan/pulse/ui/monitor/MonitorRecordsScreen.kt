package com.evan.pulse.ui.monitor

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.addressText
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppIconDropdownMenu
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import java.util.Locale
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.ListView

/** 拨测记录二级页：可按通断状态、检查类型筛选，也可搜索并清空 */
@Composable
fun MonitorRecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel()
) {
    val records by viewModel.records.collectAsState()
    val targetsById by viewModel.targetsById.collectAsState()

    val query = viewModel.recordQuery.trim().lowercase(Locale.getDefault())
    val statusFilter = viewModel.recordStatusFilter
    val typeFilter = viewModel.recordTypeFilter
    val targetId = viewModel.recordTargetId
    val filtered = remember(records, targetsById, query, statusFilter, typeFilter, targetId) {
        records.filter { record ->
            val matchesQuery = query.isEmpty() || searchText(record, targetsById).contains(query)
            val matchesStatus = when (statusFilter) {
                RecordStatusFilter.ALL -> true
                RecordStatusFilter.PASSED -> record.ok
                RecordStatusFilter.FAILED -> !record.ok
            }
            val matchesType = typeFilter == null || record.type == typeFilter
            val matchesTarget = targetId == null || record.targetId == targetId
            matchesQuery && matchesStatus && matchesType && matchesTarget
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = subPageLazyListState()
    ) {
        targetId?.let { id ->
            val name = targetsById[id]?.name
            item(key = "target_scope") {
                // 清除「仅看」时让卡片淡出、下方列表平滑上移，避免瞬间跳变
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tween(200),
                            fadeOutSpec = tween(200),
                            placementSpec = spring()
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = stringResource(
                                R.string.monitor_records_scope_target,
                                name ?: stringResource(R.string.monitor_records_scope_unknown)
                            ),
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp
                        )
                        AppIconButton(
                            onClick = { viewModel.clearRecordTarget() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.monitor_records_scope_clear),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item(key = "search") {
            AppTextField(
                value = viewModel.recordQuery,
                onValueChange = { viewModel.recordQuery = it },
                label = stringResource(R.string.monitor_records_search_hint),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = stringResource(
                            if (records.isEmpty()) R.string.monitor_records_empty
                            else R.string.monitor_records_no_match
                        ),
                        fontSize = 13.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { record ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    MonitorRecordRow(
                        record = record,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (viewModel.clearRecordsAsked) {
        AppDialog(
            show = true,
            title = stringResource(R.string.monitor_records_clear),
            onDismissRequest = { viewModel.cancelClearRecords() }
        ) {
            Column {
                AppText(stringResource(R.string.monitor_records_clear_confirm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelClearRecords() },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmClearRecords() },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 记录页顶栏筛选：状态与类型两个纯图标下拉，放在清空按钮左侧 */
@Composable
fun MonitorRecordsFilterActions(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier
) {
    val statusFilter = viewModel.recordStatusFilter
    val typeFilter = viewModel.recordTypeFilter
    val targetScoped = viewModel.recordTargetId != null
    AppIconDropdownMenu(
        entries = statusEntries(statusFilter) { viewModel.recordStatusFilter = it },
        modifier = modifier,
        minWidth = 40.dp,
        minHeight = 40.dp
    ) {
        AppIcon(
            imageVector = MiuixIcons.Light.Filter,
            contentDescription = stringResource(R.string.monitor_records_filter_status)
        )
    }
    // 单目标记录的类型是固定的，此时类型筛选没有意义
    if (!targetScoped) {
        AppIconDropdownMenu(
            entries = typeEntries(typeFilter) { viewModel.recordTypeFilter = it },
            modifier = modifier,
            minWidth = 40.dp,
            minHeight = 40.dp
        ) {
            AppIcon(
                imageVector = MiuixIcons.Light.ListView,
                contentDescription = stringResource(R.string.monitor_records_filter_type)
            )
        }
    }
}

private fun RecordStatusFilter.labelRes(): Int = when (this) {
    RecordStatusFilter.ALL -> R.string.monitor_records_filter_status_all
    RecordStatusFilter.PASSED -> R.string.monitor_records_filter_passed
    RecordStatusFilter.FAILED -> R.string.monitor_records_filter_failed
}

private fun MonitorType?.labelRes(): Int = when (this) {
    null -> R.string.monitor_records_filter_type_all
    else -> monitorTypeLabels[ordinal]
}

@Composable
private fun statusEntries(
    selected: RecordStatusFilter,
    onSelect: (RecordStatusFilter) -> Unit
): List<DropdownEntry> = listOf(
    DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.monitor_records_filter_status_all),
                selected = selected == RecordStatusFilter.ALL,
                onClick = { onSelect(RecordStatusFilter.ALL) }
            ),
            DropdownItem(
                text = stringResource(R.string.monitor_records_filter_passed),
                selected = selected == RecordStatusFilter.PASSED,
                onClick = { onSelect(RecordStatusFilter.PASSED) }
            ),
            DropdownItem(
                text = stringResource(R.string.monitor_records_filter_failed),
                selected = selected == RecordStatusFilter.FAILED,
                onClick = { onSelect(RecordStatusFilter.FAILED) }
            )
        )
    )
)

@Composable
private fun typeEntries(
    selected: MonitorType?,
    onSelect: (MonitorType?) -> Unit
): List<DropdownEntry> = listOf(
    DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = stringResource(R.string.monitor_records_filter_type_all),
                    selected = selected == null,
                    onClick = { onSelect(null) }
                )
            )
            MonitorType.entries.forEach { type ->
                add(
                    DropdownItem(
                        text = stringResource(monitorTypeLabels[type.ordinal]),
                        selected = selected == type,
                        onClick = { onSelect(type) }
                    )
                )
            }
        }
    )
)

/** 记录的检索文本：名称、失败原因、类型与目标地址一起参与匹配 */
private fun searchText(record: MonitorRecord, targetsById: Map<Long, MonitorTarget>): String {
    val target = targetsById[record.targetId]
    return listOf(
        record.targetName,
        record.message,
        record.type.name,
        target?.addressText().orEmpty()
    ).joinToString(" ").lowercase(Locale.getDefault())
}
