package com.evan.pulse.ui.tools

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 转换模式：从文本编码为 Base64，或从 Base64 解码回文本 */
private enum class Base64Mode { ENCODE, DECODE }

private fun convert(input: String, mode: Base64Mode): Base64Result {
    if (input.isBlank()) return Base64Result("", false)
    return try {
        when (mode) {
            Base64Mode.ENCODE ->
                Base64Result(
                    Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
                    false
                )

            Base64Mode.DECODE -> {
                val decoded = Base64.decode(input.trim(), Base64.DEFAULT)
                Base64Result(String(decoded, Charsets.UTF_8), false)
            }
        }
    } catch (e: IllegalArgumentException) {
        Base64Result("", true)
    }
}

private data class Base64Result(val text: String, val invalid: Boolean)

/** Base64 转换器二级页：输入框 + 模式切换 + 结果块，结果支持一键复制 */
@Composable
fun Base64ToolScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(Base64Mode.ENCODE) }

    val result = remember(input, mode) { convert(input, mode) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val modeLabels = listOf(
            stringResource(R.string.base64_encode),
            stringResource(R.string.base64_decode)
        )
        TabRowWithContour(
            tabs = modeLabels,
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = Base64Mode.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.base64_input_label),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(text = stringResource(R.string.base64_output_label))
            CopyIconButton(
                text = result.text,
                label = stringResource(R.string.action_copy)
            )
        }
        if (result.invalid) {
            Text(
                text = stringResource(R.string.base64_invalid),
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
            Card(modifier = Modifier.fillMaxWidth()) {
                MonospaceBlock(text = result.text, modifier = Modifier.padding(8.dp))
            }
        }
    }
}