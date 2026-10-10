package com.evan.pulse.domain.model

/** 代理协议类型，枚举顺序与设置页下拉项一一对应 */
enum class ProxyType {
    NONE,
    HTTP,
    SOCKS5
}

/**
 * 全局代理配置，随每次请求下发给网络引擎。
 *
 * host 为空或端口不在 1..65535 范围内时视为未生效，按直连处理，
 * 避免出现"选了代理但地址没填完"却仍尝试走代理的半成品状态。
 */
data class ProxyConfig(
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
) {
    val enabled: Boolean
        get() = type != ProxyType.NONE && host.isNotBlank() && port in 1..65535

    /** 展示用的简短描述，供设置页摘要和调试输出使用 */
    val summary: String
        get() = when {
            !enabled -> "直连，请求不经过代理"
            type == ProxyType.SOCKS5 -> "SOCKS5 ${host.trim()}:$port"
            else -> "HTTP ${host.trim()}:$port"
        }
}
