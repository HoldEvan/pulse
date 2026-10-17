package com.evan.pulse.data.settings

import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType

/**
 * 设置页持久化的全部状态。
 *
 * 各 index 均为下拉项下标，越界或历史数据缺失时统一回退到首项，
 * 因此读取端必须配合 getOrElse 使用。
 */
data class AppSettings(
    /** 外观模式下标，对应 ui.theme.ThemeModeEntries */
    val themeModeIndex: Int = 0,
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
