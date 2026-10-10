package com.evan.pulse.data.model

/**
 * 集合树派生工具。
 *
 * 以扁平有序列表作为唯一数据源，按 parentId 递归派生树形结构；
 * 同级顺序等于文件夹在扁平列表中的相对顺序，因此无需维护并行的树结构或排序字段。
 */
object CollectionTree {

    /** 取某父文件夹下按列表顺序排列的同级文件夹；parentId 为空表示根文件夹 */
    fun childrenOf(
        collections: List<CollectionInfo>,
        projectId: Long,
        parentId: Long?
    ): List<CollectionInfo> = collections.filter {
        it.projectId == projectId && it.parentId == parentId
    }

    /** 完整路径名称链，从根文件夹到自身 */
    fun pathOf(collections: List<CollectionInfo>, id: Long): List<String> {
        val byId = collections.associateBy { it.id }
        val names = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var cursor = byId[id]
        while (cursor != null && visited.add(cursor.id)) {
            names.add(cursor.name)
            cursor = cursor.parentId?.let { byId[it] }
        }
        return names.asReversed()
    }

    /** 自身之下的全部后代 id，不含自身 */
    fun descendantsOf(collections: List<CollectionInfo>, id: Long): Set<Long> {
        val root = collections.firstOrNull { it.id == id } ?: return emptySet()
        val result = mutableSetOf<Long>()
        val stack = mutableListOf(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.lastIndex)
            for (child in collections) {
                if (child.parentId == current.id && result.add(child.id)) stack.add(child)
            }
        }
        return result
    }

    /** 从目标一路向上的全部祖先 id，不含自身 */
    fun ancestorsOf(collections: List<CollectionInfo>, id: Long): Set<Long> {
        val byId = collections.associateBy { it.id }
        val result = mutableSetOf<Long>()
        var cursor = byId[id]?.parentId?.let { byId[it] }
        while (cursor != null && result.add(cursor.id)) {
            cursor = cursor.parentId?.let { byId[it] }
        }
        return result
    }

    /** 判断 candidate 是否为 target 的祖先（含自身），用于拒绝把文件夹移进自己的子树 */
    fun isAncestorOrSelf(collections: List<CollectionInfo>, candidate: Long, target: Long): Boolean =
        candidate == target || candidate in ancestorsOf(collections, target)
}
