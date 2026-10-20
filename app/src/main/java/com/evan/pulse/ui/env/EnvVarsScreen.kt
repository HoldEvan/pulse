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
import com.evan.pulse.ui.common.LocalTopBarInset
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
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.evan.pulse.ui.design.AppIconDropdownMenu

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
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val variables = env?.variables.orEmpty()
                variables.forEachIndexed { index, pair ->
                    AppInfoRow(
                        title = pair.first,
                        summary = pair.second.ifEmpty { null },
                        modifier = Modifier.fillMaxWidth(),
                        endActions = {
                            AppIconDropdownMenu(
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = stringResource(R.string.cd_edit),
                                                icon = { m ->
                                                    AppIcon(
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
                                                    AppIcon(
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
                                AppIcon(
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
                        AppIcon(
                            imageVector = MiuixIcons.Light.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = AppTheme.colors.outline
                        )
                        AppText(
                            text = stringResource(R.string.env_vars_empty),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            color = AppTheme.colors.outline
                        )
                    }
                }
            }
        }
        BottomBarClearance()
    }

    if (showVarEditor) {
        AppDialog(
            show = true,
            title = stringResource(
                if (editingNew) R.string.var_new_title else R.string.var_edit_title
            ),
            onDismissRequest = { viewModel.closeVarEditor() }
        ) {
            Column {
                AppTextField(
                    value = viewModel.varNameInput,
                    onValueChange = { viewModel.varNameInput = it },
                    label = stringResource(R.string.var_name_label)
                )
                AppTextField(
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
        AppDialog(
            show = true,
            title = stringResource(R.string.var_delete_title),
            onDismissRequest = { viewModel.cancelDeleteVar() }
        ) {
            Column {
                AppText(stringResource(R.string.var_delete_confirm, target.third))
                DialogActions(
                    onCancel = { viewModel.cancelDeleteVar() },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = { viewModel.confirmDeleteVar() }
                )
            }
        }
    }
}
