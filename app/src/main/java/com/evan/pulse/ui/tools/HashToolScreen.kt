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
import com.evan.pulse.domain.tools.DigestOutput
import com.evan.pulse.domain.tools.HashAlgorithm
import com.evan.pulse.domain.tools.HashTool
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.l10n.localizedLabels

private enum class HashMode { DIGEST, HMAC }

private val algorithmLabels = listOf(
    R.string.hash_algorithm_md5,
    R.string.hash_algorithm_sha1,
    R.string.hash_algorithm_sha256,
    R.string.hash_algorithm_sha384,
    R.string.hash_algorithm_sha512
)

/**
 * Hash 工具二级页：摘要与 HMAC 共用输入框，算法覆盖 MD5 与 SHA-1/256/384/512。
 *
 * HMAC 模式多一个密钥输入；密钥为空时不给结果，避免出现「没密钥也算出值」的误解。
 */
@Composable
fun HashToolScreen(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(HashMode.DIGEST) }
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(HashAlgorithm.SHA256) }
    var output by remember { mutableStateOf(DigestOutput.HEX) }

    val result = remember(mode, input, key, algorithm, output) {
        when (mode) {
            HashMode.DIGEST -> HashTool.hash(input, algorithm, output)
            HashMode.HMAC -> HashTool.hmac(input, key, algorithm, output)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTabRow(
            tabs = listOf(
                stringResource(R.string.hash_mode_digest),
                stringResource(R.string.hash_mode_hmac)
            ),
            selectedTabIndex = mode.ordinal,
            onTabSelected = { mode = HashMode.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        AppTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.hash_input_label),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4
        )

        if (mode == HashMode.HMAC) {
            AppTextField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.hash_key_label),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }

        AppDropdown(
            title = stringResource(R.string.hash_algorithm_label),
            items = algorithmLabels.localizedLabels(),
            selectedIndex = algorithm.ordinal,
            onSelectedIndexChange = { algorithm = HashAlgorithm.entries[it] },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        AppTabRow(
            tabs = listOf(
                stringResource(R.string.hash_output_hex),
                stringResource(R.string.hash_output_base64)
            ),
            selectedTabIndex = output.ordinal,
            onTabSelected = { output = DigestOutput.entries[it] },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(
                text = stringResource(
                    when (mode) {
                        HashMode.DIGEST -> R.string.hash_output_label
                        HashMode.HMAC -> R.string.hash_hmac_output_label
                    }
                )
            )
            CopyIconButton(text = result, label = stringResource(R.string.action_copy))
        }
        when {
            mode == HashMode.HMAC && key.isEmpty() -> Hint(
                stringResource(R.string.hash_key_required)
            )
            input.isBlank() -> Hint(stringResource(R.string.base64_empty_hint))
            else -> MonospaceBlock(text = result, modifier = Modifier.padding(top = 6.dp))
        }
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
