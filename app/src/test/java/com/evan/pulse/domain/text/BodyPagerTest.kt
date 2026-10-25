package com.evan.pulse.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyPagerTest {

    private val budget = 100

    private fun pages(text: String, charsPerPage: Int = budget) =
        BodyPager.paginate(text, charsPerPage)

    @Test
    fun emptyTextHasNoPages() {
        val result = pages("")
        assertEquals(0, result.pageCount)
        assertEquals(0, result.totalChars)
        assertEquals(0, result.totalLines)
        assertEquals("", result.slice("", 0))
    }

    @Test
    fun shortTextIsSinglePage() {
        val text = "short body"
        val result = pages(text)
        assertEquals(1, result.pageCount)
        assertEquals(text, result.slice(text, 0))
        assertEquals(1, result.totalLines)
    }

    @Test
    fun exactBudgetIsSinglePage() {
        val text = "x".repeat(budget)
        assertEquals(1, pages(text).pageCount)
    }

    @Test
    fun oneCharOverBudgetSplitsIntoTwo() {
        val text = "x".repeat(budget + 1)
        val result = pages(text)
        assertEquals(2, result.pageCount)
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun everyPageStaysWithinBudget() {
        val text = "x".repeat(budget * 7 + 13)
        val result = pages(text)
        result.ranges.forEach { range ->
            assertTrue(range.last - range.first + 1 <= budget)
        }
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun prefersNewlineBreak() {
        val line = "y".repeat(40) + "\n"
        val text = line.repeat(10)
        val result = pages(text)
        result.ranges.dropLast(1).forEach { range ->
            assertEquals('\n', text[range.last])
        }
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun breaksMidLineWhenNewlineTooEarly() {
        val text = "a\n" + "x".repeat(budget * 2)
        val result = pages(text)
        assertEquals(3, result.pageCount)
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun neverSplitsSurrogatePair() {
        val text = "a".repeat(budget - 1) + "\uD83D\uDE00" + "b".repeat(budget)
        val result = pages(text)
        result.ranges.forEach { range ->
            val page = text.substring(range.first, range.last + 1)
            assertTrue(
                "page ${range.first}..${range.last} 拆开了代理对",
                !page.startsWith("\uDE00") && !page.endsWith("\uD83D")
            )
        }
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun rangesAreContiguousAndCoverAll() {
        val text = buildString {
            repeat(60) { append("line ").append(it).append('\n') }
        }
        val result = pages(text, 50)
        assertEquals(0, result.ranges.first().first)
        assertEquals(text.length - 1, result.ranges.last().last)
        result.ranges.zipWithNext().forEach { (a, b) ->
            assertEquals(a.last + 1, b.first)
        }
    }

    @Test
    fun sliceClampsOutOfRangePage() {
        val text = "x".repeat(budget * 3)
        val result = pages(text)
        assertEquals(result.slice(text, 0), result.slice(text, -5))
        assertEquals(result.slice(text, result.pageCount - 1), result.slice(text, 99))
    }

    @Test
    fun countsTotalLines() {
        assertEquals(3, pages("a\nb\nc").totalLines)
        assertEquals(3, pages("a\nb\nc\n").totalLines)
        assertEquals(1, pages("a").totalLines)
    }

    @Test
    fun countsTotalChars() {
        val text = "中文内容".repeat(50)
        assertEquals(text.length, pages(text, 30).totalChars)
    }

    @Test
    fun singleVeryLongLineStillPaginates() {
        val text = "z".repeat(budget * 5)
        val result = pages(text)
        assertEquals(5, result.pageCount)
        assertEquals(text, joinAll(text, result))
    }

    @Test
    fun rejectsNonPositiveBudget() {
        listOf(0, 1, -10).forEach { bad ->
            val error = runCatching { BodyPager.paginate("abc", bad) }.exceptionOrNull()
            assertTrue("budget=$bad 应被拒绝", error is IllegalArgumentException)
        }
    }

    private fun joinAll(text: String, pages: BodyPages): String =
        (0 until pages.pageCount).joinToString("") { pages.slice(text, it) }
}
