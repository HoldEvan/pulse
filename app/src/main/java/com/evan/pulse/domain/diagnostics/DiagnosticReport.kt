package com.evan.pulse.domain.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一条失败历史样本，字段已最小化，避免携带请求正文 */
data class DiagnosticHistorySample(
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long,
    val createdAt: Long
)

/** 一条拨测失败样本；message 可能是异常文本，写盘前统一脱敏 */
data class DiagnosticMonitorSample(
    val type: String,
    val targetName: String,
    val message: String,
    val durationMs: Long,
    val checkedAt: Long
)

/** 诊断包原始输入，由调用方从各 Store 采集；敏感脱敏由本类统一完成 */
data class DiagnosticInput(
    val generatedAt: Long,
    val meta: Map<String, String>,
    val settings: Map<String, String>,
    val counts: Map<String, Int>,
    val historyFailures: List<DiagnosticHistorySample> = emptyList(),
    val monitorFailures: List<DiagnosticMonitorSample> = emptyList()
)

/**
 * 把采集到的输入渲染成可外发的诊断 JSON。
 *
 * 输出结构固定为 meta / settings / counts / failures 四段，方便人工比对与
 * 后续脚本解析；所有自由文本都经过 [Redactor]，URL 与头部单独处理。
 */
object DiagnosticReport {

    /** 单次导出最多携带的失败样本条数，控制文件体积 */
    const val MAX_SAMPLES = 20

    const val FILE_NAME = "pulse-diagnostics.json"

    const val MIME_TYPE = "application/json"

    private val json = Json { prettyPrint = true }

    fun build(input: DiagnosticInput): String {
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("generatedAt", input.generatedAt)
            put("meta", input.meta.toJsonObject())
            put("settings", input.settings.toJsonObject())
            put("counts", buildJsonObject { input.counts.forEach { (k, v) -> put(k, v) } })
            put("historyFailures", buildJsonArray {
                input.historyFailures.take(MAX_SAMPLES).forEach { sample ->
                    add(buildJsonObject {
                        put("method", sample.method)
                        put("url", Redactor.maskUrl(sample.url))
                        put("statusCode", sample.statusCode)
                        put("durationMs", sample.durationMs)
                        put("createdAt", sample.createdAt)
                    })
                }
            })
            put("monitorFailures", buildJsonArray {
                input.monitorFailures.take(MAX_SAMPLES).forEach { sample ->
                    add(buildJsonObject {
                        put("type", sample.type)
                        put("targetName", Redactor.maskText(sample.targetName))
                        put("message", Redactor.maskText(sample.message))
                        put("durationMs", sample.durationMs)
                        put("checkedAt", sample.checkedAt)
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** 建议文件名带时间戳，避免多次导出互相覆盖 */
    fun suggestFileName(generatedAt: Long): String {
        val stamp = java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(generatedAt))
        return "pulse-diagnostics-$stamp.json"
    }

    private fun Map<String, String>.toJsonObject(): JsonObject =
        buildJsonObject { forEach { (k, v) -> put(k, Redactor.maskText(v)) } }
}
