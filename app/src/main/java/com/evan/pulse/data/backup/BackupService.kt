package com.evan.pulse.data.backup

import android.content.Context
import android.net.Uri
import com.evan.pulse.di.ServiceLocator
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * 数据备份与恢复的统一入口。
 *
 * 本地备份：通过 SAF 把 `app_data.json` 写入用户选定的文件；
 * 本地恢复：从用户选定的备份文件读回并注入各 Store。
 * WebDAV 备份：通过 [WebDavClient] 上传/下载同一 JSON 文件。
 */
class BackupService(context: Context) {

    private val context = context.applicationContext

    private val dataFile: File
        get() = File(context.filesDir, "app_data.json")

    /** 把当前数据直接写入用户通过 SAF 选定的 Uri（导出到本地存储） */
    fun copyToUri(uri: Uri) {
        ServiceLocator.flush()
        val source = dataFile
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法写入目标文件")
        output.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }

    /** 从用户通过 SAF 选定的 Uri 恢复数据 */
    fun importLocal(uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("无法读取备份文件")
        ServiceLocator.restoreFromJson(json)
    }

    /** 导出到 WebDAV，文件名带时间戳以保留多份历史备份，返回远程文件 URL */
    suspend fun exportToWebDav(config: WebDavConfig): String {
        ServiceLocator.flush()
        val json = dataFile.readText()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileUrl = config.timestampedFileUrl(timestamp)
        WebDavClient(config).upload(fileUrl, json)
        return fileUrl
    }

    /** 从 WebDAV 下载默认备份文件并恢复数据 */
    suspend fun importFromWebDav(config: WebDavConfig): String {
        val json = WebDavClient(config).download()
        ServiceLocator.restoreFromJson(json)
        return config.backupFileName()
    }

    /** 从 WebDAV 下载指定历史备份文件并恢复数据 */
    suspend fun restoreFromWebDavUrl(config: WebDavConfig, fileUrl: String): String {
        val json = WebDavClient(config).downloadUrl(fileUrl)
        ServiceLocator.restoreFromJson(json)
        return fileUrl
    }

    /** 删除 WebDAV 上指定历史备份文件 */
    suspend fun deleteWebDavUrl(config: WebDavConfig, fileUrl: String) {
        WebDavClient(config).deleteUrl(fileUrl)
    }

    /** 列出 WebDAV 根目录下的备份文件（最近 10 个） */
    suspend fun listRemote(config: WebDavConfig): List<RemoteBackupEntry> =
        WebDavClient(config).list()

    /** 测试 WebDAV 连接：先做只读探测，探测通过后再验证目录是否可写入 */
    suspend fun testWebDav(config: WebDavConfig): WebDavTestResult {
        val start = System.currentTimeMillis()
        val client = WebDavClient(config)
        val probe = client.probe()
        val write = if (probe.ok) client.writeProbe() else null
        return WebDavTestResult(
            ok = probe.ok && (write?.ok ?: true),
            probe = probe,
            write = write,
            elapsedMs = System.currentTimeMillis() - start
        )
    }

    companion object {
        const val BACKUP_FILE_NAME = "pulse_backup.json"
    }
}

/** WebDAV 连接配置，字段均非空时有效 */
data class WebDavConfig(
    val url: String,
    val path: String,
    val username: String?,
    val password: String?,
) {
    fun backupFileName(): String = BackupService.BACKUP_FILE_NAME

    /** 完整文件 URL：url + 可选 path + / + 文件名 */
    fun fileUrl(): String = buildString {
        append(url.trimEnd('/'))
        if (path.isNotBlank()) {
            append('/')
            append(path.trim { it == '/' })
        }
        append('/').append(backupFileName())
    }

    /** 目标目录 URL（不含文件名） */
    fun dirUrl(): String = fileUrl().substringBeforeLast('/')

    /** 带时间戳的备份文件 URL，用于保留多份历史备份 */
    fun timestampedFileUrl(timestamp: String): String = dirUrl() + "/" + backupBaseName() + "_" + timestamp + ".json"

    /** 不带扩展名的备份文件基名 */
    fun backupBaseName(): String = BackupService.BACKUP_FILE_NAME.substringBeforeLast('.')

    /** 目录下的指定文件完整 URL；href 为绝对地址时原样返回 */
    fun entryUrl(href: String): String =
        if (href.startsWith("http")) href else dirUrl() + "/" + href.trimStart('/')
}

/** 远程备份条目 */
/** WebDAV 远程备份条目：url 为完整地址（保留编码），display 为解码后的文件名 */
data class RemoteBackupEntry(
    val url: String,
    val size: Long,
    val lastModified: Date,
) {
    val display: String = runCatching { URLDecoder.decode(url.substringAfterLast('/'), "UTF-8") }
        .getOrDefault(url.substringAfterLast('/'))
}

/** WebDAV 单个探测阶段的结果；连接中断时 statusCode 为 null */
data class WebDavStage(
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
    val elapsedMs: Long,
)

/** WebDAV 连接测试结果：探测通过后追加写入验证 */
data class WebDavTestResult(
    val ok: Boolean,
    val probe: WebDavStage,
    val write: WebDavStage?,
    val elapsedMs: Long,
)

/** 写入探测用的临时文件名；非 .json 后缀，因此不会出现在备份列表里 */
private const val WEBDAV_PROBE_FILE_NAME = "_pulse_probe.tmp"

/**
 * WebDAV 网络客户端（基于 OkHttp）。
 *
 * 不能用 [java.net.HttpURLConnection]：它只允许标准 HTTP 方法，
 * 发起 PROPFIND / MKCOL 会抛 `ProtocolException: Invalid HTTP method`。
 */
class WebDavClient(
    private val config: WebDavConfig,
    private val client: OkHttpClient = DEFAULT_CLIENT,
) {

    private val modFormat = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    /** 上传 JSON 内容到指定 URL，返回实际保存的 URL */
    suspend fun upload(fileUrl: String, json: String): String {
        ensureDir(config.dirUrl())
        withContext(Dispatchers.IO) {
            val code = execute(
                "PUT",
                fileUrl,
                body = jsonBody(json),
                headers = linkedMapOf("Content-Type" to "application/json")
            ).use { response -> response.code }
            require(code in 200..299) { "上传失败：HTTP $code" }
        }
        return fileUrl
    }

    /** 从 WebDAV 下载默认备份文件并返回 JSON 字符串 */
    suspend fun download(): String = downloadUrl(config.fileUrl())

    /** 按完整 URL 下载并返回 JSON 字符串（用于恢复指定的历史备份文件） */
    suspend fun downloadUrl(fileUrl: String): String = withContext(Dispatchers.IO) {
        execute("GET", fileUrl).use { response ->
            when (response.code) {
                404 -> throw IOException("远程备份文件不存在")
                in 200..299 -> response.body?.string().orEmpty()
                else -> throw IOException("下载失败：HTTP ${response.code}")
            }
        }
    }

    /** 删除远程备份文件（DELETE），404 视为已不存在 */
    suspend fun deleteUrl(fileUrl: String) = withContext(Dispatchers.IO) {
        val code = execute("DELETE", fileUrl).use { response -> response.code }
        if (code != 404) {
            require(code in 200..299) { "删除失败：HTTP $code" }
        }
    }

    /** 列出远程备份目录下的 JSON 文件（PROPFIND） */
    suspend fun list(): List<RemoteBackupEntry> = withContext(Dispatchers.IO) {
        val body = execute(
            "PROPFIND",
            config.dirUrl(),
            headers = PROPFIND_HEADERS(1)
        ).use { response ->
            when {
                response.code in 200..299 -> response.body?.string().orEmpty()
                else -> throw IOException("列出失败：HTTP ${response.code}")
            }
        }

        val entries = mutableListOf<RemoteBackupEntry>()
        // 按 <response> 块解析：href 是 response 的直接子元素，在 <prop> 外面；
        // 命名空间前缀（D:/d:/默认无前缀）因服务器而异，统一可选并忽略大小写
        val responseRegex = Regex(
            "<(?:[\\w.-]+:)?response\\b[^>]*>(.*?)</(?:[\\w.-]+:)?response>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val hrefRegex = Regex(
            "<(?:[\\w.-]+:)?href>\\s*([^<]+?)\\s*</(?:[\\w.-]+:)?href>",
            RegexOption.IGNORE_CASE
        )
        val lenRegex = Regex(
            "<(?:[\\w.-]+:)?getcontentlength>\\s*(\\d+)\\s*</(?:[\\w.-]+:)?getcontentlength>",
            RegexOption.IGNORE_CASE
        )
        val modRegex = Regex(
            "<(?:[\\w.-]+:)?getlastmodified>\\s*([^<]+?)\\s*</(?:[\\w.-]+:)?getlastmodified>",
            RegexOption.IGNORE_CASE
        )

        responseRegex.findAll(body).forEach { respMatch ->
            val respXml = respMatch.groupValues[1]
            val hrefMatch = hrefRegex.find(respXml) ?: return@forEach
            val href = hrefMatch.groupValues[1].trim().trimEnd('/')
            if (href.isEmpty() || !href.endsWith(".json")) return@forEach
            val size = lenRegex.find(respXml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val modText = modRegex.find(respXml)?.groupValues?.get(1)
            val lastModified = modText
                ?.let { runCatching { modFormat.parse(it) }.getOrNull() }
                ?: Date(0)
            entries.add(RemoteBackupEntry(config.entryUrl(href), size, lastModified))
        }
        entries.sortedByDescending { it.lastModified }.take(10)
    }

    /** 只读探测：对目标目录发起 PROPFIND，校验连通性与认证
     *
     * 规则：
     * - 2xx / 405 / 3xx -> 探测通过（服务器可达、协议支持）
     * - 401 -> 认证失败（凭据不对）
     * - 403 -> 无权访问
     * - 404 -> 目录不存在，但服务器可达，探测仍视为通过（后续会自动创建目录）
     * - 其他 4xx/5xx / 网络异常 -> 探测失败
     */
    suspend fun probe(): WebDavStage = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val code = execute(
                "PROPFIND",
                config.dirUrl(),
                headers = PROPFIND_HEADERS(0),
                timeoutMillis = PROBE_TIMEOUT_MS
            ).use { response -> response.code }
            val ok = when (code) {
                in 200..399 -> true
                401 -> false  // 认证失败
                403 -> false  // 无权访问
                404 -> true   // 目录不存在，但服务器可达
                else -> false
            }
            WebDavStage(ok, code, "", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            WebDavStage(false, null, e.message ?: "连接失败", System.currentTimeMillis() - start)
        }
    }

    /** 写入探测：上传临时文件并删除，验证目录可写；无论成败都清理临时文件 */
    suspend fun writeProbe(): WebDavStage {
        val start = System.currentTimeMillis()
        val fileUrl = config.dirUrl() + "/" + WEBDAV_PROBE_FILE_NAME
        return withContext(Dispatchers.IO) {
            var uploaded = false
            try {
                upload(fileUrl, "{}")
                uploaded = true
                deleteUrl(fileUrl)
                uploaded = false
                WebDavStage(true, null, "", System.currentTimeMillis() - start)
            } catch (e: Exception) {
                WebDavStage(false, null, e.message ?: "写入失败", System.currentTimeMillis() - start)
            } finally {
                if (uploaded) runCatching { deleteUrl(fileUrl) }
            }
        }
    }

    /** 确保 WebDAV 目录存在（MKCOL），忽略 405/409 视为已存在 */
    private suspend fun ensureDir(dirUrl: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val code = execute("MKCOL", dirUrl).use { response -> response.code }
                if (code != 405 && code != 409) {
                    require(code in 200..299) { "创建目录失败：HTTP $code" }
                }
            }
        }
    }

    /**
     * 执行一次带 Basic 认证的请求，返回调用方负责关闭的 Response。
     *
     * [timeoutMillis] 为整次调用（建连 + 收发）的总时限，覆盖客户端默认超时。
     */
    private fun execute(
        method: String,
        urlString: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ): Response {
        val builder = Request.Builder()
            .url(urlString)
            .method(method, body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        authHeader()?.let { builder.addHeader("Authorization", it) }
        val callClient = if (timeoutMillis == DEFAULT_TIMEOUT_MS) client
        else client.newBuilder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
        return callClient.newCall(builder.build()).execute()
    }

    /** Basic 认证头；用户名或密码任一为空时返回 null */
    private fun authHeader(): String? {
        val user = config.username
        val pass = config.password
        if (user.isNullOrBlank() || pass.isNullOrBlank()) return null
        return Credentials.basic(user, pass)
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val IO_TIMEOUT_SECONDS = 60L
        private const val DEFAULT_TIMEOUT_MS = 75_000L
        private const val PROBE_TIMEOUT_MS = 25_000L

        /** 所有 WebDavClient 共享，避免每次操作重建连接池与线程 */
        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** 构造 PROPFIND 请求头 */
        private fun PROPFIND_HEADERS(depth: Int): Map<String, String> = linkedMapOf(
            "Depth" to depth.toString(),
            "Accept" to "application/xml"
        )

        /** 把 JSON 字符串包装为请求体；Content-Type 由请求头显式指定 */
        private fun jsonBody(json: String): RequestBody {
            val bytes = json.encodeToByteArray()
            return object : RequestBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    sink.write(bytes)
                }
            }
        }
    }
}
