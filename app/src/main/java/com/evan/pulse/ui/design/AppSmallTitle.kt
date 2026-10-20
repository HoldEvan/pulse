package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle

@Composable
fun AppSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSmallTitle(text = text, modifier = modifier, insideMargin = insideMargin)
    } else {
        Text(
            text = text,
            modifier = modifier.padding(insideMargin),
            color = AppTheme.colors.onSurfaceSummary,
            style = AppTheme.typography.subtitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
