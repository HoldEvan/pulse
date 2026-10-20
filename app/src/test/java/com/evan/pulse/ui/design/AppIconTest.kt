package com.evan.pulse.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

class AppIconTest {
    @Test
    fun forwardNavigationUsesChevron() {
        assertEquals(Icons.AutoMirrored.Filled.KeyboardArrowRight, materialAppIcon(MiuixIcons.ChevronForward))
        assertEquals(Icons.AutoMirrored.Filled.KeyboardArrowRight, materialAppIcon(MiuixIcons.Light.ChevronForward))
    }

    @Test
    fun navigationLightIconsUseMaterialVectors() {
        assertEquals(Icons.Filled.Home, materialAppIcon(MiuixIcons.Light.Home))
        assertEquals(Icons.Filled.Folder, materialAppIcon(MiuixIcons.Light.Folder))
        assertEquals(Icons.Filled.Settings, materialAppIcon(MiuixIcons.Light.Settings))
        assertEquals(Icons.Filled.Science, materialAppIcon(Icons.Filled.Science))
    }

    @Test
    fun allUsedLightIconsAreConverted() {
        val icons = listOf(
            MiuixIcons.Light.Add, MiuixIcons.Light.ChevronBackward,
            MiuixIcons.Light.ChevronForward, MiuixIcons.Light.Close,
            MiuixIcons.Light.ConvertFile, MiuixIcons.Light.Copy,
            MiuixIcons.Light.Delete, MiuixIcons.Light.Edit,
            MiuixIcons.Light.ExpandMore, MiuixIcons.Light.Folder,
            MiuixIcons.Light.Home, MiuixIcons.Light.Image,
            MiuixIcons.Light.Link, MiuixIcons.Light.More,
            MiuixIcons.Light.Notes, MiuixIcons.Light.Pause,
            MiuixIcons.Light.Play, MiuixIcons.Light.Recent,
            MiuixIcons.Light.Send, MiuixIcons.Light.Settings,
            MiuixIcons.Light.Stopwatch,
        )
        icons.forEach { icon -> assertNotEquals(icon.name, icon, materialAppIcon(icon)) }
    }
}
