package com.evan.pulse.domain

import java.net.URI

/**
 * 从完整地址里取路径部分，给保存的接口生成默认名称。
 *
 * URI 对 ws/wss/tcp 等非 http 方案同样适用；解析失败或路径为空返回空串，
 * 由调用方决定退回原始地址。
 */
fun String.pathOnly(): String = runCatching {
    URI(this).rawPath
}.getOrNull()
    ?.takeIf { it != "/" }
    ?: ""
