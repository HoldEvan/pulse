package com.evan.pulse.ui.collection

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.spec.CollectionExportFormat
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 导出当前文件夹：选择 Postman Collection 或 OpenAPI 3，再交给 SAF 选择保存位置。
 *
 * 文本在点击格式时生成并暂存，等用户选完位置再写入；取消保存不会留下任何文件。
 */
@Composable
fun CollectionExportDialog(
    show: Boolean,
    collectionId: Long?,
    onDismiss: () -> Unit,
    viewModel: CollectionsViewModel
) {
    if (!show || collectionId == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingText by remember { mutableStateOf("") }

    val doneText = stringResource(R.string.collection_export_done)
    val failedText = stringResource(R.string.collection_export_failed)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = pendingText
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { writeText(context, uri, text) }.isSuccess
            AppToast.show(if (ok) doneText else failedText)
        }
        onDismiss()
    }

    AppDialog(
        show = true,
        title = stringResource(R.string.collection_export_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppText(
                text = stringResource(R.string.collection_export_hint),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppTextButton(
                text = stringResource(R.string.collection_export_postman),
                filled = true,
                onClick = {
                    viewModel.exportCollection(collectionId, CollectionExportFormat.POSTMAN)
                        ?.let { (name, result) ->
                            pendingText = result.text
                            launcher.launch(name)
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            AppTextButton(
                text = stringResource(R.string.collection_export_openapi),
                filled = true,
                onClick = {
                    viewModel.exportCollection(collectionId, CollectionExportFormat.OPENAPI)
                        ?.let { (name, result) ->
                            pendingText = result.text
                            launcher.launch(name)
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            AppTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 把导出文本写到用户选定的 Uri；覆盖同名文件 */
private fun writeText(context: Context, uri: android.net.Uri, text: String) {
    val output = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw java.io.IOException("无法写入目标文件")
    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}
