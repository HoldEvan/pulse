package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.domain.text.BodyPager
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme

/**
 * 分页等宽文本块：内容超过一页时只渲染当前页，顶部给出翻页控制。
 *
 * 分页栏放在正文上方，翻页后视线落在新内容开头；正文仍交给 [MonospaceBlock]，
 * 保持与其它工具页一致的可选中长按复制能力。
 */
@Composable
fun PagedTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    charsPerPage: Int = BodyPager.DEFAULT_CHARS_PER_PAGE,
) {
    val pages = remember(text, charsPerPage) { BodyPager.paginate(text, charsPerPage) }
    var page by remember(text) { mutableStateOf(0) }
    if (pages.pageCount == 0) {
        MonospaceBlock("", modifier = modifier, selectable = true)
        return
    }
    val current = page.coerceIn(0, pages.lastPageIndex)
    Column(modifier = modifier) {
        if (pages.pageCount > 1) {
            PagerBar(
                page = current,
                pageCount = pages.pageCount,
                summary = stringResource(
                    R.string.paged_view_text_summary,
                    pages.totalLines,
                    pages.pageCount
                ),
                onPageChange = { page = it }
            )
        }
        MonospaceBlock(
            text = pages.slice(text, current),
            modifier = Modifier.padding(top = 6.dp),
            selectable = true
        )
    }
}

/**
 * 分页 hex dump：按字节区间出页，避免为几 MB 的响应体一次性拼出十几 MB 的文本。
 *
 * [hint] 由调用方传入额外说明（例如内容不是合法 UTF-8），非空时显示在分页信息下方。
 */
@Composable
fun PagedHexBlock(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var page by remember(bytes) { mutableStateOf(0) }
    val pageCount = if (bytes.isEmpty()) {
        0
    } else {
        (bytes.size + HEX_BYTES_PER_PAGE - 1) / HEX_BYTES_PER_PAGE
    }
    if (pageCount == 0) {
        MonospaceBlock("", modifier = modifier, selectable = true)
        return
    }
    val current = page.coerceIn(0, pageCount - 1)
    val pageText = remember(bytes, current) {
        hexDumpRange(bytes, offset = current * HEX_BYTES_PER_PAGE)
    }
    Column(modifier = modifier) {
        PagerBar(
            page = current,
            pageCount = pageCount,
            summary = stringResource(
                R.string.paged_view_hex_summary,
                CacheCleaner.formatBytes(bytes.size.toLong()),
                pageCount
            ),
            onPageChange = { page = it },
            trailingHint = hint
        )
        MonospaceBlock(
            text = pageText,
            modifier = Modifier.padding(top = 6.dp),
            selectable = true
        )
    }
}

/** 翻页栏：上一页 / 页码 / 下一页，首尾页对应按钮置灰 */
@Composable
private fun PagerBar(
    page: Int,
    pageCount: Int,
    summary: String,
    onPageChange: (Int) -> Unit,
    trailingHint: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppTextButton(
                text = stringResource(R.string.paged_view_previous),
                onClick = { onPageChange(page - 1) },
                enabled = page > 0,
                primary = false,
                filled = false
            )
            AppText(
                text = stringResource(R.string.paged_view_position, page + 1, pageCount),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppTextButton(
                text = stringResource(R.string.paged_view_next),
                onClick = { onPageChange(page + 1) },
                enabled = page < pageCount - 1,
                primary = false,
                filled = false
            )
        }
        AppText(
            text = summary,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        if (trailingHint != null) {
            AppText(
                text = trailingHint,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
    }
}
