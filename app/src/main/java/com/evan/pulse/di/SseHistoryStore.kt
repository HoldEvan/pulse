package com.evan.pulse.di

import com.evan.pulse.data.model.SSE_HISTORY_LIMIT
import com.evan.pulse.data.model.SseHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SSE 会话历史：每次连接结束时追加一条，只在会话边界写盘。
 * 与 WS/MQTT 日志分开持久化，避免大段转录随高频消息日志反复重写。
 */
class SseHistoryStore(private val persist: PersistenceHandle) {

    private val _entries = MutableStateFlow<List<SseHistoryEntry>>(emptyList())
    val entries: StateFlow<List<SseHistoryEntry>> = _entries.asStateFlow()

    var nextId = 1L
        private set

    fun record(
        method: String,
        url: String,
        startedAt: Long,
        endedAt: Long,
        frames: Int,
        outcome: String,
        transcript: String
    ): SseHistoryEntry {
        val entry = SseHistoryEntry(nextId++, method, url, startedAt, endedAt, frames, outcome, transcript)
        _entries.value = (_entries.value + entry).takeLast(SSE_HISTORY_LIMIT)
        persist.save()
        return entry
    }

    fun delete(id: Long) {
        val after = _entries.value.filter { it.id != id }
        if (after.size == _entries.value.size) return
        _entries.value = after
        persist.save()
    }

    fun clear() {
        if (_entries.value.isEmpty()) return
        _entries.value = emptyList()
        persist.save()
    }

    fun restore(entries: List<SseHistoryEntry>, nextId: Long) {
        _entries.value = entries.takeLast(SSE_HISTORY_LIMIT)
        this.nextId = maxOf(nextId, (_entries.value.maxOfOrNull { it.id } ?: 0L) + 1L)
    }
}
