package com.evan.pulse.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import com.evan.pulse.ui.common.HtmlPreviewPage
import com.evan.pulse.ui.common.LocalSubPageCanScroll
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.TabPageScaffold
import com.evan.pulse.ui.common.ToastHost
import com.evan.pulse.ui.common.blurredBarColor
import com.evan.pulse.ui.common.rememberBlurBackdrop
import com.evan.pulse.ui.dashboard.DashboardScreen
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppBottomBar
import com.evan.pulse.ui.design.AppBottomBarItem
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppTopBar
import com.evan.pulse.ui.design.AppTopBarBehavior
import com.evan.pulse.ui.design.rememberAppTopBarBehavior
import com.evan.pulse.ui.design.uiCapabilities
import com.evan.pulse.ui.env.EnvViewModel
import com.evan.pulse.ui.env.EnvVarsScreen
import com.evan.pulse.ui.glass.LiquidGlassNavigationBar
import com.evan.pulse.ui.history.HistoryDetailScreen
import com.evan.pulse.ui.history.HistoryScreen
import com.evan.pulse.ui.history.HistoryViewModel
import com.evan.pulse.ui.history.ResponseDiffScreen
import com.evan.pulse.ui.collection.CollectionRunnerScreen
import com.evan.pulse.ui.collection.CollectionRunnerViewModel
import com.evan.pulse.ui.http.CookieDeleteRequest
import com.evan.pulse.ui.http.CookieScreen
import com.evan.pulse.ui.http.CookieViewModel
import com.evan.pulse.ui.http.HttpViewModel
import com.evan.pulse.ui.http.ScriptDocsScreen
import com.evan.pulse.ui.http.ScriptEditorScreen
import com.evan.pulse.ui.l10n.LocalizedRoot
import com.evan.pulse.ui.l10n.fromIndex
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.evan.pulse.ui.monitor.MonitorRecordsFilterActions
import com.evan.pulse.ui.monitor.MonitorRecordsScreen
import com.evan.pulse.ui.monitor.MonitorScreen
import com.evan.pulse.ui.monitor.MonitorTrendScreen
import com.evan.pulse.ui.monitor.MonitorViewModel
import com.evan.pulse.ui.project.ProjectViewModel
import com.evan.pulse.ui.project.ProjectBar
import com.evan.pulse.ui.resources.ResourcesScreen
import com.evan.pulse.ui.settings.SettingsScreen
import com.evan.pulse.ui.settings.SettingsViewModel
import com.evan.pulse.ui.settings.ThemeSettingsScreen
import com.evan.pulse.ui.settings.WebhookScreen
import com.evan.pulse.ui.test.TestScreen
import com.evan.pulse.ui.tools.EncodingToolScreen
import com.evan.pulse.ui.tools.ImageBase64Screen
import com.evan.pulse.ui.tools.JsonSchemaScreen
import com.evan.pulse.ui.tools.JsonToolScreen
import com.evan.pulse.ui.tools.QrCodeScreen
import com.evan.pulse.ui.tools.UrlParserScreen
import com.evan.pulse.ui.tools.YamlJsonScreen
import com.evan.pulse.ui.tools.TimestampToolScreen
import com.evan.pulse.ui.tools.JwtToolScreen
import com.evan.pulse.ui.tools.HashToolScreen
import com.evan.pulse.ui.tools.UuidToolScreen
import com.evan.pulse.ui.tools.RegexToolScreen
import com.evan.pulse.ui.tools.CronToolScreen
import com.evan.pulse.ui.tools.LanScanToolScreen
import top.yukonga.miuix.kmp.basic.NavigationItem
import com.evan.pulse.ui.design.AppScaffold
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
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private data class PulseTab(
    @StringRes val label: Int,
    val icon: ImageVector
)

/** 淡入淡出转场：返回手势模式选 AOSP 风格时使用，深度驱动的交叉渐变 */
private val fadeTransition: NavTransition = navGraphicsTransition { scope ->
    val d = scope.relativeDepth
    alpha = if (d <= 0f) (1f + d).coerceIn(0f, 1f) else 1f - d.coerceIn(0f, 1f)
}

/** 拖动翻页时，拖过多大比例（相对一页宽）就提前高亮相邻标签 */
private const val TAB_DRAG_TRIGGER = 0.3f

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

    /** 液态玻璃悬浮底栏开关：悬浮栏、玻璃效果、皮肤能力同时具备时才生效，只在标签页显示 */
    val capabilities = uiCapabilities
    val glassBottomBar = settings.floatingBottomBar &&
        settings.liquidGlassBottomBar &&
        capabilities.glassBottomBar

    /** 顶栏磨砂与底栏玻璃共用的内容采样层：捕获页面内容供实时模糊取样；
     *  关闭模糊或运行时不支持 RuntimeShader 时返回 null，顶栏退化为纯色、底栏退化为纯色胶囊 */
    val backdrop: LayerBackdrop? = if (settings.blurEnabled && isRuntimeShaderSupported()) {
        val surfaceColor = AppTheme.colors.surface
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
    val tabCanScroll = remember { List(5) { mutableStateOf(true) } }
    val tabScrollBehaviors = List(5) { index ->
        lateinit var behavior: AppTopBarBehavior
        behavior = rememberAppTopBarBehavior {
            tabCanScroll[index].value || !behavior.isExpanded
        }
        behavior
    }
    val topAppBarScrollBehavior = tabScrollBehaviors[tabState.selectedTab]

    /** 供玻璃底栏使用的标签项 */
    val navigationItems = tabs.map { tab ->
        NavigationItem(icon = tab.icon, label = stringResource(tab.label))
    }

    /** 供皮肤无关底栏使用的标签项 */
    val bottomBarItems = tabs.map { tab ->
        AppBottomBarItem(icon = tab.icon, label = stringResource(tab.label))
    }

    /** 标签页的一屏：底栏与五个 tab 内容（顶栏随各页滑动，见 pager 内的逐页 Scaffold）。
     *  采样层挂在覆盖整块区域的 Box 外层，玻璃底栏胶囊才能模糊到页面内容 */
    @Composable
    fun TabsScreen() {
        /** 横向滑动切换标签页；底栏点击与手势滑动经此双向同步到 tabState */
        val pagerState = rememberPagerState(initialPage = tabState.selectedTab) { tabs.size }

        /** 程序化翻页进行中（底栏点击等）：期间中间位置不再写回选中态 */
        var programmaticScrolling by remember { mutableStateOf(false) }

        // 底栏点击（或恢复上次选中）时动画翻到目标页
        LaunchedEffect(tabState.selectedTab) {
            if (pagerState.targetPage != tabState.selectedTab) {
                programmaticScrolling = true
                try {
                    pagerState.animateScrollToPage(tabState.selectedTab)
                } finally {
                    programmaticScrolling = false
                }
            }
        }

        // 拖动超过阈值（0.3 页宽）即把目标页写回全局选中状态，标题与底栏高亮跟手；
        // 松手后的回弹与甩动落点也由位置变化继续驱动，最终与停稳页一致。
        // 程序化翻页期间跳过，避免中途经过的页面被误写回选中态
        LaunchedEffect(pagerState) {
            snapshotFlow {
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            }.map { position ->
                (position + (1f - TAB_DRAG_TRIGGER)).toInt().coerceIn(0, tabs.size - 1)
            }.distinctUntilChanged().collect { page ->
                if (!programmaticScrolling) {
                    settingsViewModel.selectTab(page)
                }
            }
        }

        // 兜底：任何滚动停稳后强制对齐选中态，保证标题与底栏永不卡在旧页
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { settingsViewModel.selectTab(it) }
        }

        // 侧滑返回：非首页 tab 先回首页，再按一次才退出应用
        BackHandler(enabled = tabState.selectedTab != 0) {
            settingsViewModel.selectTab(0)
        }

        AppScaffold(
            bottomBar = {
                if (glassBottomBar) {
                    LiquidGlassNavigationBar(
                        items = navigationItems,
                        selectedIndex = tabState.selectedTab,
                        onItemClick = { settingsViewModel.selectTab(it) },
                        backdrop = backdrop,
                        isBlurActive = backdrop != null,
                        showLabels = settings.bottomBarLabelVisible
                    )
                } else {
                    AppBottomBar(
                        items = bottomBarItems,
                        selectedIndex = tabState.selectedTab,
                        onItemSelected = { settingsViewModel.selectTab(it) },
                        showLabels = settings.bottomBarLabelVisible,
                        floating = settings.floatingBottomBar
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 仅玻璃底栏开启时才在整块区域外层录制全局采样层；否则不录制，
                    // 避免与各页顶栏自己的采样层嵌套录制，顶栏磨砂取样更干净
                    .then(
                        if (backdrop != null && glassBottomBar) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        }
                    )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 预组合相邻页，滑动更顺滑；越界一页即弃
                    beyondViewportPageCount = 1
                ) { tab ->
                    // 每页自带整条顶栏，随 pager 一起横向滑动，标题自然跟页
                    // 每页独立的内容采样层：只采本页内容 Box，避免顶栏把模糊背景中的自己再采进去；
                    // 与本页顶栏在同一页作用域内创建，和 miuix example 的用法保持一致
                    val pageBackdrop = rememberBlurBackdrop()
                    val pageBehavior = tabScrollBehaviors[tab]
                    TabPageScaffold(
                        topBar = {
                            when (tab) {
                                1 -> ProjectBar(
                                    title = stringResource(tabs[1].label),
                                    showProjectSelector = true,
                                    showEnvSelector = true,
                                    viewModel = projectViewModel,
                                    behavior = pageBehavior,
                                    backdrop = pageBackdrop,
                                    isBlurActive = pageBackdrop != null
                                )
                                2 -> ProjectBar(
                                    title = stringResource(tabs[2].label),
                                    // 资源页仅在集合/环境子页显示项目选择（项目子页自带管理）
                                    showProjectSelector = tabState.resourceTab != 0,
                                    // 资源页-项目 tab 时顶栏显示"新建项目"
                                    onAddProject = if (tabState.resourceTab == 0) {
                                        { projectViewModel.openCreate() }
                                    } else {
                                        null
                                    },
                                    // 资源页-环境 tab 时顶栏显示"新建环境"
                                    onAddEnv = if (tabState.resourceTab == 2) {
                                        { envViewModel.openCreate() }
                                    } else {
                                        null
                                    },
                                    viewModel = projectViewModel,
                                    behavior = pageBehavior,
                                    backdrop = pageBackdrop,
                                    isBlurActive = pageBackdrop != null
                                )
                                3 -> ProjectBar(
                                    title = stringResource(tabs[3].label),
                                    onAddMonitor = { monitorViewModel.openCreate() },
                                    viewModel = projectViewModel,
                                    behavior = pageBehavior,
                                    backdrop = pageBackdrop,
                                    isBlurActive = pageBackdrop != null
                                )
                                else -> ProjectBar(
                                    title = stringResource(tabs[tab].label),
                                    viewModel = projectViewModel,
                                    behavior = pageBehavior,
                                    backdrop = pageBackdrop,
                                    isBlurActive = pageBackdrop != null
                                )
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(pageBehavior.nestedScrollConnection)
                                .then(
                                    if (pageBackdrop != null) {
                                        Modifier.layerBackdrop(pageBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            CompositionLocalProvider(
                                LocalTopBarInset provides padding.calculateTopPadding(),
                                LocalSubPageCanScroll provides tabCanScroll[tab]
                            ) {
                                when (tab) {
                                    0 -> DashboardScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenEncoding = { navController.pushRoute(PulseRoute.EncodingTool) },
                                        onOpenImageBase64 = { navController.pushRoute(PulseRoute.ImageBase64Tool) },
                                        onOpenJson = { navController.pushRoute(PulseRoute.JsonTool) },
                                        onOpenJsonSchema = {
                                            navController.pushRoute(PulseRoute.JsonSchemaTool)
                                        },
                                        onOpenYamlJson = {
                                            navController.pushRoute(PulseRoute.YamlJsonTool)
                                        },
                                        onOpenUrlParser = {
                                            navController.pushRoute(PulseRoute.UrlParserTool)
                                        },
                                        onOpenQrCode = {
                                            navController.pushRoute(PulseRoute.QrCodeTool)
                                        },
                                        onOpenTimestamp = { navController.pushRoute(PulseRoute.TimestampTool) },
                                        onOpenJwt = { navController.pushRoute(PulseRoute.JwtTool) },
                                        onOpenHash = { navController.pushRoute(PulseRoute.HashTool) },
                                        onOpenUuid = { navController.pushRoute(PulseRoute.UuidTool) },
                                        onOpenRegex = { navController.pushRoute(PulseRoute.RegexTool) },
                                        onOpenCron = { navController.pushRoute(PulseRoute.CronTool) },
                                        onOpenLanScan = { navController.pushRoute(PulseRoute.LanScanTool) },
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
                                        onOpenCookies = { navController.pushRoute(PulseRoute.Cookies) },
                                        onOpenHtmlPreview = { navController.pushRoute(PulseRoute.HtmlPreview) },
                                        httpViewModel = httpViewModel
                                    )
                                    2 -> ResourcesScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        resourceTab = tabState.resourceTab,
                                        onTabChange = { settingsViewModel.selectResourceTab(it) },
                                        onSwitchTab = { settingsViewModel.openHomeProtocol(it) },
                                        projectViewModel = projectViewModel,
                                        envViewModel = envViewModel,
                                        onOpenEnvVars = { navController.pushRoute(PulseRoute.EnvVars(it)) },
                                        onRunCollection = { navController.pushRoute(PulseRoute.CollectionRunner(it)) }
                                    )
                                    3 -> MonitorScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        viewModel = monitorViewModel,
                                        onOpenRecordsList = {
                                            monitorViewModel.openAllRecords()
                                            navController.pushRoute(PulseRoute.MonitorRecords)
                                        },
                                        onOpenTargetRecords = { target ->
                                            monitorViewModel.openRecordsForTarget(target)
                                            navController.pushRoute(PulseRoute.MonitorRecords)
                                        },
                                        onOpenTrend = { target ->
                                        monitorViewModel.openTrend(target)
                                        navController.pushRoute(PulseRoute.MonitorTrend(target.id))
                                        }
                                    )
                                    else -> SettingsScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        viewModel = settingsViewModel,
                                        onOpenAppearance = { navController.pushRoute(PulseRoute.Appearance) },
                                        onOpenWebhook = { navController.pushRoute(PulseRoute.Webhook) },
                                        onOpenAbout = { navController.pushRoute(PulseRoute.About) }
                                    )
                                }
                            }
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
            if (!current.value && topAppBarScrollBehavior.isExpanded) {
                topAppBarScrollBehavior.expand()
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
        // 滞后判定：顶栏一旦收缩（heightOffset<0）就保持可滚，避免收缩中内容视口变高
        // 导致 maxValue 归零、canScroll 翻转、强制复位展开的抖动循环
        lateinit var topAppBarScrollBehavior: AppTopBarBehavior
        topAppBarScrollBehavior = rememberAppTopBarBehavior {
            canScroll.value || !topAppBarScrollBehavior.isExpanded
        }
        // 二级页顶栏同样做实时磨砂：内容铺满整屏并从顶栏下方穿过，
        // 顶栏取样本页内容做模糊；顶部让位经 LocalTopBarInset 交给各页的滚动容器
        val backdrop = rememberBlurBackdrop()
        val blurActive = backdrop != null
        AppScaffold(
            topBar = {
                BlurredBar(backdrop, blurActive) {
                    AppTopBar(
                        title = title,
                        color = blurredBarColor(blurActive),
                        behavior = topAppBarScrollBehavior,
                        actions = actions,
                        navigationIcon = {
                            AppIconButton(onClick = { navController.pop() }) {
                                AppIcon(
                                    imageVector = MiuixIcons.Light.ChevronBackward,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                    )
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            ) {
                CompositionLocalProvider(
                    LocalTopBarInset provides paddingValues.calculateTopPadding(),
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
            if (!canScroll.value && topAppBarScrollBehavior.isExpanded) {
                topAppBarScrollBehavior.expand()
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

        entry<PulseRoute.Webhook> {
            SubPageScreen(title = stringResource(R.string.settings_webhook)) {
                WebhookScreen(
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
                    entryId = route.entryId,
                    onOpenDiff = { navController.pushRoute(PulseRoute.ResponseDiff(it)) },
                    onOpenHtmlPreview = { navController.pushRoute(PulseRoute.HtmlPreview) }
                )
            }
        }

        entry<PulseRoute.HtmlPreview> {
            SubPageScreen(title = stringResource(R.string.resp_view_html_preview)) {
                HtmlPreviewPage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = LocalTopBarInset.current)
                )
            }
        }

        entry<PulseRoute.ResponseDiff> { route ->
            SubPageScreen(title = stringResource(R.string.diff_title)) {
                ResponseDiffScreen(
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
                    MonitorRecordsFilterActions(viewModel = monitorViewModel)
                    AppIconButton(onClick = { monitorViewModel.requestClearRecords() }) {
                        AppIcon(
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

        entry<PulseRoute.MonitorTrend> { route ->
            SubPageScreen(
                title = stringResource(R.string.monitor_trend)
            ) {
                MonitorTrendScreen(
                    modifier = Modifier.fillMaxSize(),
                    targetId = route.targetId,
                    viewModel = monitorViewModel
                )
            }
        }

        entry<PulseRoute.ScriptEditor> { route ->
            // 脚本编辑页自带单行顶栏（返回/撤销/重做/文档），不复用会收缩的大标题 SubPageScreen
            ScriptEditorScreen(
                target = route.target,
                onBack = { navController.pop() },
                onOpenDocs = { navController.pushRoute(PulseRoute.ScriptDocs(route.target)) },
                modifier = Modifier.fillMaxSize(),
                viewModel = httpViewModel
            )
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
                    AppIconButton(onClick = { envViewModel.openVarEditor(route.envId, null) }) {
                        AppIcon(
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
                    AppIconButton(onClick = { historyViewModel.confirmClear = true }) {
                        AppIcon(
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

        entry<PulseRoute.Cookies> {
            val cookieViewModel: CookieViewModel = viewModel()
            SubPageScreen(
                title = stringResource(R.string.cookie_title),
                actions = {
                    AppIconButton(onClick = { cookieViewModel.openAdd() }) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Add,
                            contentDescription = stringResource(R.string.cd_add_cookie)
                        )
                    }
                    AppIconButton(
                        onClick = {
                            cookieViewModel.requestDelete(CookieDeleteRequest.Project)
                        }
                    ) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Delete,
                            contentDescription = stringResource(R.string.cookie_clear_all)
                        )
                    }
                }
            ) {
                CookieScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = cookieViewModel
                )
            }
        }

        entry<PulseRoute.EncodingTool> {
            SubPageScreen(title = stringResource(R.string.encoding_title)) {
                EncodingToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.ImageBase64Tool> {
            SubPageScreen(title = stringResource(R.string.image_base64_title)) {
                ImageBase64Screen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.JsonTool> {
            SubPageScreen(title = stringResource(R.string.json_tool_title)) {
                JsonToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.JsonSchemaTool> {
            SubPageScreen(title = stringResource(R.string.json_schema_title)) {
                JsonSchemaScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.YamlJsonTool> {
            SubPageScreen(title = stringResource(R.string.yaml_json_title)) {
                YamlJsonScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.UrlParserTool> {
            SubPageScreen(title = stringResource(R.string.url_parser_title)) {
                UrlParserScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.QrCodeTool> {
            SubPageScreen(title = stringResource(R.string.qr_title)) {
                QrCodeScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.TimestampTool> {
            SubPageScreen(title = stringResource(R.string.timestamp_tool_title)) {
                TimestampToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.JwtTool> {
            SubPageScreen(title = stringResource(R.string.jwt_tool_title)) {
                JwtToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.HashTool> {
            SubPageScreen(title = stringResource(R.string.hash_tool_title)) {
                HashToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.UuidTool> {
            SubPageScreen(title = stringResource(R.string.uuid_tool_title)) {
                UuidToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.RegexTool> {
            SubPageScreen(title = stringResource(R.string.regex_tool_title)) {
                RegexToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.CronTool> {
            SubPageScreen(title = stringResource(R.string.cron_tool_title)) {
                CronToolScreen(modifier = Modifier.fillMaxSize())
            }
        }

        entry<PulseRoute.LanScanTool> {
            SubPageScreen(title = stringResource(R.string.lan_scan_title)) {
                LanScanToolScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        entry<PulseRoute.CollectionRunner> { route ->
            val runnerViewModel: CollectionRunnerViewModel = viewModel()
            SubPageScreen(
                title = stringResource(R.string.runner_title),
                actions = {
                    AppIconButton(
                        onClick = { runnerViewModel.run(route.collectionId) },
                        enabled = !runnerViewModel.running
                    ) {
                        AppIcon(
                            imageVector = MiuixIcons.Light.Play,
                            contentDescription = stringResource(R.string.runner_run)
                        )
                    }
                }
            ) {
                androidx.compose.runtime.LaunchedEffect(route.collectionId) {
                    runnerViewModel.prepare(route.collectionId)
                }
                CollectionRunnerScreen(
                    collectionId = route.collectionId,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = runnerViewModel
                )
            }
        }
    }

    ToastHost()
}
