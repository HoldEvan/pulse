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
import com.evan.pulse.domain.tools.CronParser
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/** cron 解析工具二级页：按由近及远的顺序展示未来十次触发时间 */
@Composable
fun CronToolScreen(modifier: Modifier = Modifier) {
    var expression by remember { mutableStateOf("") }
    val result = remember(expression) { CronParser.parse(expression) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTextField(
            value = expression,
            onValueChange = { expression = it },
            label = stringResource(R.string.cron_input_label),
            modifier = Modifier.fillMaxWidth()
        )
        AppText(
            text = stringResource(R.string.cron_hint),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.outline
        )

        when {
            expression.isBlank() -> AppText(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            !result.valid -> AppText(
                text = stringResource(R.string.cron_invalid),
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardSectionTitle(
                        text = stringResource(R.string.cron_next_label),
                        modifier = Modifier.weight(1f)
                    )
                    CopyIconButton(text = result.runTimesText, label = stringResource(R.string.action_copy))
                }
                MonospaceBlock(text = result.runTimesText, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
