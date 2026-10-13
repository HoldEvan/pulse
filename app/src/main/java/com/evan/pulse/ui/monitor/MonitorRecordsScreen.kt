package com.evan.pulse.ui.monitor

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.addressText
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import java.util.Locale

/** 未通记录二级页：可搜索当前项目的全部未通记录，也可确认后清空 */
@Composable
fun MonitorRecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel()
) {
    val records by viewModel.records.collectAsState()
    val targetsById by viewModel.targetsById.collectAsState()

    val query = viewModel.recordQuery.trim().lowercase(Locale.getDefault())
    val filtered = if (query.isEmpty()) {
        records
    } else {
        records.filter { record -> searchText(record, targetsById).contains(query) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        TextField(
            value = viewModel.recordQuery,
            onValueChange = { viewModel.recordQuery = it },
            label = stringResource(R.string.monitor_records_search_hint),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (records.isEmpty() || filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (records.isEmpty()) R.string.monitor_records_empty
                        else R.string.monitor_records_no_match
                    ),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.outline
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .subPageOverScroll()
                    .verticalScroll(subPageScrollState())
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                        filtered.forEach { record ->
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
        }
    }

    if (viewModel.clearRecordsAsked) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.monitor_records_clear),
            onDismissRequest = { viewModel.cancelClearRecords() }
        ) {
            Column {
                Text(stringResource(R.string.monitor_records_clear_confirm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelClearRecords() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmClearRecords() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

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
