package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppSwitch
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme

/**
 * 「跳过 TLS 证书校验」开关，三个协议页共用。
 *
 * 开启后信任任意证书并跳过主机名校验，等于放弃中间人防护，
 * 因此开关下方常驻一条风险说明，而不是只在开启时提示。
 */
@Composable
fun InsecureTlsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = stringResource(R.string.insecure_tls_label),
                modifier = Modifier.weight(1f)
            )
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        AppText(
            text = stringResource(
                if (checked) R.string.insecure_tls_warning_on else R.string.insecure_tls_hint
            ),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            color = if (checked) AppTheme.colors.error else AppTheme.colors.onSurfaceSummary
        )
    }
}
