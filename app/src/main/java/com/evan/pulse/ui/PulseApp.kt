package com.evan.pulse.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.back.MiuixPredictiveBack
import com.evan.pulse.ui.common.ToastHost
import com.evan.pulse.ui.glass.LiquidGlassNavigationBar
import com.evan.pulse.ui.glass.liquidGlassBottomInset
import com.evan.pulse.ui.history.HistoryDetailScreen
import com.evan.pulse.ui.history.HistoryScreen
import com.evan.pulse.ui.history.HistoryViewModel
import com.evan.pulse.ui.home.HomeScreen
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.platform

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

@Composable
fun PulseApp(settingsViewModel: SettingsViewModel) {
    val tabs = remember {
        listOf(
            PulseTab(R.string.tab_home, Icons.Filled.Home),
            PulseTab(R.string.tab_resources, Icons.Filled.Folder),
            PulseTab(R.string.tab_monitor, Icons.Filled.NetworkCheck),
            PulseTab(R.string.tab_history, Icons.Filled.History),
            PulseTab(R.string.tab_settings, Icons.Filled.Settings)
        )
    }

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val nav by settingsViewModel.nav.collectAsStateWithLifecycle()

    /** 与历史页共用同一个 ViewModel，列表与详情看到的记录一致 */
    val historyViewModel: HistoryViewModel = viewModel()

    /** 与拨测二级页共用同一个 ViewModel，列表与记录页看到的数据一致 */
    val monitorViewModel: MonitorViewModel = viewModel()

    val previous = nav.previous
    val useMiuixBack = settings.backGestureModeIndex == 0

    /** 液态玻璃悬浮底栏开关：悬浮栏与玻璃效果同时开启时才生效 */
    val glassBottomBar = settings.floatingBottomBar && settings.liquidGlassBottomBar

    /** 只在一级标签页显示悬浮玻璃底栏 */
    val showGlassBar = glassBottomBar && nav.current == LEVEL_TABS

    /** 玻璃底栏的背景采样层：捕获页面内容供胶囊内的模糊与折射取样；
     *  运行时不支持 RuntimeShader 时返回 null，底栏退化为纯色胶囊 */
    val backdrop: LayerBackdrop? = if (showGlassBar && isRuntimeShaderSupported()) {
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

    /** 只把当前显示的页面写入采样层，避免预测返回的上一屏重复写入同一层 */
    fun backdropModifier(page: Int): Modifier =
        if (page == nav.current && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier

    val pageContent: @Composable (Int) -> Unit = { page ->
        when (page) {
            LEVEL_APPEARANCE -> ThemeSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = settingsViewModel
            )

            LEVEL_HISTORY_DETAIL -> HistoryDetailScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = historyViewModel,
                entryId = nav.historyEntryId ?: 0L
            )

            LEVEL_MONITOR_RECORDS -> MonitorRecordsScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = monitorViewModel
            )

            LEVEL_SCRIPT_EDITOR -> ScriptEditorScreen(
                target = nav.scriptTarget ?: 0,
                modifier = Modifier.fillMaxSize(),
                onOpenScriptDocs = { settingsViewModel.pushScriptDocs(nav.scriptTarget ?: 0) }
            )

            LEVEL_SCRIPT_DOCS -> ScriptDocsScreen(
                target = nav.scriptTarget ?: 0,
                modifier = Modifier.fillMaxSize()
            )

            else -> when (nav.selectedTab) {
                0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenScriptEditor = { settingsViewModel.pushScriptEditor(it) }
                )
                1 -> ResourcesScreen(
                    modifier = Modifier.fillMaxSize(),
                    onSwitchTab = { settingsViewModel.selectTab(it) }
                )
                2 -> MonitorScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = monitorViewModel,
                    onOpenRecordsList = { settingsViewModel.push(LEVEL_MONITOR_RECORDS) }
                )
                3 -> HistoryScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenDetail = { settingsViewModel.pushHistoryDetail(it) }
                )
                else -> SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = settingsViewModel,
                    onOpenAppearance = { settingsViewModel.push(LEVEL_APPEARANCE) }
                )
            }
        }
    }

    val screenTopBar: @Composable (Int) -> Unit = { page ->
        when (page) {
            LEVEL_APPEARANCE -> TopAppBar(
                title = stringResource(R.string.theme_page_title),
                navigationIcon = {
                    IconButton(onClick = { settingsViewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )

            LEVEL_HISTORY_DETAIL -> TopAppBar(
                title = stringResource(R.string.history_detail_title),
                navigationIcon = {
                    IconButton(onClick = { settingsViewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )

            LEVEL_MONITOR_RECORDS -> TopAppBar(
                title = stringResource(R.string.monitor_records_title),
                navigationIcon = {
                    IconButton(onClick = { settingsViewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )

            LEVEL_SCRIPT_EDITOR -> TopAppBar(
                title = stringResource(
                    if (nav.scriptTarget == 0) R.string.script_tab_pre else R.string.script_tab_post
                ),
                navigationIcon = {
                    IconButton(onClick = { settingsViewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )

            LEVEL_SCRIPT_DOCS -> TopAppBar(
                title = stringResource(R.string.script_docs_title),
                navigationIcon = {
                    IconButton(onClick = { settingsViewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )

            else -> ProjectBar(
                title = stringResource(tabs[nav.selectedTab].label),
                showProjectSelector = nav.selectedTab <= 1,
                showEnvSelector = nav.selectedTab == 0,
                onAddMonitor = if (nav.selectedTab == 2) {
                    { monitorViewModel.openCreate() }
                } else {
                    null
                }
            )
        }
    }

    val screenBottomBar: @Composable () -> Unit = {
        if (settings.floatingBottomBar) {
            FloatingNavigationBar(color = MiuixTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { index, tab ->
                    FloatingNavigationBarItem(
                        selected = nav.selectedTab == index,
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
                        selected = nav.selectedTab == index,
                        onClick = { settingsViewModel.selectTab(index) },
                        icon = tab.icon,
                        label = stringResource(tab.label)
                    )
                }
            }
        }
    }

    /** 完整的一屏：顶栏、底栏与内容区。采样层挂在覆盖整块 Scaffold 的外层，
     *  胶囊才能模糊到页面内容；[inner] 决定内容区如何渲染 */
    val screenFor: @Composable (Int, @Composable (Int) -> Unit) -> Unit = { page, inner ->
        Scaffold(
            topBar = { screenTopBar(page) },
            bottomBar = {
                if (page == LEVEL_TABS) {
                    if (glassBottomBar) {
                        LiquidGlassNavigationBar(
                            items = navigationItems,
                            selectedIndex = nav.selectedTab,
                            onItemClick = { settingsViewModel.selectTab(it) },
                            backdrop = backdrop,
                            isBlurActive = backdrop != null
                        )
                    } else {
                        screenBottomBar()
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().then(backdropModifier(page))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = contentBottomPadding(
                                paddingValues,
                                page,
                                settings.floatingBottomBar,
                                glassBottomBar
                            )
                        )
                ) {
                    inner(page)
                }
            }
        }
    }

    LocalizedRoot(language = fromIndex(settings.languageIndex)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (useMiuixBack) {
                MiuixPredictiveBack(
                    modifier = Modifier.fillMaxSize(),
                    enabled = previous != null,
                    onBack = { settingsViewModel.goBack() },
                    previous = { screenFor(previous ?: nav.current) { pageContent(it) } }
                ) {
                    screenFor(nav.current) { pageContent(it) }
                }
            } else {
                BackHandler(enabled = previous != null) {
                    settingsViewModel.goBack()
                }
                screenFor(nav.current) {
                    PageTransition(
                        modifier = Modifier.fillMaxSize(),
                        current = nav.current
                    ) { pageContent(it) }
                }
            }

            ToastHost()
        }
    }
}

/** 悬浮底栏（非玻璃版）从屏幕底边占用的竖向空间：栏高 52dp + 底部安全区，
 *  取值与 miuix FloatingNavigationBar 内部一致，用于给页面内容留位 */
@Composable
private fun floatingNavigationBarInset(): Dp {
    val navInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    return 52.dp + if (navInset != 0.dp) 26.dp + navInset else 36.dp
}

/** 悬浮底栏浮在内容之上，内容底部再多留一段，最后一行才不会贴到栏上沿或被压住 */
private val floatingBarSlack: Dp = 24.dp

/** 页面内容底部的留位：标签页按悬浮底栏的实际高度预留，避免最后一条被底栏遮住 */
@Composable
private fun contentBottomPadding(
    paddingValues: PaddingValues,
    page: Int,
    floating: Boolean,
    glass: Boolean,
): Dp {
    val scaffold = paddingValues.calculateBottomPadding()
    return when {
        page != LEVEL_TABS -> scaffold
        glass -> scaffold.coerceAtLeast(liquidGlassBottomInset() + floatingBarSlack)
        floating -> scaffold.coerceAtLeast(floatingNavigationBarInset() + floatingBarSlack)
        else -> scaffold
    }
}
