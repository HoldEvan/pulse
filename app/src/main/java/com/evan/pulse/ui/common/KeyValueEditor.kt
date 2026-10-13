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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close

/**
 * 通用键值对编辑器（参数 / 请求头等），供 HTTP 与 SSE 等协议页共用。
 *
 * 自动行管理：列表始终以一个空行结尾——末行输入键或值后自动补新空行，
 * 尾部出现连续空行时收敛为一个；仅剩一行时隐藏删除图标。
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
    // 基于当前列表推演本次操作的结果，把尾部空行收敛为恰好一个
    fun collapseTrailingEmpties(list: MutableList<KeyValue>) {
        while (list.size > 1 && list.last().let { it.key.isEmpty() && it.value.isEmpty() } &&
            list[list.size - 2].let { it.key.isEmpty() && it.value.isEmpty() }
        ) {
            val removedIndex = list.size - 1
            list.removeAt(removedIndex)
            onRemove(removedIndex)
        }
    }

    fun onEdited(index: Int, newValue: KeyValue) {
        onChange(index, newValue)
        val list = items.toMutableList()
        list[index] = newValue
        collapseTrailingEmpties(list)
        if (list.last().key.isNotEmpty() || list.last().value.isNotEmpty()) onAdd()
    }

    fun onDeleted(index: Int) {
        onRemove(index)
        val list = items.toMutableList()
        list.removeAt(index)
        collapseTrailingEmpties(list)
        if (list.isNotEmpty() && (list.last().key.isNotEmpty() || list.last().value.isNotEmpty())) onAdd()
    }

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
                        onValueChange = { onEdited(index, kv.copy(key = it)) },
                        label = keyLabel,
                        modifier = Modifier.weight(0.4f),
                        singleLine = true
                    )
                    TextField(
                        value = kv.value,
                        onValueChange = { onEdited(index, kv.copy(value = it)) },
                        label = valueLabel,
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(start = 6.dp),
                        singleLine = true
                    )
                    if (items.size > 1) {
                        Icon(
                            imageVector = MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.cd_delete),
                            modifier = Modifier
                                .clickable { onDeleted(index) }
                                .padding(6.dp)
                        )
                    }
                }
                valueHintProvider?.let { provider ->
                    ValueHints(hints = provider(kv.value), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
