package com.evan.pulse.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles

/** Material maps the selected Miuix-generated palette onto Material 3 color roles. */
fun materialColorScheme(colors: Colors, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.primary,
        onTertiary = colors.onPrimary,
        tertiaryContainer = colors.primaryContainer,
        onTertiaryContainer = colors.onPrimaryContainer,
        inversePrimary = colors.primaryContainer,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onBackgroundVariant,
        surfaceContainer = colors.surfaceContainer,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        outline = colors.outline,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
    ) else lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.primary,
        onTertiary = colors.onPrimary,
        tertiaryContainer = colors.primaryContainer,
        onTertiaryContainer = colors.onPrimaryContainer,
        inversePrimary = colors.primary,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onBackgroundVariant,
        surfaceContainer = colors.surfaceContainer,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        outline = colors.outline,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
    )
    return if (pureBlack && dark) base.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color.Black,
        surfaceBright = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainerLowest = Color.Black,
    )
    else base
}

/**
 * Material 字体层级：从 Miuix 文本样式按用途映射，而不是套用 M3 的默认字号。
 *
 * M3 默认 bodyLarge 是 16sp、Miuix 的正文是 main 17sp；直接取 M3 默认值会让
 * 换肤后所有正文缩小一号，行高与卡片高度跟着变。这里每个角色都取同用途的
 * Miuix 样式，保证换肤只改变字体角色，不改变文本度量。
 */
fun materialTypography(styles: TextStyles): Typography = Typography(
    displayLarge = styles.title1,
    displayMedium = styles.title2,
    displaySmall = styles.title3,
    headlineLarge = styles.title1,
    headlineMedium = styles.title2,
    headlineSmall = styles.title3,
    titleLarge = styles.title4,
    titleMedium = styles.headline1,
    titleSmall = styles.subtitle,
    bodyLarge = styles.main,
    bodyMedium = styles.body2,
    bodySmall = styles.footnote1,
    labelLarge = styles.button,
    labelMedium = styles.body2,
    labelSmall = styles.footnote2,
)

/**
 * Material 皮肤令牌：颜色取 M3 角色，字体取 [materialTypography] 的映射结果。
 *
 * 页面仍然只认 [AppTokens] 的语义角色，因此 P2/P3 迁移组件时不需要区分皮肤。
 */
fun materialAppTokens(scheme: ColorScheme, typography: Typography): AppTokens = AppTokens(
    colors = AppColors(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        secondary = scheme.secondary,
        onSecondary = scheme.onSecondary,
        background = scheme.background,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        surface = scheme.surface,
        onSurface = scheme.onSurface,
        surfaceVariant = scheme.surfaceVariant,
        onSurfaceSecondary = scheme.onSurfaceVariant,
        onSurfaceSummary = scheme.onSurfaceVariant,
        surfaceContainer = scheme.surfaceContainer,
        onSurfaceContainer = scheme.onSurface,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        onSurfaceContainerHigh = scheme.onSurface,
        outline = scheme.outline,
        divider = scheme.outlineVariant,
        error = scheme.error,
        onError = scheme.onError,
        disabledContent = scheme.onSurface.copy(alpha = 0.38f),
    ),
    typography = AppTypography(
        main = typography.bodyLarge,
        paragraph = typography.bodyLarge,
        body1 = typography.bodyLarge,
        body2 = typography.bodyMedium,
        button = typography.labelLarge,
        footnote1 = typography.bodySmall,
        footnote2 = typography.labelSmall,
        headline1 = typography.titleMedium,
        headline2 = typography.titleMedium,
        subtitle = typography.titleSmall,
        title1 = typography.headlineLarge,
        title2 = typography.headlineMedium,
        title3 = typography.headlineSmall,
        title4 = typography.titleLarge,
    ),
)
