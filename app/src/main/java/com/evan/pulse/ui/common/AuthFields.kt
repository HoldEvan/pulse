package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.l10n.localizedLabels

/** 下拉项顺序与 [AuthType] 枚举顺序一一对应 */
private val authTypeLabels = listOf(
    R.string.auth_none,
    R.string.auth_bearer,
    R.string.auth_basic,
    R.string.auth_apikey
)

/**
 * 鉴权编辑面板：类型下拉 + 对应字段。HTTP 与 SSE 页共用，
 * 字段值支持 `{{var}}` 占位符，由调用方在发请求前解析。
 */
@Composable
fun AuthFields(auth: AuthConfig, onChange: (AuthConfig) -> Unit) {
    Column {
        AppDropdown(
            items = authTypeLabels.localizedLabels(),
            selectedIndex = auth.type.ordinal,
            title = stringResource(R.string.auth_type_label),
            modifier = Modifier.fillMaxWidth(),
            showValue = true,
            onSelectedIndexChange = { onChange(auth.copy(type = AuthType.entries[it])) }
        )
        // 内容列仅在类型有内容时渲染：NONE 时空列的内边距会在卡片底部留白
        if (auth.type != AuthType.NONE) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (auth.type) {
                    AuthType.NONE -> {}
                    AuthType.BEARER -> {
                        AppTextField(
                            value = auth.bearerToken,
                            onValueChange = { onChange(auth.copy(bearerToken = it)) },
                            label = stringResource(R.string.bearer_token_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AuthType.APIKEY -> {
                        AppTextField(
                            value = auth.apiKeyName,
                            onValueChange = { onChange(auth.copy(apiKeyName = it)) },
                            label = stringResource(R.string.apikey_name_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppTextField(
                            value = auth.apiKeyValue,
                            onValueChange = { onChange(auth.copy(apiKeyValue = it)) },
                            label = stringResource(R.string.apikey_value_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AuthType.BASIC -> {
                        AppTextField(
                            value = auth.basicUsername,
                            onValueChange = { onChange(auth.copy(basicUsername = it)) },
                            label = stringResource(R.string.username_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppTextField(
                            value = auth.basicPassword,
                            onValueChange = { onChange(auth.copy(basicPassword = it)) },
                            label = stringResource(R.string.password_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
