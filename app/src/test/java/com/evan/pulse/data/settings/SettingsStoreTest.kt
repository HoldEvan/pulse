package com.evan.pulse.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private val styleKey = stringPreferencesKey("bottom_bar_style")
    private val floatingKey = booleanPreferencesKey("floating_bottom_bar")
    private val liquidGlassKey = booleanPreferencesKey("liquid_glass_bottom_bar")
    private val labelVisibleKey = booleanPreferencesKey("bottom_bar_label_visible")
    private val flagValues = listOf(false, true)

    @Test
    fun defaultBottomBarUsesFloatingStyleAndVisibleLabels() {
        val settings = preferencesOf().toAppSettings()

        assertEquals(BottomBarStyle.FLOATING, AppSettings().bottomBarStyle)
        assertEquals(AppSettings(), settings)
        assertTrue(settings.bottomBarLabelVisible)
    }

    @Test
    fun legacyBottomBarFlagsMigrateOnlyBothEnabledToOs4Glass() {
        for (floating in flagValues) {
            for (liquidGlass in flagValues) {
                val settings = preferencesOf(
                    floatingKey to floating,
                    liquidGlassKey to liquidGlass,
                ).toAppSettings()
                val expected = if (floating && liquidGlass) {
                    BottomBarStyle.OS4_GLASS
                } else {
                    BottomBarStyle.FLOATING
                }

                assertEquals("floating=$floating, liquidGlass=$liquidGlass", expected, settings.bottomBarStyle)
            }
        }
    }

    @Test
    fun missingLegacyBottomBarFlagsUseFloatingStyle() {
        assertEquals(
            BottomBarStyle.FLOATING,
            preferencesOf(floatingKey to true).toAppSettings().bottomBarStyle,
        )
        assertEquals(
            BottomBarStyle.FLOATING,
            preferencesOf(liquidGlassKey to true).toAppSettings().bottomBarStyle,
        )
    }

    @Test
    fun persistedBottomBarStylesOverrideAllLegacyFlagCombinations() {
        for (style in BottomBarStyle.entries) {
            for (floating in flagValues) {
                for (liquidGlass in flagValues) {
                    val settings = preferencesOf(
                        styleKey to style.name,
                        floatingKey to floating,
                        liquidGlassKey to liquidGlass,
                    ).toAppSettings()

                    assertEquals(
                        "style=$style, floating=$floating, liquidGlass=$liquidGlass",
                        style,
                        settings.bottomBarStyle,
                    )
                }
            }
        }
    }

    @Test
    fun unknownBottomBarStylesFallBackToFloating() {
        for (rawStyle in listOf("UNKNOWN", "", "os4_glass", " OS4_GLASS ")) {
            for (floating in flagValues) {
                for (liquidGlass in flagValues) {
                    val settings = preferencesOf(
                        styleKey to rawStyle,
                        floatingKey to floating,
                        liquidGlassKey to liquidGlass,
                    ).toAppSettings()

                    assertEquals(
                        "rawStyle='$rawStyle', floating=$floating, liquidGlass=$liquidGlass",
                        BottomBarStyle.FLOATING,
                        settings.bottomBarStyle,
                    )
                }
            }
        }
    }

    @Test
    fun bottomBarStyleWritesRoundTripAndRemoveLegacyKeys() {
        for (style in BottomBarStyle.entries) {
            for (labelVisible in flagValues) {
                val prefs = mutablePreferencesOf(
                    floatingKey to true,
                    liquidGlassKey to true,
                    labelVisibleKey to !labelVisible,
                )
                val settings = AppSettings(
                    bottomBarStyle = style,
                    bottomBarLabelVisible = labelVisible,
                )

                prefs.writeBottomBarSettings(settings)

                assertEquals(style.name, prefs[styleKey])
                assertNull(prefs[floatingKey])
                assertNull(prefs[liquidGlassKey])
                assertEquals(settings, prefs.toAppSettings())
            }
        }
    }

    @Test
    fun legacyBottomBarMigrationPreservesLabelVisibilityOnRewrite() {
        for (floating in flagValues) {
            for (liquidGlass in flagValues) {
                for (labelVisible in flagValues) {
                    val prefs = mutablePreferencesOf(
                        floatingKey to floating,
                        liquidGlassKey to liquidGlass,
                        labelVisibleKey to labelVisible,
                    )
                    val migrated = prefs.toAppSettings()
                    assertEquals(labelVisible, migrated.bottomBarLabelVisible)

                    prefs.writeBottomBarSettings(migrated)

                    assertEquals(migrated.bottomBarStyle.name, prefs[styleKey])
                    assertNull(prefs[floatingKey])
                    assertNull(prefs[liquidGlassKey])
                    assertEquals(migrated, prefs.toAppSettings())
                }
            }
        }
    }
}
