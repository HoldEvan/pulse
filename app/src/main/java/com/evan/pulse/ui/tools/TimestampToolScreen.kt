package com.evan.pulse.ui.tools

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
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.MonospaceBlock
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TimestampUnit { SECONDS, MILLIS }

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

@Composable
fun TimestampToolScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(TimestampUnit.SECONDS) }

    val millis = parseMillis(input, unit)
    val valid = input.isBlank() || millis != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeToggle(
                label = stringResource(R.string.timestamp_tool_seconds),
                selected = unit == TimestampUnit.SECONDS,
                onClick = { unit = TimestampUnit.SECONDS },
                modifier = Modifier.weight(1f)
            )
            ModeToggle(
                label = stringResource(R.string.timestamp_tool_millis),
                selected = unit == TimestampUnit.MILLIS,
                onClick = { unit = TimestampUnit.MILLIS },
                modifier = Modifier.weight(1f)
            )
        }

        TextField(
            value = input,
            onValueChange = { input = it },
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
                input = if (unit == TimestampUnit.SECONDS) (now / 1000).toString() else now.toString()
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
            MonospaceBlock(
                text = "UTC:    ${formatMillis(millis!!, ZoneId.of("UTC"))}\n" +
                    "本地:    ${formatMillis(millis)}",
                modifier = Modifier.padding(top = 6.dp)
            )
        }
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

private fun formatMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    displayFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

@Composable
private fun ModeToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surface
        )
    ) {
        TextButton(
            text = label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
    }
}
