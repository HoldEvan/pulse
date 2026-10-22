package com.evan.pulse.ui.spec

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.spec.HarImporter
import com.evan.pulse.domain.spec.ImportPlan
import com.evan.pulse.domain.spec.OpenApiImporter
import com.evan.pulse.domain.spec.PostmanImporter
import com.evan.pulse.domain.spec.SpecError
import com.evan.pulse.domain.spec.SpecFetcher
import com.evan.pulse.domain.spec.SpecFormat
import com.evan.pulse.domain.spec.SpecImportException
import com.evan.pulse.ui.collection.CollectionsViewModel
import com.evan.pulse.ui.common.ImportPreviewSheet
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入 API 描述：输入文件 URL 或粘贴原始文本，解析后先看预览再写入集合。
 * format 指定解析器（OpenAPI / Postman / HAR），其余管线完全复用。
 *
 * 预览阶段不产生任何写入，取消直接关闭。
 */
@Composable
fun SpecImportScreen(
    format: SpecFormat? = null,
    onDismiss: () -> Unit,
    viewModel: CollectionsViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settingsStore.settings.collectAsState()

    var source by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var pending by remember { mutableStateOf<ImportPlan?>(null) }
    var activeFormat by remember { mutableStateOf(format ?: SpecFormat.OPENAPI) }

    val untaggedLabel = stringResource(R.string.spec_import_untagged)

    fun parse(activeFormat: SpecFormat) {
        if (busy) return
        if (source.trim().isEmpty()) {
            failure = SpecImportException(SpecError.EMPTY, "内容为空")
            return
        }
        busy = true
        failure = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SpecFetcher.fetch(
                        source,
                        ServiceLocator.httpEngine,
                        settings.toProxyConfig()
                    )
                    when (activeFormat) {
                        SpecFormat.OPENAPI -> OpenApiImporter.plan(text, untaggedLabel)
                        SpecFormat.POSTMAN -> PostmanImporter.plan(text, untaggedLabel)
                        SpecFormat.HAR -> HarImporter.plan(text, untaggedLabel)
                    }
                }
            }
            busy = false
            result.fold(
                onSuccess = { plan -> pending = plan },
                onFailure = { error -> failure = error }
            )
        }
    }

    val title = stringResource(
        when {
            pending != null -> R.string.import_preview_title
            format == null -> R.string.spec_import_title
            format == SpecFormat.POSTMAN -> R.string.spec_import_title_postman
            format == SpecFormat.HAR -> R.string.spec_import_title_har
            else -> R.string.spec_import_title
        }
    )

    val formatLabels = listOf(
        stringResource(R.string.spec_format_openapi),
        stringResource(R.string.spec_format_postman),
        stringResource(R.string.spec_format_har)
    )

    AppDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss
    ) {
        val plan = pending
        if (plan != null) {
            ImportPreviewSheet(
                collectionName = plan.collectionName,
                groups = plan.groups,
                onDismiss = onDismiss,
                onConfirm = {
                    viewModel.importBatch(plan.collectionName, plan.groups)
                    onDismiss()
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (format == null) {
                    AppTabRow(
                        tabs = formatLabels,
                        selectedTabIndex = activeFormat.ordinal,
                        onTabSelected = { activeFormat = SpecFormat.entries[it] },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AppTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        failure = null
                        pending = null
                    },
                    label = stringResource(R.string.spec_import_source_label),
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                AppText(
                    text = stringResource(
                        when (activeFormat) {
                            SpecFormat.POSTMAN -> R.string.spec_import_source_desc_postman
                            SpecFormat.HAR -> R.string.spec_import_source_desc_har
                            SpecFormat.OPENAPI -> R.string.spec_import_source_desc
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
                failure?.let { error ->
                    AppText(
                        text = failureText(error),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = if (busy) {
                            stringResource(R.string.spec_import_parsing)
                        } else {
                            stringResource(R.string.spec_import_parse)
                        },
                        onClick = { parse(activeFormat) },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun failureText(error: Throwable): String = when (error) {
    is SpecImportException -> when (error.error) {
        SpecError.EMPTY -> stringResource(R.string.spec_import_error_empty)
        SpecError.UNPARSEABLE -> stringResource(R.string.spec_import_error_unparseable)
        SpecError.NOT_OPENAPI -> stringResource(R.string.spec_import_error_not_openapi)
        SpecError.NOT_RECOGNIZED -> stringResource(R.string.spec_import_error_not_recognized)
        SpecError.NO_OPERATIONS -> stringResource(R.string.spec_import_error_no_operations)
        SpecError.FETCH_FAILED -> stringResource(R.string.spec_import_error_fetch)
    }

    else -> error.message ?: stringResource(R.string.spec_import_error_unknown)
}
