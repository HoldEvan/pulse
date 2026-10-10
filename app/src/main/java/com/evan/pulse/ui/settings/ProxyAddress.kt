package com.evan.pulse.ui.settings

/**
 * 拆分「地址:端口」合并输入框的内容。
 *
 * 端口以最后一个冒号后的纯数字段识别；无有效端口时整串视作主机、端口留空，
 * 与 ProxyConfig.enabled 的判定口径一致（端口非法则不生效）。
 * 支持 `[::1]:8080` 形式的 IPv6，裸 IPv6（含多个冒号）不会被误拆。
 */
internal fun splitProxyAddress(text: String): Pair<String, String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "" to ""

    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end > 0) {
            val host = trimmed.substring(1, end)
            val port = trimmed.substring(end + 1).removePrefix(":")
            return if (port.isNotEmpty() && port.all { it.isDigit() }) host to port else host to ""
        }
    }

    val idx = trimmed.lastIndexOf(':')
    if (idx < 0) return trimmed to ""
    if (idx == trimmed.length - 1) return trimmed.substring(0, idx) to ""
    if (idx == 0) return trimmed to ""
    val port = trimmed.substring(idx + 1)
    if (!port.all { it.isDigit() }) return trimmed to ""
    val host = trimmed.substring(0, idx)
    if (host.contains(':')) return trimmed to ""
    return host to port
}
