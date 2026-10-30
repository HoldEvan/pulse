package com.evan.pulse.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.model.TlsConfig
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

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

/** 一行「已配置 → 文件名/大小 + 清除」，点击行从文件系统选入 PEM 文本 */
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
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(text = label, fontSize = 14.sp)
                AppText(
                    text = if (pem.isBlank()) summary else stringResource(R.string.tls_configured),
                    fontSize = 12.sp,
                    color = if (pem.isBlank()) AppTheme.colors.onSurfaceSummary
                    else AppTheme.colors.primary
                )
            }
            if (pem.isNotBlank()) {
                AppTextButton(
                    text = stringResource(R.string.tls_clear),
                    onClick = { onChange("") }
                )
            }
            AppTextButton(
                text = if (pem.isBlank()) stringResource(R.string.tls_import) else stringResource(R.string.tls_replace),
                onClick = { picker.launch(arrayOf("application/x-pem-file", "text/plain", "*/*")) }
            )
        }
        error?.let {
            AppText(
                text = stringResource(R.string.tls_pem_invalid, it),
                fontSize = 12.sp,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(top = 4.dp)
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
