package com.evan.pulse.ui.common

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 8.dp)
        .padding(10.dp)
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()
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
        Text(
            text = text,
            modifier = blockModifier,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

/** 状态码徽章：按状态段着色，字号与内边距和 [MethodBadge] 一致，便于并排比较 */
@Composable
fun StatusBadge(statusCode: Int, modifier: Modifier = Modifier) {
    Text(
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
    Text(
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
enum class BodyViewMode { RAW, JSON, XML, HEX, BASE64 }

/** [text] 为渲染结果，[truncated] 表示大内容被截断，[totalBytes] 为原始响应体字节数 */
data class RenderedBody(
    val text: String,
    val totalBytes: Int,
    val truncated: Boolean,
)

private const val MAX_TEXT_CHARS = 65536
private const val MAX_HEX_BYTES = 4096
private const val HEX_COLUMNS = 16
private const val BASE64_LINE = 76

private val HEX_NIBBLE = "0123456789ABCDEF"

private fun clipText(text: String, totalBytes: Int): RenderedBody {
    return if (text.length <= MAX_TEXT_CHARS) {
        RenderedBody(text, totalBytes, false)
    } else {
        RenderedBody(text.substring(0, MAX_TEXT_CHARS) + "\n... [truncated]", totalBytes, true)
    }
}

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

private fun prettyXml(xml: String): String {
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

private fun hexDump(bytes: ByteArray, limit: Int): RenderedBody {
    val shown = if (bytes.size > limit) bytes.copyOf(limit) else bytes
    val sb = StringBuilder(shown.size * 9 + 16)
    var offset = 0
    while (offset < shown.size) {
        val end = minOf(offset + HEX_COLUMNS, shown.size)
        sb.append(String.format(Locale.US, "%08X  ", offset))
        for (i in offset until minOf(offset + HEX_COLUMNS / 2, end)) {
            val b = shown[i].toInt() and 0xFF
            sb.append(HEX_NIBBLE[b shr 4]).append(HEX_NIBBLE[b and 0x0F]).append(' ')
        }
        sb.append(' ')
        for (i in minOf(offset + HEX_COLUMNS / 2, end) until end) {
            val b = shown[i].toInt() and 0xFF
            sb.append(HEX_NIBBLE[b shr 4]).append(HEX_NIBBLE[b and 0x0F]).append(' ')
        }
        sb.append(" |")
        for (i in offset until end) {
            val c = shown[i].toInt() and 0xFF
            sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
        }
        sb.append("|\n")
        offset += HEX_COLUMNS
    }
    return RenderedBody(sb.toString().trimEnd(), bytes.size, bytes.size > limit)
}

/** 按 [mode] 渲染响应体；解析失败的 JSON/XML 回退为原文 */
fun renderBody(body: String, mode: BodyViewMode): RenderedBody {
    val totalBytes = body.toByteArray(Charsets.UTF_8).size
    return when (mode) {
        BodyViewMode.RAW -> clipText(body, totalBytes)
        BodyViewMode.JSON -> clipText(prettyBody(body, true), totalBytes)
        BodyViewMode.XML -> clipText(prettyXml(body), totalBytes)
        BodyViewMode.HEX -> hexDump(body.toByteArray(Charsets.UTF_8), MAX_HEX_BYTES)
        BodyViewMode.BASE64 -> clipText(base64Wrapped(body.toByteArray(Charsets.UTF_8)), totalBytes)
    }
}
