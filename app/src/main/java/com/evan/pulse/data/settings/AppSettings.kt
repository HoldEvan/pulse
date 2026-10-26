package com.evan.pulse.data.settings

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType

/** 自动备份可选的周期（小时），下标与 [AppSettings.autoBackupIntervalIndex] 对应 */
val AUTO_BACKUP_INTERVAL_HOURS: List<Int> = listOf(6, 12, 24, 72, 168)

/** 自动备份默认周期：每天一次 */
const val DEFAULT_AUTO_BACKUP_INTERVAL_INDEX = 2

/** 远程保留份数的可选范围 */
const val MIN_AUTO_BACKUP_KEEP = 1
const val MAX_AUTO_BACKUP_KEEP = 30

/** 默认保留份数 */
const val DEFAULT_AUTO_BACKUP_KEEP = 7

/**
 * 设置页持久化的全部状态。
 *
 * 各 index 均为下拉项下标，越界或历史数据缺失时统一回退到首项，
 * 因此读取端必须配合 getOrElse 使用。
 */
data class AppSettings(
    /** 界面风格下标，对应 ui.design.UiStyleEntries，0 为 Miuix */
    val uiStyleIndex: Int = 0,
    /** 外观模式下标，对应 ui.theme.ThemeModeEntries */
    val themeModeIndex: Int = 0,
    val monetEnabled: Boolean = false,
    /** 主题色下标，0 表示跟随系统取色，1..N 对应 ui.theme.ThemeKeyColors */
    val keyColorIndex: Int = 0,
    /** 调色板风格下标，对应 ui.theme.PaletteStyleEntries */
    val paletteStyleIndex: Int = 0,
    /** 色彩规范下标，对应 ui.theme.ColorSpecEntries */
    val colorSpecIndex: Int = 0,
    /** 预测性返回模式，对应 ui.settings.BackGestureModeLabels，0 为 Miuix */
    val backGestureModeIndex: Int = 0,
    /** 语言下标，对应 ui.l10n.AppLanguage，0 为跟随系统 */
    val languageIndex: Int = 0,
    /** 代理类型下标，对应 domain.model.ProxyType，0 为不使用 */
    val proxyTypeIndex: Int = 0,
    val proxyHost: String = "",
    /** 以字符串存储以直接绑定输入框，非法端口在 toProxyConfig 中归零处理 */
    val proxyPort: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    /** WebDAV 服务器地址（如 https://example.com/webdav），空表示未启用 */
    val webdavUrl: String = "",
    /** WebDAV 路径下子目录（拼接在 URL 后） */
    val webdavPath: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    /** 拨测未通时是否发送系统通知 */
    val monitorNotify: Boolean = true,
    /** 底栏是否以悬浮胶囊样式显示 */
    val floatingBottomBar: Boolean = false,
    /** 悬浮底栏是否启用半透明玻璃质感 */
    val liquidGlassBottomBar: Boolean = false,
    /** 底栏是否在图标下方显示文字（false 为仅图标） */
    val bottomBarLabelVisible: Boolean = true,
    /** 顶栏磨砂效果开关，关闭后顶栏退化为纯色 */
    val blurEnabled: Boolean = true,
    /** 顶栏模糊样式下标，对应 ui.theme.BarBlurStyleEntries，0 为高斯模糊 */
    val barBlurStyleIndex: Int = 0,
    /** 深色模式是否使用纯黑背景 */
    val pureBlackInDark: Boolean = false,
    /** 界面缩放百分比，80..120，100 为原始大小 */
    val uiScalePercent: Int = 100,
    /** 是否启用定时自动备份到 WebDAV */
    val autoBackupEnabled: Boolean = false,
    /** 自动备份周期下标，对应 data.backup.BackupIntervalEntries */
    val autoBackupIntervalIndex: Int = DEFAULT_AUTO_BACKUP_INTERVAL_INDEX,
    /** 远程保留的自动备份份数，超出后删除最旧的 */
    val autoBackupKeepCount: Int = DEFAULT_AUTO_BACKUP_KEEP,
    /** 拨测状态变化时是否 POST 到 Webhook */
    val webhookEnabled: Boolean = false,
    val webhookUrl: String = "",
    /** 平台格式下标，对应 domain.webhook.WebhookFormat.ordinal */
    val webhookFormatIndex: Int = 0,
    /** 自定义模板；空则用格式对应的默认模板 */
    val webhookTemplate: String = "",
    /** 附加请求头，值可放签名或 Token；同名时覆盖内置的 Content-Type */
    val webhookHeaders: List<KeyValueEntry> = emptyList(),
) {
    /** 转换为引擎使用的代理配置，端口非法或地址为空时返回未生效配置 */
    fun toProxyConfig(): ProxyConfig = ProxyConfig(
        type = ProxyType.entries.getOrElse(proxyTypeIndex) { ProxyType.NONE },
        host = proxyHost,
        port = proxyPort.toIntOrNull() ?: 0,
        username = proxyUsername.ifBlank { null },
        password = proxyPassword.ifBlank { null },
    )
}
