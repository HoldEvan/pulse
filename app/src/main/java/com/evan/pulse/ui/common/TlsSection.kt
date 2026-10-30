package com.evan.pulse.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.model.TlsConfig
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Import

/**
 * TLS 高级配置：跳过校验、自定义 CA、客户端证书（mTLS），四个协议页共用。
 *
 * 证书/私钥通过系统文件选择器读入并缓存为 PEM 文本，避免用户手抄长串内容；
 * 只展示是否已配置的摘要，真实内容不铺在界面上，防止误截图泄露私钥。
 */
@Composable
fun TlsSection(
    insecure: Boolean,
    onInsecureChange: (Boolean) -> Unit,
    customCaPem: String,
    onCustomCaChange: (String) -> Unit,
    clientCertPem: String,
    onClientCertChange: (String) -> Unit,
    clientKeyPem: String,
    onClientKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        InsecureTlsRow(checked = insecure, onCheckedChange = onInsecureChange)
        PemPickerRow(
            label = stringResource(R.string.tls_custom_ca_label),
            summary = stringResource(R.string.tls_custom_ca_summary),
            pem = customCaPem,
            onChange = onCustomCaChange
        )
        PemPickerRow(
            label = stringResource(R.string.tls_client_cert_label),
            summary = stringResource(R.string.tls_client_cert_summary),
            pem = clientCertPem,
            onChange = onClientCertChange
        )
        PemPickerRow(
            label = stringResource(R.string.tls_client_key_label),
            summary = stringResource(R.string.tls_client_key_summary),
            pem = clientKeyPem,
            onChange = onClientKeyChange
        )
    }
}

/**
 * 单行证书配置：整行可点，点击从文件系统选入 PEM 文本。
 *
 * 兼容两套皮肤：Miuix 用 BasicComponent 拼出与 preference 一致的缩进与行高，
 * Material 用 ListItem；行尾统一是导入图标，已配置时图标换成替换语义并在行内提供清除。
 */
@Composable
private fun PemPickerRow(
    label: String,
    summary: String,
    pem: String,
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("empty")
        }.onSuccess { text ->
            if (text.contains("-----BEGIN")) {
                onChange(text)
                error = null
            } else {
                error = label
            }
        }.onFailure {
            error = label
        }
    }
    val configured = pem.isNotBlank()
    val stateText = if (configured) stringResource(R.string.tls_configured) else summary
    val iconContentDescription = stringResource(
        if (configured) R.string.tls_replace else R.string.tls_import
    )
    val pick = { picker.launch(arrayOf("application/x-pem-file", "text/plain", "*/*")); Unit }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (LocalUiStyle.current == UiStyle.MIUIX) {
            BasicComponent(
                title = label,
                summary = stateText,
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = if (configured) AppTheme.colors.primary else AppTheme.colors.onSurfaceSummary
                ),
                onClick = pick,
                endActions = {
                    if (configured) {
                        MiuixIconButton(onClick = { onChange("") }) {
                            AppIcon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.tls_clear),
                                tint = AppTheme.colors.onSurfaceSummary
                            )
                        }
                    }
                    MiuixIconButton(onClick = pick) {
                        AppIcon(
                            imageVector = if (configured) MiuixIcons.Light.ConvertFile else MiuixIcons.Light.Import,
                            contentDescription = iconContentDescription,
                            tint = AppTheme.colors.primary
                        )
                    }
                }
            )
        } else {
            ListItem(
                headlineContent = { AppText(text = label, style = AppTheme.typography.main) },
                supportingContent = {
                    AppText(
                        text = stateText,
                        style = AppTheme.typography.footnote1,
                        color = if (configured) AppTheme.colors.primary else AppTheme.colors.onSurfaceSummary
                    )
                },
                modifier = Modifier.clickable(onClick = pick),
                trailingContent = {
                    Row {
                        if (configured) {
                            AppIconButton(onClick = { onChange("") }) {
                                AppIcon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.tls_clear),
                                    tint = AppTheme.colors.onSurfaceSummary
                                )
                            }
                        }
                        AppIconButton(onClick = pick) {
                            AppIcon(
                                imageVector = MiuixIcons.Light.Import,
                                contentDescription = iconContentDescription,
                                tint = AppTheme.colors.primary
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        error?.let {
            AppText(
                text = stringResource(R.string.tls_pem_invalid, it),
                fontSize = 12.sp,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(start = AppTheme.spacing.cardContent, top = 4.dp)
            )
        }
    }
}

/** 把三个 PEM 字段装配成 [TlsConfig]，供各 ViewModel 构造连接配置时调用 */
fun tlsConfigOf(
    insecure: Boolean,
    customCaPem: String,
    clientCertPem: String,
    clientKeyPem: String
): TlsConfig = TlsConfig(
    insecure = insecure,
    customCaPem = customCaPem,
    clientCertPem = clientCertPem,
    clientKeyPem = clientKeyPem
)
