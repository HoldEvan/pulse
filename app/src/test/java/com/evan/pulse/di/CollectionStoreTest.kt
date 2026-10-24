package com.evan.pulse.di

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `duplicate inserts a copy right after the original`() {
        val (s, _) = store()
        val first = s.save("one", """{"url":"a"}""")
        s.save("two", """{"url":"b"}""")

        val copyId = s.duplicateRequest(first, "one copy")

        assertEquals(listOf("two", "one", "one copy"), s.requests.value.map { it.name })
        val copy = s.requests.value.first { it.id == copyId }
        assertEquals("""{"url":"a"}""", copy.data)
        assertEquals(s.requests.value.first { it.id == first }.collectionId, copy.collectionId)
    }

    @Test
    fun `duplicate missing request returns null and does not save`() {
        val (s, saves) = store()
        s.save("one")
        val before = saves.size
        assertNull(s.duplicateRequest(999L, "copy"))
        assertEquals(before, saves.size)
        assertEquals(1, s.requests.value.size)
    }

    @Test
    fun `duplicate rejects blank names`() {
        val (s, _) = store()
        val id = s.save("one")
        assertNull(s.duplicateRequest(id, "  "))
        assertEquals(1, s.requests.value.size)
    }

    @Test
    fun `folder variables override ancestors and can be cleared`() {
        val (s, _) = store()
        val root = s.collections.value.first().id
        s.create(1L, "child", root)
        val child = s.collections.value.first { it.name == "child" }.id
        s.setVars(root, listOf(KeyValueEntry("host", "root"), KeyValueEntry("token", "t1")))
        s.setVars(child, listOf(KeyValueEntry("host", "child")))

        val resolved = s.resolvedVars(child)
        assertEquals("child", resolved["host"])
        assertEquals("t1", resolved["token"])
        assertEquals("root", s.resolvedVars(root)["host"])

        s.setVars(child, emptyList())
        assertEquals("root", s.resolvedVars(child)["host"])
    }

    @Test
    fun `disabled and blank variables are ignored`() {
        val (s, _) = store()
        val root = s.collections.value.first().id
        s.setVars(
            root,
            listOf(
                KeyValueEntry("a", "1", enabled = false),
                KeyValueEntry(" ", "2"),
                KeyValueEntry("b", "3")
            )
        )
        val resolved = s.resolvedVars(root)
        assertFalse(resolved.containsKey("a"))
        assertFalse(resolved.containsKey(" "))
        assertEquals("3", resolved["b"])
    }

    @Test
    fun `deleting a folder drops its variables`() {
        val (s, _) = store()
        val root = s.collections.value.first().id
        s.create(1L, "child", root)
        val child = s.collections.value.first { it.name == "child" }.id
        s.setVars(child, listOf(KeyValueEntry("k", "v")))
        s.deleteRecursive(child)
        assertTrue(s.varsOf(child).isEmpty())
        assertTrue(s.resolvedVars(child).isEmpty())
    }

    @Test
    fun `snapshot round trip keeps folder variables`() {
        val (s, _) = store()
        val root = s.collections.value.first().id
        s.setVars(root, listOf(KeyValueEntry("k", "v")))
        val (restored, _) = store()
        restored.restore(s.snapshot())
        assertEquals("v", restored.resolvedVars(root)["k"])
    }

    @Test
    fun `move up and down swaps neighbours inside a folder`() {
        val (s, _) = store()
        val a = s.save("a")
        s.save("b")
        s.save("c")
        // 新条目在列表头部，展示顺序为 c、b、a
        assertTrue(s.moveRequest(a, -1))
        assertEquals(listOf("c", "a", "b"), s.requests.value.map { it.name })
        assertTrue(s.moveRequest(a, 1))
        assertEquals(listOf("c", "b", "a"), s.requests.value.map { it.name })
    }

    @Test
    fun `move stops at the folder boundaries`() {
        val (s, saves) = store()
        val first = s.save("a")
        val last = s.save("b")
        val before = saves.size
        assertFalse(s.moveRequest(first, 1))
        assertFalse(s.moveRequest(last, -1))
        assertEquals(before, saves.size)
        assertEquals(listOf("b", "a"), s.requests.value.map { it.name })
    }

    @Test
    fun `pin moves a request to the front of its own folder only`() {
        val (s, _) = store()
        s.create(1L, "other")
        val otherId = s.collections.value.first { it.name == "other" }.id
        val a = s.save("a")
        s.save("b")
        s.saveRequest(1L, otherId, RequestType.HTTP, "x", "{}", "Default")
        s.saveRequest(1L, otherId, RequestType.HTTP, "y", "{}", "Default")

        assertTrue(s.pinRequest(a))
        assertEquals(listOf("a", "b"), s.requests.value.filter { it.collectionId != otherId }.map { it.name })
        assertEquals(listOf("y", "x"), s.requests.value.filter { it.collectionId == otherId }.map { it.name })
    }

    @Test
    fun `pin on the first item does nothing`() {
        val (s, saves) = store()
        s.save("a")
        val top = s.save("b")
        val before = saves.size
        assertFalse(s.pinRequest(top))
        assertEquals(before, saves.size)
    }
}
