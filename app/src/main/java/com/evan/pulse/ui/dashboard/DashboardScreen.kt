package com.evan.pulse.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.roundedPressable
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 首页：统计概览 + 工具箱。统计卡来自各内存 Store，工具入口打开对应二级页 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenBase64: () -> Unit = {},
    onOpenJson: () -> Unit = {},
    onOpenTimestamp: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        CardSectionTitle(text = stringResource(R.string.dashboard_stats_title))
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_stat_projects),
                value = stats.projectCount,
                icon = { Icon(MiuixIcons.Light.Folder, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_stat_history),
                value = stats.historyCount,
                icon = { Icon(MiuixIcons.Light.Recent, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_stat_requests),
                value = stats.savedRequestCount,
                icon = { Icon(MiuixIcons.Light.Notes, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_stat_monitors),
                value = stats.monitorTargetCount,
                icon = { Icon(MiuixIcons.Light.Stopwatch, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
        }

        CardSectionTitle(text = stringResource(R.string.dashboard_tools_title))
        ToolEntryCard(
            title = stringResource(R.string.tool_base64),
            onClick = onOpenBase64,
            icon = { Icon(MiuixIcons.Light.ConvertFile, contentDescription = null) }
        )
        ToolEntryCard(
            title = stringResource(R.string.tool_json),
            onClick = onOpenJson,
            icon = { Icon(MiuixIcons.Light.Notes, contentDescription = null) }
        )
        ToolEntryCard(
            title = stringResource(R.string.tool_timestamp),
            onClick = onOpenTimestamp,
            icon = { Icon(MiuixIcons.Light.Stopwatch, contentDescription = null) }
        )

        BottomBarClearance()
    }
}

@Composable
private fun StatCard(
    title: String,
    value: Int,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            icon()
            Text(
                text = value.toString(),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 26.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .roundedPressable(cornerRadius = 16.dp, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = title,
                modifier = Modifier.padding(start = 10.dp),
                fontSize = 14.sp
            )
        }
    }
}