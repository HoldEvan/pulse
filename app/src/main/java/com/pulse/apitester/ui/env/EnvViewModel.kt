package com.pulse.apitester.ui.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.pulse.apitester.di.ServiceLocator
import com.pulse.apitester.data.model.EnvironmentInfo
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
    var editingVars by mutableStateOf(listOf<Pair<String, String>>())

    fun environments(projectId: Long): List<EnvironmentInfo> =
        store.environments.value.filter { it.projectId == projectId }

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
        editingVars = env.variables
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        editing = null
        creating = false
    }

    fun save() {
        val name = nameInput.trim()
        if (name.isEmpty()) {
            closeEditor()
            return
        }
        if (creating) {
            store.create(currentProjectId.value, name)
        } else {
            editing?.let { env ->
                store.rename(env.id, name)
                store.setVariables(env.id, editingVars)
            }
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

    fun updateVar(index: Int, pair: Pair<String, String>) {
        editingVars = editingVars.mapIndexed { i, p -> if (i == index) pair else p }
    }

    fun addVar() {
        editingVars = editingVars + ("" to "")
    }

    fun removeVar(index: Int) {
        editingVars = editingVars.filterIndexed { i, _ -> i != index }
    }
}
