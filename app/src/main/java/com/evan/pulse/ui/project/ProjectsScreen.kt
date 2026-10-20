package com.evan.pulse.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppInfoRow
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import com.evan.pulse.ui.design.AppIconDropdownMenu

/** 资源页-项目 tab：项目列表管理与切换，支持新建、重命名、删除（带确认） */
@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProjectViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val currentId by viewModel.currentProjectId.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val environments by viewModel.environments.collectAsState()
    val showCreate by viewModel.showCreateDialog

    var editing by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }
    var nameInput by remember { mutableStateOf("") }

    // 集合 id → 所属项目，用于把保存的请求归到项目做统计
    val projectOfCollection = remember(collections) {
        collections.associate { it.id to it.projectId }
    }

    // 顶栏触发新建时清空输入框
    LaunchedEffect(showCreate) {
        if (showCreate) nameInput = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        if (projects.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                AppText(
                    text = stringResource(R.string.projects_empty),
                    fontSize = 13.sp,
                    color = AppTheme.colors.outline
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .subPageOverScroll()
                    .verticalScroll(subPageScrollState())
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier) {
                        projects.forEachIndexed { index, project ->
                            val projectRequests =
                                requests.filter { projectOfCollection[it.collectionId] == project.id }
                            val summary = stringResource(
                                R.string.project_stats,
                                projectRequests.count { it.type == RequestType.HTTP },
                                projectRequests.count { it.type == RequestType.SSE },
                                projectRequests.count { it.type == RequestType.WS },
                                projectRequests.count { it.type == RequestType.MQTT },
                                environments.count { it.projectId == project.id }
                            )
                            ProjectRow(
                                project = project,
                                summary = summary,
                                isCurrent = project.id == currentId,
                                isFirst = index == 0,
                                isLast = index == projects.lastIndex,
                                canDelete = projects.size > 1,
                                onRename = {
                                    editing = project
                                    nameInput = project.name
                                },
                                onDelete = { deleting = project }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showCreate || editing != null) {
        val creating = showCreate
        AppDialog(
            show = true,
            title = stringResource(
                if (creating) R.string.project_new_title else R.string.project_rename_title
            ),
            onDismissRequest = {
                if (creating) viewModel.closeCreate() else editing = null
            }
        ) {
            Column {
                AppTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = stringResource(R.string.project_name_label)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = {
                            if (creating) viewModel.closeCreate() else editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            if (creating) viewModel.create(nameInput)
                            editing?.let { viewModel.rename(it.id, nameInput) }
                            if (creating) viewModel.closeCreate() else editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    deleting?.let { target ->
        AppDialog(
            show = true,
            title = stringResource(R.string.project_delete_title),
            onDismissRequest = { deleting = null }
        ) {
            Column {
                AppText(text = stringResource(R.string.project_delete_confirm, target.name))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { deleting = null },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            viewModel.delete(target.id)
                            deleting = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: ProjectInfo,
    summary: String,
    isCurrent: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    AppInfoRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isCurrent) {
                    AppTheme.colors.primary.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(
                    topStart = if (isFirst) 16.dp else 0.dp,
                    topEnd = if (isFirst) 16.dp else 0.dp,
                    bottomStart = if (isLast) 16.dp else 0.dp,
                    bottomEnd = if (isLast) 16.dp else 0.dp
                )
            ),
        title = project.name,
        summary = summary,
        endActions = {
            AppIconDropdownMenu(
                entries = listOf(
                    DropdownEntry(
                        items = buildList {
                            add(
                                DropdownItem(
                                    text = stringResource(R.string.cd_rename),
                                    icon = { m ->
                                        AppIcon(
                                            imageVector = MiuixIcons.Light.Edit,
                                            contentDescription = null,
                                            modifier = m
                                        )
                                    },
                                    onClick = onRename
                                )
                            )
                            if (canDelete) {
                                add(
                                    DropdownItem(
                                        text = stringResource(R.string.action_delete),
                                        icon = { m ->
                                            AppIcon(
                                                imageVector = MiuixIcons.Light.Delete,
                                                contentDescription = null,
                                                modifier = m
                                            )
                                        },
                                        onClick = onDelete
                                    )
                                )
                            }
                        }
                    )
                ),
                minWidth = 28.dp,
                minHeight = 28.dp,
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = 4.dp)
            ) {
                AppIcon(
                    imageVector = MiuixIcons.Light.More,
                    contentDescription = stringResource(R.string.cd_more),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}
