package com.evan.pulse.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppSwitchRow

/**
 * 「跳过 TLS 证书校验」开关，各协议页共用。
 *
 * 开启后信任任意证书并跳过主机名校验，等于放弃中间人防护，
 * 因此副标题在开启时直接说明风险，关闭时说明适用场景。
 */
@Composable
fun InsecureTlsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppSwitchRow(
        title = stringResource(R.string.insecure_tls_label),
        summary = stringResource(
            if (checked) R.string.insecure_tls_summary_on else R.string.insecure_tls_summary
        ),
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}
