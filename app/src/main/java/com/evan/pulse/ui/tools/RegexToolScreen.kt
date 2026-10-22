package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.tools.RegexTool
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/** 正则测试工具二级页：输入正则与文本，显示是否匹配及各捕获组 */
@Composable
fun RegexToolScreen(modifier: Modifier = Modifier) {
    var pattern by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val result = remember(pattern, text) { RegexTool.test(pattern, text) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = stringResource(R.string.regex_pattern_label),
            modifier = Modifier.fillMaxWidth(),
            minLines = 1
        )
        AppTextField(
            value = text,
            onValueChange = { text = it },
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