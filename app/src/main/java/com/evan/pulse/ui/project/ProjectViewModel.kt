package com.evan.pulse.ui.project

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.ProjectInfo
import com.evan.pulse.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow

class ProjectViewModel : ViewModel() {
    private val store = ServiceLocator.projectStore
    private val envStore = ServiceLocator.envStore

    val projects: StateFlow<List<ProjectInfo>> = store.projects
    val currentProjectId: StateFlow<Long> = store.currentProjectId
    val environments: StateFlow<List<EnvironmentInfo>> = envStore.environments

    /** 集合与请求，用于项目列表副标题的统计（HTTP/SSE/WS/MQTT 数量） */
    val collections = ServiceLocator.collectionStore.collections
    val requests = ServiceLocator.collectionStore.requests

    private val _showDialog = mutableStateOf(false)
    val showDialog: State<Boolean> = _showDialog

    /** 资源页-项目 tab 的新建对话框：由顶栏"添加项目"按钮触发 */
    private val _showCreateDialog = mutableStateOf(false)
    val showCreateDialog: State<Boolean> = _showCreateDialog

    fun openDialog() {
        _showDialog.value = true
    }

    fun closeDialog() {
        _showDialog.value = false
    }

    fun openCreate() {
        _showCreateDialog.value = true
    }

    fun closeCreate() {
        _showCreateDialog.value = false
    }

    fun switch(id: Long) = store.switch(id)

    fun create(name: String) {
        if (name.isNotBlank()) store.create(name.trim())
    }

    fun rename(id: Long, name: String) {
        if (name.isNotBlank()) store.rename(id, name.trim())
    }

    fun delete(id: Long) = store.delete(id)

    /** 顶栏环境切换：激活当前项目下的某个环境 */
    fun activateEnv(envId: Long) = envStore.activate(store.currentProjectId.value, envId)

    /** 顶栏环境切换：清空当前项目的激活环境，回到无环境状态 */
    fun clearActiveEnv() = envStore.deactivateAll(store.currentProjectId.value)
}
