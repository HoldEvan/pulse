package com.evan.pulse.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.evan.pulse.R
import com.evan.pulse.di.CollectionStore
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.l10n.L10n

/**
 * 协议页与集合条目的绑定关系。
 *
 * 从集合点开的请求、或刚通过保存对话框新建的请求都会记住 id；之后再点保存
 * 直接原地覆盖快照，既不弹保存窗口也不会多出一条记录。
 */
class LoadedRequestTracker(private val store: CollectionStore) {

    var requestId by mutableStateOf<Long?>(null)
        private set

    /** 加载或新建条目后绑定 id */
    fun track(id: Long) {
        if (id > 0) requestId = id
    }

    /**
     * 已绑定条目时原地覆盖并提示成功；返回 false 表示没有可覆盖的条目
     * （未绑定，或条目已被删除），调用方应弹出保存对话框。
     */
    fun saveOver(snapshot: () -> String): Boolean {
        val id = requestId ?: return false
        if (!store.updateRequest(id, snapshot())) {
            requestId = null
            return false
        }
        AppToast.show(L10n.t(R.string.toast_request_updated))
        return true
    }
}
