package com.pulse.apitester.ui.env

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit

@Composable
fun EnvironmentScreen(modifier: Modifier = Modifier, viewModel: EnvViewModel = viewModel()) {
    val projectId by viewModel.currentProjectId.collectAsState()
    val environments = viewModel.environments(projectId)
    val showEditor = viewModel.showEditor
    val deleteTarget = viewModel.deleteTarget

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        SmallTitle(text = "环境列表（当前项目）")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                environments.forEach { env ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = env.name + if (env.isActive) "  ✓ 已激活" else "",
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${env.variables.size} 个变量",
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier
                                .clickable { viewModel.openRename(env) }
                                .padding(6.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除",
                            modifier = Modifier
                                .clickable { viewModel.requestDelete(env) }
                                .padding(6.dp)
                        )
                    }
                }
                if (environments.isEmpty()) {
                    Text("当前项目还没有环境", modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openCreate() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("新建环境", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    if (showEditor) {
        SuperDialog(
            show = true,
            title = if (viewModel.creating) "新建环境" else "编辑环境",
            onDismissRequest = { viewModel.closeEditor() }
        ) {
            Column {
                TextField(
                    value = viewModel.nameInput,
                    onValueChange = { viewModel.nameInput = it },
                    label = "环境名称"
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text("变量", modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加变量",
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
                            label = "变量名",
                            modifier = Modifier.weight(0.4f)
                        )
                        TextField(
                            value = pair.second,
                            onValueChange = { viewModel.updateVar(index, pair.first to it) },
                            label = "值",
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(start = 6.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "删除变量",
                            modifier = Modifier
                                .clickable { viewModel.removeVar(index) }
                                .padding(6.dp)
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { viewModel.closeEditor() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "保存",
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        SuperDialog(
            show = true,
            title = "删除环境",
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                Text("确定删除环境「${target.name}」及其全部变量？此操作不可恢复。")
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { viewModel.cancelDelete() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "删除",
                        onClick = { viewModel.confirmDelete() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
