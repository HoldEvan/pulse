package com.evan.pulse.domain.diff

/**
 * 行级 LCS 差异：Hirschberg 分治实现，时间 O(n*m)、内存 O(min(n,m))，
 * 不需要整份 DP 矩阵，几千行的响应体也能安全对比。
 */
object LineDiff {

    enum class RowType { EQUAL, ADDED, REMOVED }

    data class Row(val type: RowType, val text: String)

    data class Result(val rows: List<Row>, val truncated: Boolean) {
        val addedCount: Int get() = rows.count { it.type == RowType.ADDED }
        val removedCount: Int get() = rows.count { it.type == RowType.REMOVED }
        val hasDifference: Boolean get() = rows.any { it.type != RowType.EQUAL }
    }

    /**
     * [maxLines] 限制两侧各自参与对比的行数，超出部分截断并在 [Result.truncated]
     * 标记，避免 O(n*m) 在超大响应体上失控。
     */
    fun diff(old: String, new: String, maxLines: Int = 2000): Result {
        val oldLines = splitLines(old)
        val newLines = splitLines(new)
        val truncated = oldLines.size > maxLines || newLines.size > maxLines
        val rows = ArrayList<Row>(oldLines.size + newLines.size)
        emit(oldLines.take(maxLines), newLines.take(maxLines), rows)
        return Result(rows, truncated)
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return text.replace("\r\n", "\n").replace('\r', '\n').split('\n').let { lines ->
            // 尾部换行产生的空行去掉，两侧末尾都有换行时不产生假差异
            if (lines.lastOrNull()?.isEmpty() == true) lines.dropLast(1) else lines
        }
    }

    private fun emit(a: List<String>, b: List<String>, out: MutableList<Row>) {
        // 公共前缀/后缀先剥离，缩小分治规模
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--
            endB--
        }
        repeat(start) { out += Row(RowType.EQUAL, a[it]) }

        val midA = a.subList(start, endA)
        val midB = b.subList(start, endB)
        when {
            midA.isEmpty() -> midB.forEach { out += Row(RowType.ADDED, it) }
            midB.isEmpty() -> midA.forEach { out += Row(RowType.REMOVED, it) }
            midA.size == 1 -> {
                // 基例：单行对多行，命中则视为一处相等，其余全部按增补计
                val index = midB.indexOf(midA[0])
                if (index >= 0) {
                    for (i in 0 until index) out += Row(RowType.ADDED, midB[i])
                    out += Row(RowType.EQUAL, midA[0])
                    for (i in index + 1 until midB.size) out += Row(RowType.ADDED, midB[i])
                } else {
                    midA.forEach { out += Row(RowType.REMOVED, it) }
                    midB.forEach { out += Row(RowType.ADDED, it) }
                }
            }
            else -> {
                val cutA = midA.size / 2
                val forward = lcsLengths(midA.subList(0, cutA), midB)
                val backward = lcsLengths(
                    midA.subList(cutA, midA.size).asReversed(),
                    midB.asReversed()
                )
                var bestK = 0
                var best = -1
                for (k in 0..midB.size) {
                    val sum = forward[k] + backward[midB.size - k]
                    if (sum > best) {
                        best = sum
                        bestK = k
                    }
                }
                emit(midA.subList(0, cutA), midB.subList(0, bestK), out)
                emit(midA.subList(cutA, midA.size), midB.subList(bestK, midB.size), out)
            }
        }

        for (i in endA until a.size) out += Row(RowType.EQUAL, a[i])
    }

    /** 返回最后一行 LCS 长度表：arr[j] = LCS(x, y[0..j))。
     * 数组恒按 y（列）的长度分配，调用方按 y 的索引（k in 0..y.size）访问，
     * 不做"较短侧为行"的交换——否则列长小于行数时数组被截短，划分循环会越界。 */
    private fun lcsLengths(x: List<String>, y: List<String>): IntArray {
        val prev = IntArray(y.size + 1)
        val curr = IntArray(y.size + 1)
        for (line in x) {
            for (j in y.indices) {
                curr[j + 1] = if (line == y[j]) prev[j] + 1 else maxOf(prev[j + 1], curr[j])
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        return curr
    }
}
