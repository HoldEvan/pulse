package com.evan.pulse.domain.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {

    private fun texts(rows: List<LineDiff.Row>, type: LineDiff.RowType? = null) =
        rows.filter { type == null || it.type == type }.joinToString("\n") { it.text }

    @Test
    fun identicalBodiesProduceOnlyEqualRows() {
        val result = LineDiff.diff("a\nb\nc", "a\nb\nc")
        assertFalse(result.hasDifference)
        assertFalse(result.truncated)
        assertEquals("a\nb\nc", texts(result.rows, LineDiff.RowType.EQUAL))
    }

    @Test
    fun trailingNewlineDoesNotCreateFakeDifference() {
        val result = LineDiff.diff("a\nb\n", "a\nb")
        assertFalse(result.hasDifference)
    }

    @Test
    fun addedLinesAreReported() {
        val result = LineDiff.diff("a\nd", "a\nb\nc\nd")
        assertEquals(2, result.addedCount)
        assertEquals(0, result.removedCount)
        assertEquals("b\nc", texts(result.rows, LineDiff.RowType.ADDED))
        assertEquals("a\nd", texts(result.rows, LineDiff.RowType.EQUAL))
    }

    @Test
    fun removedLinesAreReported() {
        val result = LineDiff.diff("a\nb\nc\nd", "a\nd")
        assertEquals(0, result.addedCount)
        assertEquals(2, result.removedCount)
        assertEquals("b\nc", texts(result.rows, LineDiff.RowType.REMOVED))
    }

    @Test
    fun oldSideIsReconstructedFromEqualAndRemovedRows() {
        val old = "GET /a\n200\nbody v1\nextra"
        val new = "GET /a\n200\nbody v2"
        val result = LineDiff.diff(old, new)
        assertTrue(result.hasDifference)
        assertEquals(old, texts(result.rows.filter { it.type != LineDiff.RowType.ADDED }))
        assertEquals(new, texts(result.rows.filter { it.type != LineDiff.RowType.REMOVED }))
    }

    @Test
    fun emptyOldSideMeansEverythingAdded() {
        val result = LineDiff.diff("", "x\ny")
        assertEquals(2, result.addedCount)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun crlfLineEndingsAreNormalized() {
        val result = LineDiff.diff("a\r\nb\r\n", "a\nb\n")
        assertFalse(result.hasDifference)
    }

    @Test
    fun oversizedInputIsTruncatedAndFlagged() {
        val old = (1..3000).joinToString("\n") { "l$it" }
        val new = (1..3000).joinToString("\n") { "l$it" }
        val result = LineDiff.diff(old, new, maxLines = 2000)
        assertTrue(result.truncated)
        assertFalse(result.hasDifference)
        assertEquals(2000, result.rows.size)
    }

    @Test
    fun swappedBodiesMirrorTheReport() {
        val added = LineDiff.diff("a", "a\nb")
        val removed = LineDiff.diff("a\nb", "a")
        assertEquals(1, added.addedCount)
        assertEquals(1, removed.removedCount)
        assertEquals(added.rows.first { it.type == LineDiff.RowType.ADDED }.text, removed.rows.first { it.type == LineDiff.RowType.REMOVED }.text)
    }

    @Test
    fun unevenMiddleWhereLeftHalfIsShorterThanRightDoesNotOverflow() {
        // 旧侧中段比新侧长，切半后左半 < 新侧列长：旧实现数组被截短，划分循环越界
        val old = "p\n1\n2\n3\ns"
        val new = "p\n5\n6\ns"
        val result = LineDiff.diff(old, new)
        assertEquals(5, result.rows.count { it.type != LineDiff.RowType.ADDED })
        assertEquals(4, result.rows.count { it.type != LineDiff.RowType.REMOVED })
    }

    @Test
    fun heavilyUnevenSidesDoNotOverflow() {
        val old = (1..50).joinToString("\n") { "old$it" }
        val new = (1..5).joinToString("\n") { "new$it" }
        val result = LineDiff.diff(old, new)
        assertEquals(50, result.rows.count { it.type != LineDiff.RowType.ADDED })
        assertEquals(5, result.rows.count { it.type != LineDiff.RowType.REMOVED })
    }
}
