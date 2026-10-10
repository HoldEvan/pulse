package com.evan.pulse.ui.l10n

import android.content.Context

/**
 * 非 Composable 场景（ViewModel 等）读取字符串资源的入口。
 * 在 PulseApp.onCreate 中调用 bind 注入应用上下文。
 */
object L10n {

    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    fun t(resId: Int, vararg args: Any?): String {
        val ctx = appContext ?: return ""
        return ctx.getString(resId, *args)
    }
}
