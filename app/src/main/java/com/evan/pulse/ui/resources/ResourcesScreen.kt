package com.evan.pulse.ui.resources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.collection.CollectionScreen
import com.evan.pulse.ui.env.EnvironmentScreen
import com.evan.pulse.ui.l10n.localizedLabels
import top.yukonga.miuix.kmp.basic.TabRowWithContour

private val resourceTabLabels = listOf(
    R.string.tab_collection,
    R.string.tab_env
)

/** 资源页：集合与环境通过顶部 TabRow 切换 */
@Composable
fun ResourcesScreen(modifier: Modifier = Modifier, onSwitchTab: (Int) -> Unit = {}) {
    var selection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRowWithContour(
            tabs = resourceTabLabels.localizedLabels(),
            selectedTabIndex = selection,
            onTabSelected = { selection = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        when (selection) {
            0 -> CollectionScreen(Modifier.fillMaxSize(), onSwitchTab)
            else -> EnvironmentScreen(Modifier.fillMaxSize())
        }
    }
}
