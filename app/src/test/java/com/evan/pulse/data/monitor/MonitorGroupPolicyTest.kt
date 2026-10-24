package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorGroupPolicyTest {

    private fun target(id: Long, group: String, enabled: Boolean = true) = MonitorTarget(
        id = id,
        projectId = 1L,
        name = "t$id",
        type = MonitorType.HTTP,
        host = "example.com",
        url = "https://example.com/$id",
        group = group,
        enabled = enabled
    )

    private val targets = listOf(
        target(1, "prod"),
        target(2, "prod", enabled = false),
        target(3, "staging"),
        target(4, "")
    )

    @Test
    fun disablesOnlyMatchingGroup() {
        val result = applyEnabledByGroup(targets, "prod", false)
        assertFalse(result.first { it.id == 1L }.enabled)
        assertFalse(result.first { it.id == 2L }.enabled)
        assertTrue(result.first { it.id == 3L }.enabled)
        assertTrue(result.first { it.id == 4L }.enabled)
    }

    @Test
    fun enablesUngroupedWithEmptyKey() {
        val result = applyEnabledByGroup(targets.map { it.copy(enabled = false) }, "", true)
        assertTrue(result.first { it.id == 4L }.enabled)
        assertFalse(result.first { it.id == 1L }.enabled)
    }

    @Test
    fun nullGroupAppliesToAll() {
        val result = applyEnabledByGroup(targets, null, false)
        assertTrue(result.none { it.enabled })
        assertEquals(targets.size, result.size)
    }

    @Test
    fun unknownGroupKeepsEverything() {
        val result = applyEnabledByGroup(targets, "nope", false)
        assertEquals(targets, result)
    }

    @Test
    fun isGroupFullyEnabledNeedsNonEmptyAndAllOn() {
        assertFalse(isGroupFullyEnabled(targets, "prod"))
        assertTrue(isGroupFullyEnabled(targets, "staging"))
        assertFalse(isGroupFullyEnabled(targets, "missing"))
    }

    @Test
    fun isAllEnabledHandlesEmptyList() {
        assertFalse(isAllEnabled(emptyList()))
        assertTrue(isAllEnabled(listOf(target(1, ""))))
        assertFalse(isAllEnabled(listOf(target(1, ""), target(2, "", enabled = false))))
    }
}
