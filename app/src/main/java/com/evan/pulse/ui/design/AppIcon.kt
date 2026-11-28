package com.evan.pulse.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

/** Icons inherit the active skin's content color unless explicitly overridden. */
@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = if (LocalUiStyle.current == UiStyle.MATERIAL) LocalContentColor.current else MiuixLocalContentColor.current,
) {
    val style = LocalUiStyle.current
    val icon = appIconForStyle(imageVector, style)
    if (style == UiStyle.MIUIX) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    } else {
        M3Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

internal fun appIconForStyle(image: ImageVector, style: UiStyle): ImageVector =
    when (style) {
        UiStyle.MIUIX -> miuixAppIcon(image)
        UiStyle.MATERIAL -> materialAppIcon(image)
    }

/** Match library vectors, not their names: custom vectors and unsupported actions pass through. */
internal fun miuixAppIcon(image: ImageVector): ImageVector {
    val light = MiuixIcons.Light
    return when (image) {
        Icons.Filled.Add -> light.Add
        Icons.AutoMirrored.Filled.ArrowBack -> light.ChevronBackward
        Icons.AutoMirrored.Filled.KeyboardArrowRight -> light.ChevronForward
        Icons.Filled.Close -> light.Close
        Icons.Filled.Transform -> light.ConvertFile
        Icons.Filled.ContentCopy -> light.Copy
        Icons.Filled.AutoAwesome -> light.Create
        Icons.Filled.Delete -> light.Delete
        Icons.Filled.Download, Icons.Filled.FileDownload -> light.Download
        Icons.Filled.Edit -> light.Edit
        Icons.Filled.ExpandLess -> light.ExpandLess
        Icons.Filled.ExpandMore, Icons.Filled.ArrowDropDown -> light.ExpandMore
        Icons.Filled.StarBorder -> light.Favorites
        Icons.Filled.Star -> light.FavoritesFill
        Icons.Filled.FilterList -> light.Filter
        Icons.Filled.Folder -> light.Folder
        Icons.Filled.Home -> light.Home
        Icons.Filled.Image -> light.Image
        Icons.AutoMirrored.Filled.Input -> light.Import
        Icons.Filled.Link -> light.Link
        Icons.Filled.ViewList -> light.ListView
        Icons.Filled.Lock -> light.Lock
        Icons.Filled.MoreVert -> light.More
        Icons.AutoMirrored.Filled.Notes, Icons.AutoMirrored.Filled.Article -> light.Notes
        Icons.Filled.Check -> light.Ok
        Icons.Filled.Pause -> light.Pause
        Icons.Filled.PlayArrow -> light.Play
        Icons.Filled.Campaign -> light.Promotions
        Icons.Filled.History -> light.Recent
        Icons.AutoMirrored.Filled.Redo -> light.Redo
        Icons.Filled.SwapHoriz -> light.Replace
        Icons.Filled.DocumentScanner -> light.Scan
        Icons.Filled.Search -> light.Search
        Icons.Filled.Radar -> light.SearchDevice
        Icons.AutoMirrored.Filled.Send -> light.Send
        Icons.Filled.Settings -> light.Settings
        Icons.Filled.Share -> light.Share
        Icons.Filled.Timer -> light.Stopwatch
        Icons.Filled.HourglassEmpty -> light.Timer
        Icons.Filled.Tune -> light.Tune
        Icons.AutoMirrored.Filled.Undo -> light.Undo
        else -> image
    }
}

/** Both Miuix weights must resolve to Material when the user changes skin. */
internal fun materialAppIcon(image: ImageVector): ImageVector {
    val miuix = MiuixIcons
    val light = MiuixIcons.Light
    return when (image) {
        miuix.Add, light.Add -> Icons.Filled.Add
        miuix.ChevronBackward, light.ChevronBackward -> Icons.AutoMirrored.Filled.ArrowBack
        miuix.ChevronForward, light.ChevronForward -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        miuix.Close, light.Close -> Icons.Filled.Close
        miuix.ConvertFile, light.ConvertFile -> Icons.Filled.Transform
        miuix.Copy, light.Copy -> Icons.Filled.ContentCopy
        miuix.Create, light.Create -> Icons.Filled.AutoAwesome
        miuix.Delete, light.Delete -> Icons.Filled.Delete
        miuix.Download, light.Download -> Icons.Filled.Download
        miuix.Edit, light.Edit -> Icons.Filled.Edit
        miuix.ExpandLess, light.ExpandLess -> Icons.Filled.ExpandLess
        miuix.ExpandMore, light.ExpandMore -> Icons.Filled.ExpandMore
        miuix.Favorites, light.Favorites -> Icons.Filled.StarBorder
        miuix.FavoritesFill, light.FavoritesFill -> Icons.Filled.Star
        miuix.Filter, light.Filter -> Icons.Filled.FilterList
        miuix.Folder, light.Folder -> Icons.Filled.Folder
        miuix.Home, light.Home -> Icons.Filled.Home
        miuix.Image, light.Image -> Icons.Filled.Image
        miuix.Import, light.Import -> Icons.AutoMirrored.Filled.Input
        miuix.Link, light.Link -> Icons.Filled.Link
        miuix.ListView, light.ListView -> Icons.Filled.ViewList
        miuix.Lock, light.Lock -> Icons.Filled.Lock
        miuix.More, light.More -> Icons.Filled.MoreVert
        miuix.Notes, light.Notes -> Icons.AutoMirrored.Filled.Notes
        miuix.Ok, light.Ok -> Icons.Filled.Check
        miuix.Pause, light.Pause -> Icons.Filled.Pause
        miuix.Play, light.Play -> Icons.Filled.PlayArrow
        miuix.Promotions, light.Promotions -> Icons.Filled.Campaign
        miuix.Recent, light.Recent -> Icons.Filled.History
        miuix.Redo, light.Redo -> Icons.AutoMirrored.Filled.Redo
        miuix.Replace, light.Replace -> Icons.Filled.SwapHoriz
        miuix.Scan, light.Scan -> Icons.Filled.DocumentScanner
        miuix.Search, light.Search -> Icons.Filled.Search
        miuix.SearchDevice, light.SearchDevice -> Icons.Filled.Radar
        miuix.Send, light.Send -> Icons.AutoMirrored.Filled.Send
        miuix.Settings, light.Settings -> Icons.Filled.Settings
        miuix.Share, light.Share -> Icons.Filled.Share
        miuix.Stopwatch, light.Stopwatch -> Icons.Filled.Timer
        miuix.Timer, light.Timer -> Icons.Filled.HourglassEmpty
        miuix.Tune, light.Tune -> Icons.Filled.Tune
        miuix.Undo, light.Undo -> Icons.AutoMirrored.Filled.Undo
        else -> image
    }
}
