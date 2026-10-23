package com.evan.pulse.domain.tools

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronParserTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parse(expression: String, type: CronType, from: Long) =
        CronParser.parse(expression, type, from)

    @Test
    fun `ten runs are ordered nearest first across year boundary`() {
        val r = CronParser.parse("*/5 * * * *", fromMs = at(2026, 12, 31, 23, 53))
        val expected = listOf(at(2026, 12, 31, 23, 55)) +
            (0..8).map { at(2027, 1, 1, 0, it * 5) }
        assertTrue(r.valid)
        assertEquals(expected, r.runTimes)
        assertEquals(r.nextRunMs, r.runTimes.first())
        assertEquals(10, r.runTimesText.lines().size)
        assertEquals("2026-12-31 23:55", r.runTimesText.lines().first())
        assertEquals("2027-01-01 00:40", r.runTimesText.lines().last())
    }

    @Test
    fun `annual expression returns ten years`() {
        val r = CronParser.parse("0 0 1 1 *", fromMs = at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals((2027..2036).map { at(it, 1, 1, 0, 0) }, r.runTimes)
    }

    @Test
    fun `leap day expression skips non leap century`() {
        val r = CronParser.parse("0 0 29 2 *", fromMs = at(2096, 3, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals((2104..2140 step 4).map { at(it, 2, 29, 0, 0) }, r.runTimes)
    }

    @Test
    fun `impossible date returns empty results`() {
        val r = CronParser.parse("0 0 30 2 *", fromMs = at(2026, 1, 1, 0, 0))
        assertFalse(r.valid)
        assertEquals("range", r.error)
        assertTrue(r.runTimes.isEmpty())
        assertEquals("", r.runTimesText)
    }

    @Test
    fun `wrong field count is invalid`() {
        val r = CronParser.parse("0 * * *", fromMs = at(2026, 1, 1, 0, 0))
        assertFalse(r.valid)
        assertEquals("fields", r.error)
    }

    @Test
    fun `bad minute is invalid`() {
        val r = CronParser.parse("70 * * * *", fromMs = at(2026, 1, 1, 0, 0))
        assertFalse(r.valid)
    }

    @Test
    fun `every minute wildcard`() {
        // 从 00:00 起，下一分钟为 00:01
        val r = CronParser.parse("* * * * *", fromMs = at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 0, 1), r.nextRunMs)
    }

    @Test
    fun `specific minute and hour`() {
        // 每天 03:30，从 2026-01-01 02:00 起，下一次是当天 03:30
        val r = CronParser.parse("30 3 * * *", fromMs = at(2026, 1, 1, 2, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 3, 30), r.nextRunMs)
    }

    @Test
    fun `every 5 minutes from midnight`() {
        // 从 00:03 起，下一次是 00:05
        val r = CronParser.parse("*/5 * * * *", fromMs = at(2026, 1, 1, 0, 3))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 0, 5), r.nextRunMs)
    }

    @Test
    fun `monthly day 15`() {
        // 每月 15 日 00:00，从 01-02 起，下一次是 01-15
        val r = CronParser.parse("0 0 15 * *", fromMs = at(2026, 1, 2, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 15, 0, 0), r.nextRunMs)
    }

    @Test
    fun `weekly monday`() {
        // 每周一 09:00，2026-01-01 是周四，下一个周一为 01-05
        val r = CronParser.parse("0 9 * * 1", fromMs = at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        // 2026-01-05 是周一
        assertEquals(at(2026, 1, 5, 9, 0), r.nextRunMs)
    }

    @Test
    fun `range field`() {
        // 每天 08-10 点的整点，从 09:30 起，下一次 10:00
        val r = CronParser.parse("0 8-10 * * *", fromMs = at(2026, 1, 1, 9, 30))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 10, 0), r.nextRunMs)
    }

    @Test
    fun `spring expression with question mark day`() {
        val r = parse("0 0 9 * * ?", CronType.SPRING, at(2026, 1, 1, 8, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 9, 0), r.nextRunMs)
    }

    @Test
    fun `spring second field controls next run`() {
        val r = parse("*/30 * * * * *", CronType.SPRING, at(2026, 1, 1, 0, 3))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 1, 0, 3, 30), r.nextRunMs)
    }

    @Test
    fun `spring results include seconds`() {
        val r = parse("30 * * * * *", CronType.SPRING, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals("2026-01-01 00:00:30", r.runTimesText.lines().first())
        assertEquals("2026-01-01 00:00:30", r.nextRunText)
    }

    @Test
    fun `linux results omit seconds`() {
        val r = CronParser.parse("0 1 * * *", fromMs = at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals("2026-01-01 01:00", r.runTimesText.lines().first())
    }

    @Test
    fun `quartz results include seconds`() {
        val r = parse("15 0 9 * * ?", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals("2026-01-01 09:00:15", r.runTimesText.lines().first())
    }

    @Test
    fun `quartz weekday names map monday`() {
        val r = parse("0 0 9 ? * MON", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 5, 9, 0), r.nextRunMs)
    }

    @Test
    fun `quartz numeric weekday starts at sunday`() {
        val r = parse("0 0 9 ? * 2", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 5, 9, 0), r.nextRunMs)
    }

    @Test
    fun `quartz last day of month`() {
        val r = parse("0 0 0 L * ?", CronType.QUARTZ, at(2026, 1, 2, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 31, 0, 0), r.nextRunMs)
    }

    @Test
    fun `quartz last friday of month`() {
        val r = parse("0 0 0 ? * 6L", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 30, 0, 0), r.nextRunMs)
    }

    @Test
    fun `quartz second monday of month`() {
        val r = parse("0 0 0 ? * MON#2", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 12, 0, 0), r.nextRunMs)
    }

    @Test
    fun `quartz nearest weekday`() {
        val r = parse("0 0 0 15W * ?", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 15, 0, 0), r.nextRunMs)
    }

    @Test
    fun `quartz requires exactly one of day and week`() {
        assertEquals("dayConflict", parse("0 0 0 15 * MON", CronType.QUARTZ, at(2026, 1, 1, 0, 0)).error)
        assertEquals("dayConflict", parse("0 0 0 ? * ?", CronType.QUARTZ, at(2026, 1, 1, 0, 0)).error)
    }

    @Test
    fun `quartz optional year limits runs`() {
        val r = parse("0 0 0 1 1 ? 2030", CronType.QUARTZ, at(2026, 1, 1, 0, 0))
        assertTrue(r.valid)
        assertEquals(at(2030, 1, 1, 0, 0), r.nextRunMs)
        assertEquals(1, r.runTimes.size)
    }

    @Test
    fun `linux aliases are resolved`() {
        val r = CronParser.parse("@daily", fromMs = at(2026, 1, 1, 3, 0))
        assertTrue(r.valid)
        assertEquals(at(2026, 1, 2, 0, 0), r.nextRunMs)
        assertEquals("unsupported", CronParser.parse("@reboot").error)
        assertEquals("fields", parse("@daily", CronType.SPRING, at(2026, 1, 1, 0, 0)).error)
    }

    @Test
    fun `linux rejects question mark`() {
        assertEquals("day", CronParser.parse("0 0 ? * *", fromMs = at(2026, 1, 1, 0, 0)).error)
    }

    @Test
    fun `builder field layout per dialect`() {
        assertEquals(
            listOf(CronField.MINUTE, CronField.HOUR, CronField.DAY_OF_MONTH, CronField.MONTH, CronField.DAY_OF_WEEK),
            CronBuilder.fields(CronType.LINUX)
        )
        assertEquals(CronField.SECOND, CronBuilder.fields(CronType.SPRING).first())
        assertEquals(CronField.YEAR, CronBuilder.fields(CronType.QUARTZ).last())
    }

    @Test
    fun `builder defaults produce wildcard expression`() {
        val quartz = CronBuilder.build(CronType.QUARTZ, CronBuilder.defaults(CronType.QUARTZ))
        assertEquals("0 * * * * ?", quartz)
        val spring = CronBuilder.build(CronType.SPRING, CronBuilder.defaults(CronType.SPRING))
        assertEquals("0 * * * * *", spring)
        assertTrue(CronParser.parse(quartz, CronType.QUARTZ).valid)
        assertTrue(CronParser.parse(spring, CronType.SPRING).valid)
    }

    @Test
    fun `builder presets build valid expressions`() {
        CronType.entries.forEach { type ->
            CronPreset.entries.forEach { preset ->
                val expression = CronBuilder.build(type, CronBuilder.preset(type, preset))
                assertTrue("$type $preset -> $expression", CronParser.parse(expression, type).valid)
            }
        }
    }

    @Test
    fun `builder preset matches expected expression`() {
        assertEquals(
            "0 9 * * 1",
            CronBuilder.build(CronType.LINUX, CronBuilder.preset(CronType.LINUX, CronPreset.WEEKLY_MONDAY_9AM))
        )
        assertEquals(
            "0 0 9 * * ?",
            CronBuilder.build(CronType.QUARTZ, CronBuilder.preset(CronType.QUARTZ, CronPreset.DAILY_9AM))
        )
    }
}
