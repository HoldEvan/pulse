package com.evan.pulse.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.l10n.L10n

/**
 * 保存到集合对话框：四个协议页共用。
 * [snapshotProvider] 在点击保存时才取协议页快照，避免对话框打开期间的过期状态。
 *
 * [existingRequestId] 非空表示当前配置来自集合里的某条接口：名称与所属集合回填成该条目
 * 的现状，确认时原地更新它（可改名、可换集合），不会新增一条记录。
 */
@Composable
fun SaveRequestDialog(
    show: Boolean,
    type: RequestType,
    defaultName: String,
    snapshotProvider: () -> String,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit = {},
    existingRequestId: Long? = null,
    viewModel: CollectionsViewModel = viewModel()
) {
    if (!show) return

    val collections by viewModel.collections.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val projectCollections = collections.filter { it.projectId == currentProjectId }
    val existing = existingRequestId?.let { viewModel.requestById(it) }

    var nameInput by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(show, defaultName, existing?.id) {
        nameInput = existing?.name ?: defaultName
        // 更新已有条目时定位到它当前所在的集合，避免误存到别处
        selectedIndex = existing
            ?.let { request -> projectCollections.indexOfFirst { it.id == request.collectionId } }
            ?.takeIf { it >= 0 }
            ?: 0
    }

    // 项目下暂无集合时显示默认集合项，保存时由 Store 自动创建
    val fallbackCollectionName = stringResource(R.string.collection_default_name)
    val items = if (projectCollections.isEmpty()) {
        listOf(stringResource(R.string.collection_default_name))
    } else {
        // 用完整路径区分不同层级里的同名文件夹
        projectCollections.map {
            CollectionTree.pathOf(collections, it.id).joinToString(" / ")
        }
    }

    AppDialog(
        show = true,
        title = stringResource(R.string.save_request_title),
        onDismissRequest = onDismiss
    ) {
        Column {
            AppTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = stringResource(R.string.request_name_label),
                modifier = Modifier.fillMaxWidth()
            )
            AppDropdown(
                items = items,
                selectedIndex = selectedIndex.coerceIn(0, items.lastIndex),
                title = stringResource(R.string.tab_collection),
                modifier = Modifier.fillMaxWidth(),
                showValue = true,
                onSelectedIndexChange = { selectedIndex = it }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                AppTextButton(
                    text = stringResource(R.string.action_save),
                    primary = true,
                    onClick = {
                        val collectionId = projectCollections.getOrNull(selectedIndex)?.id ?: -1L
                        val name = nameInput.ifBlank { existing?.name ?: defaultName }
                        val data = snapshotProvider()
                        val existingId = existingRequestId
                        if (existingId != null && viewModel.updateRequest(existingId, collectionId, name, data)) {
                            AppToast.show(L10n.t(R.string.toast_request_updated))
                            onSaved(existingId)
                        } else {
                            onSaved(
                                viewModel.saveRequest(
                                    collectionId = collectionId,
                                    type = type,
                                    name = name,
                                    data = data,
                                    fallbackCollectionName = fallbackCollectionName
                                )
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
