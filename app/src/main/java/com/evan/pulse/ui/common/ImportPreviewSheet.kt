package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.ImportedItem
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.ui.design.AppSmallTitle
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton

/**
 * 导入预览内容：按标签分组列出将要写入的接口，支持整组展开收起。
 *
 * 自身不弹窗口，由调用方放进 OverlayDialog；确认按钮是唯一写入入口。
 */
@Composable
fun ImportPreviewSheet(
    collectionName: String,
    groups: List<ImportGroup>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val totalCount = groups.sumOf { it.items.size }
    val collapsed = remember { mutableStateOf(emptyMap<String, Boolean>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
        ) {
            item(key = "summary") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    AppSmallTitle(text = collectionName)
                    AppText(
                        text = stringResource(R.string.import_summary_format, totalCount, groups.size),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

        groups.forEach { group ->
            item(key = "group:${group.tag}") {
                AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardSectionTitle(
                            text = group.tag,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                        )
                        AppText(
                            text = group.items.size.toString(),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        AppTextButton(
                            text = if (collapsed.value[group.tag] == true) {
                                stringResource(R.string.action_expand)
                            } else {
                                stringResource(R.string.action_collapse)
                            },
                            onClick = {
                                collapsed.value = collapsed.value + (
                                    group.tag to (collapsed.value[group.tag] != true)
                                )
                            }
                        )
                    }
                }
            }

            if (collapsed.value[group.tag] != true) {
                group.items.forEachIndexed { index, request ->
                    item(key = "request:${group.tag}:$index") {
                        RequestPreviewCard(request)
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            text = stringResource(R.string.import_confirm_button),
            onClick = onConfirm,
            primary = true,
            modifier = Modifier.weight(1f)
        )
    }
}
}

@Composable
private fun RequestPreviewCard(request: ImportedItem) {
    val url = remember(request.data) { requestUrl(request.data) }
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 2.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            AppText(text = request.name)
            if (url.isNotEmpty()) {
                AppText(
                    text = url,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun requestUrl(data: String): String = runCatching {
    snapshotJson.decodeFromString(HttpSnapshot.serializer(), data).url
}.getOrDefault("")
