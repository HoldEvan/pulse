package com.evan.pulse.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AppPreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable ColumnScope.() -> Unit>()

    fun item(content: @Composable ColumnScope.() -> Unit) {
        items.add(content)
    }
}

@Composable
fun AppPreferenceGroup(
    modifier: Modifier = Modifier,
    content: AppPreferenceGroupScope.() -> Unit,
) {
    // Collect visible items before resolving the first and last corners.
    val items = AppPreferenceGroupScope().apply(content).items
    if (items.isEmpty()) return

    if (LocalUiStyle.current == UiStyle.MIUIX) {
        AppCard(modifier = modifier) {
            items.forEach { item -> item() }
        }
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val topRadius = if (index == 0) 16.dp else 4.dp
            val bottomRadius = if (index == items.lastIndex) 16.dp else 4.dp
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius,
                ),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(content = item)
            }
        }
    }
}
