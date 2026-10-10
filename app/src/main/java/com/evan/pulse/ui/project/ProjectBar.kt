package com.evan.pulse.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.roundedPressable
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProjectBar(
    title: String,
    showProjectSelector: Boolean = false,
    showEnvSelector: Boolean = false,
    onAddMonitor: (() -> Unit)? = null,
    viewModel: ProjectViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val currentId by viewModel.currentProjectId.collectAsState()
    val current = projects.firstOrNull { it.id == currentId }
    val showDialog by viewModel.showDialog
    var editing by remember { mutableStateOf<ProjectInfo?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }

    val envs by viewModel.environments.collectAsState()
    val projectEnvs = envs.filter { it.projectId == currentId }
    val activeEnvNames = projectEnvs.filter { it.isActive }.map { it.name }
    var showEnvDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = title,
        actions = {
            if (showProjectSelector) {
                Row(
                    modifier = Modifier
                    .roundedPressable { viewModel.openDialog() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (current?.name ?: stringResource(R.string.project_none)).take(10),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.cd_switch_project)
                    )
                }
            }
            if (showEnvSelector) {
                val envLabel = if (activeEnvNames.isEmpty()) {
                    stringResource(R.string.env_none)
                } else {
                    activeEnvNames.joinToString("、")
                }
                Row(
                    modifier = Modifier
                    .roundedPressable { showEnvDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = envLabel.take(10),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.cd_switch_env)
                    )
                }
            }
            if (onAddMonitor != null) {
                IconButton(onClick = onAddMonitor) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.monitor_add)
                    )
                }
            }
        }
    )

    if (showDialog) {
        OverlayDialog(
            show = showDialog,
            title = stringResource(R.string.project_manage_title),
            onDismissRequest = { viewModel.closeDialog() }
        ) {
            Column {
                projects.forEach { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.switch(project.id)
                                viewModel.closeDialog()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = project.name + if (project.id == currentId) {
                                    stringResource(R.string.project_selected_mark)
                                } else {
                                    ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_rename),
                                modifier = Modifier
                                    .clickable {
                                        editing = project
                                        nameInput = project.name
                                    }
                                    .padding(6.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier
                                    .clickable { deleting = project }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
                TextButton(
                    text = stringResource(R.string.project_new_title),
                    onClick = {
                        creating = true
                        nameInput = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }

    if (creating || editing != null) {
        OverlayDialog(
            show = true,
            title = if (creating) {
                stringResource(R.string.project_new_title)
            } else {
                stringResource(R.string.project_rename_title)
            },
            onDismissRequest = {
                creating = false
                editing = null
            }
        ) {
            Column {
                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = stringResource(R.string.project_name_label)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = {
                            creating = false
                            editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            if (creating) viewModel.create(nameInput)
                            editing?.let { viewModel.rename(it.id, nameInput) }
                            creating = false
                            editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val deletingTarget = deleting
    if (deletingTarget != null) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.project_delete_title),
            onDismissRequest = { deleting = null }
        ) {
            Column {
                Text(
                    text = stringResource(R.string.project_delete_confirm, deletingTarget.name)
                )
                DialogActions(
                    onCancel = { deleting = null },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = {
                        viewModel.delete(deletingTarget.id)
                        deleting = null
                    }
                )
            }
        }
    }

    if (showEnvDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.env_title),
            onDismissRequest = { showEnvDialog = false }
        ) {
            Column {
                if (projectEnvs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.env_empty),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                } else {
                    projectEnvs.forEach { env ->
                        CheckboxPreference(
                            title = env.name,
                            checked = env.isActive,
                            onCheckedChange = {
                                if (it) {
                                    viewModel.activateEnv(env.id)
                                    showEnvDialog = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
