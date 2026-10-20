package com.evan.pulse.ui.design

import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch

/** Icons inherit the active skin's content color unless explicitly overridden. */
@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = if (LocalUiStyle.current == UiStyle.MATERIAL) LocalContentColor.current else MiuixLocalContentColor.current,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    } else {
        M3Icon(
            imageVector = materialAppIcon(imageVector),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

internal fun materialAppIcon(image: ImageVector): ImageVector {
    val miuix = MiuixIcons
    val light = MiuixIcons.Light
    return when (image) {
        miuix.Add, light.Add -> Icons.Filled.Add
        miuix.ChevronBackward, light.ChevronBackward -> Icons.AutoMirrored.Filled.ArrowBack
        miuix.ChevronForward, light.ChevronForward -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        miuix.Close, light.Close -> Icons.Filled.Close
        miuix.ConvertFile, light.ConvertFile -> Icons.Filled.Edit
        miuix.Copy, light.Copy -> Icons.Filled.ContentCopy
        miuix.Delete, light.Delete -> Icons.Filled.Delete
        miuix.Edit, light.Edit -> Icons.Filled.Edit
        miuix.ExpandMore, light.ExpandMore -> Icons.Filled.ArrowDropDown
        miuix.Folder, light.Folder -> Icons.Filled.Folder
        miuix.Home, light.Home -> Icons.Filled.Home
        miuix.Image, light.Image -> Icons.Filled.Image
        miuix.Link, light.Link -> Icons.Filled.Link
        miuix.More, light.More -> Icons.Filled.MoreVert
        miuix.Notes, light.Notes -> Icons.AutoMirrored.Filled.Notes
        miuix.Pause, light.Pause -> Icons.Filled.Pause
        miuix.Play, light.Play -> Icons.Filled.PlayArrow
        miuix.Recent, light.Recent -> Icons.Filled.Schedule
        miuix.Send, light.Send -> Icons.AutoMirrored.Filled.Send
        miuix.Settings, light.Settings -> Icons.Filled.Settings
        miuix.Stopwatch, light.Stopwatch -> Icons.Filled.Schedule
        else -> image
    }
}
