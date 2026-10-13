package com.evan.pulse.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 中文与英文正文共用的条款标题模式：中文序号、英文数字编号或日期行 */
private val HeadingPattern = Regex(
    "^(更新日期|Last updated|[一二三四五六七八九十]+、|\\d+\\.\\s\\S.{0,50}$).*"
)

/**
 * 协议类文本页（用户协议 / 隐私政策）：整页可滚动，按空行分段，
 * 短的编号行按条款标题加粗显示。
 */
@Composable
fun LegalScreen(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        text.split("\n\n").forEachIndexed { index, paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            val isHeading = index == 0 || HeadingPattern.matches(trimmed)
            Text(
                text = trimmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                fontSize = if (isHeading) 15.sp else 14.sp,
                lineHeight = 24.sp,
                fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
