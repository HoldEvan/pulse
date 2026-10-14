package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.cache.CacheCleaner
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 响应体多格式查看：RAW/JSON/XML/HEX/BASE64 五种视图，JSON 时展示树形，
 * HTTP 响应卡与历史详情共用，保证两处查看体验一致。
 */
@Composable
fun ResponseBodyView(body: String, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(BodyViewMode.RAW) }
    val modeLabels = listOf(
        stringResource(R.string.resp_view_raw),
        stringResource(R.string.resp_view_json),
        stringResource(R.string.resp_view_xml),
        stringResource(R.string.resp_view_hex),
        stringResource(R.string.resp_view_b64)
    )
    Column(modifier = modifier) {
        TabRowWithContour(
            tabs = modeLabels,
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = BodyViewMode.entries[it] },
            minWidth = 44.dp,
            maxWidth = 120.dp,
            height = 40.dp,
            itemSpacing = 4.dp,
            cornerRadius = 10.dp
        )
        val rendered = remember(body, mode) { renderBody(body, mode) }
        if (mode == BodyViewMode.JSON && isJson(body)) {
            JsonTreeView(body)
        } else {
            MonospaceBlock(rendered.text, selectable = true)
        }
        if (rendered.truncated) {
            Text(
                text = stringResource(
                    R.string.resp_truncated,
                    CacheCleaner.formatBytes(rendered.totalBytes.toLong())
                ),
                modifier = Modifier.padding(top = 6.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
    }
}
