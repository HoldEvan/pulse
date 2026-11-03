package com.evan.pulse.data.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 日志级别：priority 与 [Log] 的等级常量对齐，数值越大越严重。
 *
 * 记录规则是「设定级别及其以上」：选 DEBUG 会记下 DEBUG / INFO / WARN / ERROR，
 * 选 ERROR 则只留下错误。级别越低收集得越多，开销也越大。
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR);

    companion object {
        /** 默认级别：INFO，覆盖常规运行信息又不至于太啰嗦 */
        val Default: LogLevel = INFO

        /** 越界下标回退默认级别，历史值或损坏值不会让日志停摆 */
        fun fromIndex(index: Int): LogLevel = entries.getOrElse(index) { Default }

        fun indexOf(level: LogLevel): Int = entries.indexOf(level)
    }
}

/** 一条日志：时间是记下它的时刻，detail 为异常堆栈（无异常时为空） */
data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null,
)

/**
 * 应用内日志总入口。
 *
 * 同时写 logcat 与内存环形缓冲：logcat 供 adb 排查，缓冲供设置里的实时预览。
 * 低于当前级别的内容在入口处就丢弃，既不写 logcat 也不占缓冲。
 */
object AppLog {

    /** 环形缓冲容量：界面最多回看这么多条，超出后淘汰最旧的 */
    const val CAPACITY = 300

    @Volatile
    var minLevel: LogLevel = LogLevel.Default
        private set

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(CAPACITY)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** 当前缓冲内容，界面订阅它做实时预览 */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /** 设置页切换级别后立刻生效，无需重启 */
    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun verbose(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message, null)

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.WARN, tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, error)

    fun log(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        if (level.priority < minLevel.priority) return
        val detail = error?.let { Log.getStackTraceString(it) }
        Log.println(level.priority, tag, if (detail == null) message else "$message\n$detail")
        val entry = LogEntry(
            time = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            detail = detail
        )
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }
}

/** SimpleDateFormat 非线程安全，预览列表可能被多个线程同时格式化，按线程各持一份 */
/**
 * 写进日志的地址：去掉查询串与片段。
 *
 * URL 里常带 token、签名或一次性校验码，落进 logcat 与预览列表会外泄，
 * 因此日志只保留到路径部分。
 */
fun loggableUrl(url: String): String = url.substringBefore('?').substringBefore('#')

private val logTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

/** 预览列表用的时间文本，只保留到毫秒，便于对齐同一秒内的事件 */
fun formatLogTime(time: Long): String = logTimeFormat.get().format(Date(time))
