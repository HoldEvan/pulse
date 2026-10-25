package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.evan.pulse.domain.tools.ParsedUrl
import com.evan.pulse.domain.tools.UrlInspector
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppSwitchRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/**
 * URL 解析器二级页：把地址拆成协议、认证信息、主机、端口、路径、查询参数与锚点，
 * 并给出反向拼装结果，用来核对参数编码、端口与锚点是否被工具或网关改写。
 */
@Composable
fun UrlParserScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var decodeParams by remember { mutableStateOf(true) }

    val parsed = remember(input) { UrlInspector.parse(input) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.url_parser_input_label),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 3
        )

        when {
            input.isBlank() -> Hint(stringResource(R.string.url_parser_empty_hint))
            parsed.error != null -> AppText(
                text = urlErrorText(parsed.error),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
            else -> Unit
        }

        if (parsed.schemeInferred) {
            AppText(
                text = stringResource(R.string.url_parser_scheme_inferred),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }

        if (input.isBlank()) return@Column

        CardSectionTitle(
            text = stringResource(R.string.url_parser_parts_title),
            modifier = Modifier.padding(top = 14.dp)
        )
        AppCard(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 4.dp
            )
        ) {
            FieldRow(stringResource(R.string.url_parser_field_scheme), parsed.scheme)
            if (parsed.hasUserInfo) {
                FieldRow(stringResource(R.string.url_parser_field_user), parsed.user)
                FieldRow(stringResource(R.string.url_parser_field_password), parsed.password)
            }
            FieldRow(stringResource(R.string.url_parser_field_host), parsed.host)
            FieldRow(
                stringResource(R.string.url_parser_field_port),
                parsed.port?.toString() ?: stringResource(R.string.url_parser_port_absent)
            )
            FieldRow(stringResource(R.string.url_parser_field_path), parsed.path)
            FieldRow(stringResource(R.string.url_parser_field_fragment), parsed.fragment)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(
                text = stringResource(
                    R.string.url_parser_params_title,
                    parsed.params.size
                )
            )
        }
        AppSwitchRow(
            title = stringResource(R.string.url_parser_decode_params),
            summary = stringResource(R.string.url_parser_decode_params_summary),
            checked = decodeParams,
            onCheckedChange = { decodeParams = it },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        if (parsed.params.isEmpty()) {
            Hint(stringResource(R.string.url_parser_params_empty))
        } else {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp
                )
            ) {
                parsed.params.forEach { param ->
                    val name = if (decodeParams) param.decodedName else param.name
                    val value = if (decodeParams) param.decodedValue else param.value
                    FieldRow(name.ifEmpty { stringResource(R.string.url_parser_param_blank) }, value)
                }
            }
            if (parsed.hasDuplicateParams) {
                AppText(
                    text = stringResource(R.string.url_parser_params_duplicate),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(text = stringResource(R.string.url_parser_rebuild_title))
            CopyIconButton(
                text = parsed.rebuild(),
                label = stringResource(R.string.action_copy)
            )
        }
        MonospaceBlock(text = parsed.rebuild(), modifier = Modifier.padding(top = 6.dp))
        if (parsed.rebuild() != input.trim()) {
            AppText(
                text = stringResource(R.string.url_parser_rebuild_differs),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = label,
            modifier = Modifier.padding(end = 12.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        AppText(
            text = value.ifEmpty { "-" },
            modifier = Modifier.weight(1f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun Hint(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(vertical = 4.dp),
        fontSize = 13.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}

@Composable
private fun urlErrorText(error: String?): String = stringResource(
    when (error) {
        "empty" -> R.string.url_parser_error_empty
        "host" -> R.string.url_parser_error_host
        "port" -> R.string.url_parser_error_port
        else -> R.string.url_parser_error_invalid
    }
)
