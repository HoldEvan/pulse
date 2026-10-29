package com.evan.pulse.ui.common

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppText
import top.yukonga.miuix.kmp.squircle.squircleBackground

import java.io.StringWriter
import java.util.Base64
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

private const val indentStepKey = "{http://xml.apache.org/xslt}indent-step"

/**
 * 等宽代码块：HTTP 响应体、导出结果与历史详情页共用。
 *
 * [selectable] 为 true 时改用原生 TextView 渲染，长按后可拖动选择任意片段再复制，
 * 适合响应头、响应体这类需要只取一部分文本的场景。
 */
@Composable
fun MonospaceBlock(text: String, modifier: Modifier = Modifier, selectable: Boolean = false) {
    val blockModifier = modifier
        .fillMaxWidth()
        .padding(top = 6.dp)
        .squircleBackground(AppTheme.colors.surfaceVariant, 8.dp)
        .padding(10.dp)
    val textColor = AppTheme.colors.onSurface.toArgb()
    if (selectable) {
        AndroidView(
            modifier = blockModifier,
            factory = { context ->
                TextView(context).apply {
                    typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(textColor)
                    setTextIsSelectable(true)
                    maxLines = Int.MAX_VALUE
                }
            },
            update = { view ->
                view.text = text
                view.setTextColor(textColor)
            }
        )
    } else {
        AppText(
            text = text,
            modifier = blockModifier,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

/**
 * Preview HTML as content with active capabilities kept to a minimum:
 * JS、文件与内容访问一律关闭；CSS/图片等网络子资源放行，主框架导航（链接点击、
 * meta refresh 等）一律拦截，保证是静态预览。[baseUrl] 为响应对应的请求 URL，
 * 合法 http(s) 时作为基准解析相对路径资源。
 */
@Composable
fun HtmlPreview(
    body: String,
    baseUrl: String? = null,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false
) {
    AndroidView(
        modifier = if (fillHeight) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)
        },
        factory = { context ->
            WebView(context).apply {
                // 部分机型（Android 16/HyperOS）WebView 的 Vulkan 绘制路径会在
                // RenderThread 触发 SIGTRAP 原生崩溃，静态预览用软件渲染绕开。
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    // img/CSS 等子资源不经过此回调；拦截主框架导航防止跳转离开预览
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
            }
        },
        update = { view ->
            val base = baseUrl?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (view.tag != base to body) {
                view.tag = base to body
                view.loadDataWithBaseURL(base, body, "text/html", "UTF-8", null)
            }
        }
    )
}

/** 状态码徽章：按状态段着色，字号与内边距和 [MethodBadge] 一致，便于并排比较 */
@Composable
fun StatusBadge(statusCode: Int, modifier: Modifier = Modifier) {
    AppText(
        text = statusCode.toString(),
        color = Color.White,
        modifier = modifier
            .squircleBackground(statusColor(statusCode), 6.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 12.sp
    )
}

/** 状态码配色，供徽章与列表行的状态码文字共用 */
fun statusColor(statusCode: Int): Color = when {
    statusCode in 200..299 -> Color(0xFF2E7D32)
    statusCode in 300..399 -> Color(0xFF1565C0)
    statusCode in 400..499 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

/** 方法徽章：各方法一色，未知方法用紫色兜底 */
@Composable
fun MethodBadge(method: String, modifier: Modifier = Modifier) {
    AppText(
        text = method,
        color = Color.White,
        modifier = modifier
            .squircleBackground(methodColor(method), 6.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 12.sp
    )
}

/** 方法配色，供徽章与列表行共用 */
fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF1565C0)
    "POST" -> Color(0xFF2E7D32)
    "PUT" -> Color(0xFFEF6C00)
    "DELETE" -> Color(0xFFC62828)
    "PATCH" -> Color(0xFF6A1B9A)
    else -> Color(0xFF616161)
}

private val prettyJson = Json { prettyPrint = true }

/** 美化 JSON；不是合法 JSON 时原样返回 */
fun prettyBody(body: String, pretty: Boolean): String {
    if (!pretty) return body
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(body))
    } catch (_: Exception) {
        body
    }
}

/** 判断字符串是否为合法 JSON，供展示层决定用树形还是纯文本 */
fun isJson(body: String): Boolean = try {
    Json.parseToJsonElement(body)
    true
} catch (_: Exception) {
    false
}

/** 响应体的五种展示形态；顺序即界面标签顺序 */
enum class BodyViewMode { RAW, JSON, XML, HTML, HEX, BASE64, PREVIEW }

/** 单页 hex 覆盖的字节数：64 行 × 16 列，足够一屏且单页字符量可控 */
const val HEX_BYTES_PER_PAGE = 1024

private const val HEX_COLUMNS = 16
private const val BASE64_LINE = 76

private val HEX_NIBBLE = "0123456789ABCDEF"

private fun base64Wrapped(bytes: ByteArray): String {
    val encoded = Base64.getEncoder().encodeToString(bytes)
    if (encoded.length <= BASE64_LINE) return encoded
    val sb = StringBuilder(encoded.length + encoded.length / BASE64_LINE)
    var from = 0
    while (from < encoded.length) {
        val to = minOf(from + BASE64_LINE, encoded.length)
        sb.append(encoded, from, to)
        from = to
        if (from < encoded.length) sb.append('\n')
    }
    return sb.toString()
}

fun prettyXml(xml: String): String {
    return try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val doc = factory.newDocumentBuilder().parse(InputSource(xml))
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(indentStepKey, "2")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        writer.toString()
    } catch (_: Exception) {
        xml
    }
}

/**
 * 按字节区间渲染 hex dump，供分页展示。
 *
 * [offset] 为起始字节偏移，[byteCount] 为本页最多展示的字节数；越界自动钳到可用范围。
 */
fun hexDumpRange(bytes: ByteArray, offset: Int, byteCount: Int = HEX_BYTES_PER_PAGE): String {
    if (bytes.isEmpty() || byteCount <= 0) return ""
    val from = offset.coerceIn(0, bytes.size)
    val end = (from + byteCount).coerceAtMost(bytes.size)
    if (from >= end) return ""
    val sb = StringBuilder((end - from) * 9 + 16)
    var cursor = from
    while (cursor < end) {
        val lineEnd = minOf(cursor + HEX_COLUMNS, end)
        sb.append(String.format(Locale.US, "%08X  ", cursor))
        for (i in cursor until minOf(cursor + HEX_COLUMNS / 2, lineEnd)) {
            val b = bytes[i].toInt() and 0xFF
            sb.append(HEX_NIBBLE[b shr 4]).append(HEX_NIBBLE[b and 0x0F]).append(' ')
        }
        sb.append(' ')
        for (i in minOf(cursor + HEX_COLUMNS / 2, lineEnd) until lineEnd) {
            val b = bytes[i].toInt() and 0xFF
            sb.append(HEX_NIBBLE[b shr 4]).append(HEX_NIBBLE[b and 0x0F]).append(' ')
        }
        sb.append(" |")
        for (i in cursor until lineEnd) {
            val c = bytes[i].toInt() and 0xFF
            sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
        }
        sb.append("|\n")
        cursor = lineEnd
    }
    return sb.toString().trimEnd()
}

/**
 * 把响应体渲染成 [mode] 对应的完整文本，不做长度裁剪。
 *
 * 大文本由 [PagedTextBlock] 按页切片展示，这里返回全文即可；HEX 与媒体预览不走此函数。
 */
fun renderModeText(body: String, mode: BodyViewMode): String = when (mode) {
    BodyViewMode.RAW -> body
    BodyViewMode.JSON -> prettyBody(body, true)
    BodyViewMode.XML -> prettyXml(body)
    BodyViewMode.HTML -> body
    BodyViewMode.BASE64 -> base64Wrapped(body.toByteArray(Charsets.UTF_8))
    BodyViewMode.HEX, BodyViewMode.PREVIEW -> body
}
