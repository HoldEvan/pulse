package com.evan.pulse.ui.common

import android.content.ClipData
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evan.pulse.R
import com.evan.pulse.ui.history.UiEventLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 复制后图标变色的时长，供各页面统一反馈节奏 */
const val COPY_FEEDBACK_MS = 1200L

/** 系统剪贴板历史里显示的来源名 */
private const val CLIPBOARD_LABEL = "Pulse"

/** 已复制状态的高亮色，与响应状态码里 2xx 的绿色保持一致 */
private val COPIED_TINT = Color(0xFF2E7D32)

/**
 * 取剪贴板写入能力；返回的 lambda 可在点击回调等非 Compose 上下文里调用。
 *
 * 复制完成后统一弹一条 Toast，各处复制按钮因此不需要各自处理反馈。
 */
@Composable
fun rememberCopyToClipboard(scope: CoroutineScope = rememberCoroutineScope()): (String) -> Unit {
    val clipboard: Clipboard = LocalClipboard.current
    val copiedText = stringResource(R.string.toast_copied)
    val tooLargeText = stringResource(R.string.copy_too_large)
    return remember(clipboard, scope, copiedText, tooLargeText) {
        { text ->
            if (text.isNotEmpty()) {
                scope.launch {
                    // 超大文本（如图片 Base64）经 Binder 传给系统剪贴板会抛
                    // TransactionTooLargeException，直接崩溃，这里兜底为失败提示
                    val ok = runCatching {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIPBOARD_LABEL, text)))
                    }.isSuccess
                    AppToast.show(if (ok) copiedText else tooLargeText)
                }
            }
        }
    }
}

/**
 * 纯图标复制按钮。
 *
 * [text] 为空时整体不渲染，避免响应还没回来时出现一个点了没反应的按钮；
 * 复制成功后图标短暂变绿，作为无 Snackbar 场景下的反馈。
 */
@Composable
fun CopyIconButton(text: String?, label: String, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return

    val copy = rememberCopyToClipboard()
    val target = text
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_FEEDBACK_MS)
            copied = false
        }
    }

    Icon(
        imageVector = MiuixIcons.Light.Copy,
        contentDescription = label,
        tint = if (copied) COPIED_TINT else MiuixTheme.colorScheme.onBackground,
        modifier = modifier
            .roundedPressable(onClick = {
                copied = true
                copy(target)
            })
            .padding(6.dp)
    )
}

/** 事件流导出文本：每行 "HH:mm:ss.SSS 内容"，与界面里的时间格式一致 */
fun eventLogText(lines: List<UiEventLine>): String =
    lines.joinToString("\n") { line ->
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(line.timestamp))
        "$time ${line.text}"
    }
