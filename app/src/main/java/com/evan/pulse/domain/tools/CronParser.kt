package com.evan.pulse.domain.tools

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** cron 方言：段数、取值范围与日/周语义各不相同 */
enum class CronType { LINUX, SPRING, QUARTZ }

/** 单个字段，用于生成时按方言决定显示顺序 */
enum class CronField { SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH, DAY_OF_WEEK, YEAR }

/** 生成页的常用模板 */
enum class CronPreset {
    EVERY_MINUTE,
    EVERY_5_MINUTES,
    HOURLY,
    DAILY_MIDNIGHT,
    DAILY_9AM,
    WEEKLY_MONDAY_9AM,
    MONTHLY_FIRST,
    YEARLY
}

/** cron 解析结果 */
data class CronResult(
    val valid: Boolean,
    val error: String? = null,
    /** 自解析起点起的下一次触发时间戳（毫秒） */
    val nextRunMs: Long = 0L,
    /** 展示用下一次触发文本（如 "2026-10-22 03:30"） */
    val nextRunText: String = "",
    val runTimes: List<Long> = emptyList(),
    val runTimesText: String = ""
)

/**
 * 三种 cron 方言的解析器。
 *
 * - Linux Crontab：5 段「分 时 日 月 周」，周 0-6（0 为周日，7 也接受），支持 `@daily` 等别名
 * - Spring Cron：6 段「秒 分 时 日 月 周」，周 0-6，日/周可用 `?`
 * - Quartz Cron：6 或 7 段「秒 分 时 日 月 周 [年]」，周 1-7（1 为周日），
 *   日与周必须且只能有一个为 `?`，支持 `L`、`W`、`#` 与英文缩写
 *
 * 所有方言都支持 `*`、数字、英文缩写、列表（`,`）、区间（`-`）、步进（`/`）。
 */
object CronParser {

    private const val MAX_RUNS = 10
    private const val MAX_YEARS = 50L

    private val monthNames = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )

    private val standardDayNames = mapOf(
        "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6
    )

    /** Quartz 的周从 1 开始（1 为周日），解析后再归一到 0-6 */
    private val quartzDayNames = standardDayNames.mapValues { it.value + 1 }

    private val linuxAliases = mapOf(
        "@yearly" to "0 0 1 1 *",
        "@annually" to "0 0 1 1 *",
        "@monthly" to "0 0 1 * *",
        "@weekly" to "0 0 * * 0",
        "@daily" to "0 0 * * *",
        "@midnight" to "0 0 * * *",
        "@hourly" to "0 * * * *"
    )

    fun parse(
        expression: String,
        type: CronType = CronType.LINUX,
        fromMs: Long = System.currentTimeMillis()
    ): CronResult {
        val raw = expression.trim()
        if (raw.isEmpty()) return CronResult(false, "fields")
        if (raw.startsWith("@")) {
            if (type != CronType.LINUX) return CronResult(false, "fields")
            if (raw.lowercase() == "@reboot") return CronResult(false, "unsupported")
            val alias = linuxAliases[raw.lowercase()] ?: return CronResult(false, "fields")
            return parse(alias, type, fromMs)
        }

        val fields = raw.split(Regex("\\s+"))
        val countOk = when (type) {
            CronType.LINUX -> fields.size == 5
            CronType.SPRING -> fields.size == 6
            CronType.QUARTZ -> fields.size in 6..7
        }
        if (!countOk) return CronResult(false, "fields")

        val layout = layoutOf(type, fields)
        val seconds = parseValues(layout.second, 0..59, emptyMap()) ?: return CronResult(false, "second")
        val minutes = parseValues(layout.minute, 0..59, emptyMap()) ?: return CronResult(false, "minute")
        val hours = parseValues(layout.hour, 0..23, emptyMap()) ?: return CronResult(false, "hour")
        val months = parseValues(layout.month, 1..12, monthNames) ?: return CronResult(false, "month")

        val dom = parseDayOfMonth(layout.dayOfMonth, type) ?: return CronResult(false, "day")
        val dow = parseDayOfWeek(layout.dayOfWeek, type) ?: return CronResult(false, "week")

        val years = layout.year?.let {
            parseValues(it, 1970..2099, emptyMap()) ?: return CronResult(false, "year")
        }

        if (type == CronType.QUARTZ) {
            val domQuestion = layout.dayOfMonth.trim() == "?"
            val dowQuestion = layout.dayOfWeek.trim() == "?"
            if (domQuestion == dowQuestion) return CronResult(false, "dayConflict")
        }

        val zone = ZoneId.systemDefault()
        val withSeconds = type != CronType.LINUX
        val start = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDateTime()
        val endYear = start.year + MAX_YEARS
        val hourValues = hours.toSortedSet().ifEmpty { (0..23).toSortedSet() }
        val minuteValues = minutes.toSortedSet().ifEmpty { (0..59).toSortedSet() }
        val secondValues = seconds.toSortedSet().ifEmpty { (0..59).toSortedSet() }
        val monthValues = months.takeUnless { it.isEmpty() }
        val yearValues = years.takeUnless { it.isNullOrEmpty() }

        val runs = mutableListOf<Long>()
        var date = start.toLocalDate()
        while (date.year <= endYear) {
            if (yearValues != null && date.year !in yearValues) {
                date = date.plusYears(1).withDayOfYear(1)
                continue
            }
            if (monthValues != null && date.monthValue !in monthValues) {
                date = date.plusMonths(1).withDayOfMonth(1)
                continue
            }
            if (!dayMatches(date, dom, dow)) {
                date = date.plusDays(1)
                continue
            }
            for (hour in hourValues) {
                for (minute in minuteValues) {
                    for (second in secondValues) {
                        val moment = date.atTime(hour, minute, second)
                        if (moment.isAfter(start)) {
                            runs.add(moment.atZone(zone).toInstant().toEpochMilli())
                            if (runs.size == MAX_RUNS) {
                                return resultOf(runs, withSeconds)
                            }
                        }
                    }
                }
            }
            date = date.plusDays(1)
        }
        return if (runs.isEmpty()) CronResult(false, "range") else resultOf(runs, withSeconds)
    }

    /** Linux 表达式没有秒字段，只有 Spring/Quartz 的时间文本才带秒 */
    private fun resultOf(runs: List<Long>, withSeconds: Boolean): CronResult = CronResult(
        valid = true,
        nextRunMs = runs.first(),
        nextRunText = format(runs.first(), withSeconds),
        runTimes = runs.toList(),
        runTimesText = runs.joinToString("\n") { format(it, withSeconds) }
    )

    private class Layout(
        val second: String,
        val minute: String,
        val hour: String,
        val dayOfMonth: String,
        val month: String,
        val dayOfWeek: String,
        val year: String? = null
    )

    private fun layoutOf(type: CronType, fields: List<String>): Layout = when (type) {
        CronType.LINUX -> Layout(
            second = "0",
            minute = fields[0],
            hour = fields[1],
            dayOfMonth = fields[2],
            month = fields[3],
            dayOfWeek = fields[4]
        )

        CronType.SPRING -> Layout(
            second = fields[0],
            minute = fields[1],
            hour = fields[2],
            dayOfMonth = fields[3],
            month = fields[4],
            dayOfWeek = fields[5]
        )

        CronType.QUARTZ -> Layout(
            second = fields[0],
            minute = fields[1],
            hour = fields[2],
            dayOfMonth = fields[3],
            month = fields[4],
            dayOfWeek = fields[5],
            year = fields.getOrNull(6)
        )
    }

    private sealed interface DomSpec {
        fun matches(date: LocalDate): Boolean

        data object All : DomSpec {
            override fun matches(date: LocalDate): Boolean = true
        }

        data class Values(val days: Set<Int>) : DomSpec {
            override fun matches(date: LocalDate): Boolean = days.isEmpty() || date.dayOfMonth in days
        }

        /** `L`：当月最后一天 */
        data object Last : DomSpec {
            override fun matches(date: LocalDate): Boolean = date.dayOfMonth == date.lengthOfMonth()
        }

        /** `15W`：最接近 15 日的工作日 */
        data class NearestWeekday(val day: Int) : DomSpec {
            override fun matches(date: LocalDate): Boolean = date == nearestWeekday(date, day)
        }
    }

    private sealed interface DowSpec {
        fun matches(date: LocalDate): Boolean

        data object All : DowSpec {
            override fun matches(date: LocalDate): Boolean = true
        }

        /** 0 为周日，6 为周六 */
        data class Values(val days: Set<Int>) : DowSpec {
            override fun matches(date: LocalDate): Boolean =
                days.isEmpty() || date.dayOfWeek.index0() in days
        }

        /** `L`：当月最后一个周六 */
        data object LastSaturday : DowSpec {
            override fun matches(date: LocalDate): Boolean =
                date.dayOfWeek == DayOfWeek.SATURDAY && date.plusWeeks(1).month != date.month
        }

        /** `6L`：当月最后一个周五 */
        data class LastWeekday(val day: Int) : DowSpec {
            override fun matches(date: LocalDate): Boolean =
                date.dayOfWeek.index0() == day && date.plusWeeks(1).month != date.month
        }

        /** `MON#2`：当月第二个周一 */
        data class NthWeekday(val day: Int, val nth: Int) : DowSpec {
            override fun matches(date: LocalDate): Boolean =
                date.dayOfWeek.index0() == day && (date.dayOfMonth - 1) / 7 + 1 == nth
        }
    }

    private fun parseDayOfMonth(text: String, type: CronType): DomSpec? {
        val field = text.trim()
        if (field == "*") return DomSpec.All
        if (field == "?") return if (type == CronType.LINUX) null else DomSpec.All
        if (type == CronType.QUARTZ) {
            if (field == "L") return DomSpec.Last
            if (field.endsWith("W", ignoreCase = true)) {
                val day = field.dropLast(1).toIntOrNull() ?: return null
                if (day !in 1..31) return null
                return DomSpec.NearestWeekday(day)
            }
        }
        return parseValues(field, 1..31, emptyMap())?.let { DomSpec.Values(it) }
    }

    private fun parseDayOfWeek(text: String, type: CronType): DowSpec? {
        val field = text.trim()
        if (field == "*") return DowSpec.All
        if (field == "?") return if (type == CronType.LINUX) null else DowSpec.All
        if (type == CronType.QUARTZ) {
            if (field == "L") return DowSpec.LastSaturday
            if (field.contains('#')) {
                val parts = field.split('#')
                if (parts.size != 2) return null
                val day = parseDayName(parts[0], type) ?: return null
                val nth = parts[1].toIntOrNull() ?: return null
                if (nth !in 1..5) return null
                return DowSpec.NthWeekday(day, nth)
            }
            if (field.endsWith("L", ignoreCase = true)) {
                val day = parseDayName(field.dropLast(1), type) ?: return null
                return DowSpec.LastWeekday(day)
            }
            val values = parseValues(field, 1..7, quartzDayNames) ?: return null
            return DowSpec.Values(values.mapTo(LinkedHashSet()) { (it - 1) % 7 })
        }
        val values = parseValues(field, 0..7, standardDayNames) ?: return null
        return DowSpec.Values(values.mapTo(LinkedHashSet()) { it % 7 })
    }

    private fun parseDayName(text: String, type: CronType): Int? {
        val raw = text.trim()
        standardDayNames[raw.uppercase()]?.let { return it }
        val number = raw.toIntOrNull() ?: return null
        return when (type) {
            CronType.QUARTZ -> if (number in 1..7) (number - 1) % 7 else null
            else -> if (number in 0..7) number % 7 else null
        }
    }

    private fun dayMatches(date: LocalDate, dom: DomSpec, dow: DowSpec): Boolean {
        val domAll = dom is DomSpec.All
        val dowAll = dow is DowSpec.All
        return when {
            domAll && dowAll -> true
            domAll -> dow.matches(date)
            dowAll -> dom.matches(date)
            else -> dom.matches(date) || dow.matches(date)
        }
    }

    private fun nearestWeekday(date: LocalDate, day: Int): LocalDate {
        val length = date.lengthOfMonth()
        val target = date.withDayOfMonth(day.coerceIn(1, length))
        return when (target.dayOfWeek) {
            DayOfWeek.SATURDAY -> if (target.dayOfMonth == 1) target.plusDays(2) else target.minusDays(1)
            DayOfWeek.SUNDAY -> if (target.dayOfMonth == length) target.minusDays(2) else target.plusDays(1)
            else -> target
        }
    }

    private fun DayOfWeek.index0(): Int = value % 7

    /**
     * 解析单个字段为匹配集合；空集表示通配（`*` 或 `?`）；返回 null 表示非法。
     * 支持 `*`、`?`、数字、英文缩写、列表、区间与步进。
     */
    private fun parseValues(field: String, range: IntRange, names: Map<String, Int>): Set<Int>? {
        val trimmed = field.trim()
        if (trimmed == "*" || trimmed == "?") return emptySet()
        val result = LinkedHashSet<Int>()
        for (part in trimmed.split(",")) {
            val item = part.trim()
            if (item.isEmpty()) return null
            val slash = item.indexOf('/')
            val basePart = if (slash >= 0) item.substring(0, slash) else item
            val stepPart = if (slash >= 0) item.substring(slash + 1) else ""
            val step = if (stepPart.isEmpty()) 1 else stepPart.toIntOrNull() ?: return null
            if (step <= 0) return null

            val lo: Int
            val hi: Int
            if (basePart == "*" || basePart == "?") {
                lo = range.first
                hi = range.last
            } else {
                val dash = basePart.indexOf('-', startIndex = 1)
                if (dash >= 0) {
                    lo = resolveValue(basePart.substring(0, dash), names) ?: return null
                    hi = resolveValue(basePart.substring(dash + 1), names) ?: return null
                } else {
                    val single = resolveValue(basePart, names) ?: return null
                    lo = single
                    hi = if (slash >= 0) range.last else single
                }
            }
            if (lo < range.first || hi > range.last || lo > hi) return null
            var v = lo
            while (v <= hi) {
                result.add(v)
                v += step
            }
        }
        return result
    }

    private fun resolveValue(text: String, names: Map<String, Int>): Int? {
        val token = text.trim()
        if (token.isEmpty()) return null
        names[token.uppercase()]?.let { return it }
        return token.toIntOrNull()
    }

    private fun format(ms: Long, withSeconds: Boolean): String {
        val dateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return if (withSeconds) {
            String.format(
                java.util.Locale.US,
                "%04d-%02d-%02d %02d:%02d:%02d",
                dateTime.year,
                dateTime.monthValue,
                dateTime.dayOfMonth,
                dateTime.hour,
                dateTime.minute,
                dateTime.second
            )
        } else {
            String.format(
                java.util.Locale.US,
                "%04d-%02d-%02d %02d:%02d",
                dateTime.year,
                dateTime.monthValue,
                dateTime.dayOfMonth,
                dateTime.hour,
                dateTime.minute
            )
        }
    }
}

/** 表达式生成：按方言决定字段顺序、默认值与常用模板 */
object CronBuilder {

    fun fields(type: CronType): List<CronField> = buildList {
        if (type != CronType.LINUX) add(CronField.SECOND)
        add(CronField.MINUTE)
        add(CronField.HOUR)
        add(CronField.DAY_OF_MONTH)
        add(CronField.MONTH)
        add(CronField.DAY_OF_WEEK)
        if (type == CronType.QUARTZ) add(CronField.YEAR)
    }

    fun defaults(type: CronType): List<String> = fields(type).map { field ->
        when (field) {
            CronField.SECOND -> "0"
            CronField.DAY_OF_WEEK -> if (type == CronType.QUARTZ) "?" else "*"
            CronField.YEAR -> ""
            else -> "*"
        }
    }

    fun build(type: CronType, values: List<String>): String {
        val parts = fields(type).mapIndexed { index, field ->
            val raw = values.getOrNull(index).orEmpty().trim()
            if (field == CronField.YEAR) raw else raw.ifBlank { "*" }
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 模板：返回与 [fields] 等长的字段值列表 */
    fun preset(type: CronType, preset: CronPreset): List<String> {
        val monday = if (type == CronType.QUARTZ) "MON" else "1"
        val anyDay = "*"
        // Quartz 要求日与周恰好一个为 ?，其余方言用 * 通配
        val week = if (type == CronType.QUARTZ) "?" else "*"
        val day = if (type == CronType.QUARTZ) "?" else "*"
        val base = when (preset) {
            CronPreset.EVERY_MINUTE -> listOf("*", "*", anyDay, "*", week)
            CronPreset.EVERY_5_MINUTES -> listOf("*/5", "*", anyDay, "*", week)
            CronPreset.HOURLY -> listOf("0", "*", anyDay, "*", week)
            CronPreset.DAILY_MIDNIGHT -> listOf("0", "0", anyDay, "*", week)
            CronPreset.DAILY_9AM -> listOf("0", "9", anyDay, "*", week)
            CronPreset.WEEKLY_MONDAY_9AM -> listOf("0", "9", day, "*", monday)
            CronPreset.MONTHLY_FIRST -> listOf("0", "0", "1", "*", week)
            CronPreset.YEARLY -> listOf("0", "0", "1", "1", week)
        }
        val withSeconds = if (type == CronType.LINUX) base else listOf("0") + base
        return if (type == CronType.QUARTZ) withSeconds + "" else withSeconds
    }
}
