package com.evan.pulse.ui.collection

import androidx.lifecycle.ViewModel
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.ImportGroup
import com.evan.pulse.data.model.ImportResult
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.di.ServiceLocator

class CollectionsViewModel : ViewModel() {

    private val store = ServiceLocator.collectionStore
    private val projectStore = ServiceLocator.projectStore

    val collections = store.collections
    val requests = store.requests
    val currentProjectId = projectStore.currentProjectId
    val projects = projectStore.projects

    /** 指定父文件夹下的同级文件夹；parentId 为空表示根文件夹 */
    fun childrenOf(parentId: Long?): List<CollectionInfo> =
        CollectionTree.childrenOf(collections.value, currentProjectId.value, parentId)

    /** 当前项目下全部文件夹，用于保存对话框的扁平路径列表 */
    fun projectCollections(): List<CollectionInfo> =
        collections.value.filter { it.projectId == currentProjectId.value }

    /** 完整路径名称链，从根到自身 */
    fun pathOf(id: Long): List<String> = CollectionTree.pathOf(collections.value, id)

    /** 被移动文件夹将被带入的集合数量（含自身） */
    fun descendantFolderCount(id: Long): Int =
        CollectionTree.descendantsOf(collections.value, id).size + 1

    /** 被移动文件夹将被带入的接口条目数量 */
    fun descendantRequestCount(id: Long): Int {
        val doomed = CollectionTree.descendantsOf(collections.value, id) + id
        return requests.value.count { it.collectionId in doomed }
    }

    fun create(name: String, parentId: Long? = null) =
        store.create(currentProjectId.value, name, parentId)

    fun rename(id: Long, name: String) = store.rename(id, name)

    /** 递归删除文件夹及其全部后代与接口条目 */
    fun deleteRecursive(id: Long) = store.deleteRecursive(id)

    /** 移动文件夹；目标是自身或自身后代时返回 false，targetId 为空表示移到根层级 */
    fun move(id: Long, targetId: Long?): Boolean = store.move(id, targetId)

    /**
     * 可作为新父级的候选集合：当前项目内、排除自身与自身全部后代。
     *
     * 在弹窗里预先过滤，用户就不会有机会把集合移进自己的子树里。
     */
    fun moveTargets(id: Long): List<Long> {
        val all = collections.value
        val banned = CollectionTree.descendantsOf(all, id) + id
        return all
            .filter { it.projectId == currentProjectId.value && it.id !in banned }
            .map { it.id }
    }

    fun deleteRequest(id: Long) = store.deleteRequest(id)

    fun renameRequest(id: Long, name: String) = store.renameRequest(id, name)

    fun moveRequest(id: Long, collectionId: Long) = store.moveRequest(id, collectionId)

    /** 批量导入规范解析结果，在当前项目根下新建集合；标签多于一组时按标签建子集合 */
    fun importBatch(collectionName: String, groups: List<ImportGroup>): ImportResult =
        store.importBatch(currentProjectId.value, collectionName, groups)

    /** 点击请求行：写入待加载并通知宿主切换到对应协议 Tab */
    fun load(request: SavedRequest, onSwitchTab: (Int) -> Unit) {
        store.pendingLoad.value = request
        onSwitchTab(tabIndex(request.type))
    }

    /** 保存新接口并返回其 id，供调用方后续原地覆盖 */
    fun saveRequest(
        collectionId: Long,
        type: RequestType,
        name: String,
        data: String,
        fallbackCollectionName: String
    ): Long = store.saveRequest(
        currentProjectId.value,
        collectionId,
        type,
        name,
        data,
        fallbackCollectionName
    )

    private fun tabIndex(type: RequestType): Int = when (type) {
        RequestType.HTTP -> 0
        RequestType.SSE -> 1
        RequestType.WS -> 2
        RequestType.MQTT -> 3
    }
}
