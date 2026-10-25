package com.evan.pulse.domain.net

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * 跳过 TLS 证书校验的工具。
 *
 * 仅在调试自签名或内网证书时使用：信任任意证书并跳过主机名校验。
 * 启用后连接不再能防中间人攻击，界面必须持续标注风险，且只在单次请求/连接内生效。
 *
 * TLS 策略属于引擎级配置（与代理一样无法按请求切换），因此各引擎按
 * 「代理 + 是否跳过校验」两个维度缓存客户端。
 */
object InsecureTls {

    /** 接受任意证书链的信任管理器；两个校验方法都直接放行 */
    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** 复用同一个 SocketFactory，避免每次建连都初始化 SSLContext */
    val insecureSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }.socketFactory
    }

    /** 按开关把跳过校验的套接字工厂与主机名校验器挂到 OkHttp 构建器上 */
    fun OkHttpClient.Builder.applyInsecureTls(enabled: Boolean): OkHttpClient.Builder {
        if (!enabled) return this
        sslSocketFactory(insecureSocketFactory, trustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }
}
