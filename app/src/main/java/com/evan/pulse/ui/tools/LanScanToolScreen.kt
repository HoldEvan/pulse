package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.domain.tools.LanHost
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.design.AppButton
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppSmallTitle
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme

/** 局域网扫描工具二级页：检测网段，并发探测在线主机并实时列出 */
@Composable
fun LanScanToolScreen(
    modifier: Modifier = Modifier,
    viewModel: LanScanViewModel = viewModel()
) {
    val info = viewModel.networkInfo
    val hosts = viewModel.hosts
    val scanning = viewModel.scanning
    var selectedHost by remember { mutableStateOf<LanHost?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppText(
                    text = stringResource(R.string.lan_scan_network_label),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
                if (info == null) {
                    AppText(
                        text = stringResource(R.string.lan_scan_no_lan),
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 13.sp,
                        color = AppTheme.colors.error
                    )
                } else {
                    AppText(
                        text = "${info.ip}/${info.prefixLength} · ${info.hosts.size} ${stringResource(R.string.lan_scan_hosts_count)}",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 15.sp
                    )
                }
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        onClick = { if (scanning) viewModel.stop() else viewModel.start() },
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText(stringResource(if (scanning) R.string.lan_scan_stop else R.string.lan_scan_start))
                    }
                }
                if (scanning) {
                    AppText(
                        text = stringResource(R.string.lan_scan_progress, viewModel.progress, viewModel.total),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 13.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
        }

        AppSmallTitle(text = stringResource(R.string.lan_scan_title))
        AppCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (hosts.isEmpty()) {
                    item {
                        AppText(
                            text = stringResource(R.string.lan_scan_empty_hint),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontSize = 13.sp,
                            color = AppTheme.colors.onSurfaceSummary
                        )
                    }
                } else {
                    items(hosts, key = { it.ip }) { host ->
                        HostRow(host, onClick = { selectedHost = host })
                    }
                }
            }
        }
    }

    selectedHost?.let { host ->
        AppDialog(
            show = true,
            title = stringResource(R.string.lan_scan_add_monitor_title),
            onDismissRequest = { selectedHost = null }
        ) {
            Column {
                AppText(stringResource(R.string.lan_scan_add_monitor_confirm, host.ip))
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { selectedHost = null },
                        modifier = Modifier.weight(1f)
                    )
                    AppTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            viewModel.addToMonitor(host)
                            selectedHost = null
                        },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HostRow(host: LanHost, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = host.ip, fontSize = 15.sp)
            val detail = buildString {
                host.hostname?.let {
                    append(it)
                    append(" · ")
                }
                append(host.probe)
                append(" · ")
                append(host.latencyMs)
                append(" ms")
            }
            AppText(
                text = detail,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        CopyIconButton(text = host.ip, label = stringResource(R.string.action_copy))
    }
}
