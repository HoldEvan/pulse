package com.evan.pulse.domain.tools

import java.util.Calendar
import java.util.TimeZone

/** cron 解析结果 */
data class CronResult(
    val valid: Boolean,
    val error: String? = null,
    /** 自 [fromMs] 起的下一次触发时间戳（毫秒） */
    val nextRunMs: Long = 0L,
    /** 展示用下一次触发文本（如 "2026-10-22 03:30"） */
    val nextRunText: String = "",
    val runTimes: List<Long> = emptyList(),
    val runTimesText: String = ""
)

/**
 * 标准 5 段 cron 解析器：分 时 日 月 周。
 * 支持 *、?、数字、列表（,）、区间（-）、步进（/）。纯函数，便于单元测试。
 * 注：`?` 与 `*` 等价，日/周同时受限时按 OR 语义处理。
 */
object CronParser {

    fun parse(expression: String, fromMs: Long = System.currentTimeMillis()): CronResult {
        val fields = expression.trim().split(Regex("\\s+"))
        if (fields.size != 5) return CronResult(false, "fields")
        val (minF, hourF, dayF, monthF, weekF) = fields

        val minutes = parseField(minF, 0..59) ?: return CronResult(false, "minute")
        val hours = parseField(hourF, 0..23) ?: return CronResult(false, "hour")
        val days = parseField(dayF, 1..31) ?: return CronResult(false, "day")
        val months = parseField(monthF, 1..12) ?: return CronResult(false, "month")
        val weeks = parseField(weekF, 0..6) ?: return CronResult(false, "week")

        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = fromMs
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // 从下一分钟开始；50 年覆盖世纪非闰年附近的十次闰日计划。
        cal.add(Calendar.MINUTE, 1)
        val limit = (cal.clone() as Calendar).apply { add(Calendar.YEAR, 50) }.timeInMillis
        val runs = mutableListOf<Long>()

        while (cal.timeInMillis <= limit) {
            if (!monthMatches(cal, months) || !dayMatches(cal, days, weeks)) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                continue
            }
            if (matches(cal, Calendar.HOUR_OF_DAY, hours) &&
                matches(cal, Calendar.MINUTE, minutes)
            ) {
                runs.add(cal.timeInMillis)
                if (runs.size == 10) {
                    return CronResult(
                        valid = true,
                        nextRunMs = runs.first(),
                        nextRunText = format(runs.first()),
                        runTimes = runs.toList(),
                        runTimesText = runs.joinToString("\n") { format(it) }
                    )
                }
            }
            cal.add(Calendar.MINUTE, 1)
        }
        return CronResult(false, "range")
    }

    private fun monthMatches(cal: Calendar, months: Set<Int>): Boolean =
        months.isEmpty() || months.contains(cal.get(Calendar.MONTH) + 1)

    /** 空集表示通配，否则需命中集合 */
    private fun matches(cal: Calendar, field: Int, values: Set<Int>): Boolean =
        values.isEmpty() || values.contains(cal.get(field))

    private fun format(ms: Long): String {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = ms
        val y = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val mi = cal.get(Calendar.MINUTE)
        return String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d", y, mo, d, h, mi)
    }

    /** 解析单个字段为匹配集合；空集表示通配（* 或 ?）；返回 null 表示表达式非法 */
    private fun parseField(field: String, range: IntRange): Set<Int>? {
        val trimmed = field.trim()
        if (trimmed == "*" || trimmed == "?") return emptySet()
        val result = LinkedHashSet<Int>()
        for (part in trimmed.split(",")) {
            val step = part.contains('/')
            val (basePart, stepPart) = if (step) {
                val idx = part.indexOf('/')
                part.substring(0, idx) to part.substring(idx + 1)
            } else {
                part to ""
            }
            val stepVal = if (stepPart.isEmpty()) 1 else stepPart.toIntOrNull() ?: return null
            if (stepVal <= 0) return null

            val lo: Int
            val hi: Int
            if (basePart == "*" || basePart == "?") {
                lo = range.first
                hi = range.last
            } else {
                val dash = basePart.indexOf('-')
                if (dash >= 0) {
                    lo = basePart.substring(0, dash).toIntOrNull() ?: return null
                    hi = basePart.substring(dash + 1).toIntOrNull() ?: return null
                } else {
                    val single = basePart.toIntOrNull() ?: return null
                    lo = single
                    hi = single
                }
            }
            if (lo < range.first || hi > range.last || lo > hi) return null
            var v = lo
            while (v <= hi) {
                result.add(v)
                v += stepVal
            }
        }
        return result
    }

    /** 日与周同时受限时按 OR 语义（标准 cron 行为）；空集表示通配 */
    private fun dayMatches(cal: Calendar, days: Set<Int>, weeks: Set<Int>): Boolean {
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY // 0=周日
        val dayOk = days.isEmpty() || days.contains(dayOfMonth)
        val weekOk = weeks.isEmpty() || weeks.contains(dayOfWeek)
        return when {
            days.isEmpty() && weeks.isEmpty() -> true
            days.isEmpty() -> weekOk
            weeks.isEmpty() -> dayOk
            else -> dayOk || weekOk
        }
    }
}
