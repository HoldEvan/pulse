package com.evan.pulse.domain.runner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 运行报告导出格式 */
enum class RunReportFormat(val mime: String, val extension: String) {
    JSON("application/json", "json"),
    HTML("text/html", "html")
}

/**
 * 集合运行报告导出：把 [CollectionRunner.RunResult] 转成 JSON / HTML。
 *
 * 全部是纯函数，方便单元测试；落盘与分享由 [com.evan.pulse.data.export.ResponseFileStore] 完成。
 */
object RunReportExporter {

    private val prettyJson = Json { prettyPrint = true }

    /** 建议文件名：`run-report_<时间戳>.<后缀>` */
    fun suggestedName(format: RunReportFormat, now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        return "run-report_$stamp.${format.extension}"
    }

    fun render(
        format: RunReportFormat,
        collectionName: String,
        result: CollectionRunner.RunResult,
        generatedAt: Long
    ): String = when (format) {
        RunReportFormat.JSON -> toJson(collectionName, result, generatedAt)
        RunReportFormat.HTML -> toHtml(collectionName, result, generatedAt)
    }

    private fun toJson(
        collectionName: String,
        result: CollectionRunner.RunResult,
        generatedAt: Long
    ): String {
        val root = buildJsonObject {
            put("collection", collectionName)
            put("generatedAt", generatedAt)
            put("passed", result.passed)
            put("failed", result.failed)
            putJsonArray("steps") {
                result.steps.forEach { step ->
                    addJsonObject {
                        put("name", step.name)
                        put("type", step.type.name)
                        put("method", step.method)
                        put("url", step.url)
                        put("statusCode", step.statusCode?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("durationMs", step.durationMs?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("success", step.success)
                        put("attempts", step.attempts)
                        put("error", step.error?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("bodyPreview", step.bodyPreview)
                        putJsonArray("tests") {
                            step.tests.forEach { test ->
                                addJsonObject {
                                    put("name", test.name)
                                    put("passed", test.passed)
                                    put("detail", test.detail)
                                }
                            }
                        }
                    }
                }
            }
        }
        return prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    private fun toHtml(
        collectionName: String,
        result: CollectionRunner.RunResult,
        generatedAt: Long
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(generatedAt))
        val rows = buildString {
            result.steps.forEachIndexed { index, step ->
                val status = step.statusCode?.toString() ?: "-"
                val duration = step.durationMs?.let { "${it}ms" } ?: "-"
                val verdict = if (step.success) "PASS" else "FAIL"
                val rowClass = if (step.success) "pass" else "fail"
                val detail = buildDetail(step)
                append(
                    "<tr class=\"$rowClass\">" +
                        "<td>${index + 1}</td>" +
                        "<td>${escape(step.name)}</td>" +
                        "<td>${escape(step.method)}</td>" +
                        "<td>${escape(status)}</td>" +
                        "<td>${escape(duration)}</td>" +
                        "<td>${step.attempts}</td>" +
                        "<td>${escape(verdict)}</td>" +
                        "<td>${detail}</td>" +
                        "</tr>"
                )
            }
        }
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escape(collectionName)} - 运行报告</title>
            <style>
            body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;margin:24px;color:#1b1b1f;background:#fafafa}
            h1{font-size:20px;margin:0 0 4px}
            .meta{color:#666;font-size:13px;margin-bottom:16px}
            .summary{margin:12px 0 16px;font-size:14px}
            .summary b.pass{color:#1e8e3e}
            .summary b.fail{color:#d93025}
            table{border-collapse:collapse;width:100%;background:#fff;font-size:13px}
            th,td{border:1px solid #e0e0e0;padding:6px 8px;text-align:left;vertical-align:top}
            th{background:#f0f0f3;font-weight:600}
            tr.pass td:first-child{border-left:3px solid #1e8e3e}
            tr.fail td:first-child{border-left:3px solid #d93025}
            tr.fail{background:#fff6f5}
            .err{color:#d93025}
            .test-fail{color:#d93025}
            .test-pass{color:#1e8e3e}
            </style>
            </head>
            <body>
            <h1>${escape(collectionName)}</h1>
            <div class="meta">生成时间：${escape(time)}</div>
            <div class="summary">通过 <b class="pass">${result.passed}</b> 个，失败 <b class="fail">${result.failed}</b> 个，共 ${result.steps.size} 个请求</div>
            <table>
            <thead><tr><th>#</th><th>名称</th><th>方法</th><th>状态</th><th>耗时</th><th>尝试</th><th>结果</th><th>详情</th></tr></thead>
            <tbody>$rows</tbody>
            </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildDetail(step: CollectionRunner.Step): String {
        val parts = ArrayList<String>()
        step.error?.let { parts += "<span class=\"err\">${escape(it)}</span>" }
        step.tests.forEach { test ->
            val cls = if (test.passed) "test-pass" else "test-fail"
            val mark = if (test.passed) "✓" else "✕"
            parts += "<span class=\"$cls\">$mark ${escape(test.name)}</span>"
        }
        return parts.joinToString("<br>")
    }

    private fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
