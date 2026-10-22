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
import com.evan.pulse.domain.tools.JwtCodec
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/** JWT 解码工具二级页：粘贴 JWT，解出 header/payload 供阅读 */
@Composable
fun JwtToolScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    val result = remember(input) { JwtCodec.decode(input) }

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
            label = stringResource(R.string.jwt_input_label),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        if (input.isBlank()) {
            HintText(stringResource(R.string.jwt_empty_hint))
        } else if (!result.valid) {
            AppText(
                text = stringResource(R.string.jwt_invalid),
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
        } else {
            Section(
                label = stringResource(R.string.jwt_header_label),
                text = result.header
            )
            Section(
                label = stringResource(R.string.jwt_payload_label),
                text = result.payload
            )
            Section(
                label = stringResource(R.string.jwt_signature_label),
                text = result.signature
            )
        }
    }
}

@Composable
private fun Section(label: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = label)
        CopyIconButton(text = text, label = stringResource(R.string.action_copy))
    }
    MonospaceBlock(text = text, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun HintText(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        fontSize = 13.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}