package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 占位符解析提示：把文本里的 {{key}} 逐个展开为“名字 → 解析值”，缺失的标红。
 * 供 URL、请求头等输入框下方展示，实时反馈环境变量能否解析。
 */
@Composable
fun ValueHints(
    hints: List<Pair<String, String?>>,
    modifier: Modifier = Modifier
) {
    if (hints.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        hints.forEach { (key, value) ->
            Text(
                text = if (value != null) {
                    "{{$key}} → $value"
                } else {
                    stringResource(R.string.env_hint_missing, key)
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (value != null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.error
                }
            )
        }
    }
}
