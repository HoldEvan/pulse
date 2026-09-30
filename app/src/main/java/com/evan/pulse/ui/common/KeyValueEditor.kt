package com.evan.pulse.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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

/**
 * 通用键值对编辑器（参数 / 请求头等），供 HTTP 与 SSE 等协议页共用。
 *
 * @param keyLabel 键列输入框标签
 * @param valueLabel 值列输入框标签
 */
@Composable
fun KeyValueEditor(
    items: List<KeyValue>,
    keyLabel: String,
    valueLabel: String,
    onChange: (Int, KeyValue) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEachIndexed { index, kv ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = kv.key,
                    onValueChange = { onChange(index, kv.copy(key = it)) },
                    label = keyLabel,
                    modifier = Modifier.weight(0.4f)
                )
                TextField(
                    value = kv.value,
                    onValueChange = { onChange(index, kv.copy(value = it)) },
                    label = valueLabel,
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(start = 6.dp)
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_delete),
                    modifier = Modifier
                        .clickable { onRemove(index) }
                        .padding(6.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .clickable { onAdd() }
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add)
            )
            Text(stringResource(R.string.kv_add_row), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
