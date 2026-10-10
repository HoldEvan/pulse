package com.evan.pulse.data.cache

import android.content.Context
import java.io.File
import java.util.Locale

/** 缓存统计：文件数量与总字节数 */
data class CacheStats(val files: Int, val bytes: Long)

/** 一次清理的结果：释放了多少，清理后还剩多少 */
data class CacheCleanResult(val freed: CacheStats, val remaining: CacheStats)

/**
 * 本地缓存清理。
 *
 * 只处理系统缓存目录（内部与外部）以及原子写盘可能残留的临时文件；
 * 不碰用户数据文件与 DataStore 设置，所以项目、环境、集合与历史记录都不会丢。
 */
class CacheCleaner(context: Context) {

    private val context = context.applicationContext

    /** 当前缓存占用 */
    fun stats(): CacheStats = measure(targets())

    /** 清理缓存，返回释放量与清理后的剩余量 */
    fun clear(): CacheCleanResult {
        val before = measure(targets())
        targets().forEach { target ->
            if (target.isDirectory) {
                target.listFiles()?.forEach { it.deleteRecursively() }
            } else {
                target.deleteRecursively()
            }
        }
        val remaining = measure(targets())
        return CacheCleanResult(
            freed = CacheStats(before.files - remaining.files, before.bytes - remaining.bytes),
            remaining = remaining
        )
    }

    private fun targets(): List<File> = (cacheDirectories() + staleTempFiles()).filter { it.exists() }

    private fun cacheDirectories(): List<File> =
        (context.cacheDir?.let { listOf(it) } ?: emptyList()) + context.externalCacheDirs

    /** 变更即写用的临时文件：正常写入会被原子替换掉，只有崩溃时才会残留 */
    private fun staleTempFiles(): List<File> = listOf(File(context.filesDir, PERSISTENCE_TMP_FILE))

    companion object {
        private const val PERSISTENCE_TMP_FILE = "app_data.json.tmp"

        /** 纯函数：递归统计给定目录与文件中的文件数与总大小，不存在的路径按 0 计 */
        fun measure(files: List<File>): CacheStats {
            var count = 0
            var bytes = 0L
            for (root in files) {
                if (root.isFile) {
                    count++
                    bytes += root.length()
                } else if (root.isDirectory) {
                    root.walkBottomUp().forEach { entry ->
                        if (entry.isFile) {
                            count++
                            bytes += entry.length()
                        }
                    }
                }
            }
            return CacheStats(count, bytes)
        }

        /** 字节数转可读文本，供界面与提示复用同一套单位 */
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1 shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
