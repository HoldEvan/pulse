package com.evan.pulse.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import top.yukonga.miuix.kmp.squircle.squircleBackground

/**
 * 可折叠的两列键值表格：标题行点击展开/收起，默认折叠。
 *
 * 键列固定宽度、值列占满剩余空间，多行值在值列内自然换行，
 * 比等宽文本块更容易逐条核对请求头。
 */
@Composable
fun CollapsibleKeyValueTable(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp
            )
            AppText(
                text = stringResource(R.string.kv_table_count, items.size),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppIcon(
                // 用 Material 的实心箭头：Miuix 的细线箭头在折叠标题行里太不显眼
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.kv_table_collapse else R.string.kv_table_expand
                ),
                tint = AppTheme.colors.onSurfaceSummary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (!expanded) return@Column

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .squircleBackground(AppTheme.colors.surfaceVariant, 8.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                AppText(
                    text = stringResource(R.string.kv_table_key),
                    modifier = Modifier.width(KEY_COLUMN_WIDTH),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
                AppText(
                    text = stringResource(R.string.kv_table_value),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
            items.forEach { (key, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    AppText(
                        text = key,
                        modifier = Modifier.width(KEY_COLUMN_WIDTH),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                    AppText(
                        text = value,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurface
                    )
                }
            }
        }
    }
}

private val KEY_COLUMN_WIDTH = 132.dp
