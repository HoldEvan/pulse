package com.pulse.apitester.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.pulse.apitester.data.model.HistoryEntry
import com.pulse.apitester.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel : ViewModel() {

    private val store = ServiceLocator.historyStore
    private val projectStore = ServiceLocator.projectStore

    val currentProjectId: StateFlow<Long> = projectStore.currentProjectId

    var keyword by mutableStateOf("")
    var confirmClear by mutableStateOf(false)

    fun history(projectId: Long): List<HistoryEntry> {
        val all = store.entries.value.filter { it.projectId == projectId }
        val kw = keyword.trim()
        return if (kw.isEmpty()) all else all.filter {
            it.url.contains(kw, ignoreCase = true) || it.method.equals(kw, ignoreCase = true)
        }
    }

    fun clear() {
        store.clear()
        confirmClear = false
    }
}
