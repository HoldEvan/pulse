package com.evan.pulse.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import top.yukonga.miuix.kmp.squircle.squircleBackground

private val jsonTreeJson = Json { ignoreUnknownKeys = true }

private val JsonKeyColor = Color(0xFF6A5ACD)
private val JsonStringColor = Color(0xFF43A047)
private val JsonNumberColor = Color(0xFFEF6C00)
private val JsonBoolColor = Color(0xFF00838F)
private val JsonNullColor = Color(0xFF8E8E8E)

/**
 * 可折叠 JSON 树：HTTP 响应体与 JSON 工具共用。
 *
 * [jsonText] 解析失败时原样回退为普通文本，保证任何响应体都能显示。
 * 折叠状态以路径（$.a[0].b）为键，藏在 remember 里，随重组保留。
 */
@Composable
fun JsonTreeView(
    jsonText: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
) {
    val blockModifier = modifier
        .fillMaxWidth()
        .padding(top = 6.dp)
        .squircleBackground(AppTheme.colors.surfaceVariant, 8.dp)
        .padding(10.dp)

    val element = remember(jsonText) {
        runCatching { jsonTreeJson.parseToJsonElement(jsonText) }.getOrNull()
    }

    if (element == null) {
        MonospaceBlock(jsonText, modifier = modifier, selectable = selectable)
        return
    }

    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    Column(modifier = blockModifier) {
        JsonNodeRow(
            path = "$",
            name = null,
            element = element,
            depth = 0,
            collapsed = collapsed
        )
    }
}

@Composable
private fun JsonNodeRow(
    path: String,
    name: String?,
    element: JsonElement,
    depth: Int,
    collapsed: MutableMap<String, Boolean>,
) {
    val isContainer = element is JsonObject || element is JsonArray
    if (!isContainer) {
        JsonLeafRow(name, element, depth)
        return
    }

    val isCollapsed = collapsed[path] ?: false
    val toggle = Modifier.clickable { collapsed[path] = !isCollapsed }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .then(toggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = null,
            tint = AppTheme.colors.onSurfaceSummary,
            modifier = Modifier.padding(end = 2.dp)
        )
        JsonName(name)
        AppText(
            text = preview(element, isCollapsed),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = AppTheme.colors.onSurface
        )
    }

    if (isCollapsed) return

    when (element) {
        is JsonObject -> element.forEach { (key, value) ->
            JsonNodeRow("$path.$key", key, value, depth + 1, collapsed)
        }

        is JsonArray -> element.forEachIndexed { index, value ->
            JsonNodeRow("$path[$index]", "[$index]", value, depth + 1, collapsed)
        }
    }
}

@Composable
private fun JsonLeafRow(name: String?, element: JsonElement, depth: Int) {
    Row(modifier = Modifier.padding(start = (depth * 14 + 20).dp)) {
        JsonName(name)
        AppText(
            text = scalarText(element),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = scalarColor(element, AppTheme.colors.onSurface)
        )
    }
}

@Composable
private fun JsonName(name: String?) {
    if (name == null) return
    AppText(
        text = if (name.startsWith("[")) name else "\"$name\": ",
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = JsonKeyColor
    )
}

@Composable
private fun preview(element: JsonElement, isCollapsed: Boolean): String {
    if (!isCollapsed) return ""
    return when (element) {
        is JsonObject -> "{…} ${stringResource(R.string.json_tree_items, element.size)}"
        is JsonArray -> "[…] ${stringResource(R.string.json_tree_items, element.size)}"
        else -> scalarText(element)
    }
}

private fun scalarText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> when {
        element.isString -> "\"${element.content}\""
        element.content == "true" || element.content == "false" || element.content == "null" ->
            element.content

        else -> element.content
    }

    is JsonNull -> "null"
    else -> ""
}

private fun scalarColor(element: JsonElement, fallback: Color): Color = when (element) {
    is JsonPrimitive -> when {
        element.isString -> JsonStringColor
        element.content == "true" || element.content == "false" -> JsonBoolColor
        else -> JsonNumberColor
    }

    is JsonNull -> JsonNullColor
    else -> fallback
}
