package com.evan.pulse.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSkinTest {
    @Test
    fun liquidGlassBottomBarIsOnlyAvailableInMiuix() {
        assertTrue(UiStyle.MIUIX.capabilities().glassBottomBar)
        assertFalse(UiStyle.MATERIAL.capabilities().glassBottomBar)
    }

    @Test
    fun materialKeepsItsOtherCapabilities() {
        val capabilities = UiStyle.MATERIAL.capabilities()
        assertFalse(capabilities.blurBars)
        assertFalse(capabilities.squirclePress)
        assertTrue(capabilities.largeTitleCollapse)
        assertTrue(capabilities.tonalElevation)
    }
}
