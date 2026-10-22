package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppTextButton

/** 确认类弹窗共用的取消/确认按钮行：两键等宽，中间留出固定间距 */
@Composable
fun DialogActions(
    onCancel: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppTextButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            text = confirmText,
            onClick = onConfirm,
            primary = true,
            modifier = Modifier.weight(1f)
        )
    }
}
