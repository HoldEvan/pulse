package com.evan.pulse.ui.about

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.BuildConfig
import com.evan.pulse.R
import com.evan.pulse.ui.about.effect.BgEffectBackground
import com.evan.pulse.ui.common.BlurredBar
import com.evan.pulse.ui.common.blurredBarColor
import com.evan.pulse.ui.common.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 关于页：参考 miuix example 的 AboutPage 布局。
 *
 * 头部是应用图标 + 名称 + 版本，随滚动淡出缩小；顶栏标题以同一进度渐显，
 * 与内容形成一体的过渡。下方三张卡片：项目地址、开源组件、构建信息。
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    // App 图标：adaptive icon（mipmap XML）无法直接 painterResource，运行时画到位图
    val appIcon = remember {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    // 滚动进度：以 logoSpacer 占位 item 的滚出偏移为满进度（与 example 一致）
    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    // 磨砂只在滚动满一屏后激活：未滚动时顶栏完全透明，与头部融为一体
    val blurActive = backdrop != null && scrollProgress == 1f
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        if (scrollProgress == 1f) MiuixTheme.colorScheme.surface else Color.Transparent
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                SmallTopAppBar(
                    title = stringResource(R.string.about_title),
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(
                        alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    ),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
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
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        ) {
        // 全屏铺底的动态背景：背景与 logo 都固定不随内容滚动，卡片从 logo 下方滚上来
        BgEffectBackground(
            dynamicBackground = true,
            modifier = Modifier.fillMaxSize(),
            isFullSize = true,
            alpha = { 1f - scrollProgress }
        ) {
            // logo 段固定绘制在背景上部，随滚动分阶段淡出
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = paddingValues.calculateTopPadding() + 92.dp)
                        .onSizeChanged { with(density) { logoHeightDp = it.height.toDp() } },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                AboutHeader(
                    scrollProgress = { scrollProgress },
                    appIcon = appIcon
                )
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 24.dp
                )
            ) {
                // 透明占位：匹配 logo 段在视口中的高度，卡片从其后开始滚动
                item(key = "logoSpacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(logoHeightDp + 218.dp)
                    )
                }

            item(key = "about") {
                // 最小高度撑满一屏：保证内容不足一屏时也能把 logo 完全滚出、进度到 1
                Box {
                    Spacer(modifier = Modifier.fillParentMaxHeight())
                    Column {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp)
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.about_source),
                                endActions = {
                                    Text(
                                        text = "GitHub",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = { uriHandler.openUri("https://github.com/HoldEvan/pulse") }
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            ArrowPreference(
                                title = "Miuix",
                                summary = "Apache-2.0",
                                onClick = { uriHandler.openUri("https://github.com/miuix-kotlin-multiplatform/miuix") }
                            )
                            ArrowPreference(
                                title = "Ktor",
                                summary = "Apache-2.0",
                                onClick = { uriHandler.openUri("https://github.com/ktorio/ktor") }
                            )
                            ArrowPreference(
                                title = "Paho MQTT",
                                summary = "EPL-1.0 / EDL-1.0",
                                onClick = { uriHandler.openUri("https://github.com/eclipse/paho.mqtt.java") }
                            )
                            ArrowPreference(
                                title = "Rhino",
                                summary = "MPL-2.0",
                                onClick = { uriHandler.openUri("https://github.com/mozilla/rhino") }
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.about_agreement),
                                onClick = onOpenAgreement
                            )
                            ArrowPreference(
                                title = stringResource(R.string.about_privacy),
                                onClick = onOpenPrivacy
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.about_build_info,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE,
                                    buildTimeText()
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
        }
        }
    }
}

/** 头部：App 图标、应用名、版本号，随滚动按版本 → 名称 → 图标的次序分阶段淡出 */
@Composable
private fun AboutHeader(
    scrollProgress: () -> Float,
    appIcon: ImageBitmap
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = appIcon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val iconProgress = ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                    clip = true
                    shape = RoundedCornerShape(22.dp)
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * 0.05f
                    scaleY = 1f - iconProgress * 0.05f
                }
        )
        Text(
            text = "Pulse",
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .graphicsLayer {
                    val nameProgress = ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - nameProgress
                    scaleX = 1f - nameProgress * 0.05f
                    scaleY = 1f - nameProgress * 0.05f
                },
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp
        )
        Text(
            text = stringResource(
                R.string.version_value,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            ),
            modifier = Modifier.graphicsLayer {
                val versionProgress = ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - versionProgress
            },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp
        )
    }
}

/** 构建时间：设置页版本卡迁来，本地时区格式化 */
private fun buildTimeText(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(BuildConfig.BUILD_TIME_MS))
