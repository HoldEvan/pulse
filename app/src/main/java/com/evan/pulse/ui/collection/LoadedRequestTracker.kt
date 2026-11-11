package com.evan.pulse.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.evan.pulse.di.CollectionStore

/**
 * 协议页与集合条目的绑定关系。
 *
 * 从集合点开的请求、或刚通过保存对话框新建的请求都会记住 id。每页保存时仍然弹出
 * 保存对话框（可以改名、换集合），确认时若已绑定条目就原地更新该条，不再新建记录。
 */
class LoadedRequestTracker(private val store: CollectionStore) {

    var requestId by mutableStateOf<Long?>(null)
        private set

    /** 加载或新建条目后绑定 id */
    fun track(id: Long) {
        if (id > 0) requestId = id
    }

    /** 解绑：条目被删除后不再指向不存在的记录 */
    fun unbind() {
        requestId = null
    }

    /**
     * 已绑定条目时原地更新名称、所属集合与快照（保持条目位置与创建时间）；
     * 返回 false 表示没有可更新的条目（未绑定，或条目已被删除），调用方应改为新建。
     */
    fun update(collectionId: Long, name: String, data: String): Boolean {
        val id = requestId ?: return false
        if (name.isBlank()) return false
        val updated = store.updateRequest(id, collectionId, name, data)
        if (!updated) requestId = null
        return updated
    }
}
