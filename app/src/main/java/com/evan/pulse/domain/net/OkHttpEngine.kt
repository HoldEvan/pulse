package com.evan.pulse.domain.net

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.net.NetworkPreset
import com.evan.pulse.domain.model.buildGraphqlRequestBody
import com.evan.pulse.domain.model.HttpErrorType
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.takeFrom
import io.ktor.util.flattenEntries
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URLConnection
import kotlin.random.Random
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.asOutputStream
import okhttp3.Credentials

/**
 * 真实 HTTP 引擎：通过 Ktor + OkHttp 发起实际网络请求。
 *
 * 代理与代理认证属于 OkHttp 的引擎级配置，无法按请求切换，因此按 ProxyConfig 缓存客户端。
 * OkHttp 自身的连接/读写超时全部禁用，统一由 HttpTimeout 插件按请求下发总超时，
 * 避免引擎固定的 10 秒默认读超时先于用户设置的超时生效。
 *
 * 代理认证采用预置 Proxy-Authorization 头的方式下发，与 curl --proxy-user 行为一致；
 * SOCKS5 代理不支持认证（java.net.Proxy 不携带凭据）。
 */
class OkHttpEngine(private val appContextProvider: () -> Context? = { null }) : HttpEngine {

    private val clients = ConcurrentHashMap<ProxyConfig, HttpClient>()
    private val throttleRandom = Random.Default

    /** 按完整 URL 关联本次请求的时间线采集器；OkHttp 事件在分发线程回调，map 需线程安全 */
    private val timelines = ConcurrentHashMap<String, MutableTimeline>()

    private val timelineListener = object : okhttp3.EventListener() {
        override fun callStart(call: okhttp3.Call) {
            timelines[call.request().url.toString()]?.markCallStart(System.nanoTime())
        }

        override fun dnsStart(call: okhttp3.Call, domainName: String) {
            timelines[call.request().url.toString()]?.markDnsStart(System.nanoTime())
        }

        override fun dnsEnd(call: okhttp3.Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
            timelines[call.request().url.toString()]?.markDnsEnd(System.nanoTime())
        }

        override fun connectStart(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
            timelines[call.request().url.toString()]?.markConnectStart(System.nanoTime())
        }

        override fun secureConnectStart(call: okhttp3.Call) {
            timelines[call.request().url.toString()]?.markSecureStart(System.nanoTime())
        }

        override fun secureConnectEnd(call: okhttp3.Call, handshake: okhttp3.Handshake?) {
            timelines[call.request().url.toString()]?.markSecureEnd(System.nanoTime())
        }

        override fun connectEnd(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) {
            timelines[call.request().url.toString()]?.markConnectEnd(System.nanoTime())
        }

        override fun responseHeadersStart(call: okhttp3.Call) {
            timelines[call.request().url.toString()]?.markHeadersStart(System.nanoTime())
        }
    }

    override suspend fun execute(config: HttpCallConfig): HttpCallOutcome {
        if (!config.url.startsWith("http://", ignoreCase = true) &&
            !config.url.startsWith("https://", ignoreCase = true)
        ) {
            return HttpCallOutcome.Failure(
                HttpErrorType.UNKNOWN,
                "URL 必须以 http:// 或 https:// 开头"
            )
        }

        val body = requestBody(config)
        // 网络模拟：强制超时档阻塞指定秒数后按超时失败返回；弱网档位分两段——
        // 请求前随机 RTT + 请求体上行耗时，响应后按响应体大小估算下行耗时
        val throttle = config.throttle
        val start = System.currentTimeMillis()
        val timeline = MutableTimeline()
        timelines[config.url] = timeline
        if (throttle.isActive) {
            if (throttle.preset == NetworkPreset.FORCE_TIMEOUT) {
                delay(throttle.forceTimeoutSec * 1000L)
                timelines.remove(config.url)
                return HttpCallOutcome.Failure(
                    HttpErrorType.TIMEOUT,
                    "网络配置强制超时（${throttle.forceTimeoutSec}s）"
                )
            }
            val uploadSize = (body as? OutgoingContent)?.contentLength ?: 0
            delay(NetworkThrottle.uploadDelayMs(uploadSize, throttle, throttleRandom))
        }
        return try {
            val response = clientFor(config.proxy).request {
                method = ktorMethod(config.method)
                url {
                    takeFrom(config.url)
                    config.sendableQueryParams().forEach { encodedParameters.append(it.key, it.value) }
                }
                sendableHeaders(config, hasBody = body != null).forEach { header(it.key, it.value) }
                config.proxy.sendCredentials()
                    ?.let { credential -> header("Proxy-Authorization", credential) }
                timeout {
                    requestTimeoutMillis = config.timeoutMs
                    connectTimeoutMillis = config.timeoutMs
                    socketTimeoutMillis = config.timeoutMs
                }
                if (body != null) setBody(body)
            }
            val bytes = response.bodyAsBytes()
            // 下行模拟在读完响应后补延迟，总耗时与真实慢速下载一致
            NetworkThrottle.downloadDelayMs(bytes.size.toLong(), throttle)
                .takeIf { it > 0 }
                ?.let { delay(it) }
            val elapsed = System.currentTimeMillis() - start
            val snap = timeline.snapshot(elapsed)
            val outcome = HttpCallOutcome.Success(
                statusCode = response.status.value,
                statusText = response.status.description,
                headers = responseHeaders(response),
                body = String(bytes, charsetOf(response.headers["Content-Type"])),
                durationMs = elapsed,
                responseSizeBytes = bytes.size.toLong(),
                timeline = snap,
                bodyBytes = bytes,
                // 取 Ktor 最终组装出的请求头，包含引擎自动补的 Host/Accept/Content-Type 等
                requestHeaders = response.request.headers.flattenEntries()
                    .map { KeyValue(it.first, it.second) }
            )
            timelines.remove(config.url)
            outcome
        } catch (e: CancellationException) {
            timelines.remove(config.url)
            throw e
        } catch (e: Throwable) {
            timelines.remove(config.url)
            HttpCallOutcome.Failure(classify(e), describe(e))
        }
    }

    private fun clientFor(proxyConfig: ProxyConfig): HttpClient {
        clients[proxyConfig]?.let { return it }
        if (clients.size >= MAX_CACHED_CLIENTS) {
            clients.keys.firstOrNull()?.let { clients.remove(it) }
        }
        return clients.getOrPut(proxyConfig) { buildClient(proxyConfig) }
    }

    private fun buildClient(proxyConfig: ProxyConfig): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
        engine {
            config {
                eventListener(timelineListener)
                connectTimeout(0, TimeUnit.MILLISECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS)
                if (proxyConfig.enabled) {
                    proxy(
                        Proxy(
                            if (proxyConfig.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                            InetSocketAddress(proxyConfig.host.trim(), proxyConfig.port)
                        )
                    )
                }
            }
        }
    }

    private fun requestBody(config: HttpCallConfig): Any? = when (config.bodyType) {
        BodyType.NONE -> null

        BodyType.JSON -> config.body.takeIf { it.isNotBlank() }
            ?.let { TextContent(it, declaredContentType(config, ContentType.Application.Json)) }

        BodyType.RAW -> config.body.takeIf { it.isNotBlank() }
            ?.let { TextContent(it, declaredContentType(config, ContentType.Text.Plain)) }

        BodyType.GRAPHQL -> buildGraphqlRequestBody(config.body, config.graphqlVariables)
            .takeIf { it.isNotBlank() }
            ?.let { TextContent(it, declaredContentType(config, ContentType.Application.Json)) }

        BodyType.FORM_URLENCODED -> config.textFormFields().takeIf { it.isNotEmpty() }
            ?.let { fields ->
                FormDataContent(
                    Parameters.build { fields.forEach { append(it.name, it.value) } }
                )
            }

        BodyType.FORM_DATA -> config.formFields.filter { it.name.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { multipartBody(it) }
    }

    /**
     * Ktor formData 构造 multipart：文本字段直传，文件字段经 ContentResolver
     * 流式写入请求体，不整读进内存；openAssetFileDescriptor 取文件大小，
     * 所有字段大小已知时请求体携带 Content-Length，弱网上行模拟与上传进度计算可用。
     */
    private fun multipartBody(fields: List<FormField>): MultiPartFormDataContent {
        val resolver = appContextProvider()?.contentResolver
        val parts = formData {
            fields.forEach { field ->
                val uri = if (field.isFile) field.fileUri?.let(Uri::parse) else null
                if (uri == null || resolver == null) {
                    append(field.name, field.value)
                } else {
                    val size = runCatching {
                        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    }.getOrNull()?.takeIf { it > 0 }
                    append(
                        key = field.name,
                        filename = field.fileName?.takeIf { it.isNotBlank() } ?: "file",
                        contentType = ContentType.parse(guessFileMime(field.fileName)),
                        size = size,
                    ) {
                        resolver.openInputStream(uri)?.use { input ->
                            input.copyTo(asOutputStream())
                        } ?: throw IOException("无法读取文件：${field.fileName ?: uri}")
                    }
                }
            }
        }
        return MultiPartFormDataContent(parts)
    }

    /** 先查 Android MIME 表，再退回文件名推断，均失败按二进制流处理 */
    private fun guessFileMime(fileName: String?): String {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return URLConnection.guessContentTypeFromName(fileName.orEmpty()) ?: "application/octet-stream"
    }

    /** 请求头优先于 Content-Type 推断结果；为空或解析失败时回退到按 body 类型推断的默认值 */
    private fun declaredContentType(config: HttpCallConfig, fallback: ContentType): ContentType {
        val declared = config.headers.firstOrNull {
            it.enabled && it.key.equals("Content-Type", ignoreCase = true) && it.value.isNotBlank()
        } ?: return fallback
        return runCatching { ContentType.parse(declared.value.trim()) }.getOrNull() ?: fallback
    }

    /** 带 body 时剔除由 body 自身决定的头，避免与 Content-Type / Content-Length 重复 */
    private fun sendableHeaders(config: HttpCallConfig, hasBody: Boolean): List<KeyValue> =
        config.headers.filter { kv ->
            kv.enabled &&
                kv.key.isNotBlank() &&
                !(hasBody && kv.key.lowercase() in BODY_RESERVED_HEADERS)
        }

    private fun HttpCallConfig.sendableQueryParams(): List<KeyValue> =
        queryParams.filter { it.enabled && it.key.isNotBlank() }

    private fun HttpCallConfig.textFormFields(): List<FormField> =
        formFields.filter { it.name.isNotBlank() && !it.isFile }

    private fun ProxyConfig.sendCredentials(): String? {
        if (!enabled || type == ProxyType.SOCKS5) return null
        val username = this.username
        val password = this.password
        if (username.isNullOrBlank() || password.isNullOrBlank()) return null
        return Credentials.basic(username, password)
    }

    private fun ktorMethod(method: HttpMethod): KtorHttpMethod = when (method) {
        HttpMethod.GET -> KtorHttpMethod.Get
        HttpMethod.POST -> KtorHttpMethod.Post
        HttpMethod.PUT -> KtorHttpMethod.Put
        HttpMethod.DELETE -> KtorHttpMethod.Delete
        HttpMethod.PATCH -> KtorHttpMethod.Patch
        HttpMethod.HEAD -> KtorHttpMethod.Head
    }

    private fun responseHeaders(response: HttpResponse): List<KeyValue> =
        response.headers.flattenEntries().map { KeyValue(it.first, it.second) }

    private fun charsetOf(contentType: String?): Charset {
        val header = contentType?.lowercase() ?: return Charsets.UTF_8
        val start = header.indexOf("charset=")
        if (start < 0) return Charsets.UTF_8
        val raw = header.substring(start + "charset=".length)
            .split(';')
            .first()
            .trim()
            .trim('"')
        return runCatching { Charset.forName(raw) }.getOrNull() ?: Charsets.UTF_8
    }

    private fun classify(error: Throwable): HttpErrorType {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is HttpRequestTimeoutException,
                is SocketTimeoutException,
                is ConnectTimeoutException -> return HttpErrorType.TIMEOUT

                is UnknownHostException -> return HttpErrorType.DNS

                is SSLException -> return HttpErrorType.SSL

                is CancellationException -> return HttpErrorType.CANCELLED

                is java.net.ConnectException,
                is java.io.IOException -> return HttpErrorType.NETWORK
            }
            current = current.cause
        }
        return HttpErrorType.UNKNOWN
    }

    private fun describe(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val type = error::class.simpleName.orEmpty()
        return when {
            message.isEmpty() -> type.ifEmpty { "请求失败" }
            type.isEmpty() -> message
            else -> "$type: $message"
        }
    }

    private companion object {
        const val MAX_CACHED_CLIENTS = 8
        val BODY_RESERVED_HEADERS = setOf("content-type", "content-length")
    }
}
