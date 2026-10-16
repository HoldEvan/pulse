package com.evan.pulse.ui.tools

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.asciiDecimalCodes
import com.evan.pulse.domain.asciiFromCodes
import com.evan.pulse.domain.asciiHexCodes
import com.evan.pulse.domain.base64Decode
import com.evan.pulse.domain.base64Encode
import com.evan.pulse.domain.formatRadix
import com.evan.pulse.domain.parseRadix
import com.evan.pulse.domain.radixBases
import com.evan.pulse.domain.urlDecode
import com.evan.pulse.domain.urlEncode
import com.evan.pulse.domain.unicodeEscape
import com.evan.pulse.domain.unicodeUnescape
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信息码工具页顶层的转换项 */
private enum class EncodingTool { BASE64, RADIX, URL, UNICODE, ASCII }

/** 编码/解码双向转换项共用的方向 */
private enum class ConvertMode { ENCODE, DECODE }

/** 单值转换结果：[invalid] 为真时展示错误提示 */
private data class ConvertResult(val text: String, val invalid: Boolean)

/** 信息码转换二级页：Base64、进制、URL、Unicode、ASCII 五类常用转换，顶部切换 */
@Composable
fun EncodingToolScreen(modifier: Modifier = Modifier) {
    // 各转换项的输入在页内切换时保留
    var tool by rememberSaveable { mutableStateOf(EncodingTool.BASE64) }
    var base64Input by rememberSaveable { mutableStateOf("") }
    var base64Mode by rememberSaveable { mutableStateOf(ConvertMode.ENCODE) }
    var radixInput by rememberSaveable { mutableStateOf("") }
    var radixBase by rememberSaveable { mutableStateOf(10) }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var urlMode by rememberSaveable { mutableStateOf(ConvertMode.ENCODE) }
    var unicodeInput by rememberSaveable { mutableStateOf("") }
    var unicodeMode by rememberSaveable { mutableStateOf(ConvertMode.ENCODE) }
    var asciiInput by rememberSaveable { mutableStateOf("") }
    var asciiMode by rememberSaveable { mutableStateOf(ConvertMode.ENCODE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val toolLabels = listOf(
            stringResource(R.string.encoding_tab_base64),
            stringResource(R.string.encoding_tab_radix),
            stringResource(R.string.encoding_tab_url),
            stringResource(R.string.encoding_tab_unicode),
            stringResource(R.string.encoding_tab_ascii)
        )
        TabRowWithContour(
            tabs = toolLabels,
            selectedTabIndex = tool.ordinal,
            onTabSelected = { tool = EncodingTool.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        when (tool) {
            EncodingTool.BASE64 -> Base64Section(
                input = base64Input,
                mode = base64Mode,
                onInputChange = { base64Input = it },
                onModeChange = { base64Mode = it }
            )

            EncodingTool.RADIX -> RadixSection(
                input = radixInput,
                base = radixBase,
                onInputChange = { radixInput = it },
                onBaseChange = { radixBase = it }
            )

            EncodingTool.URL -> UrlSection(
                input = urlInput,
                mode = urlMode,
                onInputChange = { urlInput = it },
                onModeChange = { urlMode = it }
            )

            EncodingTool.UNICODE -> UnicodeSection(
                input = unicodeInput,
                mode = unicodeMode,
                onInputChange = { unicodeInput = it },
                onModeChange = { unicodeMode = it }
            )

            EncodingTool.ASCII -> AsciiSection(
                input = asciiInput,
                mode = asciiMode,
                onInputChange = { asciiInput = it },
                onModeChange = { asciiMode = it }
            )
        }
    }
}

/** Base64 编解码：与原独立工具页一致的布局 */
@Composable
private fun Base64Section(
    input: String,
    mode: ConvertMode,
    onInputChange: (String) -> Unit,
    onModeChange: (ConvertMode) -> Unit
) {
    val result = remember(input, mode) {
        when {
            input.isBlank() -> ConvertResult("", false)
            mode == ConvertMode.ENCODE -> ConvertResult(base64Encode(input), false)
            else -> base64Decode(input)?.let { ConvertResult(it, false) } ?: ConvertResult("", true)
        }
    }
    EncodeDecodeSection(
        input = input,
        mode = mode,
        onInputChange = onInputChange,
        onModeChange = onModeChange,
        minLines = 4,
        result = result,
        invalidText = stringResource(R.string.base64_invalid)
    )
}

/** URL 百分号编解码 */
@Composable
private fun UrlSection(
    input: String,
    mode: ConvertMode,
    onInputChange: (String) -> Unit,
    onModeChange: (ConvertMode) -> Unit
) {
    val result = remember(input, mode) {
        when {
            input.isBlank() -> ConvertResult("", false)
            mode == ConvertMode.ENCODE -> ConvertResult(urlEncode(input), false)
            else -> urlDecode(input)?.let { ConvertResult(it, false) } ?: ConvertResult("", true)
        }
    }
    EncodeDecodeSection(
        input = input,
        mode = mode,
        onInputChange = onInputChange,
        onModeChange = onModeChange,
        minLines = 3,
        result = result,
        invalidText = stringResource(R.string.url_invalid)
    )
}

/** Unicode 转义互转：非 ASCII 字符与 \uXXXX 序列互转 */
@Composable
private fun UnicodeSection(
    input: String,
    mode: ConvertMode,
    onInputChange: (String) -> Unit,
    onModeChange: (ConvertMode) -> Unit
) {
    val result = remember(input, mode) {
        when {
            input.isBlank() -> ConvertResult("", false)
            mode == ConvertMode.ENCODE -> ConvertResult(unicodeEscape(input), false)
            else -> ConvertResult(unicodeUnescape(input), false)
        }
    }
    // 转义还原总是成功，invalidText 不会展示
    EncodeDecodeSection(
        input = input,
        mode = mode,
        onInputChange = onInputChange,
        onModeChange = onModeChange,
        minLines = 3,
        result = result,
        invalidText = null
    )
}

/** 编码/解码双向转换的通用布局：方向切换 + 输入框 + 单值结果 */
@Composable
private fun EncodeDecodeSection(
    input: String,
    mode: ConvertMode,
    onInputChange: (String) -> Unit,
    onModeChange: (ConvertMode) -> Unit,
    minLines: Int,
    result: ConvertResult,
    invalidText: String?
) {
    val modeLabels = listOf(
        stringResource(R.string.base64_encode),
        stringResource(R.string.base64_decode)
    )
    TabRowWithContour(
        tabs = modeLabels,
        selectedTabIndex = mode.ordinal,
        onTabSelected = { onModeChange(ConvertMode.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    TextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.base64_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = minLines
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
    when {
        result.invalid && invalidText != null -> {
            Text(
                text = invalidText,
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error
            )
        }

        result.text.isBlank() -> {
            Text(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        else -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                MonospaceBlock(text = result.text, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

/** 进制转换：选定输入进制，结果同时给出二/八/十/十六四种表示 */
@Composable
private fun RadixSection(
    input: String,
    base: Int,
    onInputChange: (String) -> Unit,
    onBaseChange: (Int) -> Unit
) {
    val copy = rememberCopyToClipboard()
    val baseLabels = listOf(
        stringResource(R.string.radix_binary),
        stringResource(R.string.radix_octal),
        stringResource(R.string.radix_decimal),
        stringResource(R.string.radix_hex)
    )
    val value = parseRadix(input, base)

    Text(
        text = stringResource(R.string.radix_input_base),
        modifier = Modifier.padding(top = 10.dp),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    TabRowWithContour(
        tabs = baseLabels,
        selectedTabIndex = radixBases.indexOf(base).coerceAtLeast(0),
        onTabSelected = { onBaseChange(radixBases[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )

    TextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.radix_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
    }
    when {
        input.isBlank() -> {
            Text(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        value == null -> {
            Text(
                text = stringResource(R.string.radix_invalid),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error
            )
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultFieldRow(baseLabels[0], formatRadix(value, 2), copy)
                ResultFieldRow(baseLabels[1], formatRadix(value, 8), copy)
                ResultFieldRow(baseLabels[2], formatRadix(value, 10), copy)
                ResultFieldRow(baseLabels[3], formatRadix(value, 16), copy)
            }
            Text(
                text = stringResource(R.string.timestamp_tool_long_press_tip),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/** ASCII 码互转：编码给出十/十六进制码列表，解码把码列表还原为文本 */
@Composable
private fun AsciiSection(
    input: String,
    mode: ConvertMode,
    onInputChange: (String) -> Unit,
    onModeChange: (ConvertMode) -> Unit
) {
    val copy = rememberCopyToClipboard()
    val modeLabels = listOf(
        stringResource(R.string.base64_encode),
        stringResource(R.string.base64_decode)
    )
    TabRowWithContour(
        tabs = modeLabels,
        selectedTabIndex = mode.ordinal,
        onTabSelected = { onModeChange(ConvertMode.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    TextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.base64_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 3
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
    }
    if (mode == ConvertMode.ENCODE) {
        if (input.isBlank()) {
            Text(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultFieldRow(
                    label = stringResource(R.string.radix_decimal),
                    value = asciiDecimalCodes(input).joinToString(" ") { it.toString() },
                    copy = copy
                )
                ResultFieldRow(
                    label = stringResource(R.string.radix_hex),
                    value = asciiHexCodes(input).joinToString(" "),
                    copy = copy
                )
            }
            Text(
                text = stringResource(R.string.timestamp_tool_long_press_tip),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    } else {
        val text = asciiFromCodes(input)
        when {
            input.isBlank() -> {
                Text(
                    text = stringResource(R.string.base64_empty_hint),
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            text == null -> {
                Text(
                    text = stringResource(R.string.ascii_invalid),
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error
                )
            }

            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    MonospaceBlock(text = text, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

/** 单项结果行：长按复制该值，底色与 MonospaceBlock 保持一致 */
@Composable
private fun ResultFieldRow(label: String, value: String, copy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { copy(value) })
            .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 8.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}
