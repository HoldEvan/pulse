package com.pulse.apitester.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.apitester.data.model.HistoryEntry
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: HistoryViewModel = viewModel()) {
    val projectId by viewModel.currentProjectId.collectAsState()
    val entries = viewModel.history(projectId)
    val confirmClear = viewModel.confirmClear

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SmallTitle(text = "HTTP 历史（当前项目）")
        TextField(
            value = viewModel.keyword,
            onValueChange = { viewModel.keyword = it },
            label = "搜索 URL 或方法",
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            text = "清空历史",
            onClick = { viewModel.confirmClear = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries) { entry ->
                HistoryItem(entry)
            }
        }
    }

    if (confirmClear) {
        SuperDialog(
            show = true,
            title = "清空历史",
            onDismissRequest = { viewModel.confirmClear = false }
        ) {
            Column {
                Text("确定清空当前项目的全部历史记录？此操作不可恢复。")
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { viewModel.confirmClear = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "清空",
                        onClick = { viewModel.clear() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

@Composable
private fun HistoryItem(entry: HistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = entry.method,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            when (entry.method) {
                                "GET" -> Color(0xFF1565C0)
                                "POST" -> Color(0xFF2E7D32)
                                "PUT" -> Color(0xFFEF6C00)
                                "DELETE" -> Color(0xFFC62828)
                                else -> Color(0xFF6A1B9A)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp
                )
                Text(
                    text = entry.statusCode.toString(),
                    color = if (entry.success) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp
                )
                Text(
                    text = "${entry.durationMs}ms",
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 13.sp
                )
                Text(
                    text = timeFormat.format(Date(entry.createdAt)),
                    modifier = Modifier.weight(1f),
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
