package com.evan.pulse.ui.http

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.evan.pulse.domain.assertion.ResponseAssertion
import com.evan.pulse.domain.net.HttpTimeline
import com.evan.pulse.domain.net.NetworkPreset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.domain.http.HttpExportFormat
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.pathOnly
import com.evan.pulse.ui.collection.SaveRequestDialog
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.assertionTypeLabelRes
import com.evan.pulse.ui.common.AssertionSection
import com.evan.pulse.ui.common.CollapsibleKeyValueTable
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.HtmlPreviewStore
import com.evan.pulse.ui.common.TlsSection
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.MethodPicker
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.SectionTitleRow
import com.evan.pulse.ui.common.StatusBadge
import com.evan.pulse.ui.common.ValueHints
import com.evan.pulse.ui.common.ResponseBodyView
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.squircle.squircleSurface
import com.evan.pulse.ui.design.AppButton
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage

private val bodyTypes = listOf(BodyType.NONE, BodyType.JSON, BodyType.RAW, BodyType.FORM_URLENCODED, BodyType.FORM_DATA, BodyType.GRAPHQL)
private val bodyTypeLabels = listOf(
    R.string.body_none,
    R.string.body_json,
    R.string.body_raw,
    R.string.body_form_urlencoded,
    R.string.body_form_data,
    R.string.body_graphql
)
private val throttleLabels = listOf(
    R.string.net_throttle_off,
    R.string.net_throttle_5g,
    R.string.net_throttle_4g,
    R.string.net_throttle_3g,
    R.string.net_throttle_2g,
    R.string.net_throttle_force
)

private val authTypeLabels = listOf(
    R.string.auth_none,
    R.string.auth_bearer,
    R.string.auth_basic,
    R.string.auth_apikey
)
// 请求体紧跟在参数后面：改完参数通常就要改请求体，请求头用得少一些
private val httpTabLabels = listOf(
    R.string.tab_params,
    R.string.tab_body,
    R.string.tab_headers,
    R.string.tab_auth,
    R.string.tab_network
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
    onOpenCookies: () -> Unit = {},
    onOpenHtmlPreview: (() -> Unit)? = null,
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
                AppIconButton(onClick = onOpenHistory, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.history_title)
                    )
                }
                AppIconButton(onClick = onOpenCookies, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = Icons.Filled.Cookie,
                        contentDescription = stringResource(R.string.cookie_title)
                    )
                }
                AppIconButton(onClick = { showExport = true }, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = stringResource(R.string.cd_export_request)
                    )
                }
                AppIconButton(
                    onClick = { if (!viewModel.saveOverLoaded()) showSave = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.cd_save_request)
                    )
                }
            }
        )
        RequestEditorCard(viewModel)
        SectionTitleRow(label = stringResource(R.string.tab_script))
        ScriptEditorCard(viewModel, onOpenScriptEditor)
        SectionTitleRow(label = stringResource(R.string.http_assertions_label))
        AssertionCard(viewModel)
        SectionTitleRow(
            label = stringResource(R.string.http_response_label),
            actions = {
                CopyIconButton(
                    text = ((viewModel.outcome as? HttpCallOutcome.Success)?.body),
                    label = stringResource(R.string.cd_copy_response)
                )
            }
        )
        ResponseCard(viewModel, onOpenHtmlPreview)
        BottomBarClearance()
    }
    SaveRequestDialog(
        show = showSave,
        type = RequestType.HTTP,
        defaultName = viewModel.url.pathOnly().ifEmpty { viewModel.url },
        snapshotProvider = { viewModel.snapshot() },
        onDismiss = { showSave = false },
        onSaved = viewModel::markSaved
    )
    ExportFormatDialog(show = showExport, viewModel = viewModel, onDismiss = { showExport = false })

    missingVars?.let { missing ->
        AppDialog(
            show = true,
            title = stringResource(R.string.missing_env_title),
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                AppText(stringResource(R.string.missing_env_desc))
                AppText(
                    text = missing.joinToString(", "),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = AppTheme.colors.onBackground
                )
                AppTextButton(
                    text = stringResource(R.string.action_got_it),
                    onClick = { viewModel.dismissMissing() },
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RequestEditorCard(viewModel: HttpViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        MethodPicker(
            method = viewModel.method,
            onMethodChange = { viewModel.method = it },
            title = stringResource(R.string.http_method_label),
            modifier = Modifier.fillMaxWidth()
        )
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                label = stringResource(R.string.http_url_label),
                modifier = Modifier.fillMaxWidth()
            )
            ValueHints(hints = viewModel.placeholderHints(viewModel.url))
            AppButton(
                onClick = { viewModel.send() },
                enabled = !viewModel.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                AppIcon(
                    imageVector = MiuixIcons.Light.Send,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                AppText(if (viewModel.sending) stringResource(R.string.action_sending) else stringResource(R.string.http_send_button), modifier = Modifier.padding(start = 4.dp))
            }

            AppTabRow(
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

    AppDialog(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppTabRow(
                tabs = tabLabels,
                selectedTabIndex = picked,
                onTabSelected = { picked = it },
                modifier = Modifier.fillMaxWidth(),
                scrollable = true
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
            AppTextButton(
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

        1 -> BodyEditor(vm)

        2 -> Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
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
        3 -> AuthEditor(vm)
        4 -> NetworkEditor(vm)
    }
}

/** 网络选项：请求超时与是否套用全局代理，代理详情在设置页配置 */
@Composable
private fun NetworkEditor(viewModel: HttpViewModel) {
    Column(horizontalAlignment = Alignment.Start) {
        // 档位即开关：关闭 / 各弱网档 / 强制超时，与请求体类型下拉一样贴卡边
        AppDropdown(
            items = throttleLabels.localizedLabels(),
            selectedIndex = viewModel.throttlePreset.ordinal,
            title = stringResource(R.string.net_throttle_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { viewModel.throttlePreset = NetworkPreset.entries[it] }
        )
        // 超时与代理为常规网络配置，始终显示；强制超时输入框仅在选中该档时出现
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTextField(
                value = viewModel.timeoutSec,
                onValueChange = { viewModel.timeoutSec = it.filter(Char::isDigit) },
                label = stringResource(R.string.timeout_sec_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (viewModel.throttlePreset == NetworkPreset.FORCE_TIMEOUT) {
                Column {
                    AppTextField(
                        value = viewModel.forceTimeoutSec,
                        onValueChange = { viewModel.forceTimeoutSec = it.filter(Char::isDigit) },
                        label = stringResource(R.string.net_force_timeout_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppText(
                        text = stringResource(R.string.net_force_timeout_hint),
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
        }
        // 开关行铺满卡片宽度，水波纹才能沿卡片圆角收边
        AppSwitchRow(
            title = stringResource(R.string.use_proxy_label),
            summary = stringResource(R.string.use_proxy_summary),
            checked = viewModel.useProxy,
            onCheckedChange = { viewModel.useProxy = it },
            modifier = Modifier.padding(top = 12.dp)
        )
        TlsSection(
            insecure = viewModel.insecureTls,
            onInsecureChange = { viewModel.insecureTls = it },
            customCaPem = viewModel.customCaPem,
            onCustomCaChange = { viewModel.customCaPem = it },
            clientCertPem = viewModel.clientCertPem,
            onClientCertChange = { viewModel.clientCertPem = it },
            clientKeyPem = viewModel.clientKeyPem,
            onClientKeyChange = { viewModel.clientKeyPem = it }
        )
        AppSwitchRow(
            title = stringResource(R.string.follow_redirects_label),
            summary = stringResource(R.string.follow_redirects_summary),
            checked = viewModel.followRedirects,
            onCheckedChange = { viewModel.followRedirects = it }
        )
        if (viewModel.followRedirects) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)
            ) {
                AppTextField(
                    value = viewModel.maxRedirects,
                    onValueChange = { viewModel.maxRedirects = it.filter(Char::isDigit).take(2) },
                    label = stringResource(R.string.max_redirects_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                AppText(
                    text = stringResource(R.string.max_redirects_hint),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
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
    AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTabRow(
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

/**
 * 响应断言编辑区：按顺序校验响应体，全部通过才认为这次请求在业务上成功。
 * 类型与拨测目标一致（包含 / 不包含 / JSONPath / 状态码 / 响应头 / 耗时）。
 */
@Composable
private fun AssertionCard(viewModel: HttpViewModel) {
    AssertionSection(
        assertions = viewModel.assertions,
        onAssertionsChange = { viewModel.assertions = it },
        results = viewModel.assertionResults
    )
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

        AppText(
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
            .squircleSurface(AppTheme.colors.surfaceVariant, 8.dp)
            .clickable(onClick = onClick)
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        AppText(
            text = if (empty) placeholder else script,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (empty) {
                AppTheme.colors.onSurfaceSummary
            } else {
                AppTheme.colors.onSurface
            }
        )
    }
}

@Composable
private fun BodyEditor(viewModel: HttpViewModel) {
    Column(horizontalAlignment = Alignment.Start) {
        AppDropdown(
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
            BodyType.JSON -> {
                CardSectionTitle(text = stringResource(R.string.body_json_label))
                CodeEditorField(
                    text = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    language = CodeLanguage.JSON,
                    dark = AppTheme.colors.background.luminance() < 0.5f,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }

            BodyType.RAW -> {
                AppTextField(
                    value = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    label = stringResource(R.string.body_raw_label),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8
                )
            }

            BodyType.GRAPHQL -> {
                AppTextField(
                    value = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    label = stringResource(R.string.graphql_query_label),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8
                )
                AppTextField(
                    value = viewModel.graphqlVariables,
                    onValueChange = { viewModel.graphqlVariables = it },
                    label = stringResource(R.string.graphql_variables_label),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
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
                        AppTextField(
                            value = field.name,
                            onValueChange = { viewModel.updateFormField(index, field.copy(name = it)) },
                            label = stringResource(R.string.form_field_name),
                            modifier = Modifier.weight(0.35f)
                        )
                        if (field.isFile) {
                            AppText(
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
                            AppTextField(
                                value = field.value,
                                onValueChange = { viewModel.updateFormField(index, field.copy(value = it)) },
                                label = stringResource(R.string.kv_value_label),
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 6.dp)
                            )
                        }
                        AppIcon(
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
        AppIcon(
            imageVector = MiuixIcons.Light.Add,
            contentDescription = stringResource(R.string.cd_add)
        )
        AppText(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun AuthEditor(viewModel: HttpViewModel) {
    Column {
        AppDropdown(
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
                AppTextField(
                    value = viewModel.bearerToken,
                    onValueChange = { viewModel.bearerToken = it },
                    label = stringResource(R.string.bearer_token_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.evan.pulse.domain.model.AuthType.APIKEY -> {
                AppTextField(
                    value = viewModel.apiKeyName,
                    onValueChange = { viewModel.apiKeyName = it },
                    label = stringResource(R.string.apikey_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = viewModel.apiKeyValue,
                    onValueChange = { viewModel.apiKeyValue = it },
                    label = stringResource(R.string.apikey_value_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.evan.pulse.domain.model.AuthType.BASIC -> {
                AppTextField(
                    value = viewModel.basicUsername,
                    onValueChange = { viewModel.basicUsername = it },
                    label = stringResource(R.string.username_label),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
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
private fun ResponseCard(viewModel: HttpViewModel, onOpenHtmlPreview: (() -> Unit)?) {
    val outcome = viewModel.outcome

    AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        when (outcome) {
            null -> AppText(
                text = stringResource(R.string.http_no_response),
                modifier = Modifier.padding(16.dp)
            )

            is HttpCallOutcome.Failure -> Column(modifier = Modifier.padding(16.dp)) {
                AppText(
                    text = stringResource(R.string.http_failure, outcome.errorType.name),
                    color = Color(0xFFD32F2F)
                )
                AppText(
                    text = outcome.message,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp
                )
            }

            is HttpCallOutcome.Success -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(outcome.statusCode)
                        if (outcome.statusText.isNotBlank()) {
                            AppText(
                                text = outcome.statusText,
                                modifier = Modifier.padding(start = 10.dp),
                                fontSize = 13.sp
                            )
                        }
                        AppText(
                            text = stringResource(R.string.duration_size, outcome.durationMs, outcome.responseSizeBytes),
                            modifier = Modifier.padding(start = 10.dp),
                            fontSize = 13.sp
                        )
                    }
                    // 断言结论放在状态码旁边：HTTP 2xx 但业务字段不对时也能一眼看出请求失败
                    val failedAssertions = viewModel.assertionResults.count { !it.passed }
                    if (viewModel.assertionResults.isNotEmpty()) {
                        AppText(
                            text = if (failedAssertions == 0) {
                                stringResource(R.string.http_assertions_all_passed)
                            } else {
                                stringResource(R.string.http_assertions_failed, failedAssertions)
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = if (failedAssertions == 0) Color(0xFF2FA37A) else Color(0xFFD32F2F)
                        )
                    }
                    val tl = outcome.timeline
                    if (tl.dnsMs > 0 || tl.tcpMs > 0 || tl.tlsMs > 0 || tl.ttfbMs > 0) {
                        HttpTimingBreakdown(
                            timeline = tl,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                // 真实请求头默认折叠：多数场景只关心响应，需要排查时再展开逐条核对。
                // 两张表放在卡片内边距之外，标题行水波纹才能横向贴住卡片左右边缘
                CollapsibleKeyValueTable(
                    title = stringResource(R.string.history_request_headers),
                    items = outcome.requestHeaders.map { it.key to it.value },
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalPadding = 16.dp,
                    verticalPadding = 10.dp
                )
                // 响应头与请求头用同一套表格，字段对齐、长值换行；默认展开
                CollapsibleKeyValueTable(
                    title = stringResource(R.string.http_response_headers),
                    items = outcome.headers.map { it.key to it.value },
                    modifier = Modifier.padding(top = 2.dp),
                    initiallyExpanded = true,
                    horizontalPadding = 16.dp,
                    verticalPadding = 10.dp
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    AppText(
                        text = stringResource(R.string.http_response_body),
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                        fontSize = 13.sp
                    )
                    ResponseBodyView(
                        outcome.body,
                        viewModel.url,
                        contentType = responseContentType(outcome.headers),
                        bodyBytes = outcome.bodyBytes,
                        onOpenHtmlPreview = onOpenHtmlPreview?.let { open ->
                            { body, base -> HtmlPreviewStore.set(body, base); open() }
                        }
                    )
                }
            }
        }
    }
}

/** 从响应头里取 Content-Type（不区分大小写） */
private fun responseContentType(headers: List<KeyValue>): String? =
    headers.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value

/** 各阶段配色：与主流开发者工具的水瀑图保持一致的语义区分 */
private val TIMING_DNS_COLOR = Color(0xFF4C8DFF)
private val TIMING_TCP_COLOR = Color(0xFFF0A43C)
private val TIMING_TLS_COLOR = Color(0xFF9C6ADE)
private val TIMING_WAIT_COLOR = Color(0xFF2FA37A)
private val TIMING_DOWNLOAD_COLOR = Color(0xFF6C7BE0)

private data class TimingPhase(val labelRes: Int, val ms: Long, val color: Color)

/**
 * 耗时分解水瀑图：把 DNS / 连接 / TLS / 等待首字节 / 内容下载按耗时占比横向铺开，
 * 下方用图例列出每段毫秒数，便于一眼看出瓶颈落在哪个阶段。
 */
@Composable
private fun HttpTimingBreakdown(timeline: HttpTimeline, modifier: Modifier = Modifier) {
    val dns = timeline.dnsMs
    val tcp = timeline.tcpMs
    val tls = timeline.tlsMs
    val wait = (timeline.ttfbMs - dns - tcp - tls).coerceAtLeast(0L)
    val total = if (timeline.totalMs > 0L) {
        timeline.totalMs
    } else {
        dns + tcp + tls + timeline.ttfbMs
    }
    val download = (total - timeline.ttfbMs).coerceAtLeast(0L)

    val phases = listOf(
        TimingPhase(R.string.http_timing_dns, dns, TIMING_DNS_COLOR),
        TimingPhase(R.string.http_timing_tcp, tcp, TIMING_TCP_COLOR),
        TimingPhase(R.string.http_timing_tls, tls, TIMING_TLS_COLOR),
        TimingPhase(R.string.http_timing_wait, wait, TIMING_WAIT_COLOR),
        TimingPhase(R.string.http_timing_download, download, TIMING_DOWNLOAD_COLOR)
    ).filter { it.ms > 0L }

    if (phases.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = stringResource(R.string.http_timing_title),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppText(
                text = stringResource(R.string.http_timing_total, total),
                fontSize = 11.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            phases.forEach { phase ->
                Box(
                    modifier = Modifier
                        .weight(phase.ms.toFloat().coerceAtLeast(0.4f))
                        .fillMaxHeight()
                        .background(phase.color)
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            phases.chunked(2).forEach { rowPhases ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPhases.forEach { phase ->
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(phase.color)
                            )
                            AppText(
                                text = stringResource(phase.labelRes),
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1,
                                fontSize = 11.sp,
                                color = AppTheme.colors.onSurfaceSummary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            AppText(
                                text = stringResource(R.string.http_timing_value, phase.ms),
                                maxLines = 1,
                                fontSize = 11.sp,
                                color = AppTheme.colors.onSurface
                            )
                        }
                    }
                    if (rowPhases.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
