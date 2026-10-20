package com.evan.pulse.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsMigrationTest {
    private val modeKey = intPreferencesKey("theme_mode_index")
    private val monetKey = booleanPreferencesKey("monet_enabled")

    @Test
    fun legacyModesPreserveAppearanceAndMonetChoice() {
        for (index in 0..5) {
            val settings = preferencesOf(modeKey to index).toAppSettings()
            assertEquals(index % 3, settings.themeModeIndex)
            assertEquals(index >= 3, settings.monetEnabled)
        }
    }

    @Test
    fun persistedMonetChoiceOverridesLegacyInference() {
        assertFalse(preferencesOf(modeKey to 4, monetKey to false).toAppSettings().monetEnabled)
        assertTrue(preferencesOf(modeKey to 1, monetKey to true).toAppSettings().monetEnabled)
    }

    @Test
    fun missingAndInvalidModesUseSystemAppearance() {
        for (index in listOf(-1, 6, Int.MAX_VALUE)) {
            val settings = preferencesOf(modeKey to index).toAppSettings()
            assertEquals(0, settings.themeModeIndex)
            assertFalse(settings.monetEnabled)
        }
        assertEquals(AppSettings(), preferencesOf().toAppSettings())
    }
}
