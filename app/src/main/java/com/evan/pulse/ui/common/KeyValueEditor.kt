package com.evan.pulse.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.domain.model.KeyValue
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close

/**
 * 通用键值对编辑器（参数 / 请求头等），供 HTTP 与 SSE 等协议页共用。
 *
 * @param keyLabel 键列输入框标签
 * @param valueLabel 值列输入框标签
 * @param valueHintProvider 非空时，对每个值的占位符解析提示，供环境变量可视化
 */
@Composable
fun KeyValueEditor(
    items: List<KeyValue>,
    keyLabel: String,
    valueLabel: String,
    onChange: (Int, KeyValue) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    valueHintProvider: ((String) -> List<Pair<String, String?>>)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        items.forEachIndexed { index, kv ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = kv.key,
                        onValueChange = { onChange(index, kv.copy(key = it)) },
                        label = keyLabel,
                        modifier = Modifier.weight(0.4f),
                        singleLine = true
                    )
                    TextField(
                        value = kv.value,
                        onValueChange = { onChange(index, kv.copy(value = it)) },
                        label = valueLabel,
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(start = 6.dp),
                        singleLine = true
                    )
                    Icon(
                        imageVector = MiuixIcons.Light.Close,
                        contentDescription = stringResource(R.string.cd_delete),
                        modifier = Modifier
                            .clickable { onRemove(index) }
                            .padding(6.dp)
                    )
                }
                valueHintProvider?.let { provider ->
                    ValueHints(hints = provider(kv.value), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .roundedPressable { onAdd() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Light.Add,
                contentDescription = stringResource(R.string.cd_add)
            )
            Text(stringResource(R.string.kv_add_row), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
