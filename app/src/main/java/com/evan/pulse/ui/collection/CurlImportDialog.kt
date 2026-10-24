package com.evan.pulse.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.snapshotJson
import com.evan.pulse.domain.spec.CurlImporter
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/**
 * 从 cURL 命令导入单个请求。
 *
 * 粘贴 → 解析 → 直接写入当前文件夹（没有当前文件夹时落到项目默认集合）。
 * 解析失败只提示原因，不产生任何写入。
 */
@Composable
fun CurlImportDialog(
    show: Boolean,
    collectionId: Long?,
    onDismiss: () -> Unit,
    viewModel: CollectionsViewModel
) {
    if (!show) return
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val importedText = stringResource(R.string.curl_import_done)
    val failedText = stringResource(R.string.curl_import_failed)
    val fallbackCollection = stringResource(R.string.curl_import_collection)

    AppDialog(
        show = true,
        title = stringResource(R.string.curl_import_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppTextField(
                value = source,
                onValueChange = {
                    source = it
                    error = null
                },
                label = stringResource(R.string.curl_import_label),
                useLabelAsPlaceholder = true,
                singleLine = false,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            AppText(
                text = stringResource(R.string.curl_import_hint),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary,
                modifier = Modifier.padding(top = 8.dp)
            )
            error?.let { message ->
                AppText(
                    text = message,
                    fontSize = 12.sp,
                    color = AppTheme.colors.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            AppTextButton(
                text = stringResource(R.string.curl_import_action),
                filled = true,
                onClick = {
                    runCatching { CurlImporter.parse(source) }
                        .onSuccess { result ->
                            val id = viewModel.saveRequest(
                                collectionId = collectionId ?: 0L,
                                type = RequestType.HTTP,
                                name = result.name,
                                data = snapshotJson.encodeToString(
                                    HttpSnapshot.serializer(),
                                    result.snapshot
                                ),
                                fallbackCollectionName = fallbackCollection
                            )
                            if (id >= 0L) {
                                AppToast.show(importedText)
                                onDismiss()
                            } else {
                                error = failedText
                            }
                        }
                        .onFailure { error = it.message ?: failedText }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            AppTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
