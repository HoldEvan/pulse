package com.evan.pulse.ui.mqtt

import com.evan.pulse.data.model.MqttSubscriptionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttTopicTreeTest {

    // ---- isValidMqttFilter（需求 3.5）----

    @Test
    fun `plain filter without wildcards is valid`() {
        assertTrue(isValidMqttFilter("home/kitchen/light"))
        assertTrue(isValidMqttFilter("a//b"))
    }

    @Test
    fun `plus must occupy a whole level`() {
        assertTrue(isValidMqttFilter("home/+/light"))
        assertTrue(isValidMqttFilter("+/+/+"))
        assertFalse(isValidMqttFilter("home/+/light/++"))
        assertFalse(isValidMqttFilter("ho+me/light"))
        assertFalse(isValidMqttFilter("home/+x/light"))
    }

    @Test
    fun `hash must be the final whole level`() {
        assertTrue(isValidMqttFilter("#"))
        assertTrue(isValidMqttFilter("home/#"))
        assertFalse(isValidMqttFilter("home/#/light"))
        assertFalse(isValidMqttFilter("#/home"))
        assertFalse(isValidMqttFilter("home/h#"))
        assertFalse(isValidMqttFilter("home/#/"))
    }

    @Test
    fun `blank filter is invalid`() {
        assertFalse(isValidMqttFilter(""))
        assertFalse(isValidMqttFilter("   "))
    }

    // ---- buildMqttTopicTree（需求 3.1、4.2）----

    @Test
    fun `filters group by shared prefix segments`() {
        val tree = buildMqttTopicTree(
            listOf(
                MqttSubscriptionEntry("home/+/temp", 1),
                MqttSubscriptionEntry("home/kitchen/light", 0)
            )
        )

        assertEquals(1, tree.size)
        val home = tree.single()
        assertEquals("home", home.segment)
        assertEquals("home", home.path)
        assertEquals(0, home.leaves.size)
        assertEquals(listOf("+", "kitchen"), home.children.map { it.segment })
        assertEquals("home/+", home.children[0].path)
        assertEquals("home/kitchen", home.children[1].path)
    }

    @Test
    fun `leaves keep exact filter and qos`() {
        val subs = listOf(
            MqttSubscriptionEntry("home/+/temp", 1),
            MqttSubscriptionEntry("#", 2),
            MqttSubscriptionEntry("home/kitchen/light", 0)
        )
        val tree = buildMqttTopicTree(subs)

        val root = tree.single { it.segment == "home" }
        val plus = root.children.single { it.segment == "+" }
        assertEquals(0, plus.leaves.size)
        val temp = plus.children.single()
        assertEquals("temp", temp.segment)
        assertEquals("home/+/temp", temp.path)
        assertEquals(listOf(MqttSubscriptionEntry("home/+/temp", 1)), temp.leaves)

        val kitchen = root.children.single { it.segment == "kitchen" }
        val light = kitchen.children.single()
        assertEquals("light", light.segment)
        assertEquals("home/kitchen/light", light.path)
        assertEquals(listOf(MqttSubscriptionEntry("home/kitchen/light", 0)), light.leaves)

        val hash = tree.single { it.segment == "#" }
        assertEquals(listOf(MqttSubscriptionEntry("#", 2)), hash.leaves)
    }

    @Test
    fun `every distinct filter appears exactly once`() {
        val subs = listOf(
            MqttSubscriptionEntry("a/b", 0),
            MqttSubscriptionEntry("a/b/c", 1),
            MqttSubscriptionEntry("a/+/b", 2),
            MqttSubscriptionEntry("x", 0)
        )
        val tree = buildMqttTopicTree(subs)
        val leaves = mutableListOf<MqttSubscriptionEntry>()

        fun collect(node: MqttTopicNode) {
            leaves += node.leaves
            node.children.forEach { collect(it) }
        }
        tree.forEach { collect(it) }

        assertEquals(subs, leaves)
    }

    @Test
    fun `duplicate topics collapse to one leaf keeping first qos`() {
        val tree = buildMqttTopicTree(
            listOf(
                MqttSubscriptionEntry("home/light", 0),
                MqttSubscriptionEntry("home/light", 2)
            )
        )

        val home = tree.single()
        assertEquals(0, home.leaves.size)
        val leaf = home.children.single().leaves.single()
        assertEquals("home/light", leaf.topic)
        assertEquals(0, leaf.qos)
    }

    @Test
    fun `root level filter projects to single node with leaf`() {
        val tree = buildMqttTopicTree(listOf(MqttSubscriptionEntry("ping", 1)))

        val node = tree.single()
        assertEquals("ping", node.segment)
        assertEquals("ping", node.path)
        assertEquals(0, node.children.size)
        assertEquals(listOf(MqttSubscriptionEntry("ping", 1)), node.leaves)
    }
}
