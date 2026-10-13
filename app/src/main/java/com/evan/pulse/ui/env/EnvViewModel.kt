package com.evan.pulse.ui.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.data.model.EnvironmentInfo
import kotlinx.coroutines.flow.StateFlow

class EnvViewModel : ViewModel() {

    private val store = ServiceLocator.envStore
    private val projectStore = ServiceLocator.projectStore

    val currentProjectId: StateFlow<Long> = projectStore.currentProjectId

    var showEditor by mutableStateOf(false)
    var editing by mutableStateOf<EnvironmentInfo?>(null)
    var nameInput by mutableStateOf("")
    var creating by mutableStateOf(false)
    var deleteTarget by mutableStateOf<EnvironmentInfo?>(null)

    /** 变量二级页的编辑状态：varsEnvId 为目标环境，varEditIndex 为 null 表示新增 */
    var showVarEditor by mutableStateOf(false)
    var varsEnvId by mutableStateOf<Long?>(null)
    var varEditIndex by mutableStateOf<Int?>(null)
    var varNameInput by mutableStateOf("")
    var varValueInput by mutableStateOf("")

    /** 待删除变量：envId + 索引 + 变量名（确认弹窗展示用） */
    var varDeleteTarget by mutableStateOf<Triple<Long, Int, String>?>(null)

    val environments: StateFlow<List<EnvironmentInfo>> = store.environments

    fun openCreate() {
        creating = true
        editing = null
        nameInput = ""
        showEditor = true
    }

    fun openRename(env: EnvironmentInfo) {
        creating = false
        editing = env
        nameInput = env.name
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        editing = null
        creating = false
    }

    /** 只保存名称；变量在独立的变量页维护 */
    fun save() {
        val name = nameInput.trim()
        if (name.isEmpty()) {
            closeEditor()
            return
        }
        if (creating) {
            store.create(currentProjectId.value, name)
        } else {
            editing?.let { env -> store.rename(env.id, name) }
        }
        closeEditor()
    }

    fun requestDelete(env: EnvironmentInfo) {
        deleteTarget = env
    }

    fun confirmDelete() {
        deleteTarget?.let { store.delete(it.id) }
        deleteTarget = null
    }

    fun cancelDelete() {
        deleteTarget = null
    }

    fun activate(envId: Long) = store.activate(currentProjectId.value, envId)

    fun openVarEditor(envId: Long, index: Int?) {
        varsEnvId = envId
        val vars = store.environments.value.firstOrNull { it.id == envId }?.variables.orEmpty()
        if (index != null && index in vars.indices) {
            varEditIndex = index
            varNameInput = vars[index].first
            varValueInput = vars[index].second
        } else {
            varEditIndex = null
            varNameInput = ""
            varValueInput = ""
        }
        showVarEditor = true
    }

    fun closeVarEditor() {
        showVarEditor = false
        varsEnvId = null
        varEditIndex = null
    }

    fun saveVar() {
        val envId = varsEnvId ?: return
        val name = varNameInput.trim()
        // 变量名为空视为无效输入，保持弹窗开启等待修正
        if (name.isEmpty()) return
        val vars = store.environments.value
            .firstOrNull { it.id == envId }?.variables.orEmpty()
            .toMutableList()
        val index = varEditIndex
        if (index != null && index in vars.indices) {
            vars[index] = name to varValueInput
        } else {
            vars.add(name to varValueInput)
        }
        store.setVariables(envId, vars)
        closeVarEditor()
    }

    fun deleteVar(envId: Long, index: Int) {
        val vars = store.environments.value
            .firstOrNull { it.id == envId }?.variables.orEmpty()
            .toMutableList()
        if (index in vars.indices) {
            vars.removeAt(index)
            store.setVariables(envId, vars)
        }
    }

    fun requestDeleteVar(envId: Long, index: Int) {
        val vars = store.environments.value.firstOrNull { it.id == envId }?.variables.orEmpty()
        if (index in vars.indices) {
            varDeleteTarget = Triple(envId, index, vars[index].first)
        }
    }

    fun confirmDeleteVar() {
        varDeleteTarget?.let { (envId, index, _) -> deleteVar(envId, index) }
        varDeleteTarget = null
    }

    fun cancelDeleteVar() {
        varDeleteTarget = null
    }
}
