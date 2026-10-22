package com.evan.pulse.ui.resources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.collection.CollectionScreen
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.env.EnvironmentScreen
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.project.ProjectViewModel
import com.evan.pulse.ui.project.ProjectsScreen
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle

private val resourceTabLabels = listOf(
    R.string.tab_projects,
    R.string.tab_collection,
    R.string.tab_env
)

/** 资源页：项目、集合与环境通过顶部 TabRow 切换，选中项由 tabState 受控以便首页跳转 */
@Composable
fun ResourcesScreen(
    modifier: Modifier = Modifier,
    resourceTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onSwitchTab: (Int) -> Unit = {},
    // 必须显式传入：NavDisplay 每个 entry 有独立 ViewModelStore，
    // 默认 viewModel() 会得到与顶栏（App 层）不同的实例，顶栏"新建项目"状态将无法送达
    projectViewModel: ProjectViewModel,
    onOpenEnvVars: (Long) -> Unit = {},
    onRunCollection: (Long?) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().padding(top = LocalTopBarInset.current)) {
        AppTabRow(
            tabs = resourceTabLabels.localizedLabels(),
            selectedTabIndex = resourceTab.coerceIn(0, resourceTabLabels.lastIndex),
            onTabSelected = onTabChange,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        if (LocalUiStyle.current == UiStyle.MATERIAL) {
            Spacer(Modifier.height(8.dp))
        }
        when (resourceTab.coerceIn(0, resourceTabLabels.lastIndex)) {
            0 -> ProjectsScreen(Modifier.weight(1f).fillMaxWidth(), viewModel = projectViewModel)
            1 -> CollectionScreen(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onSwitchTab = onSwitchTab,
                onRunCollection = onRunCollection
            )
            else -> EnvironmentScreen(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onOpenVars = onOpenEnvVars
            )
        }
    }
}
