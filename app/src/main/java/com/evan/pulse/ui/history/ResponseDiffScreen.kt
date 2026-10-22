package com.evan.pulse.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.domain.diff.LineDiff
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.statusColor
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.isJson
import com.evan.pulse.ui.common.prettyBody
import com.evan.pulse.ui.common.prettyXml
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val diffTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/**
 * 响应对比页：以当前记录为基准，与另一条历史记录的响应体做行级差异对比。
 *
 * 对比对象默认取最近一次同接口（方法与 URL 相同）的记录，可手动更换；
 * 两侧均为 JSON/XML 时先做格式化再对比，避免压缩正文把整篇都判成差异。
 */
@Composable
fun ResponseDiffScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
    entryId: Long
) {
    val all by viewModel.history.collectAsState()
    val entry = all.firstOrNull { it.id == entryId }

    if (entry == null) {
        AppText(
            text = stringResource(R.string.history_detail_missing),
            modifier = modifier.fillMaxSize().padding(24.dp)
        )
        return
    }

    val candidates = remember(all, entryId) {
        all.filter { it.id != entryId }.sortedByDescending { it.createdAt }
    }
    val defaultTarget = remember(candidates, entry) {
        candidates.firstOrNull { it.method == entry.method && it.url == entry.url }
            ?: candidates.firstOrNull()
    }
    var targetId by remember { mutableStateOf<Long?>(null) }
    val target = targetId?.let { id -> all.firstOrNull { it.id == id } } ?: defaultTarget

    var showPicker by remember { mutableStateOf(false) }

    val diffResult = remember(entry.responseBody, target?.responseBody) {
        val (oldText, newText) = prepareDiffBodies(entry.responseBody, target?.responseBody.orEmpty())
        LineDiff.diff(oldText, newText)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(top = LocalTopBarInset.current)
    ) {
        EntrySummaryCard(
            entry = entry,
            label = stringResource(R.string.diff_base),
            modifier = Modifier.padding(top = 4.dp)
        )
        EntrySummaryCard(
            entry = target,
            label = stringResource(R.string.diff_target),
            onChange = { showPicker = true },
            modifier = Modifier.padding(top = 8.dp)
        )

        if (diffResult.hasDifference) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                AppText(
                    text = "+${diffResult.addedCount}",
                    color = AppTheme.colors.primary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                AppText(
                    text = "-${diffResult.removedCount}",
                    color = AppTheme.colors.error,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        val bothEmpty = entry.responseBody.isEmpty() && (target == null || target.responseBody.isEmpty())
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            state = subPageLazyListState()
        ) {
            if (bothEmpty) {
                item {
                    AppText(
                        text = stringResource(R.string.diff_no_body),
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            } else if (!diffResult.hasDifference) {
                item {
                    AppText(
                        text = stringResource(R.string.diff_no_difference),
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            } else {
                if (diffResult.truncated) {
                    item(key = "truncated") {
                        AppText(
                            text = stringResource(R.string.diff_truncated, 2000),
                            modifier = Modifier.padding(vertical = 6.dp),
                            fontSize = 12.sp,
                            color = AppTheme.colors.onSurfaceSummary
                        )
                    }
                }
                items(diffResult.rows.size) { index ->
                    DiffRowView(diffResult.rows[index])
                }
            }
        }
    }

    if (showPicker) {
        AppDialog(
            show = true,
            title = stringResource(R.string.diff_pick_title),
            onDismissRequest = { showPicker = false }
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                candidates.forEach { candidate ->
                    EntryOptionRow(
                        entry = candidate,
                        preferred = candidate.method == entry.method && candidate.url == entry.url,
                        onClick = {
                            targetId = candidate.id
                            showPicker = false
                        }
                    )
                }
                if (candidates.isEmpty()) {
                    AppText(
                        text = stringResource(R.string.diff_pick_empty),
                        modifier = Modifier.padding(vertical = 12.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
        }
    }
}

/** 两侧均为 JSON/XML 时先格式化，压缩正文才不会整篇被判成差异 */
private fun prepareDiffBodies(oldBody: String, newBody: String): Pair<String, String> {
    val xmlLike = { text: String -> text.trimStart().startsWith("<") }
    return when {
        isJson(oldBody) && isJson(newBody) -> prettyBody(oldBody, true) to prettyBody(newBody, true)
        xmlLike(oldBody) && xmlLike(newBody) -> prettyXml(oldBody) to prettyXml(newBody)
        else -> oldBody to newBody
    }
}

@Composable
private fun EntrySummaryCard(
    entry: HistoryEntry?,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (() -> Unit)? = null
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = label,
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary,
                    modifier = Modifier.weight(1f)
                )
                if (onChange != null) {
                    AppTextButton(
                        text = stringResource(R.string.diff_change),
                        onClick = onChange
                    )
                }
            }
            if (entry == null) {
                AppText(
                    text = stringResource(R.string.diff_pick_empty),
                    fontSize = 12.sp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    MethodBadge(entry.method)
                    AppText(
                        text = entry.statusCode.toString(),
                        color = statusColor(entry.statusCode),
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 13.sp
                    )
                    AppText(
                        text = diffTimeFormat.format(Date(entry.createdAt)),
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
                AppText(
                    text = entry.url,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EntryOptionRow(
    entry: HistoryEntry,
    preferred: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(entry.method)
            AppText(
                text = entry.statusCode.toString(),
                color = statusColor(entry.statusCode),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 13.sp
            )
            AppText(
                text = diffTimeFormat.format(Date(entry.createdAt)),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            if (preferred) {
                AppText(
                    text = stringResource(R.string.diff_same_api),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 11.sp,
                    color = AppTheme.colors.primary
                )
            }
        }
        AppText(
            text = entry.url,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 差异行：增删行用固定半透明底色区分，浅色/深色主题下均可读 */
@Composable
private fun DiffRowView(row: LineDiff.Row) {
    val background = when (row.type) {
        LineDiff.RowType.ADDED -> Color(0x2E22C55A)
        LineDiff.RowType.REMOVED -> Color(0x2EFF453A)
        LineDiff.RowType.EQUAL -> Color.Transparent
    }
    val prefix = when (row.type) {
        LineDiff.RowType.ADDED -> "+"
        LineDiff.RowType.REMOVED -> "-"
        LineDiff.RowType.EQUAL -> " "
    }
    AppText(
        text = prefix + row.text,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}
