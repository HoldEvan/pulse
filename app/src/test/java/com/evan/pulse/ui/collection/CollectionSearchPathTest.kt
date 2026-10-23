package com.evan.pulse.ui.collection

import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 集合搜索的判定规则与文件夹路径，直接验证从树结构派生的展示信息。
 *
 * 搜索本身在 ViewModel 里组合多个 Store，这里只覆盖不依赖 Android 的纯逻辑：
 * 路径拼接与后代排除，保证搜索结果里的「所在文件夹」描述正确。
 */
class CollectionSearchPathTest {

    private val tree = listOf(
        CollectionInfo(id = 1, projectId = 7, parentId = null, name = "订单"),
        CollectionInfo(id = 2, projectId = 7, parentId = 1, name = "支付"),
        CollectionInfo(id = 3, projectId = 7, parentId = 2, name = "退款"),
        CollectionInfo(id = 4, projectId = 8, parentId = null, name = "其他项目")
    )

    @Test
    fun `path joins names from root to leaf`() {
        assertEquals(listOf("订单", "支付", "退款"), CollectionTree.pathOf(tree, 3))
        assertEquals(listOf("订单"), CollectionTree.pathOf(tree, 1))
    }

    @Test
    fun `unknown folder has no path`() {
        assertEquals(emptyList<String>(), CollectionTree.pathOf(tree, 99))
    }

    @Test
    fun `descendants exclude the folder itself and other projects`() {
        assertEquals(setOf(2L, 3L), CollectionTree.descendantsOf(tree, 1))
        assertEquals(setOf(3L), CollectionTree.descendantsOf(tree, 2))
        assertEquals(emptySet<Long>(), CollectionTree.descendantsOf(tree, 3))
    }
}
