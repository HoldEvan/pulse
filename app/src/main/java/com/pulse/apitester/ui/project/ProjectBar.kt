package com.pulse.apitester.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.apitester.data.model.ProjectInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDialog

@Composable
fun ProjectBar(viewModel: ProjectViewModel = viewModel()) {
    val projects by viewModel.projects.collectAsState()
    val currentId by viewModel.currentProjectId.collectAsState()
    val current = projects.firstOrNull { it.id == currentId }
    val showDialog by viewModel.showDialog
    var editing by remember { mutableStateOf<ProjectInfo?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    TopAppBar(
        title = current?.name ?: "Pulse",
        actions = {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "切换项目",
                modifier = Modifier
                    .clickable { viewModel.openDialog() }
                    .padding(8.dp)
            )
        }
    )

    if (showDialog) {
        SuperDialog(
            show = showDialog,
            title = "项目管理",
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
                                text = project.name + if (project.id == currentId) " ✓" else "",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "重命名",
                                modifier = Modifier
                                    .clickable {
                                        editing = project
                                        nameInput = project.name
                                    }
                                    .padding(6.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                modifier = Modifier
                                    .clickable { viewModel.delete(project.id) }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
                TextButton(
                    text = "新建项目",
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
        SuperDialog(
            show = true,
            title = if (creating) "新建项目" else "重命名项目",
            onDismissRequest = {
                creating = false
                editing = null
            }
        ) {
            Column {
                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = "项目名称"
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            creating = false
                            editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "保存",
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
}
