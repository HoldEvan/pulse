package com.evan.pulse.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppArrowRow
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppPreferenceGroup
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Scan

/** 首页：统计概览 + 工具箱。统计卡来自各内存 Store，工具入口打开对应二级页 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenEncoding: () -> Unit = {},
    onOpenImageBase64: () -> Unit = {},
    onOpenJson: () -> Unit = {},
    onOpenJsonSchema: () -> Unit = {},
    onOpenYamlJson: () -> Unit = {},
    onOpenUrlParser: () -> Unit = {},
    onOpenTimestamp: () -> Unit = {},
    onOpenJwt: () -> Unit = {},
    onOpenHash: () -> Unit = {},
    onOpenUuid: () -> Unit = {},
    onOpenRegex: () -> Unit = {},
    onOpenCron: () -> Unit = {},
    onOpenLanScan: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
    onOpenMonitors: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
    ) {
        CardSectionTitle(text = stringResource(R.string.dashboard_stats_title))
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_stat_projects),
                value = stats.projectCount,
                icon = { AppIcon(MiuixIcons.Light.Folder, contentDescription = null) },
                onClick = onOpenProjects,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_stat_history),
                value = stats.historyCount,
                icon = { AppIcon(MiuixIcons.Light.Recent, contentDescription = null) },
                onClick = onOpenHistory,
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
                icon = { AppIcon(MiuixIcons.Light.Notes, contentDescription = null) },
                onClick = onOpenRequests,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_stat_monitors),
                value = stats.monitorTargetCount,
                icon = { AppIcon(MiuixIcons.Light.Stopwatch, contentDescription = null) },
                onClick = onOpenMonitors,
                modifier = Modifier.weight(1f)
            )
        }

        CardSectionTitle(
            text = stringResource(R.string.dashboard_tools_title),
            modifier = Modifier.padding(top = 12.dp)
        )
        AppPreferenceGroup(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_encoding),
                    summary = stringResource(R.string.tool_encoding_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.ConvertFile,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenEncoding
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_image_base64),
                    summary = stringResource(R.string.tool_image_base64_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Image,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenImageBase64
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_json),
                    summary = stringResource(R.string.tool_json_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Notes,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenJson
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_json_schema),
                    summary = stringResource(R.string.tool_json_schema_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Ok,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenJsonSchema
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_yaml_json),
                    summary = stringResource(R.string.tool_yaml_json_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.ConvertFile,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenYamlJson
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_url_parser),
                    summary = stringResource(R.string.tool_url_parser_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Link,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenUrlParser
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_timestamp),
                    summary = stringResource(R.string.tool_timestamp_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Stopwatch,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenTimestamp
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_jwt),
                    summary = stringResource(R.string.tool_jwt_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Lock,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenJwt
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_hash),
                    summary = stringResource(R.string.tool_hash_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Tune,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenHash
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_uuid),
                    summary = stringResource(R.string.tool_uuid_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Create,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenUuid
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_regex),
                    summary = stringResource(R.string.tool_regex_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Search,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenRegex
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_cron),
                    summary = stringResource(R.string.tool_cron_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Timer,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenCron
                )
            }
            item {
                AppArrowRow(
                    title = stringResource(R.string.tool_lan_scan),
                    summary = stringResource(R.string.tool_lan_scan_summary),
                    startAction = {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Scan,
                            contentDescription = null
                        )
                    },
                    onClick = onOpenLanScan
                )
            }
        }

        BottomBarClearance()
    }
}

@Composable
private fun StatCard(
    title: String,
    value: Int,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = onClick != null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            icon()
            AppText(
                text = value.toString(),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 26.sp,
                color = AppTheme.colors.onSurface
            )
            AppText(
                text = title,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
    }
}
