package com.evan.pulse.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.statusColor
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onOpenDetail: (Long) -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    val all by viewModel.history.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val confirmClear = viewModel.confirmClear

    var showFilter by remember { mutableStateOf(false) }

    // 只在历史真正涉及多个项目时才需要筛选器
    val projectsInHistory = remember(all, projects) {
        projects
            .filter { project -> all.any { it.projectId == project.id } }
            .sortedBy { it.name }
    }
    val nameById = remember(projects) { projects.associate { it.id to it.name } }
    val entries = remember(all, viewModel.keyword, viewModel.projectFilter) {
        viewModel.entries(viewModel.projectFilter, viewModel.keyword)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current, bottom = 12.dp),
        state = subPageLazyListState()
    ) {
        item(key = "search") {
            TextField(
                value = viewModel.keyword,
                onValueChange = { viewModel.keyword = it },
                label = stringResource(R.string.history_search_label),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (projectsInHistory.size >= 2) {
            item(key = "filter") {
                ProjectFilterChip(
                    label = viewModel.projectFilter?.let { nameById[it].orEmpty() }
                        ?: stringResource(R.string.history_filter_all),
                    onClick = { showFilter = true }
                )
            }
        }

        item(key = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(entries, key = { entry -> entry.id }) { entry ->
            SwipeToDeleteRow(
                modifier = Modifier.padding(vertical = 4.dp),
                deleteLabel = stringResource(R.string.history_delete),
                onDelete = { viewModel.delete(entry.id) }
            ) {
                HistoryItem(
                    entry = entry,
                    projectName = nameById[entry.projectId].orEmpty(),
                    onOpen = { onOpenDetail(entry.id) }
                )
            }
        }
        item { BottomBarClearance() }
    }

    if (showFilter) {
        ProjectFilterDialog(
            current = viewModel.projectFilter,
            options = projectsInHistory,
            onPick = { projectId ->
                viewModel.projectFilter = projectId
                showFilter = false
            },
            onDismiss = { showFilter = false }
        )
    }

    if (confirmClear) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.history_clear),
            onDismissRequest = { viewModel.confirmClear = false }
        ) {
            Column {
                Text(stringResource(R.string.history_clear_confirm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.confirmClear = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_clear),
                        onClick = { viewModel.clear() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/**
 * 侧滑删除的行容器：往左拖过触发距离松手就删，否则回弹。拖出来看到的删除图标只是提示，不需要点。
 *
 * Foundation 1.12 移除了 SwipeToDismissBox，这里用手势检测自己实现。手势检测挂在外层、
 * 点击挂在内层内容上，所以轻点仍是打开详情，横向拖动才触发删除。
 */
@Composable
private fun SwipeToDeleteRow(
    modifier: Modifier = Modifier,
    deleteLabel: String,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val animatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealWidth = remember(density) { with(density) { 56.dp.toPx() } }
    val triggerDistance = remember(density) { with(density) { 32.dp.toPx() } }
    val spec = remember { tween<Float>(durationMillis = 200) }

    Box(modifier = modifier.fillMaxWidth()) {
        Icon(
            imageVector = MiuixIcons.Light.Delete,
            contentDescription = deleteLabel,
            tint = MiuixTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatable.value }
                .pointerInput(Unit) {
                    var offset = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset = animatable.value },
                        onDragEnd = {
                            val shouldDelete = offset <= -triggerDistance
                            scope.launch {
                                animatable.animateTo(if (shouldDelete) -revealWidth else 0f, spec)
                                if (shouldDelete) onDelete()
                            }
                        },
                        onDragCancel = { scope.launch { animatable.animateTo(0f, spec) } }
                    ) { _, dragAmount ->
                        offset = (offset + dragAmount).coerceIn(-revealWidth, 0f)
                        scope.launch { animatable.snapTo(offset) }
                    }
                }
        ) {
            content()
        }
    }
}

/** 列表项可点击进详情；行内显示所属项目名，跨项目浏览时能一眼分辨来源 */
@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    projectName: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen,
        showIndication = true
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(entry.method)
                Text(
                    text = entry.statusCode.toString(),
                    color = statusColor(entry.statusCode),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp
                )
                Text(
                    text = stringResource(R.string.duration_value, entry.durationMs),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp
                )
                if (projectName.isNotBlank()) {
                    Text(
                        text = projectName,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = timeFormat.format(Date(entry.createdAt)),
                    fontSize = 12.sp
                )
            }
            Text(
                text = entry.url,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp
            )
        }
    }
}

/** 项目筛选入口：显示当前筛选值，点击展开选项 */
@Composable
private fun ProjectFilterChip(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        onClick = onClick,
        showIndication = true
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_filter_label),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = MiuixIcons.Light.ExpandMore,
                contentDescription = stringResource(R.string.history_filter_label)
            )
        }
    }
}

/** 项目筛选弹窗：第一项是"全部项目"，也就是不限项目 */
@Composable
private fun ProjectFilterDialog(
    current: Long?,
    options: List<ProjectInfo>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.history_filter_label),
        onDismissRequest = onDismiss
    ) {
        Column {
            FilterOptionRow(
                label = stringResource(R.string.history_filter_all),
                checked = current == null,
                onClick = { onPick(null) }
            )
            options.forEach { project ->
                FilterOptionRow(
                    label = project.name,
                    checked = current == project.id,
                    onClick = { onPick(project.id) }
                )
            }
        }
    }
}

@Composable
private fun FilterOptionRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().roundedPressable(onClick = onClick).padding(
            horizontal = 12.dp,
            vertical = 10.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.RadioButtonChecked
            else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
