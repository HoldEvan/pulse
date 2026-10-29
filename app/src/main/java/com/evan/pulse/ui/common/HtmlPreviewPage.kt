package com.evan.pulse.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.design.AppText

/**
 * 待预览的 HTML 正文。响应体可能有几十 MB，放进路由参数会撑爆返回栈，
 * 因此跳转前写入这里，预览页按当前进程状态读取。
 */
object HtmlPreviewStore {
    @Volatile
    var html: String = ""
        private set

    @Volatile
    var baseUrl: String = ""
        private set

    fun set(html: String, baseUrl: String?) {
        this.html = html
        this.baseUrl = baseUrl.orEmpty()
    }

    fun clear() {
        html = ""
        baseUrl = ""
    }
}

/** 全屏 HTML 预览：顶栏让位由调用方给出的 padding 提供，WebView 铺满剩余空间 */
@Composable
fun HtmlPreviewPage(modifier: Modifier = Modifier) {
    val html = HtmlPreviewStore.html
    if (html.isEmpty()) {
        AppText(
            text = stringResource(R.string.resp_preview_unavailable),
            modifier = modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    HtmlPreview(
        body = html,
        baseUrl = HtmlPreviewStore.baseUrl,
        modifier = modifier,
        fillHeight = true
    )
}
