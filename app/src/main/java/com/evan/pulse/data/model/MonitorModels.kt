package com.evan.pulse.data.model

/** 拨测类型：IP 连通 / 端口 / 接口 */
enum class MonitorType { IP, PORT, HTTP }

/** 拨测目标：按当前项目分组，配置即持久化 */
data class MonitorTarget(
    val id: Long,
    val projectId: Long,
    val name: String,
    val type: MonitorType,
    /** IP 与端口类型的主机：IP 字面量或域名 */
    val host: String = "",
    /** 端口类型的目标端口 */
    val port: Int = 0,
    /** 接口类型的请求地址，只支持 GET */
    val url: String = "",
    /** 单次检查的超时时间 */
    val timeoutMs: Long = 5000L,
    /** 拨测频率，单位秒 */
    val intervalSec: Long = 60L,
    /** 接口类型判定为通的起始状态码 */
    val statusMin: Int = 200,
    /** 接口类型判定为通的结束状态码 */
    val statusMax: Int = 399,
    val enabled: Boolean = true,
    /** 一级分组名，空字符串表示未分组 */
    val group: String = "",
    val createdAt: Long = 0L
)

/** 一次拨测的运行结果，只保存在内存里用于界面展示 */
data class MonitorRun(
    val ok: Boolean,
    /** 结果的简短说明，例如 HTTP 200、端口可连、连接超时 */
    val detail: String,
    val durationMs: Long,
    val checkedAt: Long
)

/** 未通的拨测记录，按时间倒序保留最近若干条 */
data class MonitorRecord(
    val id: Long,
    val projectId: Long,
    val targetId: Long,
    val targetName: String,
    val type: MonitorType,
    val message: String,
    val durationMs: Long,
    val checkedAt: Long
)

/** 频率选项：秒 */
val MONITOR_INTERVAL_PRESETS: List<Long> =
    listOf(15L, 30L, 60L, 300L, 900L, 3600L, 21600L)

/** IP 连通检查依次尝试的端口 */
val MONITOR_IP_PROBE_PORTS: List<Int> = listOf(80, 443)

/** 拨测记录的保留上限 */
const val MONITOR_RECORD_LIMIT = 300

/** 目标的完整地址：IP 连通显示主机，端口显示主机与端口，接口显示请求地址 */
fun MonitorTarget.addressText(): String = when (type) {
    MonitorType.IP -> host
    MonitorType.PORT -> "$host:$port"
    MonitorType.HTTP -> url
}
