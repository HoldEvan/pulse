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
import com.evan.pulse.domain.tools.HashAlgorithm
import com.evan.pulse.domain.tools.HashTool
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

/** Hash 工具二级页：选 MD5/SHA-1/SHA-256 算法，对输入计算摘要 */
@Composable
fun HashToolScreen(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(HashAlgorithm.SHA256) }

    val hash = remember(input, algorithm) { HashTool.hash(input, algorithm) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val labels = listOf("MD5", "SHA-1", "SHA-256")
        AppTabRow(
            tabs = labels,
            selectedTabIndex = algorithm.ordinal,
            onTabSelected = { algorithm = HashAlgorithm.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        AppTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.hash_input_label),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardSectionTitle(text = stringResource(R.string.hash_output_label))
            CopyIconButton(text = hash, label = stringResource(R.string.action_copy))
        }
        if (input.isBlank()) {
            AppText(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        } else {
            MonospaceBlock(text = hash, modifier = Modifier.padding(top = 6.dp))
        }
    }
}