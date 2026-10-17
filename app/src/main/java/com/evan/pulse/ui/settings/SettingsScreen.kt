package com.evan.pulse.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.BuildConfig
import com.evan.pulse.R
import com.evan.pulse.data.backup.RemoteBackupEntry
import com.evan.pulse.data.backup.WebDavStage
import com.evan.pulse.data.cache.CacheCleaner
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.SectionTitle
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LanguageLabels: List<Int> = listOf(
    R.string.lang_system,
    R.string.lang_zh_cn,
    R.string.lang_zh_tw,
    R.string.lang_en
)

private val backupDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

// 页面内间距统一走这三个值，避免各区块之间的间距和卡片内容内边距不一致
private val SectionTopGap = 12.dp
private val CardGap = 8.dp
private val CardContentPadding = 12.dp

@Composable
private fun RemoteBackupItem(
    entry: RemoteBackupEntry,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(CardContentPadding)) {
            Text(entry.display)
            Text(
                text = CacheCleaner.formatBytes(entry.size) + " · " + backupDateFormat.format(entry.lastModified),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.backup_restore),
                    onClick = onRestore,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.backup_delete),
                    onClick = onDelete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WebDavTestResultView(state: WebDavTestState) {
    if (!state.busy && state.result == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (state.busy) {
            Text(
                text = stringResource(R.string.webdav_testing),
                color = Color(0xFF8A8F98),
                fontSize = 13.sp
            )
        } else {
            state.result?.let { result ->
                Text(
                    text = if (result.ok) {
                        stringResource(R.string.webdav_test_passed, result.elapsedMs)
                    } else {
                        stringResource(R.string.webdav_test_failed, result.elapsedMs)
                    },
                    color = if (result.ok) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    fontSize = 13.sp
                )
                WebDavStageLine(
                    label = stringResource(R.string.webdav_stage_probe),
                    detail = probeDetail(result.probe),
                    ok = result.probe.ok
                )
                result.write?.let { write ->
                    WebDavStageLine(
                        label = stringResource(R.string.webdav_stage_write),
                        detail = if (write.ok) {
                            stringResource(R.string.webdav_write_ok)
                        } else {
                            write.message.ifBlank { stringResource(R.string.webdav_write_failed) }
                        },
                        ok = write.ok
                    )
                }
            }
        }
    }
}

@Composable
private fun WebDavStageLine(label: String, detail: String, ok: Boolean) {
    Text(
        text = stringResource(R.string.webdav_stage_line, label, detail),
        color = if (ok) Color(0xFF2E7D32) else Color(0xFFD32F2F),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun probeDetail(stage: WebDavStage): String = when {
    stage.ok -> stringResource(R.string.webdav_probe_ok)
    stage.statusCode == null -> stage.message.ifBlank { stringResource(R.string.webdav_probe_unreachable) }
    stage.statusCode == 401 -> stringResource(R.string.webdav_probe_auth_failed)
    stage.statusCode == 403 -> stringResource(R.string.webdav_probe_forbidden)
    stage.statusCode == 404 -> stringResource(R.string.webdav_probe_ok) // 目录不存在但服务器可达，后续自动创建
    stage.statusCode == 405 -> stringResource(R.string.webdav_probe_unsupported)
    else -> stringResource(R.string.webdav_probe_status, stage.statusCode)
}

private val ProxyTypeLabels: List<Int> = listOf(
    R.string.proxy_summary_none,
    R.string.proxy_summary_http,
    R.string.proxy_summary_socks5
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.backupStatus.collectAsStateWithLifecycle()
    val remoteBackups by viewModel.remoteBackups.collectAsStateWithLifecycle()
    val webDavTest by viewModel.webDavTest.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val cacheBusy by viewModel.cacheBusy.collectAsStateWithLifecycle()
    var showRemoteList by remember { mutableStateOf(false) }
    var showCacheConfirm by remember { mutableStateOf(false) }
    val permissionDeniedText = stringResource(R.string.monitor_permission_denied)
    var notifyPermissionGranted by remember { mutableStateOf(ServiceLocator.monitorNotifier.isGranted()) }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifyPermissionGranted = granted
        if (!granted) AppToast.show(permissionDeniedText)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current),
        state = subPageLazyListState()
    ) {
        item(key = "general_title") {
            SectionTitle(text = stringResource(R.string.settings_general))
        }

        item(key = "general_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_language),
                summary = stringResource(R.string.settings_language_summary),
                items = LanguageLabels.localizedLabels(),
                selectedIndex = settings.languageIndex,
                onSelectedIndexChange = { viewModel.setLanguage(it) }
            )

            ArrowPreference(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.appearance_summary),
                onClick = onOpenAppearance
            )

            SwitchPreference(
                checked = settings.monitorNotify,
                onCheckedChange = { value ->
                    if (value &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ServiceLocator.monitorNotifier.isGranted()
                    ) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.setMonitorNotify(value)
                },
                title = stringResource(R.string.settings_monitor_notify),
                summary = if (settings.monitorNotify && !notifyPermissionGranted) {
                    permissionDeniedText
                } else {
                    stringResource(R.string.settings_monitor_notify_summary)
                }
            )
        }
        }

        item(key = "network_title") {
            SectionTitle(
                text = stringResource(R.string.settings_network),
                topPadding = SectionTopGap
            )
        }

        item(key = "network_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_proxy),
                summary = stringResource(
                    ProxyTypeLabels.getOrElse(settings.proxyTypeIndex) { R.string.proxy_summary_none }
                ),
                items = ProxyTypeLabels.localizedLabels(),
                selectedIndex = settings.proxyTypeIndex,
                onSelectedIndexChange = { viewModel.setProxyType(it) }
            )

            if (settings.proxyTypeIndex > 0) {
                var proxyAddress by remember {
                    mutableStateOf(
                        if (settings.proxyPort.isBlank()) {
                            settings.proxyHost
                        } else {
                            "${settings.proxyHost}:${settings.proxyPort}"
                        }
                    )
                }
                Column(modifier = Modifier.padding(CardContentPadding)) {
                    TextField(
                        value = proxyAddress,
                        onValueChange = { text ->
                            proxyAddress = text
                            val (host, port) = splitProxyAddress(text)
                            viewModel.setProxyHost(host)
                            viewModel.setProxyPort(port)
                        },
                        label = stringResource(R.string.proxy_host_label),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = settings.proxyUsername,
                            onValueChange = { viewModel.setProxyUsername(it) },
                            label = stringResource(R.string.username_label),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        TextField(
                            value = settings.proxyPassword,
                            onValueChange = { viewModel.setProxyPassword(it) },
                            label = stringResource(R.string.password_label),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        }

        item(key = "backup_title") {
            SectionTitle(
                text = stringResource(R.string.settings_backup),
                topPadding = SectionTopGap
            )
        }

        val webdavConfigured = viewModel.isWebdavConfigured(settings)

        item(key = "backup_local_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CardContentPadding)) {
                CardSectionTitle(
                    text = stringResource(R.string.backup_local_title),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportLocal() },
                        enabled = !status.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_export_local))
                    }
                    Button(
                        onClick = { viewModel.importLocal() },
                        enabled = !status.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_import_local))
                    }
                }
            }
        }
        }

        item(key = "backup_webdav_card") {
        Card(modifier = Modifier.fillMaxWidth().padding(top = CardGap)) {
            Column(modifier = Modifier.padding(CardContentPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardSectionTitle(
                        text = stringResource(R.string.backup_webdav_title),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (webdavConfigured) {
                            stringResource(R.string.backup_configured)
                        } else {
                            stringResource(R.string.backup_not_configured)
                        },
                        color = if (webdavConfigured) Color(0xFF2E7D32) else Color(0xFF8A8F98),
                        fontSize = 12.sp
                    )
                }
                TextField(
                    value = settings.webdavUrl,
                    onValueChange = { viewModel.setWebdavUrl(it) },
                    label = stringResource(R.string.webdav_url_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
                TextField(
                    value = settings.webdavPath,
                    onValueChange = { viewModel.setWebdavPath(it) },
                    label = stringResource(R.string.webdav_path_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = settings.webdavUsername,
                        onValueChange = { viewModel.setWebdavUsername(it) },
                        label = stringResource(R.string.username_label),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = settings.webdavPassword,
                        onValueChange = { viewModel.setWebdavPassword(it) },
                        label = stringResource(R.string.password_label),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(
                    text = stringResource(R.string.webdav_test_connection),
                    onClick = { viewModel.testWebDav(settings) },
                    enabled = !status.busy && !webDavTest.busy && webdavConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                WebDavTestResultView(webDavTest)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportToWebDav(settings) },
                        enabled = !status.busy && webdavConfigured,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_export_webdav))
                    }
                    TextButton(
                        text = stringResource(R.string.backup_browse_remote),
                        onClick = {
                            showRemoteList = true
                            viewModel.refreshRemoteBackups(settings)
                        },
                        enabled = !status.busy && webdavConfigured,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (status.busy || status.message != null) {
            Text(
                text = if (status.busy) {
                    stringResource(R.string.backup_busy)
                } else if (status.success) {
                    status.message ?: stringResource(R.string.backup_done)
                } else {
                    status.message?.let { msg ->
                        if (msg.isBlank()) stringResource(R.string.backup_failed)
                        else stringResource(R.string.backup_failed_with_error, msg)
                    } ?: stringResource(R.string.backup_failed)
                },
                color = when {
                    status.busy -> Color(0xFF8A8F98)
                    status.success -> Color(0xFF2E7D32)
                    else -> Color(0xFFD32F2F)
                },
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CardGap, bottom = 4.dp)
            )
        }

        if (showRemoteList) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.backup_browse_remote),
                onDismissRequest = { showRemoteList = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (remoteBackups.loading) {
                        Text(stringResource(R.string.backup_busy))
                    } else if (!remoteBackups.error.isNullOrBlank()) {
                        Text(remoteBackups.error ?: stringResource(R.string.backup_failed))
                    } else if (remoteBackups.entries.isEmpty()) {
                        Text(stringResource(R.string.backup_browse_empty))
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            remoteBackups.entries.forEach { entry ->
                                RemoteBackupItem(
                                    entry = entry,
                                    busy = status.busy,
                                    onRestore = { viewModel.restoreFromWebDavUrl(settings, entry.url) },
                                    onDelete = { viewModel.deleteRemoteBackup(settings, entry.url) }
                                )
                            }
                        }
                    }

                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showRemoteList = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
        }

        item(key = "about_title") {
            SectionTitle(
                text = stringResource(R.string.settings_about),
                topPadding = SectionTopGap
            )
        }

        item(key = "about_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.cache_clear_label),
                summary = stringResource(
                    R.string.cache_size_value,
                    CacheCleaner.formatBytes(cacheStats.bytes)
                ),
                enabled = !cacheBusy,
                onClick = { showCacheConfirm = !cacheBusy }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_about),
                summary = stringResource(
                    R.string.version_value,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                onClick = onOpenAbout
            )
        }
        }

        item(key = "clearance") {
            BottomBarClearance()
        }
    }

    if (showCacheConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.cache_clear_label),
            onDismissRequest = { showCacheConfirm = false }
        ) {
            Column {
                Text(stringResource(R.string.cache_clear_confirm, CacheCleaner.formatBytes(cacheStats.bytes)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showCacheConfirm = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.cache_clear_label),
                        onClick = {
                            showCacheConfirm = false
                            viewModel.clearCache()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
