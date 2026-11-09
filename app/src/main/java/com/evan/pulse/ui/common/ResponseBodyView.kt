package com.evan.pulse.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppTextButton

/**
 * 响应体多格式查看：RAW/JSON/XML/HTML/HEX/BASE64 六种视图，JSON 时展示树形，
 * HTML 默认显示源码，可跳转单独页面做 WebView 预览。
 * 媒体预览（image/audio/video）只在响应头 Content-Type 声明为媒体类型时出现，
 * 需要实时响应的原始字节 [bodyBytes]；历史详情无字节时该标签提示不可预览。
 * 图片原生解码后由 Compose 绘制（可缩放），音频/视频才走 WebView。
 * 底部提供保存与分享，导出的是完整字节而不是展示层的截断文本。
 */
@Composable
fun ResponseBodyView(
    body: String,
    baseUrl: String = "",
    contentType: String? = null,
    bodyBytes: ByteArray? = null,
    modifier: Modifier = Modifier,
    onOpenHtmlPreview: ((body: String, baseUrl: String) -> Unit)? = null
) {
    var mode by remember { mutableStateOf(BodyViewMode.RAW) }
    val mediaType = remember(contentType) {
        contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    }
    // 预览标签的可见性由 Content-Type 决定：文本/JSON 响应不再展示预览入口；
    // 响应头缺失时保留标签，交给字节内容自行判断，避免老记录看不到预览
    val showMediaPreview = mediaType.isEmpty() || isMediaType(mediaType)
    val modes = remember(showMediaPreview) {
        if (showMediaPreview) {
            BodyViewMode.entries
        } else {
            BodyViewMode.entries.filter { it != BodyViewMode.PREVIEW }
        }
    }
    // Content-Type 变化后原选中项可能已不在标签列表里，回落到 RAW
    val currentMode = if (mode in modes) mode else BodyViewMode.RAW
    val modeLabelRes = remember {
        mapOf(
            BodyViewMode.RAW to R.string.resp_view_raw,
            BodyViewMode.JSON to R.string.resp_view_json,
            BodyViewMode.XML to R.string.resp_view_xml,
            BodyViewMode.HTML to R.string.resp_view_html,
            BodyViewMode.HEX to R.string.resp_view_hex,
            BodyViewMode.BASE64 to R.string.resp_view_b64,
            BodyViewMode.PREVIEW to R.string.resp_view_preview
        )
    }
    Column(modifier = modifier) {
        // 七个标签平铺会把宽度压到 44dp 左右，Base64/预览等文字被截断且整排
        // 刚好占满屏幕导致无法滑动；抬高最小宽度让整排溢出，两种皮肤都可横向滚动
        AppTabRow(
            tabs = modes.map { stringResource(modeLabelRes.getValue(it)) },
            selectedTabIndex = modes.indexOf(currentMode),
            onTabSelected = { mode = modes[it] },
            minWidth = 84.dp,
            maxWidth = 160.dp,
            height = 40.dp,
            itemSpacing = 4.dp,
            cornerRadius = 10.dp,
            scrollable = true
        )
        // 解析结果随 body 缓存：大 JSON 每次重组都重新解析会明显掉帧
        val jsonParsed = remember(body) { isJson(body) }
        if (currentMode == BodyViewMode.PREVIEW) {
            MediaPreview(
                contentType = contentType,
                bodyBytes = bodyBytes,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (currentMode == BodyViewMode.HTML) {
            // HTML 默认按源码展示，避免不可信页面在列表里直接执行/外联资源；
            // 需要看渲染效果时点“预览”进入独立全屏页面
            val text = remember(body) { renderModeText(body, BodyViewMode.HTML) }
            PagedTextBlock(text = text)
            if (onOpenHtmlPreview != null && isHtmlResponse(contentType, body)) {
                AppTextButton(
                    text = stringResource(R.string.resp_view_html_preview),
                    onClick = { onOpenHtmlPreview(body, baseUrl) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else if (currentMode == BodyViewMode.JSON && jsonParsed) {
            JsonTreeView(body)
        } else if (currentMode == BodyViewMode.HEX) {
            // HEX 按字节区间出页：几 MB 的响应体也能完整查看，不用一次性拼字符串
            val bytes = remember(body, bodyBytes) { bodyBytes ?: body.toByteArray(Charsets.UTF_8) }
            PagedHexBlock(bytes = bytes)
        } else {
            // 文本视图不再截断：超过单页预算时只渲染当前页，其余内容翻页可达
            val text = remember(body, currentMode) { renderModeText(body, currentMode) }
            PagedTextBlock(text = text)
        }
        ResponseExportActions(
            body = body,
            bodyBytes = bodyBytes,
            contentType = contentType,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 响应头声明的 Content-Type 是否属于图片/音频/视频 */
fun isMediaType(mediaType: String): Boolean =
    mediaType.startsWith("image/") || mediaType.startsWith("audio/") || mediaType.startsWith("video/")

/**
 * 是否按 HTML 提供预览：以响应头 Content-Type 为准；
 * 历史记录可能没有存响应头，此时按正文开头的 doctype / <html> 兜底判断。
 */
fun isHtmlResponse(contentType: String?, body: String): Boolean {
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (mediaType.isNotEmpty()) {
        return mediaType == "text/html" || mediaType == "application/xhtml+xml"
    }
    val head = body.trimStart().take(64).lowercase()
    return head.startsWith("<!doctype html") || head.startsWith("<html")
}

/**
 * 媒体预览：图片走原生解码，音频/视频仍交给 WebView 播放。
 *
 * 图片曾经拼成 data URI 塞进 HTML，Android WebView 对超长 URL（约 2MB）会静默失败，
 * 大图因此只剩一片空白；改为 BitmapFactory 解码后直接用 Compose 绘制，既没有长度限制，
 * 也省掉 base64 带来的 1/3 体积膨胀。
 */
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
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val image = remember(mediaType, bodyBytes) {
        if (mediaType.startsWith("image/")) decodePreviewImage(bodyBytes) else null
    }
    if (image != null) {
        ImagePreview(image, modifier = modifier)
        return
    }
    // 非图片，或图片解码失败（如 SVG）：回落到 WebView，让浏览器自己处理
    val html = remember(mediaType, bodyBytes) { buildPlaybackHtml(mediaType, bodyBytes) }
    HtmlPreview(html, modifier = modifier)
}

/** 解码后的预览图、原始像素尺寸与响应字节数；后两者用于界面上的说明文字 */
private class PreviewImage(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val sourceBytes: Int,
)

/** 预览图最长边上限：手机屏幕远小于这个值，再大只是白占内存 */
private const val PREVIEW_MAX_DIMENSION = 2048

/**
 * 按最长边 [PREVIEW_MAX_DIMENSION] 采样解码。
 *
 * 先只读尺寸再算采样率，避免几千万像素的原图一次性展开导致 OOM；
 * 无法识别的格式（如 SVG）返回 null，由调用方回退到 WebView。
 */
private fun decodePreviewImage(bytes: ByteArray): PreviewImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PREVIEW_MAX_DIMENSION ||
        bounds.outHeight / (sample * 2) >= PREVIEW_MAX_DIMENSION
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull() ?: return null
    return PreviewImage(bitmap, bounds.outWidth, bounds.outHeight, bytes.size)
}

/** 图片预览：等比铺满固定高度的黑底区域，支持双指缩放与拖动 */
@Composable
private fun ImagePreview(image: PreviewImage, modifier: Modifier = Modifier) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
                .pointerInput(image) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 6f)
                        // 缩回原始大小时归位，避免图片被拖出可视区域后找不回来
                        offset = if (next <= 1f) Offset.Zero else offset + pan
                        scale = next
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
        AppText(
            text = "${image.width} × ${image.height} · ${CacheCleaner.formatBytes(image.sourceBytes.toLong())}",
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 11.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

/** 音频/视频：内嵌 data URI 的 HTML，交给 WebView 播放 */
private fun buildPlaybackHtml(mediaType: String, bytes: ByteArray): String {
    val mime = mediaType.ifBlank { "application/octet-stream" }
    val dataUri = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    return """<!doctype html><html><body style="margin:0;background:#000;height:100vh;">
        <video src="$dataUri" controls autoplay playsinline style="width:100%;height:100%;object-fit:contain;"></video></body></html>"""
}