package com.evan.pulse.ui.env

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
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
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.evan.pulse.ui.design.AppIconDropdownMenu

@Composable
fun EnvironmentScreen(
    modifier: Modifier = Modifier,
    onOpenVars: (Long) -> Unit = {},
    viewModel: EnvViewModel = viewModel()
) {
    val projectId by viewModel.currentProjectId.collectAsState()
    val environments by viewModel.environments.collectAsState()
    val projectEnvironments = environments.filter { it.projectId == projectId }
    val showEditor = viewModel.showEditor
    val deleteTarget = viewModel.deleteTarget

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier) {
                projectEnvironments.forEachIndexed { index, env ->
                    val varsText = stringResource(R.string.vars_count, env.variables.size)
                    AppInfoRow(
                        title = env.name,
                        summary = varsText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                color = if (env.isActive) {
                    AppTheme.colors.primary.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                },
                                shape = RoundedCornerShape(
                                    topStart = if (index == 0) 16.dp else 0.dp,
                                    topEnd = if (index == 0) 16.dp else 0.dp,
                                    bottomStart = 0.dp,
                                    bottomEnd = 0.dp
                                )
                            ),
                        onClick = { onOpenVars(env.id) },
                        endActions = {
                            AppIconDropdownMenu(
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = stringResource(R.string.cd_rename),
                                                icon = { m ->
                                                    AppIcon(
                                                        imageVector = MiuixIcons.Light.Edit,
                                                        contentDescription = null,
                                                        modifier = m
                                                    )
                                                },
                                                onClick = { viewModel.openRename(env) }
                                            ),
                                            DropdownItem(
                                                text = stringResource(R.string.action_delete),
                                                icon = { m ->
                                                    AppIcon(
                                                        imageVector = MiuixIcons.Light.Delete,
                                                        contentDescription = null,
                                                        modifier = m
                                                    )
                                                },
                                                onClick = { viewModel.requestDelete(env) }
                                            )
                                        )
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
                if (projectEnvironments.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = AppTheme.colors.outline
                        )
                        AppText(
                            text = stringResource(R.string.env_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = AppTheme.colors.outline
                        )
                    }
                }
                AppInfoRow(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.openCreate() },
                    title = stringResource(R.string.env_create_title),
                    startAction = {
                        AppIcon(
imageVector = MiuixIcons.Light.Add,
                            contentDescription = stringResource(R.string.cd_add)
                        )
                    }
                )
            }
        }
        BottomBarClearance()
    }

    if (showEditor) {
        AppDialog(
            show = true,
            title = if (viewModel.creating) {
                stringResource(R.string.env_create_title)
            } else {
                stringResource(R.string.env_edit_title)
            },
            onDismissRequest = { viewModel.closeEditor() }
        ) {
            Column {
                AppTextField(
                    value = viewModel.nameInput,
                    onValueChange = { viewModel.nameInput = it },
                    label = stringResource(R.string.env_name_label)
                )
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.closeEditor() },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AppDialog(
            show = true,
            title = stringResource(R.string.env_delete_title),
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                AppText(stringResource(R.string.env_delete_confirm, target.name))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelDelete() },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmDelete() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
