package com.evan.pulse.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.StatusBadge
import com.evan.pulse.ui.common.prettyBody
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/**
 * 单条历史记录的详情页：摘要 + 请求 + 响应。
 *
 * 早期版本写入的记录没有保存详情字段，此时只显示摘要并给一句提示，
 * 而不是渲染三块空白内容。
 */
@Composable
fun HistoryDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
    entryId: Long
) {
    val all by viewModel.history.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val nameById = remember(projects) { projects.associate { it.id to it.name } }
    val entry = all.firstOrNull { it.id == entryId }

    if (entry == null) {
        Text(
            text = stringResource(R.string.history_detail_missing),
            modifier = modifier.fillMaxSize().padding(24.dp)
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HistorySummary(entry, nameById[entry.projectId].orEmpty())

        if (!hasDetail(entry)) {
            Text(
                text = stringResource(R.string.history_detail_legacy),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        SectionTitleRow(label = stringResource(R.string.http_request_label))
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                MonospaceBlock(entry.url)
                if (entry.requestHeaders.isNotEmpty()) {
                    CardSectionTitle(stringResource(R.string.history_request_headers))
                    MonospaceBlock(entry.requestHeaders.joinToString("\n"))
                }
                if (entry.requestBody.isNotEmpty()) {
                    CardSectionTitle(stringResource(R.string.history_request_body))
                    MonospaceBlock(entry.requestBody)
                    TruncatedHint(entry.requestBodyTruncated)
                }
            }
        }

        SectionTitleRow(label = stringResource(R.string.http_response_label))
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (entry.responseHeaders.isNotEmpty()) {
                    CardSectionTitle(stringResource(R.string.http_response_headers))
                    MonospaceBlock(entry.responseHeaders.joinToString("\n"))
                }
                if (entry.responseBody.isNotEmpty()) {
                    ResponseBody(entry.responseBody)
                    TruncatedHint(entry.responseBodyTruncated)
                }
            }
        }
    }
}

/** 详情是否真的落库过；早期版本只写摘要 */
private fun hasDetail(entry: HistoryEntry): Boolean =
    entry.requestHeaders.isNotEmpty() ||
        entry.requestBody.isNotEmpty() ||
        entry.responseHeaders.isNotEmpty() ||
        entry.responseBody.isNotEmpty()

@Composable
private fun TruncatedHint(truncated: Boolean) {
    if (!truncated) return
    Text(
        text = stringResource(R.string.history_body_truncated),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 11.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
}

@Composable
private fun HistorySummary(entry: HistoryEntry, projectName: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(entry.method)
                StatusBadge(entry.statusCode, modifier = Modifier.padding(start = 8.dp))
                if (entry.statusText.isNotBlank()) {
                    Text(
                        text = entry.statusText,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = detailTimeFormat.format(Date(entry.createdAt)),
                    fontSize = 12.sp
                )
            }
            Row {
                Text(
                    text = stringResource(R.string.duration_value, entry.durationMs),
                    fontSize = 12.sp
                )
                Text(
                    text = stringResource(R.string.history_size_value, entry.responseSizeBytes),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp
                )
                if (projectName.isNotBlank()) {
                    Text(
                        text = projectName,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Text(
                text = entry.url,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp
            )
        }
    }
}

/** 响应体：可格式化 JSON，也可整块复制 */
@Composable
private fun ResponseBody(body: String) {
    var pretty by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(stringResource(R.string.http_response_body), Modifier.weight(1f))
        CopyIconButton(text = body, label = stringResource(R.string.cd_copy_response))
    }
    MonospaceBlock(prettyBody(body, pretty))
    TextButton(
        text = if (pretty) stringResource(R.string.http_view_raw)
        else stringResource(R.string.http_format_json),
        onClick = { pretty = !pretty },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}
