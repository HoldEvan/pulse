package com.evan.pulse.ui.env

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 环境变量二级页：列表展示每个变量的名称与值，修改/删除收在每项的更多菜单里，
 * 新增由顶栏加号触发；编辑弹窗为两行输入框（变量名、值各占一行）。
 */
@Composable
fun EnvVarsScreen(
    envId: Long,
    modifier: Modifier = Modifier,
    viewModel: EnvViewModel
) {
    val environments by viewModel.environments.collectAsState()
    val env = environments.firstOrNull { it.id == envId }
    val showVarEditor = viewModel.showVarEditor
    val editingNew = viewModel.varEditIndex == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val variables = env?.variables.orEmpty()
                variables.forEachIndexed { index, pair ->
                    BasicComponent(
                        title = pair.first,
                        summary = pair.second.ifEmpty { null },
                        modifier = Modifier.fillMaxWidth(),
                        endActions = {
                            OverlayIconDropdownMenu(
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = stringResource(R.string.cd_edit),
                                                icon = { m ->
                                                    Icon(
                                                        imageVector = MiuixIcons.Light.Edit,
                                                        contentDescription = null,
                                                        modifier = m
                                                    )
                                                },
                                                onClick = { viewModel.openVarEditor(envId, index) }
                                            ),
                                            DropdownItem(
                                                text = stringResource(R.string.action_delete),
                                                icon = { m ->
                                                    Icon(
                                                        imageVector = MiuixIcons.Light.Delete,
                                                        contentDescription = null,
                                                        modifier = m
                                                    )
                                                },
                                                onClick = { viewModel.requestDeleteVar(envId, index) }
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
                                Icon(
                                    imageVector = MiuixIcons.Light.More,
                                    contentDescription = stringResource(R.string.cd_more),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
                if (variables.isEmpty()) {
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
                            text = stringResource(R.string.env_vars_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        BottomBarClearance()
    }

    if (showVarEditor) {
        OverlayDialog(
            show = true,
            title = stringResource(
                if (editingNew) R.string.var_new_title else R.string.var_edit_title
            ),
            onDismissRequest = { viewModel.closeVarEditor() }
        ) {
            Column {
                TextField(
                    value = viewModel.varNameInput,
                    onValueChange = { viewModel.varNameInput = it },
                    label = stringResource(R.string.var_name_label)
                )
                TextField(
                    value = viewModel.varValueInput,
                    onValueChange = { viewModel.varValueInput = it },
                    label = stringResource(R.string.kv_value_label),
                    modifier = Modifier.padding(top = 8.dp)
                )
                DialogActions(
                    onCancel = { viewModel.closeVarEditor() },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = { viewModel.saveVar() }
                )
            }
        }
    }

    viewModel.varDeleteTarget?.let { target ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.var_delete_title),
            onDismissRequest = { viewModel.cancelDeleteVar() }
        ) {
            Column {
                Text(stringResource(R.string.var_delete_confirm, target.third))
                DialogActions(
                    onCancel = { viewModel.cancelDeleteVar() },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = { viewModel.confirmDeleteVar() }
                )
            }
        }
    }
}
