package com.evan.pulse.di

import com.evan.pulse.data.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {

    private fun store(): HistoryStore = HistoryStore(PersistenceHandle())

    private fun entry(url: String, createdAt: Long) = HistoryEntry(
        id = 0L,
        projectId = 1L,
        method = "GET",
        url = url,
        statusCode = 200,
        durationMs = 10L,
        success = true,
        createdAt = createdAt
    )

    @Test
    fun `toggle favorite updates entry and can be reversed`() {
        val store = store()
        store.add(entry("https://example.com/", 1L))
        val id = store.entries.value.single().id

        assertTrue(store.toggleFavorite(id))
        assertTrue(store.entries.value.single().favorite)
        assertTrue(store.toggleFavorite(id))
        assertFalse(store.entries.value.single().favorite)
    }

    @Test
    fun `toggle favorite returns false for missing entry`() {
        assertFalse(store().toggleFavorite(42L))
    }

    @Test
    fun `favorite survives snapshot restore`() {
        val original = store()
        original.add(entry("https://example.com/", 1L))
        val id = original.entries.value.single().id
        original.toggleFavorite(id)

        val restored = store()
        restored.restore(original.snapshot())

        assertTrue(restored.entries.value.single().favorite)
        assertTrue(restored.toggleFavorite(id))
        assertFalse(restored.entries.value.single().favorite)
    }

    @Test
    fun `clear removes favorites with history`() {
        val store = store()
        store.add(entry("https://example.com/", 1L))
        store.toggleFavorite(store.entries.value.single().id)

        store.clear()

        assertTrue(store.entries.value.isEmpty())
        assertEquals(0, store.snapshot().entries.size)
    }
}
