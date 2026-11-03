package com.evan.pulse.ui.resources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evan.pulse.ui.collection.CollectionScreen
import com.evan.pulse.ui.common.LocalStickyTabInset
import com.evan.pulse.ui.env.EnvViewModel
import com.evan.pulse.ui.env.EnvironmentScreen
import com.evan.pulse.ui.project.ProjectViewModel
import com.evan.pulse.ui.project.ProjectsScreen

/**
 * 资源页：项目、集合与环境三个子页的容器。
 *
 * 标签条由外层的磨砂顶栏承载（见 [ResourceTabBar]），本容器只负责按 [resourceTab]
 * 切换子页，并把标签条高度经 [LocalStickyTabInset] 透传给各子页，
 * 让它们的滚动内容在顶栏让位之外再留出标签条高度，从而从磨砂标签条下方穿过。
 */
@Composable
fun ResourcesScreen(
    modifier: Modifier = Modifier,
    resourceTab: Int = 1,
    onSwitchTab: (Int) -> Unit = {},
    // 必须显式传入：NavDisplay 每个 entry 有独立 ViewModelStore，
    // 默认 viewModel() 会得到与顶栏（App 层）不同的实例，顶栏"新建项目"状态将无法送达
    projectViewModel: ProjectViewModel,
    // 与顶栏"新建环境"按钮共享同一个实例，否则按钮改不到本页状态
    envViewModel: EnvViewModel,
    onOpenEnvVars: (Long) -> Unit = {},
    onRunCollection: (Long?) -> Unit = {},
    stickyTabInset: Dp = 0.dp
) {
    CompositionLocalProvider(LocalStickyTabInset provides stickyTabInset) {
        Column(modifier = modifier.fillMaxSize()) {
            when (resourceTab.coerceIn(0, resourceTabLabels.lastIndex)) {
                0 -> ProjectsScreen(Modifier.weight(1f).fillMaxWidth(), viewModel = projectViewModel)
                1 -> CollectionScreen(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onSwitchTab = onSwitchTab,
                    onRunCollection = onRunCollection
                )
                else -> EnvironmentScreen(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onOpenVars = onOpenEnvVars,
                    viewModel = envViewModel
                )
            }
        }
    }
}
