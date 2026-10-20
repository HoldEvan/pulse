package com.evan.pulse.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MaterialSkinTest {
    @Test
    fun accentRolesUseTheSelectedPaletteInBothAppearances() {
        for (dark in listOf(false, true)) {
            val colors = (if (dark) darkColorScheme() else lightColorScheme()).copy(
                primary = Color(0xFF236C38),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFB5F1BA),
                onPrimaryContainer = Color(0xFF002109),
                secondaryContainer = Color(0xFFD4E8D0),
                onSecondaryContainer = Color(0xFF102010)
            )
            val scheme = materialColorScheme(colors, dark, pureBlack = false)
            assertEquals(colors.primary, scheme.primary)
            assertEquals(colors.secondaryContainer, scheme.secondaryContainer)
            assertEquals(colors.onSecondaryContainer, scheme.onSecondaryContainer)
            assertEquals(colors.primary, scheme.tertiary)
            assertEquals(colors.onPrimary, scheme.onTertiary)
            assertEquals(colors.primaryContainer, scheme.tertiaryContainer)
            assertEquals(colors.onPrimaryContainer, scheme.onTertiaryContainer)
            assertEquals(if (dark) colors.primaryContainer else colors.primary, scheme.inversePrimary)
            assertEquals(colors.errorContainer, scheme.errorContainer)
            assertEquals(colors.onErrorContainer, scheme.onErrorContainer)
        }
    }

    @Test
    fun pureBlackKeepsAccentColors() {
        val colors = darkColorScheme()
        val scheme = materialColorScheme(colors, dark = true, pureBlack = true)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(colors.primaryContainer, scheme.primaryContainer)
        assertEquals(colors.secondaryContainer, scheme.secondaryContainer)
    }
}
