package com.evan.pulse.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import kotlinx.coroutines.launch

/** 集合运行结果页：按集合顺序跑 HTTP 请求，展示每步状态、控制台与断言 */
class CollectionRunnerViewModel : ViewModel() {

    private val store = ServiceLocator.collectionStore
    private val projectStore = ServiceLocator.projectStore

    var running by mutableStateOf(false)
    var result by mutableStateOf<CollectionRunner.RunResult?>(null)
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
        requestCount = loadRequests(collectionId).size
    }

    fun run(collectionId: Long?) {
        if (running) return
        viewModelScope.launch {
            running = true
            result = null
            try {
                result = CollectionRunner.run(loadRequests(collectionId), projectStore.currentProjectId.value)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                val steps = result?.steps.orEmpty()
                if (steps.isEmpty()) {
                    AppText(
                        text = stringResource(R.string.runner_empty, viewModel.requestCount),
                        color = AppTheme.colors.onSurfaceSummary,
                        fontSize = 13.sp
                    )
                } else {
                    steps.forEachIndexed { index, step ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    maxLines = 1
                                )
                                AppText(
                                    text = when {
                                        step.error != null && step.statusCode == null -> "✕"
                                        step.success -> "✓"
                                        else -> "✕"
                                    },
                                    fontSize = 15.sp,
                                    color = if (step.success) AppTheme.colors.primary else AppTheme.colors.error
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
                            if (step.console.isNotEmpty()) {
                                AppText(
                                    text = step.console.joinToString("\n"),
                                    modifier = Modifier.padding(top = 2.dp, start = 24.dp),
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.onSurfaceSummary,
                                    maxLines = 6
                                )
                            }
                            if (step.tests.isNotEmpty()) {
                                step.tests.forEach { t ->
                                    AppText(
                                        text = "${if (t.passed) "✓" else "✕"} ${t.name}",
                                        modifier = Modifier.padding(top = 1.dp, start = 24.dp),
                                        fontSize = 12.sp,
                                        color = if (t.passed) AppTheme.colors.primary else AppTheme.colors.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (running) {
            AppText(
                text = stringResource(R.string.runner_running),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        } else if (result != null) {
            AppText(
                text = stringResource(R.string.runner_summary, result!!.passed, result!!.failed),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        BottomBarClearance()
    }
}