package com.evan.pulse.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppText
import top.yukonga.miuix.kmp.squircle.squircleBackground

/** Toast 显示时长，与复制图标的变绿时长配合使用 */
const val TOAST_DURATION_MS = 1600L

/** 一条待显示的 Toast；id 单调递增，连点两次也能重新触发 */
data class ToastItem(val id: Long, val text: String)

/**
 * 全局 Toast 的持有者。
 *
 * 提示需要出现在任意页面上，不能挂在某个页面的组合里，所以用一个进程内单例承载消息流，
 * 由根层的 [ToastHost] 统一消费。
 */
object AppToast {

    private val sequence = AtomicLong(0L)
    private val _item = MutableStateFlow<ToastItem?>(null)

    val item: StateFlow<ToastItem?> = _item.asStateFlow()

    fun show(text: String) {
        _item.value = ToastItem(sequence.incrementAndGet(), text)
    }

    internal fun dismiss() {
        _item.value = null
    }
}

/**
 * 底部居中的文字提示，挂在应用根层。
 *
 * Box 铺满父层并靠底居中，提示浮在导航栏之上；用 AnimatedVisibility 做淡入淡出，
 * 避免消息清空时提示块突兀地消失。
 */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val item by AppToast.item.collectAsState()

    LaunchedEffect(item?.id) {
        if (item == null) return@LaunchedEffect
        delay(TOAST_DURATION_MS)
        AppToast.dismiss()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = item != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val toast = item ?: return@AnimatedVisibility
            ToastPill(toast, modifier = Modifier.padding(bottom = 64.dp))
        }
    }
}

@Composable
private fun ToastPill(item: ToastItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .squircleBackground(
                AppTheme.colors.onSurface.copy(alpha = 0.92f),
                20.dp
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = item.text,
            color = AppTheme.colors.surface,
            fontSize = 13.sp
        )
    }
}
