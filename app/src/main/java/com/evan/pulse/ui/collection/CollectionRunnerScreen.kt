package com.evan.pulse.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.export.ResponseFileStore
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.runner.CollectionRunner
import com.evan.pulse.domain.runner.DatasetError
import com.evan.pulse.domain.runner.DatasetFormatException
import com.evan.pulse.domain.runner.RunDataset
import com.evan.pulse.domain.runner.RunReportExporter
import com.evan.pulse.domain.runner.RunReportFormat
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.evan.pulse.ui.design.AppButton
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.squircle.squircleClip
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle
import kotlinx.coroutines.launch

/** 集合运行结果页：按集合顺序跑 HTTP 请求，展示每步状态、控制台与断言 */
class CollectionRunnerViewModel : ViewModel() {

    private val store = ServiceLocator.collectionStore
    private val projectStore = ServiceLocator.projectStore

    var running by mutableStateOf(false)
    var result by mutableStateOf<CollectionRunner.RunResult?>(null)

    /** 单步失败后的额外重试次数（0 表示不重试） */
    var retries by mutableStateOf("0")
    /** 相邻两步之间的等待时长（毫秒） */
    var stepDelayMs by mutableStateOf("0")
    /** 某步重试耗尽仍失败时是否终止整条链 */
    var stopOnFailure by mutableStateOf(false)

    /** 已完成的步骤：运行过程中逐步追加，界面因此能边跑边显示每一项结果 */
    var steps by mutableStateOf<List<CollectionRunner.Step>>(emptyList())
    var requestCount by mutableStateOf(0)

    /** 当前运行的集合名，用于报告导出；为空表示整项目运行 */
    var collectionName by mutableStateOf("")

    /** 已加载的数据集；非空时按行迭代整条链 */
    var dataset by mutableStateOf<RunDataset?>(null)
    var datasetFileName by mutableStateOf<String?>(null)
    var datasetError by mutableStateOf<DatasetError?>(null)

    /** 选择数据集文件：读取文本后按 CSV / JSON 自动解析 */
    fun loadDataset(uri: android.net.Uri) {
        val resolver = ServiceLocator.appContext.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: uri.toString()
        val text = runCatching {
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            datasetError = DatasetError.EMPTY
            dataset = null
            datasetFileName = name
            return
        }
        try {
            dataset = RunDataset.parse(text)
            datasetError = null
            datasetFileName = name
        } catch (e: DatasetFormatException) {
            dataset = null
            datasetError = e.reason
            datasetFileName = name
        }
    }

    fun clearDataset() {
        dataset = null
        datasetFileName = null
        datasetError = null
    }

    private fun loadRequests(collectionId: Long?): List<SavedRequest> {
        val pid = projectStore.currentProjectId.value
        val collections = store.collections.value.filter { it.projectId == pid }
        val doomed = if (collectionId == null) {
            collections.map { it.id }.toSet()
        } else {
            (CollectionTree.descendantsOf(collections, collectionId) + collectionId).toSet()
        }
        val requests = store.requests.value.filter { it.collectionId in doomed }
        return requests.sortedBy { it.createdAt }
    }

    fun prepare(collectionId: Long?) {
        result = null
        steps = emptyList()
        requestCount = loadRequests(collectionId).size
        collectionName = if (collectionId == null) {
            ""
        } else {
            store.collections.value.firstOrNull { it.id == collectionId }?.name.orEmpty()
        }
    }

    fun run(collectionId: Long?) {
        if (running) return
        viewModelScope.launch {
            running = true
            result = null
            steps = emptyList()
            try {
                result = CollectionRunner.run(
                    requests = loadRequests(collectionId),
                    projectId = projectStore.currentProjectId.value,
                    options = CollectionRunner.RunnerOptions(
                        retries = retries.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        stopOnFailure = stopOnFailure,
                        stepDelayMs = stepDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    ),
                    dataset = dataset,
                    onStep = { step -> steps = steps + step }
                )
            } finally {
                running = false
            }
        }
    }
}

@Composable
fun CollectionRunnerScreen(
    collectionId: Long?,
    modifier: Modifier = Modifier,
    viewModel: CollectionRunnerViewModel = viewModel(),
) {
    val result = viewModel.result
    val running = viewModel.running
    val steps = viewModel.steps

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        if (running) {
            AppText(
                text = stringResource(
                    R.string.runner_progress,
                    steps.size.coerceAtMost(viewModel.requestCount),
                    viewModel.requestCount
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        RunnerOptionsCard(viewModel = viewModel, enabled = !running)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            if (steps.isEmpty()) {
                AppText(
                    text = if (running) {
                        stringResource(R.string.runner_waiting)
                    } else {
                        stringResource(R.string.runner_empty, viewModel.requestCount)
                    },
                    modifier = Modifier.padding(12.dp),
                    color = AppTheme.colors.onSurfaceSummary,
                    fontSize = 13.sp
                )
            } else {
                // 整段按卡片形状裁剪：首项的上圆角、末项的下圆角都与卡片边缘重合，
                // 点击水波纹因此能贴合卡片，而不是被 12dp 的内边距挡在里面
                Column(modifier = Modifier.runnerCardClip()) {
                    steps.forEachIndexed { index, step ->
                        RunnerStepRow(step = step, index = index)
                    }
                }
            }
        }
        if (!running && result != null) {
            AppText(
                text = stringResource(R.string.runner_summary, result.passed, result.failed),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            RunnerReportExport(
                collectionName = viewModel.collectionName.ifBlank {
                    stringResource(R.string.runner_report_default_collection)
                },
                result = result
            )
        }
        BottomBarClearance()
    }
}

/** 运行报告导出：把整条链的结果保存为 JSON 或 HTML 文件 */
@Composable
private fun RunnerReportExport(collectionName: String, result: CollectionRunner.RunResult) {
    val context = LocalContext.current
    val store = remember(context) { ResponseFileStore(context) }
    val savedText = stringResource(R.string.runner_report_saved)
    val failedText = stringResource(R.string.runner_report_failed)

    fun write(uri: android.net.Uri, format: RunReportFormat) {
        val ok = runCatching {
            val text = RunReportExporter.render(format, collectionName, result, System.currentTimeMillis())
            store.writeTo(uri, text.toByteArray(Charsets.UTF_8))
        }.isSuccess
        AppToast.show(if (ok) savedText else failedText)
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RunReportFormat.JSON.mime)
    ) { uri -> uri?.let { write(it, RunReportFormat.JSON) } }
    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RunReportFormat.HTML.mime)
    ) { uri -> uri?.let { write(it, RunReportFormat.HTML) } }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppButton(
            onClick = { jsonLauncher.launch(RunReportExporter.suggestedName(RunReportFormat.JSON)) },
            modifier = Modifier.weight(1f)
        ) {
            AppText(stringResource(R.string.runner_report_export_json))
        }
        AppButton(
            onClick = { htmlLauncher.launch(RunReportExporter.suggestedName(RunReportFormat.HTML)) },
            modifier = Modifier.weight(1f)
        ) {
            AppText(stringResource(R.string.runner_report_export_html))
        }
    }
}

/** 运行选项：单步重试次数、步间延时与失败即停 */
@Composable
private fun RunnerOptionsCard(viewModel: CollectionRunnerViewModel, enabled: Boolean) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                AppTextField(
                    value = viewModel.retries,
                    onValueChange = { viewModel.retries = it.filter(Char::isDigit) },
                    label = stringResource(R.string.runner_retries_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                AppTextField(
                    value = viewModel.stepDelayMs,
                    onValueChange = { viewModel.stepDelayMs = it.filter(Char::isDigit) },
                    label = stringResource(R.string.runner_step_delay_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    singleLine = true
                )
            }
            AppSwitchRow(
                title = stringResource(R.string.runner_stop_on_failure_label),
                summary = stringResource(R.string.runner_stop_on_failure_summary),
                checked = viewModel.stopOnFailure,
                onCheckedChange = { viewModel.stopOnFailure = it },
                enabled = enabled
            )
            DatasetSection(viewModel = viewModel, enabled = enabled)
        }
    }
}

/** 数据集选择：CSV / JSON 文件，按行迭代整条链 */
@Composable
private fun DatasetSection(viewModel: CollectionRunnerViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::loadDataset) }
    val dataset = viewModel.dataset
    val summary = when {
        viewModel.datasetError != null -> datasetErrorText(viewModel.datasetError!!)
        dataset != null -> stringResource(
            R.string.runner_dataset_summary,
            viewModel.datasetFileName ?: "",
            dataset.rows.size,
            dataset.columns.size
        )
        else -> stringResource(R.string.runner_dataset_none)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(R.string.runner_dataset_label),
                fontSize = 14.sp
            )
            AppText(
                text = summary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (viewModel.datasetError != null) AppTheme.colors.error else AppTheme.colors.onSurfaceSummary
            )
        }
        AppText(
            text = stringResource(R.string.runner_dataset_pick),
            modifier = Modifier
                .roundedPressable { if (enabled) pickLauncher.launch(arrayOf("*/*")) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = 13.sp,
            color = if (enabled) AppTheme.colors.primary else AppTheme.colors.onSurfaceSummary
        )
        if (dataset != null || viewModel.datasetError != null) {
            AppText(
                text = stringResource(R.string.runner_dataset_clear),
                modifier = Modifier
                    .roundedPressable { if (enabled) viewModel.clearDataset() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
    }
}

@Composable
private fun datasetErrorText(error: DatasetError): String = stringResource(
    when (error) {
        DatasetError.EMPTY -> R.string.runner_dataset_error_empty
        DatasetError.BAD_JSON -> R.string.runner_dataset_error_bad_json
        DatasetError.NOT_ARRAY_OF_OBJECTS -> R.string.runner_dataset_error_not_array
        DatasetError.NESTED_VALUE -> R.string.runner_dataset_error_nested
        DatasetError.CSV_EMPTY_HEADER -> R.string.runner_dataset_error_header
        DatasetError.CSV_ROW_MISMATCH -> R.string.runner_dataset_error_mismatch
    }
)

/** 单步结果：点击展开控制台、断言与响应体预览 */
@Composable
private fun RunnerStepRow(step: CollectionRunner.Step, index: Int) {
    var expanded by remember(step.requestId) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = (index + 1).toString(),
                modifier = Modifier.size(24.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            if (step.type == RequestType.HTTP) {
                MethodBadge(method = step.method)
            }
            AppText(
                text = step.name,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (step.attempts > 1) {
                AppText(
                    text = stringResource(R.string.runner_attempts_badge, step.attempts),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 11.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
            AppText(
                text = if (step.success) "✓" else "✕",
                fontSize = 15.sp,
                color = if (step.success) AppTheme.colors.primary else AppTheme.colors.error
            )
            AppIcon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.kv_table_collapse else R.string.kv_table_expand
                ),
                tint = AppTheme.colors.onSurfaceSummary,
                modifier = Modifier.padding(start = 4.dp).size(18.dp)
            )
        }
        AppText(
            text = when {
                step.error != null -> step.error
                step.statusCode != null -> "${step.method} ${step.url} → ${step.statusCode} ${step.durationMs}ms"
                else -> step.url
            },
            modifier = Modifier.padding(top = 2.dp, start = 24.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary,
            maxLines = 3
        )
        if (expanded) {
            if (step.tests.isNotEmpty()) {
                step.tests.forEach { t ->
                    AppText(
                        text = "${if (t.passed) "✓" else "✕"} ${t.name}",
                        modifier = Modifier.padding(top = 2.dp, start = 24.dp),
                        fontSize = 12.sp,
                        color = if (t.passed) AppTheme.colors.primary else AppTheme.colors.error
                    )
                }
            }
            if (step.console.isNotEmpty()) {
                AppText(
                    text = stringResource(R.string.runner_console),
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
                MonospaceBlock(
                    text = step.console.joinToString("\n"),
                    modifier = Modifier.padding(start = 24.dp),
                    selectable = true
                )
            }
            if (step.bodyPreview.isNotBlank()) {
                AppText(
                    text = stringResource(R.string.runner_body_preview),
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
                MonospaceBlock(
                    text = step.bodyPreview,
                    modifier = Modifier.padding(start = 24.dp),
                    selectable = true
                )
            }
        }
    }
}

/** Miuix 卡片的圆角半径，与 CardDefaults.cornerRadius 保持一致 */
private val RUNNER_CARD_CORNER = 16.dp

/**
 * 与 [AppCard] 完全一致的圆角裁剪。
 *
 * Miuix 卡片用 16dp 的连续圆角，Material 卡片用 M3 的 extraLarge 圆角，
 * 两种皮肤各取各的半径，裁剪结果才和卡片外沿严丝合缝。
 */
@Composable
private fun Modifier.runnerCardClip(): Modifier =
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        squircleClip(RUNNER_CARD_CORNER)
    } else {
        clip(MaterialTheme.shapes.extraLarge)
    }
