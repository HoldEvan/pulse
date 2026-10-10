package com.evan.pulse.ui.env

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.BottomBarClearance
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EnvironmentScreen(modifier: Modifier = Modifier, viewModel: EnvViewModel = viewModel()) {
    val projectId by viewModel.currentProjectId.collectAsState()
    val environments by viewModel.environments.collectAsState()
    val projectEnvironments = environments.filter { it.projectId == projectId }
    val showEditor = viewModel.showEditor
    val deleteTarget = viewModel.deleteTarget

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                projectEnvironments.forEach { env ->
                    val varsText = stringResource(R.string.vars_count, env.variables.size)
                    RadioButtonPreference(
                        title = env.name,
                        selected = env.isActive,
                        onClick = { viewModel.activate(env.id) },
                        modifier = Modifier.fillMaxWidth(),
                        summary = if (env.isActive) {
                            listOf(stringResource(R.string.env_active_mark), varsText)
                                .joinToString(" · ")
                        } else {
                            varsText
                        },
                        radioButtonLocation = RadioButtonLocation.Start,
                        endActions = {
                            IconButton(
                                onClick = { viewModel.openRename(env) },
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Light.Edit,
                                    contentDescription = stringResource(R.string.cd_edit)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.requestDelete(env) },
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Light.Delete,
                                    contentDescription = stringResource(R.string.action_delete)
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
                        Icon(
                            imageVector = MiuixIcons.Light.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MiuixTheme.colorScheme.outline
                        )
                        Text(
                            text = stringResource(R.string.env_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.outline
                        )
                    }
                }
                BasicComponent(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.openCreate() },
                    title = stringResource(R.string.env_create_title),
                    startAction = {
                        Icon(
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
        OverlayDialog(
            show = true,
            title = if (viewModel.creating) {
                stringResource(R.string.env_create_title)
            } else {
                stringResource(R.string.env_edit_title)
            },
            onDismissRequest = { viewModel.closeEditor() }
        ) {
            Column {
                TextField(
                    value = viewModel.nameInput,
                    onValueChange = { viewModel.nameInput = it },
                    label = stringResource(R.string.env_name_label)
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.env_vars_title), modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = MiuixIcons.Light.Add,
                        contentDescription = stringResource(R.string.cd_add_var),
                        modifier = Modifier.clickable { viewModel.addVar() }
                    )
                }
                viewModel.editingVars.forEachIndexed { index, pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = pair.first,
                            onValueChange = { viewModel.updateVar(index, it to pair.second) },
                            label = stringResource(R.string.var_name_label),
                            modifier = Modifier.weight(0.4f)
                        )
                        TextField(
                            value = pair.second,
                            onValueChange = { viewModel.updateVar(index, pair.first to it) },
                            label = stringResource(R.string.kv_value_label),
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(start = 6.dp)
                        )
                        Icon(
                            imageVector = MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.cd_remove_var),
                            modifier = Modifier
                                .clickable { viewModel.removeVar(index) }
                                .padding(6.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.closeEditor() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.env_delete_title),
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                Text(stringResource(R.string.env_delete_confirm, target.name))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { viewModel.cancelDelete() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { viewModel.confirmDelete() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
