package com.evan.pulse.di

import com.evan.pulse.data.model.MONITOR_HISTORY_LIMIT
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorStoreTest {

    private fun store() = MonitorStore(PersistenceHandle())

    private fun target(id: Long) = MonitorTarget(
        id = id,
        projectId = 1L,
        name = "target-$id",
        type = MonitorType.HTTP,
        url = "https://example.test/$id"
    )

    @Test
    fun `history limit applies per target instead of globally`() {
        val store = store()
        val a = store.create(target(0))
        val b = store.create(target(0))

        repeat(MONITOR_HISTORY_LIMIT) { index ->
            store.recordHistory(a, ok = true, slow = false, durationMs = index.toLong(), checkedAt = index.toLong())
        }
        // 另一个目标只查了少数几次：全局截断会把这些样本挤掉
        repeat(3) { index ->
            store.recordHistory(b, ok = false, slow = false, durationMs = index.toLong(), checkedAt = index.toLong())
        }

        assertEquals(MONITOR_HISTORY_LIMIT, store.historyFor(a.id).size)
        assertEquals(3, store.historyFor(b.id).size)
        assertEquals(store.historyFor(b.id).maxOf { it.checkedAt }, store.historyFor(b.id).first().checkedAt)
    }

    @Test
    fun `deleting a target without records is still persisted`() {
        var saves = 0
        val handle = PersistenceHandle()
        handle.onSave { saves++ }
        val store = MonitorStore(handle)
        val created = store.create(target(0))
        saves = 0

        store.delete(created.id)

        assertTrue(store.targets.value.isEmpty())
        assertEquals(1, saves)
    }

    @Test
    fun `records are shared fairly between targets`() {
        val store = store()
        val a = store.create(target(0))
        val b = store.create(target(0))

        // 一个目标刷满记录，另一个只留几条：均分额度保证后者不被挤掉
        repeat(2_000) { index ->
            store.recordResult(a, ok = true, message = "ok", durationMs = 1L, checkedAt = index.toLong())
        }
        repeat(5) { index ->
            store.recordResult(b, ok = false, message = "fail", durationMs = 1L, checkedAt = index.toLong())
        }

        val records = store.records.value
        assertEquals(5, records.count { it.targetId == b.id })
        assertEquals(1_000, records.count { it.targetId == a.id })
    }

    @Test
    fun `restore drops records of deleted targets`() {
        val original = store()
        val kept = original.create(target(0))
        val removed = original.create(target(0))
        original.recordResult(kept, true, "ok", 1L, 1L)
        original.recordResult(removed, false, "fail", 1L, 2L)

        val snapshot = original.snapshot()
        val restored = store()
        restored.restore(
            MonitorStoreSnapshot(
                targets = snapshot.targets.filter { it.id == kept.id },
                records = snapshot.records,
                history = snapshot.history,
                nextTargetId = snapshot.nextTargetId,
                nextRecordId = snapshot.nextRecordId,
                nextHistoryId = snapshot.nextHistoryId
            )
        )

        assertEquals(1, restored.records.value.size)
        assertEquals(kept.id, restored.records.value.first().targetId)
        assertNull(restored.get(removed.id))
    }
}
