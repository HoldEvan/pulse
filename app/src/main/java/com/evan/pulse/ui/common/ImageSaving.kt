package com.evan.pulse.ui.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片落盘：API 29+ 写入公共相册 Pictures/Pulse，低版本写入应用私有 Pictures 目录。
 *
 * 返回 false 表示无法识别格式或写入失败，由调用方决定提示文案。
 */
internal fun saveImageBytes(context: Context, bytes: ByteArray): Boolean {
    val mime = detectImageMime(bytes) ?: return false
    val ext = when (mime) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        else -> "webp"
    }
    val name = "Pulse_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pulse")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        written
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
        runCatching { File(dir, name).writeBytes(bytes) }.isSuccess
    }
}

/** PNG 编码：无损保留二维码点阵，避免 JPEG 压缩在密集模块上产生噪点 */
internal fun Bitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/** 按魔数识别常见图片格式，用于确定保存的 MIME 与扩展名 */
internal fun detectImageMime(bytes: ByteArray): String? = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"

    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte() -> "image/jpeg"

    bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() -> "image/gif"

    bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "image/webp"

    else -> null
}
