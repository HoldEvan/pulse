package com.evan.pulse.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel : ViewModel() {

    private val store = ServiceLocator.historyStore
    private val projectStore = ServiceLocator.projectStore

    val projects: StateFlow<List<ProjectInfo>> = projectStore.projects
    val history: StateFlow<List<HistoryEntry>> = store.entries

    var keyword by mutableStateOf("")

    /** 项目筛选；为空表示不限项目，也就是默认展示全部项目的记录 */
    var projectFilter by mutableStateOf<Long?>(null)

    var confirmClear by mutableStateOf(false)

    /** 按关键词与项目过滤后的记录，列表页与筛选器共用这一份结果 */
    fun entries(projectFilter: Long?, keyword: String): List<HistoryEntry> {
        val kw = keyword.trim()
        return store.entries.value.filter { entry ->
            (projectFilter == null || entry.projectId == projectFilter) &&
                (kw.isEmpty() ||
                    entry.url.contains(kw, ignoreCase = true) ||
                    entry.method.equals(kw, ignoreCase = true))
        }
    }

    /** 历史里出现过的项目，用于判断是否需要展示筛选器 */
    fun projectsInHistory(): List<ProjectInfo> =
        projectStore.projects.value
            .filter { project -> store.entries.value.any { it.projectId == project.id } }
            .sortedBy { it.name }

    fun nameOf(projectId: Long): String =
        projectStore.projects.value.firstOrNull { it.id == projectId }?.name.orEmpty()

    fun byId(id: Long): HistoryEntry? = store.entries.value.firstOrNull { it.id == id }

    fun clear() {
        store.clear()
        confirmClear = false
    }

    /** 侧滑删除单条记录；详情页正打开着这条时一并退回历史列表 */
    fun delete(id: Long) {
        store.delete(id)
    }
}
