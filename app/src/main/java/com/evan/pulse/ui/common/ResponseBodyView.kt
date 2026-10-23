package com.evan.pulse.ui.common

import android.util.Base64
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
 * HTML 时展示静态预览；提供 PREVIEW 媒体预览（image/audio/video）。
 * 媒体预览需要实时响应的原始字节 [bodyBytes]；历史详情无字节时该标签提示不可预览。
 */
@Composable
fun ResponseBodyView(
    body: String,
    baseUrl: String = "",
    contentType: String? = null,
    bodyBytes: ByteArray? = null,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(BodyViewMode.RAW) }
    val modeLabels = listOf(
        stringResource(R.string.resp_view_raw),
        stringResource(R.string.resp_view_json),
        stringResource(R.string.resp_view_xml),
        stringResource(R.string.resp_view_html),
        stringResource(R.string.resp_view_hex),
        stringResource(R.string.resp_view_b64),
        stringResource(R.string.resp_view_preview)
    )
    Column(modifier = modifier) {
        // 七个标签平铺会把宽度压到 44dp 左右，Base64/预览等文字被截断且整排
        // 刚好占满屏幕导致无法滑动；抬高最小宽度让整排溢出，两种皮肤都可横向滚动
        AppTabRow(
            tabs = modeLabels,
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = BodyViewMode.entries[it] },
            minWidth = 84.dp,
            maxWidth = 160.dp,
            height = 40.dp,
            itemSpacing = 4.dp,
            cornerRadius = 10.dp,
            scrollable = true
        )
        if (mode == BodyViewMode.PREVIEW) {
            MediaPreview(
                contentType = contentType,
                bodyBytes = bodyBytes,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
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
}

/** 媒体预览：根据 Content-Type 把字节转 data URI 交给 WebView；无字节时提示 */
@Composable
private fun MediaPreview(contentType: String?, bodyBytes: ByteArray?, modifier: Modifier = Modifier) {
    if (bodyBytes == null || bodyBytes.isEmpty()) {
        AppText(
            text = stringResource(R.string.resp_preview_unavailable),
            modifier = modifier,
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        return
    }
    val mediaType = contentType?.substringBefore(';')?.trim().orEmpty()
    val html = remember(mediaType, bodyBytes) { buildMediaHtml(mediaType, bodyBytes) }
    HtmlPreview(html, modifier = modifier)
}

/** 把响应字节按媒体类型封装成内嵌 data URI 的 HTML，交给 WebView 渲染 */
private fun buildMediaHtml(mediaType: String, bytes: ByteArray): String {
    val mime = mediaType.ifBlank { "application/octet-stream" }
    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val dataUri = "data:$mime;base64,$b64"
    return if (mime.startsWith("image/")) {
        """<!doctype html><html><body style="margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh;">
        <img src="$dataUri" style="max-width:100%;max-height:100%;object-fit:contain;"></body></html>"""
    } else {
        """<!doctype html><html><body style="margin:0;background:#000;height:100vh;">
        <video src="$dataUri" controls autoplay playsinline style="width:100%;height:100%;object-fit:contain;"></video></body></html>"""
    }
}