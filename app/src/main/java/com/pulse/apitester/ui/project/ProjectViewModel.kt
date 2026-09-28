package com.pulse.apitester.ui.project

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.pulse.apitester.data.model.ProjectInfo
import com.pulse.apitester.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow

class ProjectViewModel : ViewModel() {
    private val store = ServiceLocator.projectStore

    val projects: StateFlow<List<ProjectInfo>> = store.projects
    val currentProjectId: StateFlow<Long> = store.currentProjectId

    private val _showDialog = mutableStateOf(false)
    val showDialog: State<Boolean> = _showDialog

    fun openDialog() {
        _showDialog.value = true
    }

    fun closeDialog() {
        _showDialog.value = false
    }

    fun switch(id: Long) = store.switch(id)

    fun create(name: String) {
        if (name.isNotBlank()) store.create(name.trim())
    }

    fun rename(id: Long, name: String) {
        if (name.isNotBlank()) store.rename(id, name.trim())
    }

    fun delete(id: Long) = store.delete(id)
}
