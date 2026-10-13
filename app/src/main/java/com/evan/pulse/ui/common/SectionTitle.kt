package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

/** 卡片内的区块小标题：去掉 SmallTitle 默认的全宽内边距，与卡片内容左对齐 */
@Composable
fun CardSectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin = PaddingValues(0.dp)
    )
}

/** 卡片外的区块标题：左内边距 16dp，与卡片内 preference 内容对齐 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp
) {
    SmallTitle(
        text = text,
        modifier = modifier.padding(top = topPadding),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 卡片外的区块标题行：标题占满剩余宽度，右侧放图标操作按钮。
 *
 * 内边距与 [SectionTitle] 一致（16dp），与卡片内 preference 内容左对齐。
 */
@Composable
fun SectionTitleRow(
    label: String,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallTitle(
            text = label,
            modifier = Modifier.weight(1f),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        )
        actions()
    }
}

/** 滚动内容末尾的底部留白 box：悬浮底栏浮在内容之上，滚动到最底部时
 *  需要一个空白区让最后一条内容不被底栏遮住。放进各标签页滚动区末尾。 */
@Composable
fun BottomBarClearance(modifier: Modifier = Modifier) {
    val navInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    Spacer(modifier = modifier.height(96.dp + navInset))
}
