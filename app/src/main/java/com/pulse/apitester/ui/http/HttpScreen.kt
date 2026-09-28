package com.pulse.apitester.ui.http

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.apitester.domain.model.BodyType
import com.pulse.apitester.domain.model.FormField
import com.pulse.apitester.domain.model.HttpCallOutcome
import com.pulse.apitester.domain.model.HttpMethod
import com.pulse.apitester.domain.model.KeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val httpMethods = HttpMethod.entries.toList()
private val bodyTypes = listOf(BodyType.NONE, BodyType.JSON, BodyType.RAW, BodyType.FORM_URLENCODED, BodyType.FORM_DATA)

@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    SuperDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column {
            options.forEachIndexed { index, option ->
                Text(
                    text = if (index == selectedIndex) "● $option" else "○ $option",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun HttpScreen(modifier: Modifier = Modifier, viewModel: HttpViewModel = viewModel()) {
    val scroll = rememberScrollState()
    val showEnvDialog = viewModel.showEnvDialog
    val missingVars = viewModel.missingVars

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp)
    ) {
        SmallTitle(text = "请求")
        RequestEditorCard(viewModel)
        SmallTitle(text = "响应")
        ResponseCard(viewModel)
    }

    if (viewModel.showMethodDialog) {
        SelectionDialog(
            title = "选择方法",
            options = httpMethods.map { it.name },
            selectedIndex = httpMethods.indexOf(viewModel.method),
            onSelect = {
                viewModel.method = httpMethods[it]
                viewModel.showMethodDialog = false
            },
            onDismiss = { viewModel.showMethodDialog = false }
        )
    }

    if (viewModel.showBodyTypeDialog) {
        SelectionDialog(
            title = "选择请求体类型",
            options = bodyTypes.map { it.name },
            selectedIndex = bodyTypes.indexOf(viewModel.bodyType),
            onSelect = {
                viewModel.bodyType = bodyTypes[it]
                viewModel.showBodyTypeDialog = false
            },
            onDismiss = { viewModel.showBodyTypeDialog = false }
        )
    }

    if (showEnvDialog) {
        val envList = viewModel.environments.collectAsState().value
        val pid = viewModel.currentProjectId.collectAsState().value
        val projectEnvs = envList.filter { it.projectId == pid }
        val options = listOf("无环境") + projectEnvs.map { it.name }
        val activeIdx = projectEnvs.indexOfFirst { it.isActive }.let { if (it >= 0) it + 1 else 0 }
        SelectionDialog(
            title = "切换环境",
            options = options,
            selectedIndex = activeIdx,
            onSelect = { idx ->
                viewModel.showEnvDialog = false
                if (idx > 0) viewModel.envStoreActivate(projectEnvs[idx - 1].id)
            },
            onDismiss = { viewModel.showEnvDialog = false }
        )
    }

    missingVars?.let { missing ->
        SuperDialog(
            show = true,
            title = "缺失环境变量",
            onDismissRequest = { viewModel.dismissMissing() }
        ) {
            Column {
                Text("以下变量在激活环境中不存在：")
                Text(
                    text = missing.joinToString(", "),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onBackground
                )
                TextButton(
                    text = "知道了",
                    onClick = { viewModel.dismissMissing() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RequestEditorCard(viewModel: HttpViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.method.name,
                    modifier = Modifier
                        .clickable { viewModel.showMethodDialog = true }
                        .background(
                            MiuixTheme.colorScheme.primaryVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                TextField(
                    value = viewModel.url,
                    onValueChange = { viewModel.url = it },
                    label = "URL，支持 {{var}}",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Button(
                    onClick = { viewModel.send() },
                    enabled = !viewModel.sending,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (viewModel.sending) "..." else "发送")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("参数", "请求头", "请求体", "认证", "环境")
                tabs.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        color = if (viewModel.activeTab == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clickable { viewModel.activeTab = index }
                            .background(
                                if (viewModel.activeTab == index) MiuixTheme.colorScheme.primaryContainer else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            when (viewModel.activeTab) {
                0 -> KeyValueEditor(
                    items = viewModel.params,
                    keyLabel = "参数名",
                    valueLabel = "参数值",
                    onChange = viewModel::updateParams,
                    onAdd = viewModel::addParam,
                    onRemove = viewModel::removeParam
                )

                1 -> KeyValueEditor(
                    items = viewModel.headers,
                    keyLabel = "Header 名",
                    valueLabel = "Header 值",
                    onChange = viewModel::updateHeaders,
                    onAdd = viewModel::addHeader,
                    onRemove = viewModel::removeHeader
                )

                2 -> BodyEditor(viewModel)
                3 -> AuthEditor(viewModel)
                4 -> {
                    TextButton(
                        text = "当前环境：${viewModel.activeEnvName()}",
                        onClick = { viewModel.showEnvDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValueEditor(
    items: List<KeyValue>,
    keyLabel: String,
    valueLabel: String,
    onChange: (Int, KeyValue) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column {
        items.forEachIndexed { index, kv ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = kv.key,
                    onValueChange = { onChange(index, kv.copy(key = it)) },
                    label = keyLabel,
                    modifier = Modifier.weight(0.4f)
                )
                TextField(
                    value = kv.value,
                    onValueChange = { onChange(index, kv.copy(value = it)) },
                    label = valueLabel,
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(start = 6.dp)
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除",
                    modifier = Modifier
                        .clickable { onRemove(index) }
                        .padding(6.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加",
                modifier = Modifier.clickable { onAdd() }
            )
            Text("添加一行", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun BodyEditor(viewModel: HttpViewModel) {
    Column {
        TextButton(
            text = "类型：${viewModel.bodyType.name}",
            onClick = { viewModel.showBodyTypeDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
        when (viewModel.bodyType) {
            BodyType.NONE -> {}
            BodyType.JSON, BodyType.RAW -> {
                TextField(
                    value = viewModel.bodyText,
                    onValueChange = { viewModel.bodyText = it },
                    label = if (viewModel.bodyType == BodyType.JSON) "JSON 内容" else "纯文本内容",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BodyType.FORM_URLENCODED -> {
                val items = viewModel.formFields.map { KeyValue(it.name, it.value) }
                KeyValueEditor(
                    items = items,
                    keyLabel = "字段",
                    valueLabel = "值",
                    onChange = { i, kv -> viewModel.updateFormField(i, FormField(kv.key, kv.value)) },
                    onAdd = viewModel::addFormField,
                    onRemove = viewModel::removeFormField
                )
            }

            BodyType.FORM_DATA -> {
                viewModel.formFields.forEachIndexed { index, field ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = field.name,
                            onValueChange = { viewModel.updateFormField(index, field.copy(name = it)) },
                            label = "字段",
                            modifier = Modifier.weight(0.35f)
                        )
                        if (field.isFile) {
                            Text(
                                text = field.fileName ?: "已选文件",
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 6.dp),
                                maxLines = 1
                            )
                        } else {
                            TextField(
                                value = field.value,
                                onValueChange = { viewModel.updateFormField(index, field.copy(value = it)) },
                                label = "值",
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 6.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "删除",
                            modifier = Modifier
                                .clickable { viewModel.removeFormField(index) }
                                .padding(6.dp)
                        )
                    }
                }
                FormFileRow(viewModel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加",
                        modifier = Modifier.clickable { viewModel.addFormField() }
                    )
                    Text("添加文本字段", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun FormFileRow(viewModel: HttpViewModel) {
    TextButton(
        text = "添加文件字段（文件选择将在数据层接入后可用）",
        onClick = { },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthEditor(viewModel: HttpViewModel) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                com.pulse.apitester.domain.model.AuthType.NONE to "无",
                com.pulse.apitester.domain.model.AuthType.BEARER to "Bearer",
                com.pulse.apitester.domain.model.AuthType.BASIC to "Basic"
            ).forEach { (type, label) ->
                Text(
                    text = label,
                    color = if (viewModel.authType == type) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { viewModel.authType = type }
                        .background(
                            if (viewModel.authType == type) MiuixTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        when (viewModel.authType) {
            com.pulse.apitester.domain.model.AuthType.NONE -> {}
            com.pulse.apitester.domain.model.AuthType.BEARER -> {
                TextField(
                    value = viewModel.bearerToken,
                    onValueChange = { viewModel.bearerToken = it },
                    label = "Token，支持 {{var}}",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            com.pulse.apitester.domain.model.AuthType.BASIC -> {
                TextField(
                    value = viewModel.basicUsername,
                    onValueChange = { viewModel.basicUsername = it },
                    label = "用户名",
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = viewModel.basicPassword,
                    onValueChange = { viewModel.basicPassword = it },
                    label = "密码",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResponseCard(viewModel: HttpViewModel) {
    val outcome = viewModel.outcome
    var pretty by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        when (outcome) {
            null -> Text(
                text = "尚未发送请求",
                modifier = Modifier.padding(16.dp)
            )

            is HttpCallOutcome.Failure -> Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "请求失败（${outcome.errorType.name}）",
                    color = Color(0xFFD32F2F)
                )
                Text(
                    text = outcome.message,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp
                )
            }

            is HttpCallOutcome.Success -> Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(outcome.statusCode)
                    Text(
                        text = "${outcome.durationMs}ms · ${outcome.responseSizeBytes}B",
                        modifier = Modifier.padding(start = 10.dp),
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = prettyBody(outcome.body, pretty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(
                            MiuixTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                TextButton(
                    text = if (pretty) "查看原文" else "格式化 JSON",
                    onClick = { pretty = !pretty },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(statusCode: Int) {
    val color = when {
        statusCode in 200..299 -> Color(0xFF2E7D32)
        statusCode in 300..399 -> Color(0xFF1565C0)
        statusCode in 400..499 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    Text(
        text = statusCode.toString(),
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private val prettyJson = Json { prettyPrint = true }

private fun prettyBody(body: String, pretty: Boolean): String {
    if (!pretty) return body
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(body))
    } catch (_: Exception) {
        body
    }
}
