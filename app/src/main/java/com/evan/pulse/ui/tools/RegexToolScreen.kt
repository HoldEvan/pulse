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
import com.evan.pulse.domain.tools.ExtractPreset
import com.evan.pulse.domain.tools.RegexExtractor
import com.evan.pulse.domain.tools.RegexTool
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.l10n.localizedLabels

/** 提取预设的展示名称，顺序与 [ExtractPreset] 枚举一致 */
private val extractPresetLabels = ExtractPreset.entries.map {
    when (it) {
        ExtractPreset.URL -> R.string.extract_preset_url
        ExtractPreset.MOBILE -> R.string.extract_preset_mobile
        ExtractPreset.EMAIL -> R.string.extract_preset_email
        ExtractPreset.IPV4 -> R.string.extract_preset_ipv4
        ExtractPreset.MAC -> R.string.extract_preset_mac
        ExtractPreset.ID_CARD -> R.string.extract_preset_id_card
        ExtractPreset.DATE -> R.string.extract_preset_date
        ExtractPreset.TIME -> R.string.extract_preset_time
        ExtractPreset.UUID -> R.string.extract_preset_uuid
        ExtractPreset.HEX_COLOR -> R.string.extract_preset_hex_color
        ExtractPreset.NUMBER -> R.string.extract_preset_number
        ExtractPreset.CHINESE -> R.string.extract_preset_chinese
    }
}

/**
 * 正则工具二级页：两种用法共用一个文本输入框。
 *
 * 「匹配」用来调正则并查看捕获组，「提取」用内置规则批量抓取网址、手机号等，
 * 两者互不干扰，切换标签不会清空已输入的内容。
 */
@Composable
fun RegexToolScreen(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }
    var pattern by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTabRow(
            tabs = listOf(
                stringResource(R.string.regex_tab_match),
                stringResource(R.string.regex_tab_extract)
            ),
            selectedTabIndex = tab,
            onTabSelected = { tab = it },
            modifier = Modifier.fillMaxWidth()
        )

        if (tab == 0) {
            MatchPane(
                pattern = pattern,
                onPatternChange = { pattern = it },
                text = text,
                onTextChange = { text = it }
            )
        } else {
            ExtractPane(text = text, onTextChange = { text = it })
        }
    }
}

@Composable
private fun MatchPane(
    pattern: String,
    onPatternChange: (String) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
) {
    val result = remember(pattern, text) { RegexTool.test(pattern, text) }

    AppTextField(
        value = pattern,
        onValueChange = onPatternChange,
        label = stringResource(R.string.regex_pattern_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 1
    )
    AppTextField(
        value = text,
        onValueChange = onTextChange,
        label = stringResource(R.string.regex_text_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 5
    )

    when {
        pattern.isBlank() -> Hint(stringResource(R.string.regex_empty_hint))
        result.patternError != null -> AppText(
            text = stringResource(R.string.regex_pattern_error),
            modifier = Modifier.padding(vertical = 8.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )
        !result.matched -> Hint(stringResource(R.string.regex_no_match))
        else -> {
            AppText(
                text = stringResource(R.string.regex_matches_count, result.count),
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 14.sp,
                color = AppTheme.colors.primary
            )
            result.groups.forEachIndexed { i, groups ->
                val formatted = groups.withIndex().joinToString("\n") { (g, value) ->
                    "#$g: $value"
                }
                MonospaceBlock(
                    text = "Match ${i + 1}\n$formatted",
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ExtractPane(text: String, onTextChange: (String) -> Unit) {
    var presetIndex by remember { mutableStateOf(0) }
    var distinct by remember { mutableStateOf(true) }
    val preset = ExtractPreset.entries[presetIndex]
    val result = remember(preset, text, distinct) {
        RegexExtractor.extract(preset, text, distinct)
    }

    AppTextField(
        value = text,
        onValueChange = onTextChange,
        label = stringResource(R.string.regex_text_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 5
    )
    AppDropdown(
        title = stringResource(R.string.regex_extract_preset_label),
        items = extractPresetLabels.localizedLabels(),
        selectedIndex = presetIndex,
        onSelectedIndexChange = { presetIndex = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    AppSwitchRow(
        title = stringResource(R.string.regex_extract_distinct),
        summary = stringResource(R.string.regex_extract_distinct_summary),
        checked = distinct,
        onCheckedChange = { distinct = it },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )

    when {
        text.isBlank() -> Hint(stringResource(R.string.regex_extract_empty_hint))
        result.patternError != null -> AppText(
            text = stringResource(R.string.regex_pattern_error),
            modifier = Modifier.padding(vertical = 8.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )
        result.count == 0 -> Hint(stringResource(R.string.regex_extract_no_result))
        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = stringResource(R.string.regex_extract_count, result.count),
                    fontSize = 14.sp,
                    color = AppTheme.colors.primary
                )
            }
            // 每行一条，方便长按逐条复制
            MonospaceBlock(
                text = result.values.joinToString("\n"),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                selectable = true
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        fontSize = 13.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}
