package com.evan.pulse.ui.l10n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource

/**
 * 应用内可选语言。
 *
 * SYSTEM 走系统语言，其余为固定 locale；entries 下标即 SettingsStore 中持久化的值，
 * 顺序必须与 SettingsScreen 中的 LanguageLabels 保持一致。
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ZH_CN("zh-CN"),
    ZH_TW("zh-Hant-TW"),
    EN("en"),
}

/** 下标越界或历史数据缺失时回退到简体中文，避免出现空白文案 */
fun fromIndex(index: Int): AppLanguage =
    AppLanguage.entries.getOrElse(index) { AppLanguage.ZH_CN }

/**
 * 按固定 locale 派生出一个新的 Context，供 stringResource 解析本地化资源。
 *
 * 用 createConfigurationContext 而不是 AppCompatDelegate.setApplicationLocales：
 * 后者要求配置 android:localeConfig 并使用 AppCompatActivity，本项目是纯 ComponentActivity。
 * 每次都从最新配置重建，避免缓存住旧的系统语言。
 *
 * minSdk 26 高于 API 24，直接用 setLocales(LocaleList)，
 * 不再需要区分 SDK 分支处理旧的 setLocale(Locale)。
 */
fun Context.localeContext(language: AppLanguage): Context {
    val tag = language.tag ?: return this

    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList(java.util.Locale.forLanguageTag(tag)))
    return createConfigurationContext(config)
}

/**
 * 为整棵子树切换资源语言。
 *
 * stringResource 实际读取 LocalResources，其默认值来自 LocalContext，
 * 因此两者同时提供，确保覆盖对 Miuix 等第三方组件内部文案同样生效。
 */
@Composable
fun LocalizedRoot(language: AppLanguage, content: @Composable () -> Unit) {
    val localized = LocalContext.current.localeContext(language)

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources
    ) {
        content()
    }
}

/** 把资源 id 列表解析成当前语言下的文案，供 OverlayDropdownPreference 等组件使用 */
@Composable
fun List<Int>.localizedLabels(): List<String> = map { stringResource(it) }
