package com.evan.pulse.domain.net

/** TLS 配置解析失败，消息已可直接展示给用户 */
class TlsConfigException(message: String) : Exception(message)
