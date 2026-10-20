package com.evan.pulse.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

/**
 * 设计令牌：页面只通过这里取颜色、字号、间距与圆角，不直接读 MiuixTheme。
 *
 * 这一层是换皮肤的基础：两套皮肤共享同一套语义角色，实现各自取值。
 * 语义按用途命名（surfaceContainer / onSurfaceSummary），不按控件命名，
 * 这样 Material 皮肤可以把同一角色映射到自己的 colorScheme。
 */
@Immutable
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceSecondary: Color,
    /** 摘要、提示、次要说明文字 */
    val onSurfaceSummary: Color,
    val surfaceContainer: Color,
    val onSurfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurfaceContainerHigh: Color,
    val outline: Color,
    val divider: Color,
    val error: Color,
    val onError: Color,
    val disabledContent: Color,
)

/** 字号体系：与当前 Miuix 文本样式一一对应，换皮肤时按层级映射，避免基线平移 */
@Immutable
data class AppTypography(
    val main: TextStyle,
    val paragraph: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val button: TextStyle,
    val footnote1: TextStyle,
    val footnote2: TextStyle,
    val headline1: TextStyle,
    val headline2: TextStyle,
    val subtitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val title4: TextStyle,
)

/** 间距体系：4dp 网格。页面外边距与卡片内容边距单独列出，避免每次手写 12/16 */
@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** 页面左右边距 */
    val pageHorizontal: Dp = 12.dp,
    /** 卡片内 preference 内容的左右边距 */
    val cardContent: Dp = 16.dp,
    /** 卡片内 preference 内容的上下边距 */
    val cardContentVertical: Dp = 16.dp,
    /** 区块标题左右边距，与 cardContent 对齐 */
    val sectionTitle: Dp = 16.dp,
    /** 区块标题上下边距 */
    val sectionTitleVertical: Dp = 8.dp,
)

/** 圆角体系 */
@Immutable
data class AppRadii(
    val card: Dp = 16.dp,
    val control: Dp = 12.dp,
    val chip: Dp = 8.dp,
    val dialog: Dp = 28.dp,
)

@Immutable
data class AppTokens(
    val colors: AppColors,
    val typography: AppTypography,
    val spacing: AppSpacing = AppSpacing(),
    val radii: AppRadii = AppRadii(),
)

/**
 * Miuix 皮肤的令牌：直接取当前 Miuix 配色与文本样式。
 *
 * 不做 remember 缓存：Miuix 的 Colors/TextStyles 是原地更新的可变状态对象，
 * 按对象身份做缓存键会在切换配色后拿到旧值。数据类按值比较，值不变时
 * 不会引发下游重组，直接构造的代价可以忽略。
 *
 * Material 皮肤的对应实现在 [materialAppTokens]。
 */
@Composable
fun currentAppTokens(): AppTokens = appTokensOf(MiuixTheme.colorScheme, MiuixTheme.textStyles)

private fun appTokensOf(colors: Colors, textStyles: TextStyles): AppTokens = AppTokens(
    colors = AppColors(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        background = colors.background,
        onBackground = colors.onBackground,
        onBackgroundVariant = colors.onBackgroundVariant,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceSecondary = colors.onSurfaceSecondary,
        onSurfaceSummary = colors.onSurfaceVariantSummary,
        surfaceContainer = colors.surfaceContainer,
        onSurfaceContainer = colors.onSurfaceContainer,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        onSurfaceContainerHigh = colors.onSurfaceContainerHigh,
        outline = colors.outline,
        divider = colors.dividerLine,
        error = colors.error,
        onError = colors.onError,
        disabledContent = colors.disabledOnSurface,
    ),
    typography = AppTypography(
        main = textStyles.main,
        paragraph = textStyles.paragraph,
        body1 = textStyles.body1,
        body2 = textStyles.body2,
        button = textStyles.button,
        footnote1 = textStyles.footnote1,
        footnote2 = textStyles.footnote2,
        headline1 = textStyles.headline1,
        headline2 = textStyles.headline2,
        subtitle = textStyles.subtitle,
        title1 = textStyles.title1,
        title2 = textStyles.title2,
        title3 = textStyles.title3,
        title4 = textStyles.title4,
    ),
)

/** 主题层提供的令牌；未提供时（预览等）回退到当前 Miuix 主题 */
val LocalAppTokens = staticCompositionLocalOf<AppTokens?> { null }

/** 令牌访问入口，写法与 MaterialTheme 对齐 */
object AppTheme {
    val tokens: AppTokens
        @Composable
        get() = LocalAppTokens.current ?: currentAppTokens()

    val colors: AppColors
        @Composable
        get() = tokens.colors

    val typography: AppTypography
        @Composable
        get() = tokens.typography

    val spacing: AppSpacing
        @Composable
        get() = tokens.spacing

    val radii: AppRadii
        @Composable
        get() = tokens.radii
}
