package com.evan.pulse.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.evan.pulse.R
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.ui.common.BarBlurStyle
import com.evan.pulse.ui.common.LocalBarBlurStyle
import com.evan.pulse.ui.common.LocalBlurEnabled
import com.evan.pulse.ui.design.LocalAppTokens
import com.evan.pulse.ui.design.LocalUiCapabilities
import com.evan.pulse.ui.design.LocalUiStyle
import com.evan.pulse.ui.design.UiStyle
import com.evan.pulse.ui.design.UiStyleEntries
import com.evan.pulse.ui.design.capabilities
import com.evan.pulse.ui.design.currentAppTokens
import com.evan.pulse.ui.design.materialAppTokens
import com.evan.pulse.ui.design.materialColorScheme
import com.evan.pulse.ui.design.materialTypography
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 未开启动态取色时的兜底种子色 */
private const val FALLBACK_SEED = 0xFF6750A4.toInt()

/** Appearance mode is independent of Monet color generation. */
val ThemeModeEntries: List<Pair<Int, ColorSchemeMode>> = listOf(
    R.string.appearance_system to ColorSchemeMode.System,
    R.string.appearance_dark to ColorSchemeMode.Dark,
    R.string.appearance_light to ColorSchemeMode.Light
)

val ThemeModeLabels: List<Int> = ThemeModeEntries.map { it.first }

/**
 * 外观模式在下拉里的展示顺序：跟随系统、浅色、深色。
 *
 * 这里只调整展示位置，[ThemeModeEntries] 的下标即持久化值，保持不变以免已存设置被改写。
 */
val ThemeModeDisplayOrder: List<Int> = listOf(0, 2, 1)

/** 自定义主题色，下标 1 起对应；index 0 表示跟随系统取色 */
val ThemeKeyColors: List<Pair<Int, Color>> = listOf(
    R.string.color_blue to Color(0xFF3482FF),
    R.string.color_green to Color(0xFF36D167),
    R.string.color_purple to Color(0xFF7C4DFF),
    R.string.color_yellow to Color(0xFFFFB21D),
    R.string.color_orange to Color(0xFFFF5722),
    R.string.color_pink to Color(0xFFE91E63),
    R.string.color_cyan to Color(0xFF00BCD4)
)

val PaletteStyleEntries: List<Pair<Int, ThemePaletteStyle>> = listOf(
    R.string.palette_tonal_spot to ThemePaletteStyle.TonalSpot,
    R.string.palette_neutral to ThemePaletteStyle.Neutral,
    R.string.palette_vibrant to ThemePaletteStyle.Vibrant,
    R.string.palette_expressive to ThemePaletteStyle.Expressive,
    R.string.palette_rainbow to ThemePaletteStyle.Rainbow,
    R.string.palette_fruit_salad to ThemePaletteStyle.FruitSalad,
    R.string.palette_monochrome to ThemePaletteStyle.Monochrome,
    R.string.palette_fidelity to ThemePaletteStyle.Fidelity,
    R.string.palette_content to ThemePaletteStyle.Content
)

val PaletteStyleLabels: List<Int> = PaletteStyleEntries.map { it.first }

val ColorSpecEntries: List<Pair<Int, ThemeColorSpec>> = listOf(
    R.string.spec_material_2021 to ThemeColorSpec.Spec2021,
    R.string.spec_expressive_2025 to ThemeColorSpec.Spec2025
)

val ColorSpecLabels: List<Int> = ColorSpecEntries.map { it.first }

/** 顶栏模糊样式：高斯为整条均匀模糊，渐进为顶边最强向下渐弱 */
val BarBlurStyleEntries: List<Pair<Int, BarBlurStyle>> = listOf(
    R.string.blur_style_gaussian to BarBlurStyle.GAUSSIAN,
    R.string.blur_style_progressive to BarBlurStyle.PROGRESSIVE
)

val BarBlurStyleLabels: List<Int> = BarBlurStyleEntries.map { it.first }

/**
 * 读取系统的莫奈种子色，用于「跟随系统取色」。
 *
 * 直接取回种子色而不是让主题库内部取色，这样用户选择的调色板风格与色彩规范
 * 才会在跟随系统取色时继续生效。
 *
 * 优先级：Android 13+ 的个性化壁纸种子色 -> Android 11+ 的系统强调色 -> 兜底色。
 */
private fun systemSeedColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val overlay = readThemeCustomizationSeed(context)
        if (overlay != null) return Color(overlay)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Color(context.getColor(android.R.color.system_accent1_500))
    }

    return Color(FALLBACK_SEED)
}

private fun readThemeCustomizationSeed(context: Context): Int? {
    return try {
        val json = Settings.Secure.getString(
            context.contentResolver,
            "theme_customization_overlay_packages"
        ) ?: return null

        val jsonObject = org.json.JSONObject(json)
        val hex = jsonObject.optString("android.theme.customization.system_palette", "")
            .trim()
            .removePrefix("#")
        if (hex.length < 6) return null

        AndroidColor.parseColor("#$hex")
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PulseTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current

    val uiStyle = UiStyleEntries.getOrElse(settings.uiStyleIndex) { UiStyleEntries[0] }.second
    val monet = uiStyle == UiStyle.MIUIX && settings.monetEnabled
    val paletteEnabled = uiStyle == UiStyle.MATERIAL || monet
    val appearance = ThemeModeEntries.getOrElse(settings.themeModeIndex) { ThemeModeEntries[0] }.second
    val mode = if (paletteEnabled) {
        when (appearance) {
            ColorSchemeMode.Dark -> ColorSchemeMode.MonetDark
            ColorSchemeMode.Light -> ColorSchemeMode.MonetLight
            else -> ColorSchemeMode.MonetSystem
        }
    } else appearance
    val paletteStyle = PaletteStyleEntries.getOrElse(settings.paletteStyleIndex) {
        PaletteStyleEntries[0]
    }.second
    val colorSpec = ColorSpecEntries.getOrElse(settings.colorSpecIndex) {
        ColorSpecEntries[0]
    }.second

    // 莫奈模式下始终提供种子色：选中具体主题色用该色，否则取系统种子色。
    // 只有传入了种子色，调色板风格与色彩规范才参与配色计算。
    val keyColor: Color? = when {
        uiStyle == UiStyle.MATERIAL && settings.keyColorIndex <= 0 -> systemSeedColor(context)
        monet && settings.keyColorIndex <= 0 -> systemSeedColor(context)
        uiStyle == UiStyle.MATERIAL || monet ->
            ThemeKeyColors.getOrNull(settings.keyColorIndex - 1)?.second
        else -> null
    }

    val controller = remember(mode, keyColor, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            colorSpec = colorSpec,
            paletteStyle = paletteStyle
        )
    }

    // 纯黑背景仅在深色模式下生效，动态取色模式的深色状态也覆盖
    val systemDark = isSystemInDarkTheme()
    val darkActive = when (mode) {
        ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> controller.isDark ?: systemDark
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        else -> false
    }
    val baseColors = controller.currentColors()
    val colors = if (settings.pureBlackInDark && darkActive) {
        // 纯黑模式：背景、surface 以及卡片/容器类表面一并压成纯黑，
        // 卡片与页面融为一体（与其他 App 的纯黑观感一致）；
        // 分隔线/描边保持原色，仍能看出卡片边界与层次
        baseColors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
        )
    } else {
        baseColors
    }

    // 界面缩放：整体按比例放大或缩小 dp 与 sp，范围 80%..120%
    val baseDensity = LocalDensity.current
    val scale = settings.uiScalePercent.coerceIn(80, 120) / 100f
    val scaledDensity = remember(baseDensity, scale) {
        Density(
            density = baseDensity.density * scale,
            fontScale = baseDensity.fontScale * scale
        )
    }

    val blurStyle = BarBlurStyleEntries.getOrElse(settings.barBlurStyleIndex) {
        BarBlurStyleEntries[0]
    }.second

    val capabilities = uiStyle.capabilities()
    val scheme = materialColorScheme(colors, darkActive, settings.pureBlackInDark)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalBlurEnabled provides (settings.blurEnabled && capabilities.blurBars),
        LocalBarBlurStyle provides blurStyle,
        LocalUiStyle provides uiStyle,
        LocalUiCapabilities provides capabilities,
        MiuixLocalContentColor provides if (uiStyle == UiStyle.MATERIAL) {
            scheme.onBackground
        } else colors.onBackground
    ) {
        MiuixTheme(colors = colors) {
            val typography = materialTypography(MiuixTheme.textStyles)
            val tokens = when (uiStyle) {
                UiStyle.MIUIX -> currentAppTokens()
                UiStyle.MATERIAL -> materialAppTokens(scheme, typography)
            }
            // Keep the content at one composition location so changing skins preserves navigation.
            MaterialTheme(colorScheme = scheme, typography = typography) {
                CompositionLocalProvider(
                    LocalAppTokens provides tokens,
                    content = content
                )
            }
        }
    }
}
