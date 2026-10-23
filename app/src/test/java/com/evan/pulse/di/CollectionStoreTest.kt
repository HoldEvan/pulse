package com.evan.pulse.di

import com.evan.pulse.data.model.RequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionStoreTest {

    private fun store(): Pair<CollectionStore, MutableList<Int>> {
        val saves = mutableListOf<Int>()
        val handle = PersistenceHandle()
        var count = 0
        handle.action = { count++; saves.add(count) }
        return CollectionStore(handle) to saves
    }

    private fun CollectionStore.save(name: String, data: String = "{}"): Long =
        saveRequest(1L, -1L, RequestType.HTTP, name, data, "Default")

    @Test
    fun `save returns new request id`() {
        val (s, _) = store()
        val id = s.save("one")
        assertTrue(id > 0)
        assertEquals(listOf("one"), s.requests.value.map { it.name })
    }

    @Test
    fun `update overwrites snapshot in place`() {
        val (s, _) = store()
        val id = s.save("one", """{"url":"a"}""")
        s.save("two", """{"url":"b"}""")

        assertTrue(s.updateRequest(id, """{"url":"c"}"""))

        val list = s.requests.value
        assertEquals(listOf("two", "one"), list.map { it.name })
        assertEquals("""{"url":"c"}""", list.first { it.id == id }.data)
        assertEquals(2, list.size)
    }

    @Test
    fun `update keeps name and collection`() {
        val (s, _) = store()
        val id = s.save("one")
        val before = s.requests.value.first { it.id == id }
        s.updateRequest(id, """{"url":"c"}""")
        val after = s.requests.value.first { it.id == id }
        assertEquals(before.name, after.name)
        assertEquals(before.collectionId, after.collectionId)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test
    fun `update missing request returns false`() {
        val (s, saves) = store()
        s.save("one")
        val before = saves.size
        assertFalse(s.updateRequest(999L, "{}"))
        assertEquals(before, saves.size)
        assertEquals(1, s.requests.value.size)
    }

    @Test
    fun `blank name is rejected`() {
        val (s, _) = store()
        assertEquals(-1L, s.saveRequest(1L, -1L, RequestType.HTTP, " ", "{}", "Default"))
        assertTrue(s.requests.value.isEmpty())
    }
}
