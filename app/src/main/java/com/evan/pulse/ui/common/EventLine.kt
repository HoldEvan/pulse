package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.history.UiEventLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val prettyJson = Json { prettyPrint = true }

/**
 * 消息流单行卡片。系统事件用 surfaceVariant 底色区分，收到的数据用主色容器底色，
 * 供 WS / SSE 等流式协议页共用。
 *
 * @param systemLabel 系统行的方向标签
 * @param recvLabel 收到数据行的方向标签
 */
@Composable
fun EventLine(line: UiEventLine, systemLabel: String, recvLabel: String) {
    val directionLabel = if (line.isSystem) systemLabel else recvLabel
    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(line.timestamp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .squircleBackground(
                if (line.isSystem) {
                    MiuixTheme.colorScheme.surfaceVariant
                } else {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                },
                16.dp
            )
            .padding(8.dp)
    ) {
        Text(text = stringResource(R.string.event_time_direction, time, directionLabel), fontSize = 11.sp)
        Text(
            text = prettyLine(line.text),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** JSON 文本自动缩进美化，非 JSON 内容原样返回 */
private fun prettyLine(text: String): String = try {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
} catch (_: Exception) {
    text
}
