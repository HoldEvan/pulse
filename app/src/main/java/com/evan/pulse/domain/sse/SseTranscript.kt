package com.evan.pulse.domain.sse

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * SSE 会话转录：按 text/event-stream 格式累积，导出的文件可被任意 SSE 解析器重放。
 *
 * - 系统事件（连接、重连、断开、服务端注释）写成 `:` 开头的注释行，并带 ISO 时间戳；
 * - 事件帧写成 id / event / data 字段，多行 data 逐行加前缀，帧之间空行分隔。
 *
 * 长连接可能持续数小时，超过 [maxChars] 时丢弃最早的内容、只保留尾部
 * （结尾通常更能说明会话如何结束），并对齐到帧边界，避免留下半个帧。
 */
class SseTranscript(private val maxChars: Int = DEFAULT_MAX_CHARS) {

    private val buffer = StringBuilder()

    /** 因超长被丢弃的字符数；非 0 时渲染结果开头带截断说明 */
    var droppedChars: Long = 0
        private set

    /** 已记录的事件帧数（不含注释） */
    var frames: Int = 0
        private set

    val isEmpty: Boolean get() = buffer.isEmpty() && droppedChars == 0L

    fun comment(text: String, timestamp: Long) {
        val stamp = Instant.ofEpochMilli(timestamp).toString()
        text.split('\n').forEachIndexed { index, line ->
            buffer.append(": ")
            if (index == 0) buffer.append('[').append(stamp).append("] ")
            buffer.append(line.removeSuffix("\r")).append('\n')
        }
        trim()
    }

    fun frame(id: String?, event: String, data: String) {
        if (!id.isNullOrBlank()) buffer.append("id: ").append(id).append('\n')
        if (event.isNotBlank()) buffer.append("event: ").append(event).append('\n')
        data.split('\n').forEach { buffer.append("data: ").append(it.removeSuffix("\r")).append('\n') }
        buffer.append('\n')
        frames++
        trim()
    }

    fun render(): String =
        if (droppedChars == 0L) buffer.toString()
        else ": [truncated $droppedChars earlier characters]\n\n$buffer"

    /** 留一段余量再裁剪，避免每追加一帧都搬移整段缓冲 */
    private fun trim() {
        if (buffer.length <= maxChars + maxChars / 4) return
        val target = buffer.length - maxChars
        val frameEnd = buffer.indexOf("\n\n", target)
        val lineEnd = buffer.indexOf("\n", target)
        val cut = when {
            frameEnd >= 0 -> frameEnd + 2
            lineEnd >= 0 -> lineEnd + 1
            else -> target
        }
        droppedChars += cut
        buffer.delete(0, cut)
    }

    companion object {
        /** 单个会话最多保留的字符数（约 256 KB） */
        const val DEFAULT_MAX_CHARS = 256 * 1024

        const val MIME = "text/plain"

        /** 导出文件名：sse-20261031-062711.txt，按会话开始时间命名 */
        fun suggestedName(startedAt: Long): String =
            "sse-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt)) + ".txt"
    }
}
