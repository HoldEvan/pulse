package com.evan.pulse.data.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 响应体导出的纯逻辑：决定文件后缀、MIME 与建议文件名。
 *
 * 全部是纯函数，方便单元测试；Android 相关的写文件与分享在 [ResponseFileStore] 里。
 */
object ResponseExport {

    /** 常见后缀与 MIME 的对应关系，命中不了就按二进制处理 */
    private val EXTENSIONS = mapOf(
        "application/json" to "json",
        "application/xml" to "xml",
        "text/xml" to "xml",
        "text/html" to "html",
        "text/plain" to "txt",
        "text/css" to "css",
        "text/csv" to "csv",
        "text/javascript" to "js",
        "application/javascript" to "js",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/svg+xml" to "svg",
        "audio/mpeg" to "mp3",
        "audio/wav" to "wav",
        "video/mp4" to "mp4"
    )

    /** 去掉 charset 等参数，返回小写 MIME；空值回落到二进制 */
    fun baseMime(contentType: String?): String {
        val raw = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return raw.ifEmpty { "application/octet-stream" }
    }

    /** 由 MIME 推文件后缀；未知类型用 bin */
    fun extensionFor(contentType: String?): String {
        val mime = baseMime(contentType)
        return EXTENSIONS[mime] ?: when {
            mime.startsWith("text/") -> "txt"
            mime.startsWith("image/") -> mime.substringAfter('/', "img")
            mime.startsWith("audio/") -> mime.substringAfter('/', "audio")
            mime.startsWith("video/") -> mime.substringAfter('/', "video")
            mime.contains("json") -> "json"
            mime.contains("xml") -> "xml"
            else -> "bin"
        }
    }

    /**
     * 建议文件名：`response_<时间戳>.<后缀>`。
     *
     * 不把 URL 拼进文件名——路径里的 `?`、`/` 与中文在各家文件管理器的
     * SAF 实现里表现不一致，时间戳已经足够区分同一目录下的多份导出。
     */
    fun suggestedName(contentType: String?, now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        return "response_$stamp.${extensionFor(contentType)}"
    }

    /**
     * 导出用的字节：优先原始字节，没有则把文本按 UTF-8 编码。
     *
     * 实时响应的 [rawBytes] 是完整的（展示层才做 64K 截断），
     * 历史记录只存文本，因此两条路径都能拿到可用内容。
     */
    fun bytesOf(text: String, rawBytes: ByteArray?): ByteArray =
        rawBytes ?: text.toByteArray(Charsets.UTF_8)
}
