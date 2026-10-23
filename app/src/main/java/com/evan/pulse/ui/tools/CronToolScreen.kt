package com.evan.pulse.ui.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.evan.pulse.domain.tools.CronBuilder
import com.evan.pulse.domain.tools.CronField
import com.evan.pulse.domain.tools.CronParser
import com.evan.pulse.domain.tools.CronPreset
import com.evan.pulse.domain.tools.CronType
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

private enum class CronMode { PARSE, GENERATE }

/** cron 工具二级页：可解析表达式，也可按方言逐字段生成表达式 */
@Composable
fun CronToolScreen(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(CronMode.PARSE) }

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
                stringResource(R.string.cron_mode_parse),
                stringResource(R.string.cron_mode_generate)
            ),
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = CronMode.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )
        when (mode) {
            CronMode.PARSE -> CronParseSection()
            CronMode.GENERATE -> CronGenerateSection()
        }
    }
}

@Composable
private fun CronParseSection() {
    var type by remember { mutableStateOf(CronType.LINUX) }
    var expression by remember { mutableStateOf("") }
    val result = remember(expression, type) { CronParser.parse(expression, type) }

    CronTypeTabs(type) { type = it }

    AppTextField(
        value = expression,
        onValueChange = { expression = it },
        label = stringResource(R.string.cron_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )
    AppText(
        text = stringResource(
            when (type) {
                CronType.LINUX -> R.string.cron_hint_linux
                CronType.SPRING -> R.string.cron_hint_spring
                CronType.QUARTZ -> R.string.cron_hint_quartz
            }
        ),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = AppTheme.colors.onSurfaceSummary
    )

    when {
        expression.isBlank() -> AppText(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 8.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )

        !result.valid -> AppText(
            text = cronErrorText(result.error),
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
                CopyIconButton(
                    text = result.runTimesText,
                    label = stringResource(R.string.action_copy)
                )
            }
            MonospaceBlock(text = result.runTimesText, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CronGenerateSection() {
    var type by remember { mutableStateOf(CronType.LINUX) }
    var values by remember(type) { mutableStateOf(CronBuilder.defaults(type)) }
    val fields = CronBuilder.fields(type)
    val expression = CronBuilder.build(type, values)
    val result = remember(expression, type) { CronParser.parse(expression, type) }

    CronTypeTabs(type) {
        type = it
        values = CronBuilder.defaults(it)
    }

    CardSectionTitle(
        text = stringResource(R.string.cron_generate_preset),
        modifier = Modifier.padding(top = 14.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CronPreset.entries.forEach { preset ->
            AppTextButton(
                text = stringResource(presetLabelRes(preset)),
                onClick = { values = CronBuilder.preset(type, preset) }
            )
        }
    }

    fields.forEachIndexed { index, field ->
        AppTextField(
            value = values.getOrNull(index).orEmpty(),
            onValueChange = { input ->
                values = values.toMutableList().also { list ->
                    while (list.size <= index) list.add("")
                    list[index] = input
                }
            },
            label = fieldLabel(type, field),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(
            text = stringResource(R.string.cron_generate_expression),
            modifier = Modifier.weight(1f)
        )
        CopyIconButton(text = expression, label = stringResource(R.string.action_copy))
    }
    MonospaceBlock(text = expression, modifier = Modifier.padding(top = 6.dp))

    if (!result.valid) {
        AppText(
            text = cronErrorText(result.error),
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )
    } else {
        CardSectionTitle(
            text = stringResource(R.string.cron_next_label),
            modifier = Modifier.padding(top = 14.dp)
        )
        MonospaceBlock(text = result.runTimesText, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CronTypeTabs(selected: CronType, onSelect: (CronType) -> Unit) {
    AppTabRow(
        tabs = listOf(
            stringResource(R.string.cron_type_linux),
            stringResource(R.string.cron_type_spring),
            stringResource(R.string.cron_type_quartz)
        ),
        selectedTabIndex = selected.ordinal,
        onTabSelected = { onSelect(CronType.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        scrollable = true
    )
}

private fun presetLabelRes(preset: CronPreset): Int = when (preset) {
    CronPreset.EVERY_MINUTE -> R.string.cron_preset_every_minute
    CronPreset.EVERY_5_MINUTES -> R.string.cron_preset_every_5_minutes
    CronPreset.HOURLY -> R.string.cron_preset_hourly
    CronPreset.DAILY_MIDNIGHT -> R.string.cron_preset_daily_midnight
    CronPreset.DAILY_9AM -> R.string.cron_preset_daily_9am
    CronPreset.WEEKLY_MONDAY_9AM -> R.string.cron_preset_weekly_monday
    CronPreset.MONTHLY_FIRST -> R.string.cron_preset_monthly_first
    CronPreset.YEARLY -> R.string.cron_preset_yearly
}

@Composable
private fun fieldLabel(type: CronType, field: CronField): String = when (field) {
    CronField.SECOND -> stringResource(R.string.cron_field_second)
    CronField.MINUTE -> stringResource(R.string.cron_field_minute)
    CronField.HOUR -> stringResource(R.string.cron_field_hour)
    CronField.DAY_OF_MONTH -> stringResource(R.string.cron_field_day)
    CronField.MONTH -> stringResource(R.string.cron_field_month)
    CronField.DAY_OF_WEEK -> stringResource(
        if (type == CronType.QUARTZ) R.string.cron_field_week_quartz
        else R.string.cron_field_week
    )

    CronField.YEAR -> stringResource(R.string.cron_field_year)
}

@Composable
private fun cronErrorText(error: String?): String = stringResource(
    when (error) {
        "fields" -> R.string.cron_error_fields
        "second" -> R.string.cron_error_second
        "minute" -> R.string.cron_error_minute
        "hour" -> R.string.cron_error_hour
        "day" -> R.string.cron_error_day
        "month" -> R.string.cron_error_month
        "week" -> R.string.cron_error_week
        "year" -> R.string.cron_error_year
        "dayConflict" -> R.string.cron_error_day_conflict
        "range" -> R.string.cron_error_range
        "unsupported" -> R.string.cron_error_unsupported
        else -> R.string.cron_error_generic
    }
)
