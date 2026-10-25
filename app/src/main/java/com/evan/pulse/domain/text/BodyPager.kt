package com.evan.pulse.domain.text

/**
 * 长文本分页结果：把原文切成若干连续区间，供界面按页渲染。
 *
 * 一次性渲染几十万字符会让 Compose 文本测量直接卡住主线程，因此大响应体只在
 * 当前页渲染，翻页时再取下一段。区间始终连续覆盖 `[0, totalChars)`，拼接各页即为原文。
 */
data class BodyPages(
    val ranges: List<IntRange>,
    val totalChars: Int,
    val totalLines: Int,
) {

    /** 页数；空文本为 0 页，界面按「无可展示内容」处理 */
    val pageCount: Int get() = ranges.size

    /** 最后一页的下标；空文本仍是 0，调用方配合 [pageCount] 判断是否可翻页 */
    val lastPageIndex: Int get() = (ranges.size - 1).coerceAtLeast(0)

    /** 取第 [page] 页的文本；越界时钳到首尾，不抛异常 */
    fun slice(text: String, page: Int): String {
        if (ranges.isEmpty()) return ""
        val range = ranges[page.coerceIn(0, ranges.lastIndex)]
        val from = range.first.coerceIn(0, text.length)
        val to = (range.last + 1).coerceIn(from, text.length)
        return text.substring(from, to)
    }
}

/**
 * 按字符预算切页，尽量在换行处断开，避免把一行拆到两页。
 *
 * 预算只约束单页字符数，不做行数上限：一行很长时宁可硬切，也不让某一页只放一行。
 */
object BodyPager {

    /** 单页字符预算：一行 60 列、每屏约 40 行时约 5 屏，翻页成本与渲染成本都还能接受 */
    const val DEFAULT_CHARS_PER_PAGE = 12_000

    fun paginate(text: String, charsPerPage: Int = DEFAULT_CHARS_PER_PAGE): BodyPages {
        require(charsPerPage > 1) { "每页字符数至少为 2" }
        if (text.isEmpty()) return BodyPages(emptyList(), 0, 0)
        val ranges = ArrayList<IntRange>(text.length / charsPerPage + 1)
        var start = 0
        while (start < text.length) {
            var end = minOf(start + charsPerPage, text.length)
            if (end < text.length) end = adjustBreak(text, start, end, charsPerPage)
            ranges += start until end
            start = end
        }
        val lines = text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1
        return BodyPages(ranges, text.length, lines)
    }

    /** 优先在预算末段找换行；找不到就硬切，但不能把一个代理对拆到两页 */
    private fun adjustBreak(text: String, start: Int, end: Int, charsPerPage: Int): Int {
        val newline = text.lastIndexOf('\n', end - 1)
        if (newline >= start + charsPerPage / 2) return newline + 1
        if (Character.isHighSurrogate(text[end - 1]) && end - 1 > start) return end - 1
        return end
    }
}
