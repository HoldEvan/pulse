package com.evan.pulse.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.ToastHost
import com.evan.pulse.ui.dashboard.DashboardScreen
import com.evan.pulse.ui.glass.LiquidGlassNavigationBar
import com.evan.pulse.ui.history.HistoryDetailScreen
import com.evan.pulse.ui.history.HistoryScreen
import com.evan.pulse.ui.history.HistoryViewModel
import com.evan.pulse.ui.http.ScriptDocsScreen
import com.evan.pulse.ui.http.ScriptEditorScreen
import com.evan.pulse.ui.l10n.LocalizedRoot
import com.evan.pulse.ui.l10n.fromIndex
import com.evan.pulse.ui.monitor.MonitorRecordsScreen
import com.evan.pulse.ui.monitor.MonitorScreen
import com.evan.pulse.ui.monitor.MonitorViewModel
import com.evan.pulse.ui.project.ProjectBar
import com.evan.pulse.ui.resources.ResourcesScreen
import com.evan.pulse.ui.settings.SettingsScreen
import com.evan.pulse.ui.settings.SettingsViewModel
import com.evan.pulse.ui.settings.ThemeSettingsScreen
import com.evan.pulse.ui.test.TestScreen
import com.evan.pulse.ui.tools.Base64ToolScreen
import com.evan.pulse.ui.tools.JsonToolScreen
import com.evan.pulse.ui.tools.TimestampToolScreen
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class PulseTab(
    @StringRes val label: Int,
    val icon: ImageVector
)

/** 页面切换：使用 AnimatedContent 默认的交叉淡入淡出过渡 */
@Composable
private fun PageTransition(
    modifier: Modifier,
    current: Int,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = current,
        modifier = modifier,
        label = "页面切换"
    ) { page ->
        content(page)
    }
}

/** 淡入淡出转场：返回手势模式选 AOSP 风格时使用，深度驱动的交叉渐变 */
private val fadeTransition: NavTransition = navGraphicsTransition { scope ->
    val d = scope.relativeDepth
    alpha = if (d <= 0f) (1f + d).coerceIn(0f, 1f) else 1f - d.coerceIn(0f, 1f)
}

@Composable
fun PulseApp(settingsViewModel: SettingsViewModel) {
    val tabs = remember {
        listOf(
            PulseTab(R.string.tab_home, MiuixIcons.Light.Home),
            PulseTab(R.string.tab_test, Icons.Filled.Science),
            PulseTab(R.string.tab_resources, MiuixIcons.Light.Folder),
            PulseTab(R.string.tab_monitor, Icons.Filled.NetworkCheck),
            PulseTab(R.string.tab_settings, MiuixIcons.Light.Settings)
        )
    }

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val tabState by settingsViewModel.tabState.collectAsStateWithLifecycle()

    /** 与历史页共用同一个 ViewModel，列表与详情看到的记录一致 */
    val historyViewModel: HistoryViewModel = viewModel()

    /** 与拨测二级页共用同一个 ViewModel，列表与记录页看到的数据一致 */
    val monitorViewModel: MonitorViewModel = viewModel()

    val navController = rememberNavController<PulseRoute>(PulseRoute.Tabs)

    /** 返回手势模式同时决定页面转场：0 = Miuix 滑动，1 = 淡入淡出；
     *  预测性返回由 miuix-nav 内置驱动，两种模式都支持跟手 */
    val transition = if (settings.backGestureModeIndex == 0) {
        NavTransitions.MiuixDefault
    } else {
        fadeTransition
    }

    /** 液态玻璃悬浮底栏开关：悬浮栏与玻璃效果同时开启时才生效，只在标签页显示 */
    val glassBottomBar = settings.floatingBottomBar && settings.liquidGlassBottomBar

    /** 玻璃底栏的背景采样层：捕获页面内容供胶囊内的模糊与折射取样；
     *  运行时不支持 RuntimeShader 时返回 null，底栏退化为纯色胶囊 */
    val backdrop: LayerBackdrop? = if (glassBottomBar && isRuntimeShaderSupported()) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        rememberLayerBackdrop(
            onDraw = {
                drawRect(surfaceColor)
                drawContent()
            }
        )
    } else {
        null
    }

    /** 供玻璃底栏使用的标签项 */
    val navigationItems = tabs.map { tab ->
        NavigationItem(icon = tab.icon, label = stringResource(tab.label))
    }

    /** 标签页的一屏：顶栏、底栏与五个 tab 内容。转场中间态由 NavDisplay 统一裁剪，
     *  采样层挂在覆盖整块 Scaffold 的外层，胶囊才能模糊到页面内容 */
    @Composable
    fun TabsScreen() {
        Scaffold(
            topBar = {
                ProjectBar(
                    title = stringResource(tabs[tabState.selectedTab].label),
                    showProjectSelector = tabState.selectedTab in 1..2,
                    showEnvSelector = tabState.selectedTab == 1,
                    onAddMonitor = if (tabState.selectedTab == 3) {
                        { monitorViewModel.openCreate() }
                    } else {
                        null
                    }
                )
            },
            bottomBar = {
                if (glassBottomBar) {
                    LiquidGlassNavigationBar(
                        items = navigationItems,
                        selectedIndex = tabState.selectedTab,
                        onItemClick = { settingsViewModel.selectTab(it) },
                        backdrop = backdrop,
                        isBlurActive = backdrop != null
                    )
                } else if (settings.floatingBottomBar) {
                    FloatingNavigationBar(color = MiuixTheme.colorScheme.surfaceContainer) {
                        tabs.forEachIndexed { index, tab ->
                            FloatingNavigationBarItem(
                                selected = tabState.selectedTab == index,
                                onClick = { settingsViewModel.selectTab(index) },
                                icon = tab.icon,
                                label = stringResource(tab.label)
                            )
                        }
                    }
                } else {
                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = tabState.selectedTab == index,
                                onClick = { settingsViewModel.selectTab(index) },
                                icon = tab.icon,
                                label = stringResource(tab.label)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                    PageTransition(
                        modifier = Modifier.fillMaxSize(),
                        current = tabState.selectedTab
                    ) { tab ->
                        when (tab) {
                            0 -> DashboardScreen(
                                modifier = Modifier.fillMaxSize(),
                                onOpenBase64 = { navController.pushRoute(PulseRoute.Base64Tool) },
                                onOpenJson = { navController.pushRoute(PulseRoute.JsonTool) },
                                onOpenTimestamp = { navController.pushRoute(PulseRoute.TimestampTool) }
                            )
                            1 -> TestScreen(
                                modifier = Modifier.fillMaxSize(),
                                protocolTab = tabState.homeProtocolTab,
                                onProtocolTabChange = { settingsViewModel.selectHomeProtocol(it) },
                                onOpenScriptEditor = { navController.pushRoute(PulseRoute.ScriptEditor(it)) },
                                onOpenHistory = { navController.pushRoute(PulseRoute.History) }
                            )
                            2 -> ResourcesScreen(
                                modifier = Modifier.fillMaxSize(),
                                onSwitchTab = { settingsViewModel.openHomeProtocol(it) }
                            )
                            3 -> MonitorScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = monitorViewModel,
                                onOpenRecordsList = { navController.pushRoute(PulseRoute.MonitorRecords) }
                            )
                            else -> SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = settingsViewModel,
                                onOpenAppearance = { navController.pushRoute(PulseRoute.Appearance) }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 二级/三级页的一屏：标题 + 返回键 + 内容 */
    @Composable
    fun SubPageScreen(title: String, content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { navController.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Light.ChevronBackward,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                content()
            }
        }
    }

    NavDisplay(
        navController = navController,
        transition = transition,
        effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius())
    ) {
        entry<PulseRoute.Tabs> {
            TabsScreen()
        }

        entry<PulseRoute.Appearance> {
            SubPageScreen(title = stringResource(R.string.theme_page_title)) {
                ThemeSettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = settingsViewModel
                )
            }
        }

        entry<PulseRoute.HistoryDetail> { route ->
            SubPageScreen(title = stringResource(R.string.history_detail_title)) {
                HistoryDetailScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = historyViewModel,
                    entryId = route.entryId
                )
            }
        }

        entry<PulseRoute.MonitorRecords> {
            SubPageScreen(title = stringResource(R.string.monitor_records_title)) {
                MonitorRecordsScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = monitorViewModel
                )
            }
        }

        entry<PulseRoute.ScriptEditor> { route ->
            SubPageScreen(
                title = stringResource(
                    if (route.target == 0) R.string.script_tab_pre else R.string.script_tab_post
                )
            ) {
                ScriptEditorScreen(
                    target = route.target,
                    modifier = Modifier.fillMaxSize(),
                    onOpenScriptDocs = { navController.pushRoute(PulseRoute.ScriptDocs(route.target)) }
                )
            }
        }

        entry<PulseRoute.ScriptDocs> { route ->
            SubPageScreen(title = stringResource(R.string.script_docs_title)) {
                ScriptDocsScreen(
                    target = route.target,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        entry<PulseRoute.History> {
            SubPageScreen(title = stringResource(R.string.history_title)) {
                HistoryScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenDetail = { navController.pushRoute(PulseRoute.HistoryDetail(it)) },
                    viewModel = historyViewModel
                )
            }
        }

        entry<PulseRoute.Base64Tool> {
            SubPageScreen(title = stringResource(R.string.base64_title)) {
                Base64ToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.JsonTool> {
            SubPageScreen(title = stringResource(R.string.json_tool_title)) {
                JsonToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.TimestampTool> {
            SubPageScreen(title = stringResource(R.string.timestamp_tool_title)) {
                TimestampToolScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    ToastHost()
}
