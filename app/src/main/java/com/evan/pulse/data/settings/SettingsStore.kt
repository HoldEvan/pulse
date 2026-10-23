package com.evan.pulse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "pulse_settings")

private val KEY_UI_STYLE = intPreferencesKey("ui_style_index")
private val KEY_THEME_MODE = intPreferencesKey("theme_mode_index")
private val KEY_MONET_ENABLED = booleanPreferencesKey("monet_enabled")
private val KEY_COLOR = intPreferencesKey("key_color_index")
private val KEY_PALETTE = intPreferencesKey("palette_style_index")
private val KEY_COLOR_SPEC = intPreferencesKey("color_spec_index")
private val KEY_BACK_GESTURE = intPreferencesKey("back_gesture_mode_index")
private val KEY_LANGUAGE = intPreferencesKey("language_index")
private val KEY_PROXY_TYPE = intPreferencesKey("proxy_type_index")
private val KEY_PROXY_HOST = stringPreferencesKey("proxy_host")
private val KEY_PROXY_PORT = stringPreferencesKey("proxy_port")
private val KEY_PROXY_USER = stringPreferencesKey("proxy_username")
private val KEY_PROXY_PASS = stringPreferencesKey("proxy_password")
private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
private val KEY_WEBDAV_PATH = stringPreferencesKey("webdav_path")
private val KEY_WEBDAV_USER = stringPreferencesKey("webdav_username")
private val KEY_WEBDAV_PASS = stringPreferencesKey("webdav_password")
private val KEY_MONITOR_NOTIFY = booleanPreferencesKey("monitor_notify")
private val KEY_FLOATING_BOTTOM_BAR = booleanPreferencesKey("floating_bottom_bar")
private val KEY_LIQUID_GLASS_BOTTOM_BAR = booleanPreferencesKey("liquid_glass_bottom_bar")
private val KEY_BOTTOM_BAR_LABEL_VISIBLE = booleanPreferencesKey("bottom_bar_label_visible")
private val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
private val KEY_BAR_BLUR_STYLE = intPreferencesKey("bar_blur_style_index")
private val KEY_PURE_BLACK_IN_DARK = booleanPreferencesKey("pure_black_in_dark")
private val KEY_UI_SCALE_PERCENT = intPreferencesKey("ui_scale_percent")
private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
private val KEY_AUTO_BACKUP_INTERVAL = intPreferencesKey("auto_backup_interval_index")
private val KEY_AUTO_BACKUP_KEEP = intPreferencesKey("auto_backup_keep_count")

internal fun Preferences.toAppSettings(): AppSettings = AppSettings(
    uiStyleIndex = (get(KEY_UI_STYLE) ?: 0).coerceAtLeast(0),
    themeModeIndex = when (val index = get(KEY_THEME_MODE) ?: 0) {
        in 0..2 -> index
        in 3..5 -> index - 3
        else -> 0
    },
    monetEnabled = get(KEY_MONET_ENABLED) ?: ((get(KEY_THEME_MODE) ?: 0) in 3..5),
    keyColorIndex = (get(KEY_COLOR) ?: 0).coerceAtLeast(0),
    paletteStyleIndex = (get(KEY_PALETTE) ?: 0).coerceAtLeast(0),
    colorSpecIndex = (get(KEY_COLOR_SPEC) ?: 0).coerceAtLeast(0),
    backGestureModeIndex = (get(KEY_BACK_GESTURE) ?: 0).coerceAtLeast(0),
    languageIndex = (get(KEY_LANGUAGE) ?: 0).coerceAtLeast(0),
    proxyTypeIndex = (get(KEY_PROXY_TYPE) ?: 0).coerceAtLeast(0),
    proxyHost = get(KEY_PROXY_HOST) ?: "",
    proxyPort = get(KEY_PROXY_PORT) ?: "",
    proxyUsername = get(KEY_PROXY_USER) ?: "",
    proxyPassword = get(KEY_PROXY_PASS) ?: "",
    webdavUrl = get(KEY_WEBDAV_URL) ?: "",
    webdavPath = get(KEY_WEBDAV_PATH) ?: "",
    webdavUsername = get(KEY_WEBDAV_USER) ?: "",
    webdavPassword = get(KEY_WEBDAV_PASS) ?: "",
    monitorNotify = get(KEY_MONITOR_NOTIFY) ?: true,
    floatingBottomBar = get(KEY_FLOATING_BOTTOM_BAR) ?: false,
    liquidGlassBottomBar = get(KEY_LIQUID_GLASS_BOTTOM_BAR) ?: false,
    bottomBarLabelVisible = get(KEY_BOTTOM_BAR_LABEL_VISIBLE) ?: true,
    blurEnabled = get(KEY_BLUR_ENABLED) ?: true,
    barBlurStyleIndex = (get(KEY_BAR_BLUR_STYLE) ?: 0).coerceAtLeast(0),
    pureBlackInDark = get(KEY_PURE_BLACK_IN_DARK) ?: false,
    uiScalePercent = (get(KEY_UI_SCALE_PERCENT) ?: 100).coerceIn(80, 120),
    autoBackupEnabled = get(KEY_AUTO_BACKUP_ENABLED) ?: false,
    autoBackupIntervalIndex = (get(KEY_AUTO_BACKUP_INTERVAL) ?: DEFAULT_AUTO_BACKUP_INTERVAL_INDEX)
        .coerceIn(0, AUTO_BACKUP_INTERVAL_HOURS.lastIndex),
    autoBackupKeepCount = (get(KEY_AUTO_BACKUP_KEEP) ?: DEFAULT_AUTO_BACKUP_KEEP)
        .coerceIn(MIN_AUTO_BACKUP_KEEP, MAX_AUTO_BACKUP_KEEP),
)

class SettingsStore(context: Context) {

    private val dataStore = context.settingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings> = dataStore.data
        .map { it.toAppSettings() }
        .catch { error ->
            if (error is IOException) emit(AppSettings()) else throw error
        }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toAppSettings())
            prefs[KEY_UI_STYLE] = next.uiStyleIndex
            prefs[KEY_THEME_MODE] = next.themeModeIndex
            prefs[KEY_MONET_ENABLED] = next.monetEnabled
            prefs[KEY_COLOR] = next.keyColorIndex
            prefs[KEY_PALETTE] = next.paletteStyleIndex
            prefs[KEY_COLOR_SPEC] = next.colorSpecIndex
            prefs[KEY_BACK_GESTURE] = next.backGestureModeIndex
            prefs[KEY_LANGUAGE] = next.languageIndex
            prefs[KEY_PROXY_TYPE] = next.proxyTypeIndex
            prefs[KEY_PROXY_HOST] = next.proxyHost
            prefs[KEY_PROXY_PORT] = next.proxyPort
            prefs[KEY_PROXY_USER] = next.proxyUsername
            prefs[KEY_PROXY_PASS] = next.proxyPassword
            prefs[KEY_WEBDAV_URL] = next.webdavUrl
            prefs[KEY_WEBDAV_PATH] = next.webdavPath
            prefs[KEY_WEBDAV_USER] = next.webdavUsername
            prefs[KEY_WEBDAV_PASS] = next.webdavPassword
            prefs[KEY_MONITOR_NOTIFY] = next.monitorNotify
            prefs[KEY_FLOATING_BOTTOM_BAR] = next.floatingBottomBar
            prefs[KEY_LIQUID_GLASS_BOTTOM_BAR] = next.liquidGlassBottomBar
            prefs[KEY_BOTTOM_BAR_LABEL_VISIBLE] = next.bottomBarLabelVisible
            prefs[KEY_BLUR_ENABLED] = next.blurEnabled
            prefs[KEY_BAR_BLUR_STYLE] = next.barBlurStyleIndex
            prefs[KEY_PURE_BLACK_IN_DARK] = next.pureBlackInDark
            prefs[KEY_UI_SCALE_PERCENT] = next.uiScalePercent.coerceIn(80, 120)
            prefs[KEY_AUTO_BACKUP_ENABLED] = next.autoBackupEnabled
            prefs[KEY_AUTO_BACKUP_INTERVAL] = next.autoBackupIntervalIndex
                .coerceIn(0, AUTO_BACKUP_INTERVAL_HOURS.lastIndex)
            prefs[KEY_AUTO_BACKUP_KEEP] = next.autoBackupKeepCount
                .coerceIn(MIN_AUTO_BACKUP_KEEP, MAX_AUTO_BACKUP_KEEP)
        }
    }
}
