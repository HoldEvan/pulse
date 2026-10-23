package com.evan.pulse.ui.tools

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class TimestampDirection { TS_TO_DATE, DATE_TO_TS, FORMAT }

private enum class TimestampUnit { SECONDS, MILLIS }

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

private val dateTimeInputFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
)

/** 时间格式化页的默认格式 */
private const val DEFAULT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

/** 格式说明：模式字母与含义，按常用顺序排列 */
private val formatLegendTokens = listOf(
    "yyyy" to R.string.timestamp_fmt_legend_year,
    "MM" to R.string.timestamp_fmt_legend_month,
    "dd" to R.string.timestamp_fmt_legend_day,
    "HH" to R.string.timestamp_fmt_legend_hour24,
    "mm" to R.string.timestamp_fmt_legend_minute,
    "ss" to R.string.timestamp_fmt_legend_second,
    "SSS" to R.string.timestamp_fmt_legend_millis,
    "E" to R.string.timestamp_fmt_legend_weekday,
    "a" to R.string.timestamp_fmt_legend_ampm,
    "hh" to R.string.timestamp_fmt_legend_hour12,
    "Z" to R.string.timestamp_fmt_legend_zone
)

@Composable
fun TimestampToolScreen(modifier: Modifier = Modifier) {
    var direction by remember { mutableStateOf(TimestampDirection.TS_TO_DATE) }
    var input by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(TimestampUnit.SECONDS) }
    var formatInput by remember { mutableStateOf(formatMillis(System.currentTimeMillis())) }
    var formatPattern by remember { mutableStateOf(DEFAULT_TIME_PATTERN) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val directionLabels = listOf(
            stringResource(R.string.timestamp_tool_dir_ts_date),
            stringResource(R.string.timestamp_tool_dir_date_ts),
            stringResource(R.string.timestamp_tool_dir_format)
        )
        AppTabRow(
            tabs = directionLabels,
            selectedTabIndex = direction.ordinal,
            onTabSelected = {
                if (direction != TimestampDirection.entries[it]) {
                    direction = TimestampDirection.entries[it]
                    input = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            scrollable = true
        )

        when (direction) {
            TimestampDirection.TS_TO_DATE -> TimestampToDateSection(
                input = input,
                unit = unit,
                onInputChange = { input = it },
                onUnitChange = { unit = it }
            )

            TimestampDirection.DATE_TO_TS -> DateToTimestampSection(
                input = input,
                onInputChange = { input = it }
            )

            TimestampDirection.FORMAT -> TimeFormatSection(
                input = formatInput,
                pattern = formatPattern,
                onInputChange = { formatInput = it },
                onPatternChange = { formatPattern = it }
            )
        }
    }
}

@Composable
private fun TimestampToDateSection(
    input: String,
    unit: TimestampUnit,
    onInputChange: (String) -> Unit,
    onUnitChange: (TimestampUnit) -> Unit
) {
    val millis = parseMillis(input, unit)
    val valid = input.isBlank() || millis != null
    val copy = rememberCopyToClipboard()

    val modeLabels = listOf(
        stringResource(R.string.timestamp_tool_seconds),
        stringResource(R.string.timestamp_tool_millis)
    )
    AppTabRow(
        tabs = modeLabels,
        selectedTabIndex = unit.ordinal,
        onTabSelected = { onUnitChange(TimestampUnit.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = input,
            onValueChange = onInputChange,
            label = stringResource(R.string.timestamp_tool_input_label),
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            text = stringResource(R.string.timestamp_tool_now),
            onClick = {
                val now = System.currentTimeMillis()
                onInputChange(
                    if (unit == TimestampUnit.SECONDS) (now / 1000).toString() else now.toString()
                )
            },
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    AppText(
        text = stringResource(R.string.timestamp_tool_now_tip),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = AppTheme.colors.onSurfaceSummary
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
        CopyIconButton(
            text = millis?.let { formatMillis(it) } ?: "",
            label = stringResource(R.string.action_copy)
        )
    }
    if (!valid) {
        AppText(
            text = stringResource(R.string.timestamp_tool_invalid),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )
    } else if (input.isBlank()) {
        AppText(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    } else {
        val utcLabel = stringResource(R.string.timestamp_tool_utc)
        val localLabel = stringResource(R.string.timestamp_tool_local)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TimestampFieldRow(
                label = utcLabel,
                value = formatMillis(millis!!, ZoneId.of("UTC")),
                copy = copy
            )
            TimestampFieldRow(
                label = localLabel,
                value = formatMillis(millis),
                copy = copy
            )
        }
        AppText(
            text = stringResource(R.string.timestamp_tool_long_press_tip),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

@Composable
private fun DateToTimestampSection(
    input: String,
    onInputChange: (String) -> Unit
) {
    val millis = parseDateToMillis(input)
    val valid = input.isBlank() || millis != null
    val copy = rememberCopyToClipboard()

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = input,
            onValueChange = onInputChange,
            label = stringResource(R.string.timestamp_tool_date_input_label),
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            text = stringResource(R.string.timestamp_tool_now),
            onClick = { onInputChange(formatMillis(System.currentTimeMillis())) },
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    AppText(
        text = stringResource(R.string.timestamp_tool_date_hint),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = AppTheme.colors.onSurfaceSummary
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
        CopyIconButton(
            text = if (millis != null) "${millis / 1000}\n$millis" else "",
            label = stringResource(R.string.action_copy)
        )
    }
    if (!valid) {
        AppText(
            text = stringResource(R.string.timestamp_tool_invalid),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )
    } else if (input.isBlank()) {
        AppText(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    } else {
        val secLabel = stringResource(R.string.timestamp_tool_out_sec)
        val millisLabel = stringResource(R.string.timestamp_tool_out_millis)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TimestampFieldRow(
                label = secLabel,
                value = (millis!! / 1000).toString(),
                copy = copy
            )
            TimestampFieldRow(
                label = millisLabel,
                value = millis.toString(),
                copy = copy
            )
        }
        AppText(
            text = stringResource(R.string.timestamp_tool_long_press_tip),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

/** 时间格式化：时间 + 格式两行输入，下方给出模式字母说明 */
@Composable
private fun TimeFormatSection(
    input: String,
    pattern: String,
    onInputChange: (String) -> Unit,
    onPatternChange: (String) -> Unit
) {
    val copy = rememberCopyToClipboard()
    val formatter = remember(pattern) { patternFormatter(pattern) }
    val millis = parseTimeInput(input, pattern)
    val formatted = millis
        ?.takeIf { formatter != null }
        ?.let { formatter!!.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = input,
            onValueChange = onInputChange,
            label = stringResource(R.string.timestamp_fmt_input_label),
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            text = stringResource(R.string.timestamp_tool_now),
            onClick = { onInputChange(formatMillis(System.currentTimeMillis())) },
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    AppTextField(
        value = pattern,
        onValueChange = onPatternChange,
        label = stringResource(R.string.timestamp_fmt_pattern_label),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.timestamp_fmt_output))
        CopyIconButton(
            text = formatted.orEmpty(),
            label = stringResource(R.string.action_copy)
        )
    }
    when {
        formatter == null -> AppText(
            text = stringResource(R.string.timestamp_fmt_invalid_pattern),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )

        input.isBlank() -> AppText(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )

        millis == null -> AppText(
            text = stringResource(R.string.timestamp_fmt_invalid_time),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.error
        )

        else -> {
            TimestampFieldRow(
                label = pattern,
                value = formatted.orEmpty(),
                copy = copy
            )
            AppText(
                text = stringResource(R.string.timestamp_tool_long_press_tip),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
    }

    CardSectionTitle(
        text = stringResource(R.string.timestamp_fmt_legend_title),
        modifier = Modifier.padding(top = 16.dp)
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        formatLegendTokens.forEach { (token, descriptionRes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = token,
                    modifier = Modifier.widthIn(min = 46.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurface
                )
                AppText(
                    text = stringResource(descriptionRes),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
        }
    }
    AppText(
        text = stringResource(R.string.timestamp_fmt_quote_tip),
        modifier = Modifier.padding(top = 8.dp),
        fontSize = 12.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}

/** 单项结果行：长按复制该值，底色与 MonospaceBlock 保持一致 */
@Composable
private fun TimestampFieldRow(label: String, value: String, copy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .squircleClip(8.dp)
            .combinedClickable(onClick = {}, onLongClick = { copy(value) })
            .squircleBackground(AppTheme.colors.surfaceVariant, 8.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppText(
            text = label,
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        AppText(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = AppTheme.colors.onSurface
        )
    }
}

private fun parseMillis(input: String, unit: TimestampUnit): Long? {
    if (input.isBlank()) return null
    val raw = input.trim().toLongOrNull() ?: return null
    return when (unit) {
        TimestampUnit.SECONDS -> raw * 1000
        TimestampUnit.MILLIS -> raw
    }
}

/** 按给定格式解析时间；格式不适用时回退到内置的几种常见格式 */
private fun parseTimeInput(input: String, pattern: String): Long? {
    if (input.isBlank()) return null
    val text = input.trim()
    val zone = ZoneId.systemDefault()
    val formatter = patternFormatter(pattern)
    if (formatter != null) {
        runCatching { LocalDateTime.parse(text, formatter) }.getOrNull()?.let {
            return it.atZone(zone).toInstant().toEpochMilli()
        }
        runCatching { LocalDate.parse(text, formatter) }.getOrNull()?.let {
            return it.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
    return parseDateToMillis(text)
}

/** 解析用户输入的格式串，非法返回 null */
private fun patternFormatter(pattern: String): DateTimeFormatter? {
    if (pattern.isBlank()) return null
    return try {
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun parseDateToMillis(input: String): Long? {
    if (input.isBlank()) return null
    val text = input.trim()
    for (formatter in dateTimeInputFormatters) {
        try {
            return LocalDateTime.parse(text, formatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }
    return null
}

private fun formatMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    displayFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))
