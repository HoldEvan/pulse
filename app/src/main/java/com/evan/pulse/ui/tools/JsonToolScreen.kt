package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val formatJson = Json { prettyPrint = true }

private enum class JsonMode { PRETTY, MINIFY }

private data class JsonResult(val text: String, val invalid: Boolean)

private fun convert(input: String, mode: JsonMode): JsonResult {
    if (input.isBlank()) return JsonResult("", false)
    return try {
        val element = Json.parseToJsonElement(input)
        val output = when (mode) {
            JsonMode.PRETTY -> formatJson.encodeToString(JsonElement.serializer(), element)
            JsonMode.MINIFY -> element.toString()
        }
        JsonResult(output, false)
    } catch (e: Exception) {
        JsonResult("", true)
    }
}

/** JSON 格式化/压缩工具二级页：输入框 + 模式切换 + 结果块，结果支持一键复制 */
@Composable
fun JsonToolScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(JsonMode.PRETTY) }

    val result = remember(input, mode) { convert(input, mode) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val modeLabels = listOf(
            stringResource(R.string.json_tool_pretty),
            stringResource(R.string.json_tool_minify)
        )
        TabRowWithContour(
            tabs = modeLabels,
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = JsonMode.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.json_tool_input_label),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(text = stringResource(R.string.json_tool_output_label))
            CopyIconButton(
                text = result.text,
                label = stringResource(R.string.action_copy)
            )
        }
        if (result.invalid) {
            Text(
                text = stringResource(R.string.json_tool_invalid),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error
            )
        } else if (result.text.isBlank()) {
            Text(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else {
            MonospaceBlock(text = result.text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
