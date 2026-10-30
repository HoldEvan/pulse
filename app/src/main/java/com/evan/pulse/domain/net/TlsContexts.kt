package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.TlsConfig
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 从 [TlsConfig] 构建 [SSLContext]，供 HTTP / WS / SSE / MQTT 四个引擎共用。
 *
 * - 自定义 CA：在系统默认信任库基础上追加用户提供的 CA，而不是替换（避免内网 CA
 *   配置后反而无法访问公网服务）。
 * - 客户端证书：把证书与 PKCS#8 私钥装入内存 KeyStore，交给 KeyManager 做双向认证。
 * - 跳过校验：复用 [InsecureTls] 的信任管理器，信任任意证书并跳过主机名校验。
 *
 * 任一步解析失败都抛 [TlsConfigException]，由调用方转成用户可读的错误提示。
 */
object TlsContexts {

    private const val KEY_ALIAS = "pulse-client"
    private const val KEY_PASSWORD = "pulse"

    fun build(config: TlsConfig): SSLContext {
        if (config.isDefault) {
            return SSLContext.getInstance("TLS").apply { init(null, null, SecureRandom()) }
        }
        val keyManagers = if (config.hasClientCert) clientKeyManagers(config) else null
        val trustManagers = trustManagers(config)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustManagers, SecureRandom())
        }
    }

    /**
     * 取该策略对应的服务端信任管理器，供 OkHttp 的 `sslSocketFactory(factory, trustManager)` 使用。
     * 跳过校验时返回放行一切的管理器，否则返回组合后的管理器。
     */
    fun serverTrustManager(config: TlsConfig): X509TrustManager =
        trustManagers(config).filterIsInstance<X509TrustManager>().first()

    /** 跳过校验时用一个放行一切的管理器；否则按「系统默认 + 自定义 CA」组合 */
    private fun trustManagers(config: TlsConfig): Array<TrustManager> {
        if (config.insecure) return arrayOf(InsecureTls.trustManager)
        val defaultTm = buildTrustManagerFactory(null).trustManagers
        if (!config.hasCustomCa) return defaultTm
        val caTm = buildTrustManagerFactory(config.customCaPem).trustManagers
        // 组合两个管理器：任一信任即放行，兼顾系统根证书与用户追加的 CA
        val delegates = (defaultTm + caTm).filterIsInstance<X509TrustManager>()
        return arrayOf(CombinedTrustManager(delegates))
    }

    private fun buildTrustManagerFactory(customCaPem: String?): TrustManagerFactory {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        if (customCaPem.isNullOrBlank()) {
            factory.init(null as KeyStore?)
            return factory
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        parseCertificates(customCaPem).forEachIndexed { index, cert ->
            store.setCertificateEntry("pulse-ca-$index", cert)
        }
        factory.init(store)
        return factory
    }

    private fun clientKeyManagers(config: TlsConfig): Array<KeyManager> {
        val chain = parseCertificates(config.clientCertPem)
        if (chain.isEmpty()) {
            throw TlsConfigException("客户端证书为空或格式无法识别")
        }
        val key = parsePrivateKey(config.clientKeyPem)
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        store.setKeyEntry(KEY_ALIAS, key, KEY_PASSWORD.toCharArray(), chain.toTypedArray())
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, KEY_PASSWORD.toCharArray())
        return factory.keyManagers
    }

    /** 解析一段或一段以上的 PEM 证书（一个文件里可能有 CA 链） */
    fun parseCertificates(pem: String): List<X509Certificate> {
        val cleaned = stripNonPem(pem)
        if (cleaned.isBlank()) return emptyList()
        val factory = CertificateFactory.getInstance("X.509")
        return runCatching {
            factory.generateCertificates(ByteArrayInputStream(cleaned.toByteArray()))
                .filterIsInstance<X509Certificate>()
        }.getOrElse { throw TlsConfigException("证书解析失败：${it.message ?: "格式错误"}") }
    }

    /**
     * 解析 PKCS#8 私钥。RSA/EC 都支持：先按 key 的算法 OID 猜测，失败再依次尝试。
     */
    fun parsePrivateKey(pem: String): PrivateKey {
        val body = stripNonPem(pem)
        if (body.isBlank()) throw TlsConfigException("客户端私钥为空")
        val der = runCatching { Base64.getMimeDecoder().decode(body) }
            .getOrElse { throw TlsConfigException("私钥 Base64 解码失败") }
        val spec = PKCS8EncodedKeySpec(der)
        // PKCS#8 的 algorithm identifier 在头部，试 RSA → EC → 其他常见算法
        for (algorithm in listOf("RSA", "EC", "DSA", "Ed25519")) {
            runCatching { return KeyFactory.getInstance(algorithm).generatePrivate(spec) }
        }
        throw TlsConfigException("私钥格式不受支持（需 PKCS#8 未加密私钥）")
    }

    /** 去掉 PEM 的 -----BEGIN/END----- 头尾与所有空白，只留 Base64 正文 */
    private fun stripNonPem(pem: String): String = pem.lines()
        .filterNot { it.startsWith("-----") }
        .joinToString("")
        .trim()
}

/** 组合多个 X509TrustManager：任一校验通过即视为通过 */
private class CombinedTrustManager(
    private val delegates: List<X509TrustManager>
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        dispatch(chain, authType) { it.checkClientTrusted(chain, authType) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        dispatch(chain, authType) { it.checkServerTrusted(chain, authType) }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()

    private fun dispatch(
        chain: Array<out X509Certificate>?,
        authType: String?,
        check: (X509TrustManager) -> Unit
    ) {
        var last: java.security.cert.CertificateException? = null
        for (delegate in delegates) {
            try {
                check(delegate)
                return
            } catch (e: java.security.cert.CertificateException) {
                last = e
            }
        }
        throw last ?: java.security.cert.CertificateException("没有任何信任管理器通过校验")
    }
}

