package com.evan.pulse.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.evan.pulse.R
import com.evan.pulse.data.settings.AppSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 未开启动态取色时的兜底种子色 */
private const val FALLBACK_SEED = 0xFF6750A4.toInt()

/** 外观模式：前三个为固定配色，后三个为莫奈取色 */
val ThemeModeEntries: List<Pair<Int, ColorSchemeMode>> = listOf(
    R.string.appearance_system to ColorSchemeMode.System,
    R.string.appearance_dark to ColorSchemeMode.Dark,
    R.string.appearance_light to ColorSchemeMode.Light,
    R.string.appearance_monet_system to ColorSchemeMode.MonetSystem,
    R.string.appearance_monet_dark to ColorSchemeMode.MonetDark,
    R.string.appearance_monet_light to ColorSchemeMode.MonetLight
)

val ThemeModeLabels: List<Int> = ThemeModeEntries.map { it.first }

private val MonetModes = setOf(
    ColorSchemeMode.MonetSystem,
    ColorSchemeMode.MonetLight,
    ColorSchemeMode.MonetDark
)

fun isMonetMode(index: Int): Boolean =
    ThemeModeEntries.getOrElse(index) { ThemeModeEntries[0] }.second in MonetModes

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

    val mode = ThemeModeEntries.getOrElse(settings.themeModeIndex) { ThemeModeEntries[0] }.second
    val paletteStyle = PaletteStyleEntries.getOrElse(settings.paletteStyleIndex) {
        PaletteStyleEntries[0]
    }.second
    val colorSpec = ColorSpecEntries.getOrElse(settings.colorSpecIndex) {
        ColorSpecEntries[0]
    }.second

    // 莫奈模式下始终提供种子色：选中具体主题色用该色，否则取系统种子色。
    // 只有传入了种子色，调色板风格与色彩规范才参与配色计算。
    val keyColor: Color? = when {
        !isMonetMode(settings.themeModeIndex) -> null
        settings.keyColorIndex <= 0 -> systemSeedColor(context)
        else -> ThemeKeyColors.getOrNull(settings.keyColorIndex - 1)?.second
    }

    val controller = remember(mode, keyColor, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            colorSpec = colorSpec,
            paletteStyle = paletteStyle
        )
    }

    MiuixTheme(controller = controller, content = content)
}
