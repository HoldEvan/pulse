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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.HistoryAssertion
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CollapsibleKeyValueTable
import com.evan.pulse.ui.common.assertionTypeLabelRes
import com.evan.pulse.ui.common.assertionOperatorLabelRes
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.HtmlPreviewStore
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.StatusBadge
import com.evan.pulse.ui.common.ResponseBodyView
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.design.AppIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Replace

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
    entryId: Long,
    onOpenDiff: (Long) -> Unit = {},
    onOpenHtmlPreview: (() -> Unit)? = null
) {
    val all by viewModel.history.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val nameById = remember(projects) { projects.associate { it.id to it.name } }
    val entry = all.firstOrNull { it.id == entryId }

    if (entry == null) {
        AppText(
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
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
    ) {
        HistorySummary(entry, nameById[entry.projectId].orEmpty())

        if (!hasDetail(entry)) {
            AppText(
                text = stringResource(R.string.history_detail_legacy),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }

        SectionTitleRow(label = stringResource(R.string.http_request_label))
        AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                MonospaceBlock(entry.url)
            }
            // 折叠表放在卡片内边距之外，标题行水波纹才能横向贴住卡片左右边缘
            if (entry.requestHeaders.isNotEmpty()) {
                CollapsibleKeyValueTable(
                    title = stringResource(R.string.history_request_headers),
                    items = entry.requestHeaders.map(::splitHeaderLine),
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp
                )
            }
            if (entry.requestBody.isNotEmpty()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    CardSectionTitle(stringResource(R.string.history_request_body))
                    MonospaceBlock(entry.requestBody)
                    TruncatedHint(entry.requestBodyTruncated)
                }
            }
        }

        SectionTitleRow(label = stringResource(R.string.http_response_label))
        AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (entry.responseHeaders.isNotEmpty()) {
                CollapsibleKeyValueTable(
                    title = stringResource(R.string.http_response_headers),
                    items = entry.responseHeaders.map(::splitHeaderLine),
                    initiallyExpanded = true,
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp
                )
            }
            if (entry.assertions.isNotEmpty()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    CardSectionTitle(stringResource(R.string.http_assertions_label))
                    AssertionSummary(entry.assertions)
                }
            }
            if (entry.responseBody.isNotEmpty()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ResponseBody(
                        body = entry.responseBody,
                        url = entry.url,
                        contentType = contentTypeOf(entry.responseHeaders),
                        onOpenDiff = { onOpenDiff(entry.id) },
                        onOpenHtmlPreview = onOpenHtmlPreview
                    )
                    TruncatedHint(entry.responseBodyTruncated)
                }
            }
        }
    }
}

/** 断言复盘：先给一句总结，再逐条列出类型、表达式与失败原因 */
@Composable
private fun AssertionSummary(assertions: List<HistoryAssertion>) {
    val failed = assertions.count { it.passed.not() }
    AppText(
        text = if (failed == 0) {
            stringResource(R.string.http_assertions_all_passed)
        } else {
            stringResource(R.string.http_assertions_failed, failed)
        },
        fontSize = 13.sp,
        color = if (failed == 0) Color(0xFF2FA37A) else Color(0xFFD32F2F)
    )
    assertions.forEach { item ->
        val label = stringResource(assertionTypeLabelRes(item.type))
        val operator = item.operator
        val operatorLabel = if (operator.isBlank()) {
            ""
        } else {
            stringResource(assertionOperatorLabelRes(operator)) + " "
        }
        val expression = if (item.expected.isBlank()) {
            "$operatorLabel${item.expression}"
        } else {
            "$operatorLabel${item.expression} = ${item.expected}"
        }
        MonospaceBlock(
            "${if (item.passed) "PASS" else "FAIL"} $label $expression" +
                if (item.detail.isBlank()) "" else " - ${item.detail}"
        )
    }
}

/** 历史里请求头按 "key: value" 存成一行，展示前拆成两列 */
private fun splitHeaderLine(line: String): Pair<String, String> {
    val index = line.indexOf(':')
    return if (index < 0) {
        line.trim() to ""
    } else {
        line.substring(0, index).trim() to line.substring(index + 1).trim()
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
    AppText(
        text = stringResource(R.string.history_body_truncated),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 11.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}

@Composable
private fun HistorySummary(entry: HistoryEntry, projectName: String) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(entry.method)
                StatusBadge(entry.statusCode, modifier = Modifier.padding(start = 8.dp))
                if (entry.statusText.isNotBlank()) {
                    AppText(
                        text = entry.statusText,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AppText(
                    text = detailTimeFormat.format(Date(entry.createdAt)),
                    fontSize = 12.sp
                )
            }
            Row {
                AppText(
                    text = stringResource(R.string.duration_value, entry.durationMs),
                    fontSize = 12.sp
                )
                AppText(
                    text = stringResource(R.string.history_size_value, entry.responseSizeBytes),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp
                )
                if (projectName.isNotBlank()) {
                    AppText(
                        text = projectName,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
            AppText(
                text = entry.url,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp
            )
        }
    }
}

/** 响应体：六种格式查看，可整块复制；标题行可进入与另一条记录的响应对比 */
@Composable
private fun ResponseBody(
    body: String,
    url: String,
    contentType: String?,
    onOpenDiff: () -> Unit,
    onOpenHtmlPreview: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(stringResource(R.string.http_response_body), Modifier.weight(1f))
        AppIcon(
            imageVector = MiuixIcons.Light.Replace,
            contentDescription = stringResource(R.string.diff_open),
            modifier = Modifier
                .roundedPressable(onClick = onOpenDiff)
                .padding(6.dp)
        )
        CopyIconButton(text = body, label = stringResource(R.string.cd_copy_response))
    }
    ResponseBodyView(
        body = body,
        baseUrl = url,
        contentType = contentType,
        onOpenHtmlPreview = onOpenHtmlPreview?.let { open ->
            { html, base -> HtmlPreviewStore.set(html, base); open() }
        }
    )
}

/** 历史里的响应头以 "key: value" 行存储，取 Content-Type 供导出与预览推断 MIME */
private fun contentTypeOf(headers: List<String>): String? =
    headers.firstOrNull { it.substringBefore(':').equals("Content-Type", ignoreCase = true) }
        ?.substringAfter(':', "")
        ?.trim()
