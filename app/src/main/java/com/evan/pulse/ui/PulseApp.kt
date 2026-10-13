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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.about.AboutScreen
import com.evan.pulse.ui.about.LegalScreen
import com.evan.pulse.ui.common.BlurredBar
import com.evan.pulse.ui.common.LocalSubPageCanScroll
import com.evan.pulse.ui.common.ToastHost
import com.evan.pulse.ui.common.topBarPadding
import com.evan.pulse.ui.dashboard.DashboardScreen
import com.evan.pulse.ui.env.EnvViewModel
import com.evan.pulse.ui.env.EnvVarsScreen
import com.evan.pulse.ui.glass.LiquidGlassNavigationBar
import com.evan.pulse.ui.history.HistoryDetailScreen
import com.evan.pulse.ui.history.HistoryScreen
import com.evan.pulse.ui.history.HistoryViewModel
import com.evan.pulse.ui.http.HttpViewModel
import com.evan.pulse.ui.http.ScriptDocsScreen
import com.evan.pulse.ui.http.ScriptEditorScreen
import com.evan.pulse.ui.l10n.LocalizedRoot
import com.evan.pulse.ui.l10n.fromIndex
import com.evan.pulse.ui.monitor.MonitorRecordsScreen
import com.evan.pulse.ui.monitor.MonitorScreen
import com.evan.pulse.ui.monitor.MonitorViewModel
import com.evan.pulse.ui.project.ProjectViewModel
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Add
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

    /** 与顶栏项目选择器、资源页项目 tab 共用的项目 ViewModel */
    val projectViewModel: ProjectViewModel = viewModel()

    /** 与 HTTP 协议页、脚本编辑页共用的 ViewModel：脚本编辑是独立 route entry，不显式传入会拿到不同实例 */
    val httpViewModel: HttpViewModel = viewModel()

    /** 与资源页环境 tab、环境变量页共用的环境 ViewModel：变量页是独立 route entry，需显式传入 */
    val envViewModel: EnvViewModel = viewModel()

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

    /** 顶栏磨砂与底栏玻璃共用的内容采样层：捕获页面内容供实时模糊取样；
     *  运行时不支持 RuntimeShader 时返回 null，顶栏退化为纯色、底栏退化为纯色胶囊 */
    val backdrop: LayerBackdrop? = if (isRuntimeShaderSupported()) {
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

    /** 各标签页独立的大标题收缩行为：进入页面时大标题展开，上滚收缩成小标题吸顶。
     *  MiuixScrollBehavior 内部经 rememberSaveable 保持状态，直接创建即可；
     *  每个 tab 配一个"内容可滚动"槽位，内容不足一屏时顶栏不收缩；
     *  已收缩状态下保持可滚，避免收缩过程中视口变高引发的复位抖动 */
    val tabStates = List(5) { rememberTopAppBarState() }
    val tabCanScroll = remember { List(5) { mutableStateOf(true) } }
    val tabScrollBehaviors = List(5) { index ->
        val state = tabStates[index]
        val canScrollCheck = remember { { tabCanScroll[index].value || state.heightOffset < -1f } }
        MiuixScrollBehavior(state = state, canScroll = canScrollCheck)
    }
    val topAppBarScrollBehavior = tabScrollBehaviors[tabState.selectedTab]
    val isBlurActive = backdrop != null

    /** 供玻璃底栏使用的标签项 */
    val navigationItems = tabs.map { tab ->
        NavigationItem(icon = tab.icon, label = stringResource(tab.label))
    }

    /** 标签页的一屏：顶栏、底栏与五个 tab 内容。转场中间态由 NavDisplay 统一裁剪，
     *  采样层挂在覆盖整块 Scaffold 的外层，顶栏磨砂与玻璃底栏胶囊才能模糊到页面内容 */
    @Composable
    fun TabsScreen() {
        Scaffold(
            topBar = {
                ProjectBar(
                    title = stringResource(tabs[tabState.selectedTab].label),
                    // 拨测页显示项目选择；资源页仅在集合/环境子页显示（项目子页自带管理）
                    showProjectSelector = tabState.selectedTab == 1 ||
                        (tabState.selectedTab == 2 && tabState.resourceTab != 0),
                    showEnvSelector = tabState.selectedTab == 1,
                    onAddMonitor = if (tabState.selectedTab == 3) {
                        { monitorViewModel.openCreate() }
                    } else {
                        null
                    },
                    // 资源页-项目 tab 时顶栏显示"新建项目"
                    onAddProject = if (tabState.selectedTab == 2 && tabState.resourceTab == 0) {
                        { projectViewModel.openCreate() }
                    } else {
                        null
                    },
                    viewModel = projectViewModel,
                    scrollBehavior = topAppBarScrollBehavior,
                    backdrop = backdrop,
                    isBlurActive = isBlurActive
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
                    .topBarPadding(paddingValues)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                PageTransition(
                    modifier = Modifier.fillMaxSize(),
                    current = tabState.selectedTab
                ) { tab ->
                    CompositionLocalProvider(
                        LocalSubPageCanScroll provides tabCanScroll[tab]
                    ) {
                        when (tab) {
                                0 -> DashboardScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onOpenBase64 = { navController.pushRoute(PulseRoute.Base64Tool) },
                                    onOpenJson = { navController.pushRoute(PulseRoute.JsonTool) },
                                    onOpenTimestamp = { navController.pushRoute(PulseRoute.TimestampTool) },
                                    onOpenProjects = { settingsViewModel.openResourceTab(0) },
                                    onOpenHistory = { navController.pushRoute(PulseRoute.History) },
                                    onOpenRequests = { settingsViewModel.openResourceTab(1) },
                                    onOpenMonitors = { settingsViewModel.selectTab(3) }
                                )
                            1 -> TestScreen(
                                modifier = Modifier.fillMaxSize(),
                                protocolTab = tabState.homeProtocolTab,
                                onProtocolTabChange = { settingsViewModel.selectHomeProtocol(it) },
                                onOpenScriptEditor = { navController.pushRoute(PulseRoute.ScriptEditor(it)) },
                                onOpenHistory = { navController.pushRoute(PulseRoute.History) },
                                httpViewModel = httpViewModel
                            )
                                2 -> ResourcesScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    resourceTab = tabState.resourceTab,
                                    onTabChange = { settingsViewModel.selectResourceTab(it) },
                                    onSwitchTab = { settingsViewModel.openHomeProtocol(it) },
                                    projectViewModel = projectViewModel,
                                    onOpenEnvVars = { navController.pushRoute(PulseRoute.EnvVars(it)) }
                                )

                                3 -> MonitorScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = monitorViewModel,
                                    onOpenRecordsList = { navController.pushRoute(PulseRoute.MonitorRecords) }
                                )
                                else -> SettingsScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = settingsViewModel,
                                    onOpenAppearance = { navController.pushRoute(PulseRoute.Appearance) },
                                    onOpenAbout = { navController.pushRoute(PulseRoute.About) }
                                )
                        }
                    }
                }
            }
        }
        LaunchedEffect(tabState.selectedTab, tabCanScroll[tabState.selectedTab].value) {
            // 内容变短后顶栏若停在收缩态会卡住，直接展开复位。
            // 仅在顶栏仍处于展开态时复位：拨测页等内容只略超一屏的页面，
            // 收缩会让视口变高、maxValue 归零、canScroll 翻 false，
            // 若此时强制展开会与进行中的收缩形成循环，标题反复弹跳。
            val current = tabCanScroll[tabState.selectedTab]
            if (!current.value && topAppBarScrollBehavior.state.heightOffset >= -1f) {
                topAppBarScrollBehavior.state.heightOffset = 0f
                topAppBarScrollBehavior.state.contentOffset = 0f
            }
        }
    }

    /** 二级/三级页的一屏：大标题随滚动收缩成小标题，顶栏磨砂透出内容；内容不足一屏时保持大标题不吸顶 */
    @Composable
    fun SubPageScreen(
        title: String,
        actions: @Composable RowScope.() -> Unit = {},
        content: @Composable () -> Unit
    ) {
        val canScroll = remember { mutableStateOf(true) }
        val topAppBarState = rememberTopAppBarState()
        // 滞后判定：顶栏一旦收缩（heightOffset<0）就保持可滚，避免收缩中内容视口变高
        // 导致 maxValue 归零、canScroll 翻转、强制复位展开的抖动循环
        val canScrollCheck = remember { { canScroll.value || topAppBarState.heightOffset < -1f } }
        val topAppBarScrollBehavior = MiuixScrollBehavior(state = topAppBarState, canScroll = canScrollCheck)
        // 与 example 常规二级页一致：顶栏恒为 surface 实体栏，不做实时磨砂——
        // 磨砂采样会让滑动中穿过顶栏下沿的内容显出模糊暗带
        Scaffold(
            topBar = {
                TopAppBar(
                    title = title,
                    color = MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = actions,
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
                    .topBarPadding(paddingValues)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            ) {
                CompositionLocalProvider(
                    LocalSubPageCanScroll provides canScroll
                ) {
                    content()
                }
            }
        }
        LaunchedEffect(canScroll.value) {
            // 内容变短（旋转/字号调整等）后顶栏若停在收缩态会卡住，直接展开复位。
            // 仅在顶栏仍处于展开态时复位，避免与进行中的收缩形成弹跳循环
            // （内容只略超一屏的二级页收缩后视口变高，maxValue 会归零翻转）。
            if (!canScroll.value && topAppBarScrollBehavior.state.heightOffset >= -1f) {
                topAppBarScrollBehavior.state.heightOffset = 0f
                topAppBarScrollBehavior.state.contentOffset = 0f
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

        entry<PulseRoute.About> {
            AboutScreen(
                onBack = { navController.pop() },
                onOpenAgreement = { navController.pushRoute(PulseRoute.Agreement) },
                onOpenPrivacy = { navController.pushRoute(PulseRoute.Privacy) }
            )
        }

        entry<PulseRoute.Agreement> {
            SubPageScreen(title = stringResource(R.string.about_agreement)) {
                LegalScreen(text = stringResource(R.string.agreement_body))
            }
        }

        entry<PulseRoute.Privacy> {
            SubPageScreen(title = stringResource(R.string.about_privacy)) {
                LegalScreen(text = stringResource(R.string.privacy_body))
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
            SubPageScreen(
                title = stringResource(R.string.monitor_records_title),
                actions = {
                    IconButton(onClick = { monitorViewModel.requestClearRecords() }) {
                        Icon(
                            imageVector = MiuixIcons.Light.Delete,
                            contentDescription = stringResource(R.string.monitor_records_clear)
                        )
                    }
                }
            ) {
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
                    onOpenScriptDocs = { navController.pushRoute(PulseRoute.ScriptDocs(route.target)) },
                    viewModel = httpViewModel
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

        entry<PulseRoute.EnvVars> { route ->
            val envs by envViewModel.environments.collectAsStateWithLifecycle()
            SubPageScreen(
                title = envs.firstOrNull { it.id == route.envId }?.name
                    ?: stringResource(R.string.env_title),
                actions = {
                    IconButton(onClick = { envViewModel.openVarEditor(route.envId, null) }) {
                        Icon(
                            imageVector = MiuixIcons.Light.Add,
                            contentDescription = stringResource(R.string.cd_add_var)
                        )
                    }
                }
            ) {
                EnvVarsScreen(
                    envId = route.envId,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = envViewModel
                )
            }
        }

        entry<PulseRoute.History> {
            SubPageScreen(
                title = stringResource(R.string.history_title),
                actions = {
                    IconButton(onClick = { historyViewModel.confirmClear = true }) {
                        Icon(
                            imageVector = MiuixIcons.Light.Delete,
                            contentDescription = stringResource(R.string.history_clear)
                        )
                    }
                }
            ) {
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
