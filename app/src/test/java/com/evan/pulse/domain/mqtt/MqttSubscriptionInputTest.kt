package com.evan.pulse.domain.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttSubscriptionInputTest {

    @Test
    fun `single topic uses the default qos`() {
        val parsed = MqttSubscriptionInput.parse("home/light", 1)

        assertEquals(listOf(TopicQos("home/light", 1)), parsed.topics)
        assertFalse(parsed.hasInvalid)
    }

    @Test
    fun `newline comma and semicolon all separate topics`() {
        val parsed = MqttSubscriptionInput.parse("a/b\nc/d, e/f; g/h", 0)

        assertEquals(listOf("a/b", "c/d", "e/f", "g/h"), parsed.topics.map { it.topic })
    }

    @Test
    fun `suffix overrides qos per topic`() {
        val parsed = MqttSubscriptionInput.parse("a/b@2, c/d, e/f@0", 1)

        assertEquals(
            listOf(TopicQos("a/b", 2), TopicQos("c/d", 1), TopicQos("e/f", 0)),
            parsed.topics
        )
    }

    @Test
    fun `duplicate topics keep the last qos`() {
        val parsed = MqttSubscriptionInput.parse("a/b@0\na/b@2", 0)

        assertEquals(listOf(TopicQos("a/b", 2)), parsed.topics)
    }

    @Test
    fun `invalid filter is reported and skipped`() {
        val parsed = MqttSubscriptionInput.parse("home/#/light, ok/topic", 0)

        assertEquals(listOf("ok/topic"), parsed.topics.map { it.topic })
        assertEquals(listOf("home/#/light"), parsed.invalid)
    }

    @Test
    fun `out of range or non numeric qos suffix is invalid`() {
        val parsed = MqttSubscriptionInput.parse("a/b@9, c/d@x, e/f@2", 0)

        assertEquals(listOf("e/f"), parsed.topics.map { it.topic })
        assertEquals(listOf("a/b@9", "c/d@x"), parsed.invalid)
    }

    @Test
    fun `blank input yields nothing`() {
        val parsed = MqttSubscriptionInput.parse("  \n , ; ", 2)

        assertTrue(parsed.topics.isEmpty())
        assertFalse(parsed.hasInvalid)
    }

    @Test
    fun `default qos outside range is clamped`() {
        val parsed = MqttSubscriptionInput.parse("a/b", 7)

        assertEquals(2, parsed.topics.single().qos)
    }

    @Test
    fun `at suffix that is not a valid qos is rejected instead of silently becoming a topic`() {
        val parsed = MqttSubscriptionInput.parse("user@home/state, ok/topic", 0)

        assertEquals(listOf("ok/topic"), parsed.topics.map { it.topic })
        assertEquals(listOf("user@home/state"), parsed.invalid)
    }

    @Test
    fun `filter validator rejects partial wildcards`() {
        assertTrue(MqttTopicFilters.isValid("home/+/light"))
        assertFalse(MqttTopicFilters.isValid("home/+x/light"))
        assertFalse(MqttTopicFilters.isValid("home/#/light"))
        assertFalse(MqttTopicFilters.isValid("  "))
    }
}
