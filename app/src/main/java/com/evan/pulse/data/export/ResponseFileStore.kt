package com.evan.pulse.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 响应体的落地与分享。
 *
 * 保存走 SAF（由调用方传入目标 Uri），分享则先写到缓存目录下的 exports/，
 * 再通过 [FileProvider] 生成 content:// 交给系统分享面板——直接传 file://
 * 在 targetSdk 24 起会抛 FileUriExposedException。
 */
class ResponseFileStore(context: Context) {

    private val context = context.applicationContext

    private fun exportDir(): File = File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    /** 把响应写到用户选定的 Uri；覆盖同名文件 */
    fun writeTo(uri: Uri, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法写入目标文件")
        output.use { it.write(bytes) }
    }

    /**
     * 写入缓存并返回可分享的 content Uri。
     *
     * 每次导出前先清空目录，避免连续分享把缓存撑大。
     */
    fun stageForShare(name: String, bytes: ByteArray): Uri {
        clearStaged()
        val file = File(exportDir(), name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** 拉起系统分享面板；没有可用 App 时抛出，由调用方提示 */
    fun share(uri: Uri, mime: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 清掉上一次导出的文件，避免缓存目录无限增长 */
    fun clearStaged() {
        exportDir().listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val DIR_NAME = "exports"
    }
}
