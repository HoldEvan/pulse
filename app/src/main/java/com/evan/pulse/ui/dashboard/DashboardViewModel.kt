package com.evan.pulse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evan.pulse.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 首页统计卡片数据：各维度数量，全部来源于内存 Store，跟随数据变化实时刷新 */
data class DashboardStats(
    val projectCount: Int = 0,
    val historyCount: Int = 0,
    val savedRequestCount: Int = 0,
    val monitorTargetCount: Int = 0,
)

class DashboardViewModel : ViewModel() {

    private val projectStore = ServiceLocator.projectStore
    private val historyStore = ServiceLocator.historyStore
    private val collectionStore = ServiceLocator.collectionStore
    private val monitorStore = ServiceLocator.monitorStore

    val stats: StateFlow<DashboardStats> = combine(
        projectStore.projects,
        historyStore.entries,
        collectionStore.requests,
        monitorStore.targets
    ) { projects, history, requests, targets ->
        DashboardStats(
            projectCount = projects.size,
            historyCount = history.size,
            savedRequestCount = requests.size,
            monitorTargetCount = targets.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardStats()
    )
}