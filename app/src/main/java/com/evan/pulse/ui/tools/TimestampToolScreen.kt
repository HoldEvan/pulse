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
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class TimestampDirection { TS_TO_DATE, DATE_TO_TS }

private enum class TimestampUnit { SECONDS, MILLIS }

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

private val dateTimeInputFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
)

@Composable
fun TimestampToolScreen(modifier: Modifier = Modifier) {
    var direction by remember { mutableStateOf(TimestampDirection.TS_TO_DATE) }
    var input by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(TimestampUnit.SECONDS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val directionLabels = listOf(
            stringResource(R.string.timestamp_tool_dir_ts_date),
            stringResource(R.string.timestamp_tool_dir_date_ts)
        )
        TabRowWithContour(
            tabs = directionLabels,
            selectedTabIndex = direction.ordinal,
            onTabSelected = {
                if (direction != TimestampDirection.entries[it]) {
                    direction = TimestampDirection.entries[it]
                    input = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (direction == TimestampDirection.TS_TO_DATE) {
            TimestampToDateSection(
                input = input,
                unit = unit,
                onInputChange = { input = it },
                onUnitChange = { unit = it }
            )
        } else {
            DateToTimestampSection(
                input = input,
                onInputChange = { input = it }
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
    TabRowWithContour(
        tabs = modeLabels,
        selectedTabIndex = unit.ordinal,
        onTabSelected = { onUnitChange(TimestampUnit.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    TextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.timestamp_tool_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    Text(
        text = stringResource(R.string.timestamp_tool_now_tip),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    TextButton(
        text = stringResource(R.string.timestamp_tool_now),
        onClick = {
            val now = System.currentTimeMillis()
            onInputChange(
                if (unit == TimestampUnit.SECONDS) (now / 1000).toString() else now.toString()
            )
        },
        modifier = Modifier.padding(top = 2.dp)
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
        Text(
            text = stringResource(R.string.timestamp_tool_invalid),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.error
        )
    } else if (input.isBlank()) {
        Text(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
        Text(
            text = stringResource(R.string.timestamp_tool_long_press_tip),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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

    TextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.timestamp_tool_date_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )

    Text(
        text = stringResource(R.string.timestamp_tool_date_hint),
        modifier = Modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    TextButton(
        text = stringResource(R.string.timestamp_tool_now),
        onClick = { onInputChange(formatMillis(System.currentTimeMillis())) },
        modifier = Modifier.padding(top = 2.dp)
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
        Text(
            text = stringResource(R.string.timestamp_tool_invalid),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.error
        )
    } else if (input.isBlank()) {
        Text(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
        Text(
            text = stringResource(R.string.timestamp_tool_long_press_tip),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 单项结果行：长按复制该值，底色与 MonospaceBlock 保持一致 */
@Composable
private fun TimestampFieldRow(label: String, value: String, copy: (String) -> Unit) {
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

private fun parseMillis(input: String, unit: TimestampUnit): Long? {
    if (input.isBlank()) return null
    val raw = input.trim().toLongOrNull() ?: return null
    return when (unit) {
        TimestampUnit.SECONDS -> raw * 1000
        TimestampUnit.MILLIS -> raw
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
