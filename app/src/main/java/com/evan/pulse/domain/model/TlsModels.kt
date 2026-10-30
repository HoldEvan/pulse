package com.evan.pulse.domain.model

/**
 * TLS 客户端策略：跳过校验、自定义 CA、客户端证书（mTLS）。
 *
 * 与代理一样属于引擎级配置，各引擎按「代理 + TLS 策略」组合缓存客户端。
 * 字段全部用默认值，旧调用方不传即保持原有行为（仅系统信任库 + 不跳过校验）。
 *
 * - [insecure] 信任任意证书并跳过主机名校验，仅用于自签名/内网调试
 * - [customCaPem] 额外的 CA 证书（PEM 文本），在系统信任库之外追加信任
 * - [clientCertPem] / [clientKeyPem] 客户端证书与私钥（PEM 文本），用于双向 TLS
 */
data class TlsConfig(
    val insecure: Boolean = false,
    val customCaPem: String = "",
    val clientCertPem: String = "",
    val clientKeyPem: String = ""
) {
    /** 是否配置了自定义 CA：决定是否走自定义 TrustManager 而不是系统默认 */
    val hasCustomCa: Boolean get() = customCaPem.isNotBlank()

    /** 是否配置了客户端证书：决定是否挂载 KeyManager 做双向认证 */
    val hasClientCert: Boolean get() = clientCertPem.isNotBlank() && clientKeyPem.isNotBlank()

    /** 是否为完全默认策略：引擎可直接用缓存键判断，避免无谓重建 */
    val isDefault: Boolean get() = !insecure && !hasCustomCa && !hasClientCert

    val summary: String
        get() = when {
            insecure -> "insecure"
            hasClientCert && hasCustomCa -> "ca+client"
            hasClientCert -> "client"
            hasCustomCa -> "ca"
            else -> "default"
        }

    companion object {
        /** 由旧的布尔开关构造：兼容尚未迁移到完整 TLS 配置的调用方 */
        fun of(insecure: Boolean): TlsConfig = TlsConfig(insecure = insecure)
    }
}
