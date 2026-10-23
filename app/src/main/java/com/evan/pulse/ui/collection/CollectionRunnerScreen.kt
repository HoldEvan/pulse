package com.evan.pulse.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.runner.CollectionRunner
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
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

    /** 已完成的步骤：运行过程中逐步追加，界面因此能边跑边显示每一项结果 */
    var steps by mutableStateOf<List<CollectionRunner.Step>>(emptyList())
    var requestCount by mutableStateOf(0)

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
        }
        BottomBarClearance()
    }
}

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
