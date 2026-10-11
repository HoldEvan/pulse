package com.evan.pulse.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown

/**
 * Markdown 渲染封装：基于 multiplatform-markdown-renderer 的 M3 主题实现，
 * 供 SSE 拼接预览等场景把流式文本按 Markdown 展示。
 */
@Composable
fun MarkdownBlock(markdown: String, modifier: Modifier = Modifier) {
    if (markdown.isBlank()) return
    Markdown(
        content = markdown,
        modifier = modifier
    )
}