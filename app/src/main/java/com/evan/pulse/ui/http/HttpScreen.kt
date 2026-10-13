package com.evan.pulse.ui.http

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.domain.http.HttpExportFormat
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BodyViewMode
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.StatusBadge
import com.evan.pulse.ui.common.JsonTreeView
import com.evan.pulse.ui.common.ValueHints
import com.evan.pulse.ui.common.isJson
import com.evan.pulse.ui.common.renderBody
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val httpMethods = HttpMethod.entries.toList()
private val bodyTypes = listOf(BodyType.NONE, BodyType.JSON, BodyType.RAW, BodyType.FORM_URLENCODED, BodyType.FORM_DATA)
private val bodyTypeLabels = listOf(
    R.string.body_none,
    R.string.body_json,
    R.string.body_raw,
    R.string.body_form_urlencoded,
    R.string.body_form_data
)
private val authTypeLabels = listOf(
    R.string.auth_none,
    R.string.auth_bearer,
    R.string.auth_basic,
    R.string.auth_apikey
)
private val httpTabLabels = listOf(
    R.string.tab_params,
    R.string.tab_headers,
    R.string.tab_body,
    R.string.tab_auth
)
private val scriptTabLabels = listOf(
    R.string.script_tab_pre,
    R.string.script_tab_post
)

@Composable
fun HttpScreen(
    modifier: Modifier = Modifier,
    onOpenScriptEditor: (Int) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: HttpViewModel = viewModel(),
) {
    val scroll = subPageScrollState()
    val missingVars = viewModel.missingVars
    var showSave by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll()
            .verticalScroll(scroll)
    ) {
        SectionTitleRow(
            label = stringResource(R.string.http_request_label),
            actions = {
                IconButton(onClick = onOpenHistory, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.history_title)
                    )
                }
                IconButton(onClick = { showExport = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = stringResource(R.string.cd_export_request)
                    )
                }
                IconButton(onClick = { showSave = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.cd_save_request)
                    )
                }
            }
        )
        RequestEditorCard(viewModel)
        SectionTitleRow(label = stringResource(R.string.tab_script))
        ScriptEditorCard(viewModel, onOpenScriptEditor)
        SectionTitleRow(
            label = stringResource(R.string.http_response_label),
            actions = {
                CopyIconButton(
                    text = ((viewModel.outcome as? HttpCallOutcome.Success)?.body),
                    label = stringResource(R.string.cd_copy_response)
                )
            }
        )
        ResponseCard(viewModel)
        BottomBarClearance()
    }
    SaveRequestDialog(
        show = showSave,
        type = RequestType.HTTP,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false }
    )
    ExportFormatDialog(show = showExport, viewModel = viewModel, onDismiss = { showExport = false })

    missingVars?.let { missing ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.missing_env_title),
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                Text(stringResource(R.string.missing_env_desc))
                Text(
                    text = missing.joinToString(", "),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onBackground
                )
                TextButton(
                    text = stringResource(R.string.action_got_it),
                    onClick = { viewModel.dismissMissing() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RequestEditorCard(viewModel: HttpViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        OverlayDropdownPreference(
            items = httpMethods.map { it.name },
            selectedIndex = httpMethods.indexOf(viewModel.method),
            title = stringResource(R.string.http_method_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { viewModel.method = httpMethods[it] }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            TextField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                label = stringResource(R.string.http_url_label),
                modifier = Modifier.fillMaxWidth()
            )
            ValueHints(hints = viewModel.placeholderHints(viewModel.url))
            Button(
                onClick = { viewModel.send() },
                enabled = !viewModel.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Light.Send,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(if (viewModel.sending) stringResource(R.string.action_sending) else stringResource(R.string.http_send_button), modifier = Modifier.padding(start = 4.dp))
            }

            TabRowWithContour(
                tabs = httpTabLabels.localizedLabels(),
                selectedTabIndex = viewModel.activeTab,
                onTabSelected = { viewModel.activeTab = it },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        // Tab 内容区贴卡片边缘：类型下拉行与“请求方法”行对齐，具体内容再自行内缩；
        // 上方内缩列的 12dp 底部内边距恰好等于原来的 Tab 行间距
        Column(modifier = Modifier.fillMaxWidth()) {
            TabContent(vm = viewModel)
        }
    }
}

/**
 * 导出格式选择对话框：顶部一排 Tab 切格式，下面实时预览对应文本，底部一键复制。
 *
 * 每个 Tab 的预览在切到它时才算一次并缓存，来回切换不会重复生成；
 * 复制后的反馈走全局 Toast（[rememberCopyToClipboard] 内部弹出），本弹窗不自己处理。
 */
@Composable
private fun ExportFormatDialog(show: Boolean, viewModel: HttpViewModel, onDismiss: () -> Unit) {
    if (!show) return

    // Tab 上是短标签，五个格式并排才能放下；完整名称已在选项被选中前由预览体现
    val tabLabels = listOf(
        stringResource(R.string.export_tab_curl_bash),
        stringResource(R.string.export_tab_curl_powershell),
        stringResource(R.string.export_tab_http_raw),
        stringResource(R.string.export_tab_python),
        stringResource(R.string.export_tab_js_fetch)
    )

    var picked by remember { mutableStateOf(0) }
    val preview = remember(picked) { viewModel.exportText(HttpExportFormat.entries[picked]) }
    val copy = rememberCopyToClipboard()

    OverlayDialog(
        show = true,
        title = stringResource(R.string.export_format_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRowWithContour(
                tabs = tabLabels,
                selectedTabIndex = picked,
                onTabSelected = { picked = it },
                modifier = Modifier.fillMaxWidth()
            )
            // 预览区限制高度并内部滚动，避免长报文把复制按钮挤出屏幕；
            // 可选中文本，长按拖选即可复制任意片段
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                MonospaceBlock(preview, selectable = true)
            }
            TextButton(
                text = stringResource(R.string.action_copy),
                onClick = { copy(preview) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun TabContent(vm: HttpViewModel) {
    when (vm.activeTab) {
        0 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            KeyValueEditor(
                items = vm.params,
                keyLabel = stringResource(R.string.kv_param_key),
                valueLabel = stringResource(R.string.kv_param_value),
                onChange = vm::updateParams,
                onAdd = vm::addParam,
                onRemove = vm::removeParam
            )
        }

        1 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            KeyValueEditor(
                items = vm.headers,
                keyLabel = stringResource(R.string.kv_header_key),
                valueLabel = stringResource(R.string.kv_header_value),
                onChange = vm::updateHeaders,
                onAdd = vm::addHeader,
                onRemove = vm::removeHeader,
                valueHintProvider = vm::placeholderHints
            )
        }

        2 -> BodyEditor(vm)
        3 -> AuthEditor(vm)
    }
}

/**
 * 独立的脚本卡片：前置与后置各占一个 Tab，两段脚本共享同一份变量作用域。
 *
 * 前置脚本跑在占位符解析之前，所以脚本里 pm.variables.set 设过的值当次请求就能被 {{var}} 引用；
 * 后置脚本拿到响应后再跑，主要用来写断言。输出区在两个 Tab 下共用，汇总控制台日志、断言结果与脚本报错。
 */
@Composable
private fun ScriptEditorCard(viewModel: HttpViewModel, onOpenScriptEditor: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            TabRowWithContour(
                tabs = scriptTabLabels.localizedLabels(),
                selectedTabIndex = viewModel.scriptTab,
                onTabSelected = { viewModel.scriptTab = it },
                modifier = Modifier.fillMaxWidth()
            )
            Column(modifier = Modifier.padding(top = 12.dp)) {
                ScriptEditor(viewModel, onOpenScriptEditor)
            }
        }
    }
}

@Composable
private fun ScriptEditor(viewModel: HttpViewModel, onOpenScriptEditor: (Int) -> Unit) {
    val isPre = viewModel.scriptTab == 0

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        ScriptPreview(
            script = if (isPre) viewModel.preScript else viewModel.postScript,
            placeholder = stringResource(if (isPre) R.string.script_pre_label else R.string.script_post_label),
            onClick = { onOpenScriptEditor(viewModel.scriptTab) }
        )

        val console = viewModel.scriptConsole
        val tests = viewModel.scriptTests
        val error = viewModel.scriptError
        if (console.isEmpty() && tests.isEmpty() && error == null) return

        Text(
            text = stringResource(R.string.script_console),
            modifier = Modifier.padding(top = 4.dp)
        )
        if (console.isNotEmpty()) MonospaceBlock(console.joinToString("\n"))
        tests.forEach { test ->
            val detail = test.detail
            MonospaceBlock(
                "${if (test.passed) "PASS" else "FAIL"} ${test.name}${if (detail.isNotBlank()) " - $detail" else ""}"
            )
        }
        error?.let { MonospaceBlock(it) }
    }
}

/** 只读脚本预览框：点击进入二级代码编辑器，长脚本可内部滚动 */
@Composable
private fun ScriptPreview(script: String, placeholder: String, onClick: () -> Unit) {
    val empty = script.isEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 160.dp)
            .squircleSurface(MiuixTheme.colorScheme.surfaceVariant, 8.dp)
            .clickable(onClick = onClick)
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        Text(
            text = if (empty) placeholder else script,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (empty) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun BodyEditor(viewModel: HttpViewModel) {
    Column(horizontalAlignment = Alignment.Start) {
        OverlayDropdownPreference(
            items = bodyTypeLabels.localizedLabels(),
            selectedIndex = bodyTypes.indexOf(viewModel.bodyType),
            title = stringResource(R.string.body_type_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { viewModel.bodyType = bodyTypes[it] }
        )
        // 内容列仅在类型有内容时渲染：NONE 时空列的内边距会在卡片底部留白
        if (viewModel.bodyType != BodyType.NONE) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            when (viewModel.bodyType) {
            BodyType.NONE -> {}
            BodyType.JSON, BodyType.RAW -> {
                TextField(
                    value = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    label = if (viewModel.bodyType == BodyType.JSON) stringResource(R.string.body_json_label) else stringResource(R.string.body_raw_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BodyType.FORM_URLENCODED -> {
                val items = viewModel.formFields.map { KeyValue(it.name, it.value) }
                KeyValueEditor(
                    items = items,
                    keyLabel = stringResource(R.string.form_field_name),
                    valueLabel = stringResource(R.string.kv_value_label),
                    onChange = { i, kv -> viewModel.updateFormField(i, FormField(kv.key, kv.value)) },
                    onAdd = viewModel::addFormField,
                    onRemove = viewModel::removeFormField
                )
            }

            BodyType.FORM_DATA -> {
                val pendingFileIndex = remember { mutableStateOf<Int?>(null) }
                val pickFileLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        val idx = pendingFileIndex.value ?: (viewModel.formFields.size - 1).coerceAtLeast(0)
                        viewModel.setFormFieldFile(idx, uri)
                    }
                    pendingFileIndex.value = null
                }
                viewModel.formFields.forEachIndexed { index, field ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = field.name,
                            onValueChange = { viewModel.updateFormField(index, field.copy(name = it)) },
                            label = stringResource(R.string.form_field_name),
                            modifier = Modifier.weight(0.35f)
                        )
                        if (field.isFile) {
                            Text(
                                text = field.fileName ?: stringResource(R.string.form_file_selected),
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 6.dp)
                                    .clickable {
                                        pendingFileIndex.value = index
                                        pickFileLauncher.launch(arrayOf("*/*"))
                                    },
                                maxLines = 1
                            )
                        } else {
                            TextField(
                                value = field.value,
                                onValueChange = { viewModel.updateFormField(index, field.copy(value = it)) },
                                label = stringResource(R.string.kv_value_label),
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 6.dp)
                            )
                        }
                        Icon(
                            imageVector = MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.cd_delete),
                            modifier = Modifier
                                .clickable { viewModel.removeFormField(index) }
                                .padding(6.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AddFieldButton(stringResource(R.string.form_add_text_field)) {
                        viewModel.addFormField()
                    }
                    AddFieldButton(stringResource(R.string.form_add_file_field)) {
                        viewModel.addFileField()
                        pendingFileIndex.value = viewModel.formFields.size - 1
                        pickFileLauncher.launch(arrayOf("*/*"))
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun AddFieldButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .roundedPressable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MiuixIcons.Light.Add,
            contentDescription = stringResource(R.string.cd_add)
        )
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun AuthEditor(viewModel: HttpViewModel) {
    Column {
        OverlayDropdownPreference(
            items = authTypeLabels.localizedLabels(),
            selectedIndex = viewModel.authType.ordinal,
            title = stringResource(R.string.auth_type_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { viewModel.authType = AuthType.entries[it] }
        )
        // 内容列仅在类型有内容时渲染：NONE 时空列的内边距会在卡片底部留白
        if (viewModel.authType != com.evan.pulse.domain.model.AuthType.NONE) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            when (viewModel.authType) {
            com.evan.pulse.domain.model.AuthType.NONE -> {}
            com.evan.pulse.domain.model.AuthType.BEARER -> {
                TextField(
                    value = viewModel.bearerToken,
                    onValueChange = { viewModel.bearerToken = it },
                    label = stringResource(R.string.bearer_token_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.evan.pulse.domain.model.AuthType.APIKEY -> {
                TextField(
                    value = viewModel.apiKeyName,
                    onValueChange = { viewModel.apiKeyName = it },
                    label = stringResource(R.string.apikey_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = viewModel.apiKeyValue,
                    onValueChange = { viewModel.apiKeyValue = it },
                    label = stringResource(R.string.apikey_value_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.evan.pulse.domain.model.AuthType.BASIC -> {
                TextField(
                    value = viewModel.basicUsername,
                    onValueChange = { viewModel.basicUsername = it },
                    label = stringResource(R.string.username_label),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = viewModel.basicPassword,
                    onValueChange = { viewModel.basicPassword = it },
                    label = stringResource(R.string.password_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
        }
    }
}

@Composable
private fun ResponseCard(viewModel: HttpViewModel) {
    val outcome = viewModel.outcome
    var mode by remember { mutableStateOf(BodyViewMode.RAW) }
    val modeLabels = listOf(
        stringResource(R.string.resp_view_raw),
        stringResource(R.string.resp_view_json),
        stringResource(R.string.resp_view_xml),
        stringResource(R.string.resp_view_hex),
        stringResource(R.string.resp_view_b64)
    )

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        when (outcome) {
            null -> Text(
                text = stringResource(R.string.http_no_response),
                modifier = Modifier.padding(16.dp)
            )

            is HttpCallOutcome.Failure -> Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.http_failure, outcome.errorType.name),
                    color = Color(0xFFD32F2F)
                )
                Text(
                    text = outcome.message,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp
                )
            }

            is HttpCallOutcome.Success -> Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(outcome.statusCode)
                    if (outcome.statusText.isNotBlank()) {
                        Text(
                            text = outcome.statusText,
                            modifier = Modifier.padding(start = 10.dp),
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.duration_size, outcome.durationMs, outcome.responseSizeBytes),
                        modifier = Modifier.padding(start = 10.dp),
                        fontSize = 13.sp
                    )
                }
                val headersText = outcome.headers.joinToString("\n") { "${it.key}: ${it.value}" }
                if (headersText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.http_response_headers),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        fontSize = 13.sp
                    )
                    MonospaceBlock(headersText, selectable = true)
                }
                Text(
                    text = stringResource(R.string.http_response_body),
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                    fontSize = 13.sp
                )
                TabRowWithContour(
                    tabs = modeLabels,
                    selectedTabIndex = mode.ordinal,
                    onTabSelected = { mode = BodyViewMode.entries[it] },
                    minWidth = 44.dp,
                    maxWidth = 120.dp,
                    height = 40.dp,
                    itemSpacing = 4.dp,
                    cornerRadius = 10.dp
                )
                val rendered = remember(outcome.body, mode) { renderBody(outcome.body, mode) }
                if (mode == BodyViewMode.JSON && isJson(outcome.body)) {
                    JsonTreeView(outcome.body)
                } else {
                    MonospaceBlock(rendered.text, selectable = true)
                }
                if (rendered.truncated) {
                    Text(
                        text = stringResource(
                            R.string.resp_truncated,
                            CacheCleaner.formatBytes(rendered.totalBytes.toLong())
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
