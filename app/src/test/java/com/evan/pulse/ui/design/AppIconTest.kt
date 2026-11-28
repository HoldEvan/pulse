package com.evan.pulse.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

class AppIconTest {
    @Test
    fun forwardNavigationUsesChevronInBothWeights() {
        assertSame(Icons.AutoMirrored.Filled.KeyboardArrowRight, materialAppIcon(MiuixIcons.ChevronForward))
        assertSame(Icons.AutoMirrored.Filled.KeyboardArrowRight, materialAppIcon(MiuixIcons.Light.ChevronForward))
    }

    @Test
    fun materialActionsFollowSkinChangesWithoutReplacingTheOriginalInput() {
        val actions = listOf(
            Icons.Filled.Share to MiuixIcons.Light.Share,
            Icons.Filled.History to MiuixIcons.Light.Recent,
            Icons.AutoMirrored.Filled.Undo to MiuixIcons.Light.Undo,
            Icons.AutoMirrored.Filled.Redo to MiuixIcons.Light.Redo,
        )
        actions.forEach { (material, miuix) ->
            assertSame(material, appIconForStyle(material, UiStyle.MATERIAL))
            assertSame(miuix, appIconForStyle(material, UiStyle.MIUIX))
            assertSame(material, appIconForStyle(material, UiStyle.MATERIAL))
        }
    }

    @Test
    fun nativeMiuixWeightSurvivesSkinSwitches() {
        listOf(MiuixIcons.Share, MiuixIcons.Light.Share).forEach { image ->
            assertSame(image, appIconForStyle(image, UiStyle.MIUIX))
            assertSame(Icons.Filled.Share, appIconForStyle(image, UiStyle.MATERIAL))
            assertSame(image, appIconForStyle(image, UiStyle.MIUIX))
        }
    }

    @Test
    fun aliasesUseMiuixWhileKeepingTheirExactMaterialVectors() {
        val aliases = listOf(
            Icons.Filled.Download to MiuixIcons.Light.Download,
            Icons.Filled.FileDownload to MiuixIcons.Light.Download,
            Icons.Filled.ExpandMore to MiuixIcons.Light.ExpandMore,
            Icons.Filled.ArrowDropDown to MiuixIcons.Light.ExpandMore,
        )
        aliases.forEach { (material, miuix) ->
            assertSame(miuix, appIconForStyle(material, UiStyle.MIUIX))
            assertSame(material, appIconForStyle(material, UiStyle.MATERIAL))
        }
    }

    @Test
    fun favoriteStatesStayDistinctInBothSkins() {
        val favorite = miuixAppIcon(Icons.Filled.Star)
        val unfavorite = miuixAppIcon(Icons.Filled.StarBorder)
        assertSame(MiuixIcons.Light.FavoritesFill, favorite)
        assertSame(MiuixIcons.Light.Favorites, unfavorite)
        assertNotEquals(favorite, unfavorite)
        assertSame(Icons.Filled.Star, materialAppIcon(favorite))
        assertSame(Icons.Filled.StarBorder, materialAppIcon(unfavorite))
    }

    @Test
    fun semanticDifferencesArePreservedInsteadOfMatchingSimilarNames() {
        listOf(MiuixIcons.Timer, MiuixIcons.Light.Timer).forEach { image ->
            assertSame(Icons.Filled.HourglassEmpty, materialAppIcon(image))
        }
        listOf(MiuixIcons.Stopwatch, MiuixIcons.Light.Stopwatch).forEach { image ->
            assertSame(Icons.Filled.Timer, materialAppIcon(image))
        }
        assertSame(Icons.Filled.Campaign, materialAppIcon(MiuixIcons.Light.Promotions))
        assertSame(Icons.Filled.Radar, materialAppIcon(MiuixIcons.Light.SearchDevice))
    }

    @Test
    fun actionsWithoutSemanticEquivalentsKeepTheirMaterialFallback() {
        val fallbacks = listOf(
            Icons.Filled.Save, Icons.Filled.Code, Icons.Filled.Cookie,
            Icons.Filled.Science, Icons.Filled.NetworkCheck, Icons.Filled.LinkOff,
            Icons.Filled.Schedule, Icons.Filled.QrCode2, Icons.Filled.TrendingUp,
        )
        fallbacks.forEach { image ->
            UiStyle.entries.forEach { style ->
                assertSame(image, appIconForStyle(image, style))
            }
        }
    }

    @Test
    fun unknownAndCustomVectorsPassThroughEvenWithALibraryIconName() {
        val custom = ImageVector.Builder(
            name = MiuixIcons.Light.Share.name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).build()
        listOf(custom, MiuixIcons.Light.Music).forEach { image ->
            UiStyle.entries.forEach { style ->
                assertSame(image, appIconForStyle(image, style))
            }
        }
    }
}
