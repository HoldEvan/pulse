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
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton

/**
 * 响应体多格式查看：RAW/JSON/XML/HTML/HEX/BASE64 六种视图，JSON 时展示树形，
 * HTML 时展示静态预览；HTTP 响应卡与历史详情共用，保证两处查看体验一致。
 * [baseUrl] 为响应对应的请求 URL，供 HTML 预览解析相对路径资源。
 */
@Composable
fun ResponseBodyView(body: String, baseUrl: String = "", modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(BodyViewMode.RAW) }
    val modeLabels = listOf(
        stringResource(R.string.resp_view_raw),
        stringResource(R.string.resp_view_json),
        stringResource(R.string.resp_view_xml),
        stringResource(R.string.resp_view_html),
        stringResource(R.string.resp_view_hex),
        stringResource(R.string.resp_view_b64)
    )
    Column(modifier = modifier) {
        AppTabRow(
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
        if (mode == BodyViewMode.HTML) {
            HtmlPreview(body, baseUrl, modifier = Modifier.padding(top = 8.dp))
            AppTextButton(
                text = stringResource(R.string.resp_view_html_source),
                onClick = { mode = BodyViewMode.RAW },
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (mode == BodyViewMode.JSON && isJson(body)) {
            JsonTreeView(body)
        } else {
            MonospaceBlock(rendered.text, selectable = true)
        }
        if (rendered.truncated) {
            AppText(
                text = stringResource(
                    R.string.resp_truncated,
                    CacheCleaner.formatBytes(rendered.totalBytes.toLong())
                ),
                modifier = Modifier.padding(top = 6.dp),
                color = AppTheme.colors.onSurfaceSummary,
                fontSize = 12.sp
            )
        }
    }
}
