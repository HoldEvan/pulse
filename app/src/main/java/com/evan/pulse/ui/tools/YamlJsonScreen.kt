package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.tools.YamlJsonTool
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage

private enum class YamlJsonMode { YAML_TO_JSON, JSON_TO_YAML }

/**
 * YAML 与 JSON 互转二级页：输入即转换，两个方向共用同一个输入框。
 *
 * 切换方向不会清空已输入的内容，方便把一份配置来回转换做校验。
 */
@Composable
fun YamlJsonScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(YamlJsonMode.YAML_TO_JSON) }

    val result = remember(input, mode) {
        when (mode) {
            YamlJsonMode.YAML_TO_JSON -> YamlJsonTool.yamlToJson(input)
            YamlJsonMode.JSON_TO_YAML -> YamlJsonTool.jsonToYaml(input)
        }
    }

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
                stringResource(R.string.yaml_json_tab_to_json),
                stringResource(R.string.yaml_json_tab_to_yaml)
            ),
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = YamlJsonMode.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        CardSectionTitle(
            text = stringResource(
                when (mode) {
                    YamlJsonMode.YAML_TO_JSON -> R.string.yaml_json_input_yaml
                    YamlJsonMode.JSON_TO_YAML -> R.string.yaml_json_input_json
                }
            ),
            modifier = Modifier.padding(top = 12.dp)
        )
        CodeEditorField(
            text = input,
            onValueChange = { input = it },
            language = if (mode == YamlJsonMode.YAML_TO_JSON) CodeLanguage.YAML else CodeLanguage.JSON,
            dark = AppTheme.colors.background.luminance() < 0.5f,
            modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(text = stringResource(R.string.yaml_json_output_label))
            CopyIconButton(
                text = result.text,
                label = stringResource(R.string.action_copy)
            )
        }
        when {
            result.error != null -> AppText(
                text = yamlJsonErrorText(result.error),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
            result.text.isBlank() -> AppText(
                text = stringResource(R.string.yaml_json_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            else -> MonospaceBlock(text = result.text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun yamlJsonErrorText(error: String?): String = stringResource(
    when (error) {
        "empty" -> R.string.yaml_json_error_empty
        "yamlInvalid" -> R.string.yaml_json_error_yaml
        "jsonInvalid" -> R.string.yaml_json_error_json
        "depth" -> R.string.yaml_json_error_depth
        else -> R.string.yaml_json_error_generic
    }
)
