package com.evan.pulse.ui.common

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
import com.evan.pulse.ui.history.UiEventLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import top.yukonga.miuix.kmp.squircle.squircleBackground

private val prettyJson = Json { prettyPrint = true }

/** 单条消息超过该字符数时默认不展开树形，避免长流卡顿 */
private const val JSON_INLINE_TREE_LIMIT = 20_000

/**
 * 消息流单行卡片。系统事件用 surfaceVariant 底色区分，收到的数据用主色容器底色，
 * 供 WS / SSE 等流式协议页共用。
 *
 * 非系统行若内容是可解析的 JSON，提供「树形 / 原文」切换：树形视图复用响应体的
 * 可折叠结构，便于逐层核对嵌套字段。
 *
 * @param systemLabel 系统行的方向标签
 * @param recvLabel 收到数据行的方向标签
 */
@Composable
fun EventLine(line: UiEventLine, systemLabel: String, recvLabel: String) {
    val directionLabel = if (line.isSystem) systemLabel else recvLabel
    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(line.timestamp))
    // 只有非系统行、可解析且不太大时才提供树形切换
    val jsonCapable = !line.isSystem && line.text.length <= JSON_INLINE_TREE_LIMIT &&
        runCatching { Json.parseToJsonElement(line.text) }.isSuccess
    var treeMode by remember(line.text) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .squircleBackground(
                if (line.isSystem) {
                    AppTheme.colors.surfaceVariant
                } else {
                    AppTheme.colors.primary.copy(alpha = 0.15f)
                },
                16.dp
            )
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = stringResource(R.string.event_time_direction, time, directionLabel),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            if (jsonCapable) {
                AppTextButton(
                    text = stringResource(
                        if (treeMode) R.string.event_view_raw else R.string.event_view_tree
                    ),
                    onClick = { treeMode = !treeMode }
                )
            }
        }
        if (jsonCapable && treeMode) {
            JsonTreeView(jsonText = line.text, modifier = Modifier.padding(top = 4.dp))
        } else {
            AppText(
                text = prettyLine(line.text),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** JSON 文本自动缩进美化，非 JSON 内容原样返回 */
private fun prettyLine(text: String): String = try {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
} catch (_: Exception) {
    text
}
